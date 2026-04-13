package ch.uzh.ifi.hase.soprafs26.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.entity.Household;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMember;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMemberId;
import ch.uzh.ifi.hase.soprafs26.repository.ConsumptionLogRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdMemberRepository;
import ch.uzh.ifi.hase.soprafs26.repository.HouseholdRepository;
import ch.uzh.ifi.hase.soprafs26.repository.PantryItemRepository;

@Service
@Transactional
public class HouseholdService {

    public record HouseholdAccess(Household household, String role) {
    }

    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 6;
    private static final int INVITE_CODE_MAX_ATTEMPTS = 10;
    private static final Duration INVITE_CODE_TTL = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final PantryItemRepository pantryItemRepository;
    private final ConsumptionLogRepository consumptionLogRepository;

    public HouseholdService(HouseholdRepository householdRepository,
            HouseholdMemberRepository householdMemberRepository,
            PantryItemRepository pantryItemRepository,
            ConsumptionLogRepository consumptionLogRepository) {
        this.householdRepository = householdRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.pantryItemRepository = pantryItemRepository;
        this.consumptionLogRepository = consumptionLogRepository;
    }

    public Household createHousehold(String name, Long ownerId) {
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Household name must not be empty.");
        }

        Household household = new Household();
        household.setId(null);
        household.setName(name.trim());
        household.setOwnerId(ownerId);
        refreshInviteCode(household);
        household = householdRepository.save(household);
        householdRepository.flush();

        HouseholdMemberId memberId = new HouseholdMemberId(ownerId, household.getId());
        HouseholdMember member = new HouseholdMember();
        member.setId(memberId);
        householdMemberRepository.save(member);
        householdMemberRepository.flush();

        return household;
    }

    public List<HouseholdAccess> getHouseholdsForUser(Long requesterUserId) {
        List<HouseholdMember> memberships = householdMemberRepository.findByIdUserId(requesterUserId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        List<Long> householdIds = memberships.stream()
                .map(HouseholdMember::getId)
                .map(HouseholdMemberId::getHouseholdId)
                .distinct()
                .toList();

        Map<Long, Household> householdsById = new LinkedHashMap<>();
        for (Household household : householdRepository.findAllById(householdIds)) {
            householdsById.put(household.getId(), household);
        }

        return householdIds.stream()
                .map(householdsById::get)
                .filter(household -> household != null)
                .map(household -> new HouseholdAccess(household, resolveRole(household, requesterUserId)))
                .toList();
    }

    public HouseholdAccess getHouseholdForUser(Long householdId, Long requesterUserId) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found."));

        HouseholdMemberId membershipId = new HouseholdMemberId(requesterUserId, householdId);
        if (!householdMemberRepository.existsById(membershipId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a member of this household.");
        }

        return new HouseholdAccess(household, resolveRole(household, requesterUserId));
    }

    public void deleteHousehold(Long householdId, Long requesterUserId) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found."));

        if (!household.getOwnerId().equals(requesterUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the household owner can delete this household.");
        }

        consumptionLogRepository.deleteByHouseholdId(householdId);
        pantryItemRepository.deleteByHouseholdId(householdId);
        householdMemberRepository.deleteByIdHouseholdId(householdId);
        householdRepository.delete(household);
        householdRepository.flush();
    }

    public Household regenerateInviteCode(Long householdId, Long requesterUserId) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Household not found."));

        if (!household.getOwnerId().equals(requesterUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the household owner can generate invite codes.");
        }

        refreshInviteCode(household);
        household = householdRepository.save(household);
        householdRepository.flush();
        return household;
    }

    public Household joinHouseholdByInviteCode(String inviteCode, Long requesterUserId) {
        if (inviteCode == null || inviteCode.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invite code must not be empty.");
        }

        String normalizedCode = inviteCode.trim().toUpperCase();
        Household household = householdRepository.findByInviteCode(normalizedCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invite code is invalid."));

        Instant expiresAt = household.getInviteCodeExpiresAt();
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Invite code has expired. Please request a new code.");
        }

        HouseholdMemberId memberId = new HouseholdMemberId(requesterUserId, household.getId());
        if (householdMemberRepository.existsById(memberId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member of this household.");
        }

        HouseholdMember member = new HouseholdMember();
        member.setId(memberId);
        householdMemberRepository.save(member);
        householdMemberRepository.flush();
        return household;
    }

    private String resolveRole(Household household, Long requesterUserId) {
        return household.getOwnerId().equals(requesterUserId) ? "owner" : "member";
    }

    private void refreshInviteCode(Household household) {
        household.setInviteCode(generateUniqueInviteCode());
        household.setInviteCodeExpiresAt(Instant.now().plus(INVITE_CODE_TTL));
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < INVITE_CODE_MAX_ATTEMPTS; attempt++) {
            String code = generateInviteCode();
            if (householdRepository.findByInviteCode(code).isEmpty()) {
                return code;
            }
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate a unique invite code.");
    }

    private String generateInviteCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_CHARS.charAt(RANDOM.nextInt(INVITE_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
