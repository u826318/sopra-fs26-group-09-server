package ch.uzh.ifi.hase.soprafs26.rest.mapper;

import org.junit.jupiter.api.Test;

import ch.uzh.ifi.hase.soprafs26.constant.UserStatus;
import ch.uzh.ifi.hase.soprafs26.entity.DailyNutrientIntake;
import ch.uzh.ifi.hase.soprafs26.entity.Household;
import ch.uzh.ifi.hase.soprafs26.entity.LifeStageGroup;
import ch.uzh.ifi.hase.soprafs26.entity.PantryItem;
import ch.uzh.ifi.hase.soprafs26.entity.PantryItemMicronutrients;
import ch.uzh.ifi.hase.soprafs26.entity.User;
import ch.uzh.ifi.hase.soprafs26.entity.UserPersonalProfile;
import ch.uzh.ifi.hase.soprafs26.rest.dto.DailyNutrientIntakeGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.HouseholdGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.PantryItemGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserAuthDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPersonalProfileGetDTO;
import ch.uzh.ifi.hase.soprafs26.rest.dto.UserPostDTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DTOMapperTest
 * Tests if the mapping between the internal and the external/API representation
 * works.
 */
class DTOMapperTest {
	@Test
	void testCreateUser_fromUserPostDTO_toUser_success() {
		// create UserPostDTO
		UserPostDTO userPostDTO = new UserPostDTO();
		userPostDTO.setName("name");
		userPostDTO.setUsername("username");
		userPostDTO.setPassword("password123");

		// MAP -> Create user
		User user = DTOMapper.INSTANCE.convertUserPostDTOtoEntity(userPostDTO);

		// check content
		assertEquals(userPostDTO.getName(), user.getName());
		assertEquals(userPostDTO.getUsername(), user.getUsername());
		assertEquals(userPostDTO.getPassword(), user.getPassword());
	}

	@Test
	void testGetUser_fromUser_toUserGetDTO_success() {
		// create User
		User user = new User();
		user.setName("Firstname Lastname");
		user.setUsername("firstname@lastname");
		user.setStatus(UserStatus.OFFLINE);
		user.setToken("1");

		// MAP -> Create UserGetDTO
		UserGetDTO userGetDTO = DTOMapper.INSTANCE.convertEntityToUserGetDTO(user);

		// check content
		assertEquals(user.getId(), userGetDTO.getId());
		assertEquals(user.getName(), userGetDTO.getName());
		assertEquals(user.getUsername(), userGetDTO.getUsername());
		assertEquals(user.getStatus(), userGetDTO.getStatus());
	}

	@Test
	void testAuthUser_fromUser_toUserAuthDTO_success() {
		User user = new User();
		user.setId(4L);
		user.setName("Auth User");
		user.setUsername("auth-user");
		user.setToken("token-123");
		user.setStatus(UserStatus.ONLINE);

		UserAuthDTO userAuthDTO = DTOMapper.INSTANCE.convertEntityToUserAuthDTO(user);

		assertEquals(user.getId(), userAuthDTO.getId());
		assertEquals(user.getName(), userAuthDTO.getName());
		assertEquals(user.getUsername(), userAuthDTO.getUsername());
		assertEquals(user.getToken(), userAuthDTO.getToken());
		assertEquals(user.getStatus(), userAuthDTO.getStatus());
	}

	// -----------------------------------------------------------------------
	// Household mapping
	// -----------------------------------------------------------------------

	@Test
	void testConvertEntityToHouseholdGetDTO_mapsAllFields() {
		Household household = new Household();
		household.setId(7L);
		household.setName("My Household");
		household.setInviteCode("INV-CODE");
		household.setOwnerId(42L);
		Instant now = Instant.now();
		household.setCreatedAt(now);
		Instant expires = now.plusSeconds(3600);
		household.setInviteCodeExpiresAt(expires);

		HouseholdGetDTO dto = DTOMapper.INSTANCE.convertEntityToHouseholdGetDTO(household);

		assertEquals(7L, dto.getHouseholdId());
		assertEquals("My Household", dto.getName());
		assertEquals("INV-CODE", dto.getInviteCode());
		assertEquals(42L, dto.getOwnerId());
		assertEquals(now, dto.getCreatedAt());
		assertEquals(expires, dto.getInviteCodeExpiresAt());
	}

	// -----------------------------------------------------------------------
	// PantryItem mapping — without micronutrients
	// -----------------------------------------------------------------------

