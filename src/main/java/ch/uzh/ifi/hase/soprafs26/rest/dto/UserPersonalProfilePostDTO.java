package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.time.LocalDate;

import ch.uzh.ifi.hase.soprafs26.entity.LifeStageGroup;

public class UserPersonalProfilePostDTO {

    private LocalDate birthDate;
    private LifeStageGroup lifeStageGroup;

    public LocalDate getBirthDate() {
        return this.birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }
    
    public LifeStageGroup getLifeStageGroup() {
        return this.lifeStageGroup;
    }

    public void setLifeStageGroup(LifeStageGroup lifeStageGroup) {
        this.lifeStageGroup = lifeStageGroup;
    }
}
