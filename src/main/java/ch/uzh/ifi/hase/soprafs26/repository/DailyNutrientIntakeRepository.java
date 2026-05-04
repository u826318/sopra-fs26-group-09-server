package ch.uzh.ifi.hase.soprafs26.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ch.uzh.ifi.hase.soprafs26.entity.DailyNutrientIntake;

@Repository("dailyNutrientIntakeRepository")
public interface DailyNutrientIntakeRepository extends JpaRepository<DailyNutrientIntake, Long> {

    Optional<DailyNutrientIntake> findByUserIdAndIntakeDate(Long userId, LocalDate intakeDate);

    void deleteByUserId(Long userId);
}
