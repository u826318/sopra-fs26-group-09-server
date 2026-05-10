package ch.uzh.ifi.hase.soprafs26.service;

import ch.uzh.ifi.hase.soprafs26.entity.UserHealthGoal;
import ch.uzh.ifi.hase.soprafs26.repository.UserHealthGoalRepository;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserHealthGoalPutDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class UserHealthGoalService {

    private final UserHealthGoalRepository repository;

    public UserHealthGoalService(UserHealthGoalRepository repository) {
        this.repository = repository;
    }

    public UserHealthGoal getGoal(Long userId) {
        return repository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Health goal not set."));
    }

    public UserHealthGoal upsertGoal(Long userId, UserHealthGoalPutDTO dto) {
        UserHealthGoal goal = repository.findByUserId(userId).orElse(new UserHealthGoal());
        goal.setUserId(userId);
        goal.setGoalType(dto.getGoalType());
        goal.setTargetWeight(dto.getTargetWeight());
        goal.setWeeksToGoal(dto.getWeeksToGoal());
        // targetRate is a derived field: (currentWeight - targetWeight) / weeks.
        // Only computed when inputs are valid; calculate() below will throw 400 for invalid inputs.
        if ("LOSE_WEIGHT".equals(dto.getGoalType())
                && dto.getTargetWeight() != null && dto.getWeeksToGoal() != null
                && dto.getWeeksToGoal() > 0 && dto.getTargetWeight() < dto.getWeight()) {
            goal.setTargetRate((dto.getWeight() - dto.getTargetWeight()) / dto.getWeeksToGoal());
        } else {
            goal.setTargetRate(null);
        }
        goal.setAge(dto.getAge());
        goal.setSex(dto.getSex());
        goal.setHeight(dto.getHeight());
        goal.setWeight(dto.getWeight());
        goal.setActivityLevel(dto.getActivityLevel());
        goal.setRecommendedDailyCalories(calculate(dto));
        return repository.save(goal);
    }

    static double calculate(UserHealthGoalPutDTO dto) {
        double activityFactor = switch (dto.getActivityLevel()) {
            case "SEDENTARY"   -> 1.2;
            case "LIGHT"       -> 1.375;
            case "MODERATE"    -> 1.55;
            case "ACTIVE"      -> 1.725;
            case "VERY_ACTIVE" -> 1.9;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid activity level: " + dto.getActivityLevel());
        };

        // TDEE at current body weight — used as reference for cap and non-loss goals
        double tdee0 = mifflinBmr(dto.getWeight(), dto.getHeight(), dto.getAge(), dto.getSex())
                * activityFactor;

        return switch (dto.getGoalType()) {
            case "LOSE_WEIGHT" -> {
                // targetWeight and weeksToGoal are required inputs for weight-loss calculation
                if (dto.getTargetWeight() == null || dto.getWeeksToGoal() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "targetWeight and weeksToGoal are required for LOSE_WEIGHT");
                }
                // Guard against inputs that would produce nonsensical or infinite results
                if (dto.getWeeksToGoal() <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "weeksToGoal must be greater than 0");
                }
                if (dto.getTargetWeight() >= dto.getWeight()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "targetWeight must be less than current weight for LOSE_WEIGHT");
                }

                // Hall (2012): use TDEE at the average weight across the loss journey,
                // then apply ~10% metabolic adaptation factor.
                double wAvg = (dto.getWeight() + dto.getTargetWeight()) / 2.0;
                double tdeeAtAvg = mifflinBmr(wAvg, dto.getHeight(), dto.getAge(), dto.getSex())
                        * activityFactor;
                double tdeeAdapted = tdeeAtAvg * 0.90;

                // Energy density of mixed tissue (≈75% fat + 25% lean): 7700 kcal/kg
                double rate = (dto.getWeight() - dto.getTargetWeight()) / dto.getWeeksToGoal();
                double dailyDeficit = rate * 7700.0 / 7.0;

                // Safety: cap deficit at 35% of current TDEE; enforce sex-based calorie floor
                double maxDeficit = tdee0 * 0.35;
                double floor = "MALE".equals(dto.getSex()) ? 1500.0 : 1200.0;

                yield Math.max(
                        Math.max(tdeeAdapted - dailyDeficit, tdee0 - maxDeficit),
                        floor
                );
            }
            case "MAINTAIN"    -> tdee0;
            case "GAIN_MUSCLE" -> tdee0 + 300.0;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid goal type: " + dto.getGoalType());
        };
    }

    // Mifflin-St Jeor BMR. "OTHER" uses the average of male (+5) and female (-161) offsets = -78.
    private static double mifflinBmr(double weight, double height, int age, String sex) {
        double base = 10.0 * weight + 6.25 * height - 5.0 * age;
        return switch (sex) {
            case "FEMALE" -> base - 161.0;
            case "MALE"   -> base + 5.0;
            default       -> base - 78.0;
        };
    }
}
