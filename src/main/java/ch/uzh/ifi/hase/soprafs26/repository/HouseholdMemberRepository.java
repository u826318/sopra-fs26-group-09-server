package ch.uzh.ifi.hase.soprafs26.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMember;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMemberId;

@Repository("householdMemberRepository")
public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, HouseholdMemberId> {
    List<HouseholdMember> findByIdUserId(Long userId);
    List<HouseholdMember> findByIdHouseholdId(Long householdId);
    void deleteByIdHouseholdId(Long householdId);
}