package ch.uzh.ifi.hase.soprafs26.repository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMember;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMemberId;

@DataJpaTest
class HouseholdMemberRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private HouseholdMemberRepository householdMemberRepository;

    @Test
    void saveHouseholdMember_success() {
        HouseholdMemberId memberId = new HouseholdMemberId(10L, 20L);
        HouseholdMember member = new HouseholdMember();
        member.setId(memberId);

        entityManager.persist(member);
        entityManager.flush();

        Optional<HouseholdMember> found = householdMemberRepository.findById(memberId);

        assertTrue(found.isPresent());
        assertEquals(10L, found.get().getId().getUserId());
        assertEquals(20L, found.get().getId().getHouseholdId());
        assertNotNull(found.get().getJoinedAt());
    }

    @Test
    void findByIdUserId_success() {
        HouseholdMemberId memberId = new HouseholdMemberId(11L, 21L);
        HouseholdMember member = new HouseholdMember();
        member.setId(memberId);

        entityManager.persist(member);
        entityManager.flush();

        List<HouseholdMember> found = householdMemberRepository.findByIdUserId(11L);

        assertEquals(1, found.size());
        assertEquals(21L, found.get(0).getId().getHouseholdId());
    }

    @Test
    void findByIdUserId_notFound() {
        List<HouseholdMember> found = householdMemberRepository.findByIdUserId(999L);
        assertTrue(found.isEmpty());
    }

    @Test
    void findByIdHouseholdId_returnsAllMembers() {
        HouseholdMember member1 = new HouseholdMember();
        member1.setId(new HouseholdMemberId(101L, 50L));

        HouseholdMember member2 = new HouseholdMember();
        member2.setId(new HouseholdMemberId(102L, 50L));

        entityManager.persist(member1);
        entityManager.persist(member2);
        entityManager.flush();

        List<HouseholdMember> members = householdMemberRepository.findByIdHouseholdId(50L);

        assertEquals(2, members.size());
    }
}
