package ch.uzh.ifi.hase.soprafs26.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.entity.LifeStageGroup;
import ch.uzh.ifi.hase.soprafs26.entity.UserPersonalProfile;
import ch.uzh.ifi.hase.soprafs26.rest.dto.MicronutrientRequirementGetDTO;
import ch.uzh.ifi.hase.soprafs26.service.reference.MicronutrientReferenceCsvLoader;
import ch.uzh.ifi.hase.soprafs26.service.reference.MicronutrientReferenceRow;

class MicronutrientReferenceServiceTest {

    private MicronutrientReferenceCsvLoader csvLoader;
    private UserPersonalProfileService userPersonalProfileService;
    private MicronutrientReferenceService service;

    @BeforeEach
    void setUp() {
        csvLoader = mock(MicronutrientReferenceCsvLoader.class);
        userPersonalProfileService = mock(UserPersonalProfileService.class);
        service = new MicronutrientReferenceService(csvLoader, userPersonalProfileService);
    }

    @Test
    void findRequirements_filtersByLifeStageAndAgeAndPrefersRdaValue() {
        MicronutrientReferenceRow matching = row("iron", 216, 600, "8", "18", "45");
        MicronutrientReferenceRow tooYoung = row("calcium", 0, 215, "1000", null, null);
        MicronutrientReferenceRow missingMinimumAge = row("zinc", null, 600, "11", null, null);
        MicronutrientReferenceRow missingMaximumAge = row("iodine", 216, null, "150", null, null);

        when(csvLoader.getRowsForLifeStageGroup(LifeStageGroup.FEMALE))
                .thenReturn(List.of(matching, tooYoung, missingMinimumAge, missingMaximumAge));

        List<MicronutrientRequirementGetDTO> result = service.findRequirements(LifeStageGroup.FEMALE, 300);

        assertEquals(1, result.size());
        MicronutrientRequirementGetDTO dto = result.get(0);
        assertEquals("iron", dto.getNutrientKey());
        assertEquals("Iron", dto.getDisplayName());
        assertEquals("mineral", dto.getCategory());
        assertEquals(LifeStageGroup.FEMALE, dto.getLifeStageGroup());
        assertEquals(216, dto.getAgeMinMonths());
        assertEquals(600, dto.getAgeMaxMonths());
        assertEquals("mcg", dto.getUnit());
        assertBigDecimalEquals("8", dto.getRdaValue());
        assertBigDecimalEquals("18", dto.getAiValue());
        assertBigDecimalEquals("45", dto.getUpperLimitValue());
        assertBigDecimalEquals("8", dto.getRecommendedValue());
        assertEquals("RDA", dto.getRecommendedReferenceType());
        assertEquals("source.csv", dto.getSourceFiles());
    }

    @Test
    void findRequirements_usesAiWhenRdaIsMissing() {
        when(csvLoader.getRowsForLifeStageGroup(LifeStageGroup.CHILD))
                .thenReturn(List.of(row("vitamin-d", 12, 36, null, "15", null)));

        List<MicronutrientRequirementGetDTO> result = service.findRequirements(LifeStageGroup.CHILD, 24);

        assertEquals(1, result.size());
        assertNull(result.get(0).getRdaValue());
        assertBigDecimalEquals("15", result.get(0).getRecommendedValue());
        assertEquals("AI", result.get(0).getRecommendedReferenceType());
    }

    @Test
    void findRequirements_leavesRecommendedValueEmptyWhenRdaAndAiAreMissing() {
        when(csvLoader.getRowsForLifeStageGroup(LifeStageGroup.MALE))
                .thenReturn(List.of(row("sodium", 216, 600, null, null, "2300")));

        List<MicronutrientRequirementGetDTO> result = service.findRequirements(LifeStageGroup.MALE, 300);

        assertEquals(1, result.size());
        assertNull(result.get(0).getRecommendedValue());
        assertNull(result.get(0).getRecommendedReferenceType());
    }

    @Test
    void findRequirements_rejectsInvalidLookupInput() {
        ResponseStatusException missingLifeStage = assertThrows(
                ResponseStatusException.class,
                () -> service.findRequirements(null, 24)
        );
        ResponseStatusException negativeAge = assertThrows(
                ResponseStatusException.class,
                () -> service.findRequirements(LifeStageGroup.CHILD, -1)
        );

        assertEquals(400, missingLifeStage.getStatusCode().value());
        assertEquals(400, negativeAge.getStatusCode().value());
    }

    @Test
    void getRequirementsForUser_usesAuthenticatedProfileAgeAndLifeStage() {
        UserPersonalProfile profile = new UserPersonalProfile();
        profile.setBirthDate(LocalDate.of(2000, 1, 1));
        profile.setLifeStageGroup(LifeStageGroup.MALE);

        when(userPersonalProfileService.getPersonalProfile(10L, 10L)).thenReturn(profile);
        when(userPersonalProfileService.calculateAgeInMonths(profile.getBirthDate())).thenReturn(300);
        when(csvLoader.getRowsForLifeStageGroup(LifeStageGroup.MALE))
                .thenReturn(List.of(row("zinc", 216, 600, "11", null, null)));

        List<MicronutrientRequirementGetDTO> result = service.getRequirementsForUser(10L, 10L);

        assertEquals(1, result.size());
        assertEquals("zinc", result.get(0).getNutrientKey());
    }

    private MicronutrientReferenceRow row(
            String nutrientKey,
            Integer ageMinMonths,
            Integer ageMaxMonths,
            String rdaValue,
            String aiValue,
            String ulValue
    ) {
        MicronutrientReferenceRow row = new MicronutrientReferenceRow();
        row.setStandard("DRI");
        row.setNutrientKey(nutrientKey);
        row.setDisplayName(toDisplayName(nutrientKey));
        row.setCategory("mineral");
        row.setLifeStageGroup(LifeStageGroup.FEMALE);
        row.setAgeMinMonths(ageMinMonths);
        row.setAgeMaxMonths(ageMaxMonths);
        row.setUnit("mcg");
        row.setRdaValue(decimalOrNull(rdaValue));
        row.setAiValue(decimalOrNull(aiValue));
        row.setUlValue(decimalOrNull(ulValue));
        row.setSourceFiles("source.csv");
        return row;
    }

    private String toDisplayName(String nutrientKey) {
        return nutrientKey.substring(0, 1).toUpperCase() + nutrientKey.substring(1);
    }

    private BigDecimal decimalOrNull(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private void assertBigDecimalEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
