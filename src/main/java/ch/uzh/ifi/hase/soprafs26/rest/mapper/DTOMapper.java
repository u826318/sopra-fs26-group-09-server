package ch.uzh.ifi.hase.soprafs26.rest.mapper;

import org.mapstruct.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.mapstruct.factory.Mappers;

import ch.uzh.ifi.hase.soprafs26.entity.Household;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;
import ch.uzh.ifi.hase.soprafs26.entity.PantryItemMicronutrients;
import ch.uzh.ifi.hase.soprafs26.entity.UserPersonalProfile;
import ch.uzh.ifi.hase.soprafs26.entity.DailyNutrientIntake;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserAuthDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPostDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PantryItemGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPersonalProfileGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.DailyNutrientIntakeGetDTO;

/**
 * DTOMapper
 * This class is responsible for generating classes that will automatically
 * transform/map the internal representation
 * of an entity (e.g., the User) to the external/API representation (e.g.,
 * UserGetDTO for getting, UserPostDTO for creating)
 * and vice versa.
 * Additional mappers can be defined for new entities.
 * Always created one mapper for getting information (GET) and one mapper for
 * creating information (POST).
 */
@Mapper
public interface DTOMapper {

	DTOMapper INSTANCE = Mappers.getMapper(DTOMapper.class);

	@Mapping(source = "name", target = "name")
	@Mapping(source = "username", target = "username")
	@Mapping(source = "password", target = "password")
	User convertUserPostDTOtoEntity(UserPostDTO userPostDTO);

	@Mapping(source = "id", target = "id")
	@Mapping(source = "name", target = "name")
	@Mapping(source = "username", target = "username")
	@Mapping(source = "status", target = "status")
	UserGetDTO convertEntityToUserGetDTO(User user);

	@Mapping(source = "id", target = "id")
	@Mapping(source = "name", target = "name")
	@Mapping(source = "username", target = "username")
	@Mapping(source = "token", target = "token")
	@Mapping(source = "status", target = "status")
	UserAuthDTO convertEntityToUserAuthDTO(User user);

	@Mapping(source = "id", target = "householdId")
	@Mapping(source = "name", target = "name")
	@Mapping(source = "inviteCode", target = "inviteCode")
	@Mapping(source = "ownerId", target = "ownerId")
	@Mapping(source = "createdAt", target = "createdAt")
	@Mapping(source = "inviteCodeExpiresAt", target = "inviteCodeExpiresAt")
	HouseholdGetDTO convertEntityToHouseholdGetDTO(Household household);

	@Mapping(source = "id", target = "id")
	@Mapping(source = "householdId", target = "householdId")
	@Mapping(source = "barcode", target = "barcode")
	@Mapping(source = "name", target = "name")
	@Mapping(source = "kcalPerPackage", target = "kcalPerPackage")
	// Issue #114 — map new amount/unit fields; kcal fields have matching names so no explicit mapping needed
	@Mapping(source = "amount", target = "amount")
	@Mapping(source = "initialAmount", target = "initialAmount")
	@Mapping(source = "amountUnit", target = "amountUnit")
	@Mapping(source = "addedAt", target = "addedAt")
	@Mapping(target = "nutritionBasisAmount", ignore = true)
	@Mapping(target = "nutritionBasisUnit", ignore = true)
	@Mapping(target = "packageQuantity", ignore = true)
	@Mapping(target = "packageQuantityUnit", ignore = true)
	@Mapping(target = "servingQuantity", ignore = true)
	@Mapping(target = "servingQuantityUnit", ignore = true)
	@Mapping(target = "availableConsumptionUnits", ignore = true)
	PantryItemGetDTO convertEntityToPantryItemGetDTO(PantryItem pantryItem);

	@AfterMapping
	default void enrichPantryItemGetDTO(PantryItem pantryItem, @MappingTarget PantryItemGetDTO dto) {
		if (pantryItem == null || dto == null || pantryItem.getMicronutrients() == null) {
			return;
		}

		PantryItemMicronutrients micronutrients = pantryItem.getMicronutrients();
		dto.setNutritionBasisAmount(toDouble(micronutrients.getNutritionBasisAmount()));
		dto.setNutritionBasisUnit(micronutrients.getNutritionBasisUnit());
		dto.setPackageQuantity(toDouble(micronutrients.getPackageQuantityValue()));
		dto.setPackageQuantityUnit(micronutrients.getPackageQuantityUnit());
		dto.setServingQuantity(toDouble(micronutrients.getServingQuantityValue()));
		dto.setServingQuantityUnit(micronutrients.getServingQuantityUnit());
		dto.setAvailableConsumptionUnits(buildAvailableConsumptionUnits(pantryItem, micronutrients));
	}

	default List<String> buildAvailableConsumptionUnits(PantryItem pantryItem, PantryItemMicronutrients micronutrients) {
		List<String> units = new ArrayList<>();
		String basisUnit = micronutrients.getNutritionBasisUnit();
		BigDecimal basisAmount = micronutrients.getNutritionBasisAmount();
		boolean hasBasis = isPositive(basisAmount) && ("g".equals(basisUnit) || "ml".equals(basisUnit));

		if (hasBasis) {
			units.add(basisUnit);
		}
		if (hasBasis
				&& isPositive(micronutrients.getServingQuantityValue())
				&& basisUnit.equals(micronutrients.getServingQuantityUnit())) {
			units.add("serving");
		}
		if (hasBasis
				&& isPositive(micronutrients.getPackageQuantityValue())
				&& basisUnit.equals(micronutrients.getPackageQuantityUnit())) {
			units.add("package");
		}
		if (units.isEmpty() && pantryItem != null && pantryItem.getAmountUnit() != null) {
			units.add(pantryItem.getAmountUnit());
		}
		return units;
	}

	default boolean isPositive(BigDecimal value) {
		return value != null && value.compareTo(BigDecimal.ZERO) > 0;
	}

	default Double toDouble(BigDecimal value) {
		return value == null ? null : value.doubleValue();
	}
	
	@Mapping(source = "id", target = "id")
	@Mapping(source = "user.id", target = "userId")
	@Mapping(source = "birthDate", target = "birthDate")
	@Mapping(source = "lifeStageGroup", target = "lifeStageGroup")
	UserPersonalProfileGetDTO convertEntityToUserPersonalProfileGetDTO(UserPersonalProfile userPersonalProfile);

	@Mapping(source = "id", target = "id")
	@Mapping(source = "userId", target = "userId")
	@Mapping(source = "intakeDate", target = "intakeDate")
	DailyNutrientIntakeGetDTO convertEntityToDailyNutrientIntakeGetDTO(DailyNutrientIntake dailyNutrientIntake);
}
