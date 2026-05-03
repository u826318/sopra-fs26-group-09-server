package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.time.LocalDate;

import ch.uzh.ifi.hase.soprafs26.entity.LifeStageGroup;

public class UserPersonalProfileGetDTO {

    private Long id;
    private Long userId;
    private LocalDate birthDate;
    private LifeStageGroup lifeStageGroup;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public LifeStageGroup getLifeStageGroup() {
        return lifeStageGroup;
    }

    public void setLifeStageGroup(LifeStageGroup lifeStageGroup) {
        this.lifeStageGroup = lifeStageGroup;
    }
    
}
