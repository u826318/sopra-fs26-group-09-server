package ch.uzh.ifi.hase.soprafs26.repository;

import ch.uzh.ifi.hase.soprafs26.entity.UserHealthGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserHealthGoalRepository extends JpaRepository<UserHealthGoal, Long> {
    Optional<UserHealthGoal> findByUserId(Long userId);
}