	@Test
	void testConvertEntityToPantryItemGetDTO_withoutMicronutrients() {
		PantryItem item = new PantryItem();
		item.setId(1L);
		item.setHouseholdId(2L);
		item.setBarcode("1234567890");
		item.setName("Milk");
		item.setAmount(500.0);
		item.setInitialAmount(1000.0);
		item.setAmountUnit("ml");
		item.setKcalPerPackage(120.0);
		item.setKcalPerServing(60.0);
		Instant addedAt = Instant.now();
		item.setAddedAt(addedAt);
		// no micronutrients set → enrichPantryItemGetDTO guard should fire

		PantryItemGetDTO dto = DTOMapper.INSTANCE.convertEntityToPantryItemGetDTO(item);

		assertEquals(1L, dto.getId());
		assertEquals(2L, dto.getHouseholdId());
		assertEquals("1234567890", dto.getBarcode());
		assertEquals("Milk", dto.getName());
		assertEquals(500.0, dto.getAmount());
		assertEquals(1000.0, dto.getInitialAmount());
		assertEquals("ml", dto.getAmountUnit());
		assertEquals(120.0, dto.getKcalPerPackage());
		assertEquals(60.0, dto.getKcalPerServing());
		assertEquals(addedAt, dto.getAddedAt());
		// no micronutrient fields populated
		assertNull(dto.getNutritionBasisAmount());
		assertNull(dto.getNutritionBasisUnit());
		assertNull(dto.getPackageQuantity());
		assertNull(dto.getServingQuantity());
		assertNull(dto.getAvailableConsumptionUnits());
	}

	// -----------------------------------------------------------------------
	// PantryItem mapping — with micronutrients (g basis, serving + package)
	// -----------------------------------------------------------------------

	@Test
	void testConvertEntityToPantryItemGetDTO_withMicronutrients_includesAllUnits() {
		PantryItemMicronutrients micro = new PantryItemMicronutrients();
		micro.setNutritionBasisAmount(new BigDecimal("100"));
		micro.setNutritionBasisUnit("g");
		micro.setServingQuantityValue(new BigDecimal("30"));
		micro.setServingQuantityUnit("g");
		micro.setPackageQuantityValue(new BigDecimal("500"));
		micro.setPackageQuantityUnit("g");

		PantryItem item = new PantryItem();
		item.setId(5L);
		item.setHouseholdId(3L);
		item.setName("Oats");
		item.setAmount(400.0);
		item.setInitialAmount(500.0);
		item.setAmountUnit("g");
		item.setAddedAt(Instant.now());
		item.setMicronutrients(micro);

		PantryItemGetDTO dto = DTOMapper.INSTANCE.convertEntityToPantryItemGetDTO(item);

		assertEquals(100.0, dto.getNutritionBasisAmount());
		assertEquals("g", dto.getNutritionBasisUnit());
		assertEquals(30.0, dto.getServingQuantity());
		assertEquals("g", dto.getServingQuantityUnit());
		assertEquals(500.0, dto.getPackageQuantity());
		assertEquals("g", dto.getPackageQuantityUnit());

		List<String> units = dto.getAvailableConsumptionUnits();
		assertNotNull(units);
		assertTrue(units.contains("g"));
		assertTrue(units.contains("serving"));
		assertTrue(units.contains("package"));
	}

	// -----------------------------------------------------------------------
	// buildAvailableConsumptionUnits — fallback to amountUnit when no basis
	// -----------------------------------------------------------------------

	@Test
	void testBuildAvailableConsumptionUnits_fallsBackToAmountUnit_whenNoBasis() {
		PantryItemMicronutrients micro = new PantryItemMicronutrients();
		// nutritionBasisAmount is null → hasBasis = false

		PantryItem item = new PantryItem();
		item.setId(9L);
		item.setHouseholdId(1L);
		item.setName("Juice");
		item.setAmount(250.0);
		item.setInitialAmount(250.0);
		item.setAmountUnit("package");
		item.setAddedAt(Instant.now());
		item.setMicronutrients(micro);

		PantryItemGetDTO dto = DTOMapper.INSTANCE.convertEntityToPantryItemGetDTO(item);

		List<String> units = dto.getAvailableConsumptionUnits();
		assertNotNull(units);
		assertEquals(1, units.size());
		assertEquals("package", units.get(0));
	}

	@Test
	void testBuildAvailableConsumptionUnits_mlBasis_servingUnitMismatch_noServingOrPackage() {
		PantryItemMicronutrients micro = new PantryItemMicronutrients();
		micro.setNutritionBasisAmount(new BigDecimal("100"));
		micro.setNutritionBasisUnit("ml");
		// serving unit is "g" (mismatch) — should not add "serving"
		micro.setServingQuantityValue(new BigDecimal("250"));
		micro.setServingQuantityUnit("g");
		// package unit is "ml" but value is null — should not add "package"

		PantryItem item = new PantryItem();
		item.setId(11L);
		item.setHouseholdId(1L);
		item.setName("Soda");
		item.setAmount(1.0);
		item.setInitialAmount(1.0);
		item.setAmountUnit("ml");
		item.setAddedAt(Instant.now());
		item.setMicronutrients(micro);

		PantryItemGetDTO dto = DTOMapper.INSTANCE.convertEntityToPantryItemGetDTO(item);

		List<String> units = dto.getAvailableConsumptionUnits();
		assertNotNull(units);
		assertTrue(units.contains("ml"));
		assertFalse(units.contains("serving"));
		assertFalse(units.contains("package"));
	}

