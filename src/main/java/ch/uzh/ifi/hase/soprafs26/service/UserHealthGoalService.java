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
        goal.setTargetRate(dto.getTargetRate());
        goal.setAge(dto.getAge());
        goal.setSex(dto.getSex());
        goal.setHeight(dto.getHeight());
        goal.setWeight(dto.getWeight());
        goal.setActivityLevel(dto.getActivityLevel());
        goal.setRecommendedDailyCalories(calculate(dto));
        return repository.save(goal);
    }

    static double calculate(UserHealthGoalPutDTO dto) {
        double bmr;
        if ("FEMALE".equals(dto.getSex())) {
            bmr = 10 * dto.getWeight() + 6.25 * dto.getHeight() - 5 * dto.getAge() - 161;
        } else if ("MALE".equals(dto.getSex())) {
            bmr = 10 * dto.getWeight() + 6.25 * dto.getHeight() - 5 * dto.getAge() + 5;
        } else {
            double female = 10 * dto.getWeight() + 6.25 * dto.getHeight() - 5 * dto.getAge() - 161;
            double male = 10 * dto.getWeight() + 6.25 * dto.getHeight() - 5 * dto.getAge() + 5;
            bmr = (female + male) / 2.0;
        }

        double factor = switch (dto.getActivityLevel()) {
            case "SEDENTARY" -> 1.2;
            case "LIGHT" -> 1.375;
            case "MODERATE" -> 1.55;
            case "ACTIVE" -> 1.725;
            case "VERY_ACTIVE" -> 1.9;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid activity level: " + dto.getActivityLevel());
        };

        double tdee = bmr * factor;

        return switch (dto.getGoalType()) {
            case "LOSE_WEIGHT" -> tdee - (dto.getTargetRate() != null ? dto.getTargetRate() * 1000 : 500);
            case "MAINTAIN" -> tdee;
            case "GAIN_MUSCLE" -> tdee + 300;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid goal type: " + dto.getGoalType());
        };
    }
}
