package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.time.LocalDate;

public class UserPersonalProfilePostDTO {

    private LocalDate birthDate;

    public LocalDate getBirthDate() {
        return this.birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }
}