	@Test
	void testBuildAvailableConsumptionUnits_noMicronutrients_nullPantryItem_returnsEmpty() {
		// Direct call to buildAvailableConsumptionUnits with null pantryItem and empty micro
		DTOMapper mapper = DTOMapper.INSTANCE;
		PantryItemMicronutrients micro = new PantryItemMicronutrients();
		// no basis amount set → hasBasis = false, no fallback (pantryItem == null)
		List<String> units = mapper.buildAvailableConsumptionUnits(null, micro);
		assertNotNull(units);
		assertTrue(units.isEmpty());
	}

	// -----------------------------------------------------------------------
	// enrichPantryItemGetDTO — null guards
	// -----------------------------------------------------------------------

	@Test
	void testEnrichPantryItemGetDTO_nullPantryItem_doesNotThrow() {
		// MapStruct calls the @AfterMapping method; when entity is null the guard must exit
		// We verify by calling the method directly
		DTOMapper mapper = DTOMapper.INSTANCE;
		PantryItemGetDTO dto = new PantryItemGetDTO();
		// Should not throw; guard returns early for null pantryItem
		mapper.enrichPantryItemGetDTO(null, dto);
		assertNull(dto.getNutritionBasisUnit());
	}

	@Test
	void testEnrichPantryItemGetDTO_noMicronutrients_doesNotPopulateFields() {
		DTOMapper mapper = DTOMapper.INSTANCE;
		PantryItem item = new PantryItem();
		item.setId(20L);
		item.setHouseholdId(1L);
		item.setName("Eggs");
		item.setAmount(6.0);
		item.setInitialAmount(12.0);
		item.setAmountUnit("package");
		item.setAddedAt(Instant.now());
		// micronutrients == null
		PantryItemGetDTO dto = new PantryItemGetDTO();
		mapper.enrichPantryItemGetDTO(item, dto);
		assertNull(dto.getNutritionBasisUnit());
		assertNull(dto.getAvailableConsumptionUnits());
	}

	// -----------------------------------------------------------------------
	// UserPersonalProfile mapping
	// -----------------------------------------------------------------------

	@Test
	void testConvertEntityToUserPersonalProfileGetDTO_mapsAllFields() {
		User user = new User();
		user.setId(99L);

		UserPersonalProfile profile = new UserPersonalProfile();
		profile.setId(10L);
		profile.setUser(user);
		profile.setBirthDate(LocalDate.of(1990, 5, 20));
		profile.setLifeStageGroup(LifeStageGroup.MALE);

		UserPersonalProfileGetDTO dto = DTOMapper.INSTANCE.convertEntityToUserPersonalProfileGetDTO(profile);

		assertEquals(10L, dto.getId());
		assertEquals(99L, dto.getUserId());
		assertEquals(LocalDate.of(1990, 5, 20), dto.getBirthDate());
		assertEquals(LifeStageGroup.MALE, dto.getLifeStageGroup());
	}

	// -----------------------------------------------------------------------
	// DailyNutrientIntake mapping
	// -----------------------------------------------------------------------

	@Test
	void testConvertEntityToDailyNutrientIntakeGetDTO_mapsBaseFields() {
		DailyNutrientIntake intake = new DailyNutrientIntake();
		intake.setId(55L);
		intake.setUserId(77L);
		intake.setIntakeDate(LocalDate.of(2026, 1, 15));
		intake.setCalcium(new BigDecimal("1000"));
		intake.setIron(new BigDecimal("8"));

		DailyNutrientIntakeGetDTO dto = DTOMapper.INSTANCE.convertEntityToDailyNutrientIntakeGetDTO(intake);

		assertEquals(55L, dto.getId());
		assertEquals(77L, dto.getUserId());
		assertEquals(LocalDate.of(2026, 1, 15), dto.getIntakeDate());
		assertEquals(new BigDecimal("1000"), dto.getCalcium());
		assertEquals(new BigDecimal("8"), dto.getIron());
	}

	// -----------------------------------------------------------------------
	// isPositive / toDouble helpers
	// -----------------------------------------------------------------------

	@Test
	void testIsPositive_returnsFalseForNull() {
		assertFalse(DTOMapper.INSTANCE.isPositive(null));
	}

	@Test
	void testIsPositive_returnsFalseForZero() {
		assertFalse(DTOMapper.INSTANCE.isPositive(BigDecimal.ZERO));
	}

	@Test
	void testIsPositive_returnsTrueForPositive() {
		assertTrue(DTOMapper.INSTANCE.isPositive(new BigDecimal("0.001")));
	}

	@Test
	void testToDouble_returnsNullForNull() {
		assertNull(DTOMapper.INSTANCE.toDouble(null));
	}

	@Test
	void testToDouble_convertsValue() {
		assertEquals(3.14, DTOMapper.INSTANCE.toDouble(new BigDecimal("3.14")), 0.0001);
	}
}
