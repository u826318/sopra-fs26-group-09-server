package ch.uzh.ifi.hase.soprafs26.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ch.uzh.ifi.hase.soprafs26.entity.LifeStageGroup;
import ch.uzh.ifi.hase.soprafs26.entity.UserPersonalProfile;
import ch.uzh.ifi.hase.soprafs26.rest.dto.MicronutrientRequirementGetDTO;
import ch.uzh.ifi.hase.soprafs26.service.reference.MicronutrientReferenceCsvLoader;
import ch.uzh.ifi.hase.soprafs26.service.reference.MicronutrientReferenceRow;

@Service
@Transactional(readOnly = true)
public class MicronutrientReferenceService {

    private static final String RDA_REFERENCE_TYPE = "RDA";
    private static final String AI_REFERENCE_TYPE = "AI";

    private final MicronutrientReferenceCsvLoader csvLoader;
    private final UserPersonalProfileService userPersonalProfileService;

    public MicronutrientReferenceService(
        MicronutrientReferenceCsvLoader csvLoader,
        UserPersonalProfileService userPersonalProfileService
    ) {
        this.csvLoader = csvLoader;
        this.userPersonalProfileService = userPersonalProfileService;
    }

    public List<MicronutrientRequirementGetDTO> getRequirementsForUser(
        Long userId,
        Long authenticatedUserId
    ) {
        UserPersonalProfile profile = userPersonalProfileService.getPersonalProfile(userId, authenticatedUserId);
        int ageInMonths = userPersonalProfileService.calculateAgeInMonths(profile.getBirthDate());

        return findRequirements(profile.getLifeStageGroup(), ageInMonths);
    }

    public List<MicronutrientRequirementGetDTO> findRequirements(
        LifeStageGroup lifeStageGroup,
        int ageInMonths
    ) {
        validateLookupInput(lifeStageGroup, ageInMonths);

        return csvLoader.getRowsForLifeStageGroup(lifeStageGroup).stream()
            .filter(row -> row.getAgeMinMonths() != null)
            .filter(row -> row.getAgeMaxMonths() != null)
            .filter(row -> row.getAgeMinMonths() <= ageInMonths)
            .filter(row -> ageInMonths <= row.getAgeMaxMonths())
            .map(this::toDto)
            .toList();
    }

    private void validateLookupInput(LifeStageGroup lifeStageGroup, int ageInMonths) {
        if (lifeStageGroup == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Life stage group must be provided.");
        }
        if (ageInMonths < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Age in months cannot be negative.");
        }
    }

    private MicronutrientRequirementGetDTO toDto(MicronutrientReferenceRow row) {
        MicronutrientRequirementGetDTO dto = new MicronutrientRequirementGetDTO();

        dto.setStandard(row.getStandard());
        dto.setNutrientKey(row.getNutrientKey());
        dto.setDisplayName(row.getDisplayName());
        dto.setCategory(row.getCategory());
        dto.setLifeStageGroup(row.getLifeStageGroup());
        dto.setAgeMinMonths(row.getAgeMinMonths());
        dto.setAgeMaxMonths(row.getAgeMaxMonths());
        dto.setUnit(row.getUnit());
        dto.setRdaValue(row.getRdaValue());
        dto.setAiValue(row.getAiValue());
        dto.setUpperLimitValue(row.getUlValue());
        dto.setSourceFiles(row.getSourceFiles());

        RecommendedValue recommendedValue = chooseRecommendedValue(row);
        dto.setRecommendedValue(recommendedValue.value());
        dto.setRecommendedReferenceType(recommendedValue.referenceType());

        return dto;
    }

    private RecommendedValue chooseRecommendedValue(MicronutrientReferenceRow row) {
        if (row.getRdaValue() != null) {
            return new RecommendedValue(row.getRdaValue(), RDA_REFERENCE_TYPE);
        }
        if (row.getAiValue() != null) {
            return new RecommendedValue(row.getAiValue(), AI_REFERENCE_TYPE);
        }
        return new RecommendedValue(null, null);
    }

    private record RecommendedValue(BigDecimal value, String referenceType) {
    }
}
