package ch.uzh.ifi.hase.soprafs26.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Embeddable;

@Embeddable
public class HouseholdMemberId implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private Long householdId;

    public HouseholdMemberId() {}

    public HouseholdMemberId(Long userId, Long householdId) {
        this.userId = userId;
        this.householdId = householdId;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getHouseholdId() { return householdId; }
    public void setHouseholdId(Long householdId) { this.householdId = householdId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HouseholdMemberId)) return false;
        HouseholdMemberId that = (HouseholdMemberId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(householdId, that.householdId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, householdId);
    }
}
