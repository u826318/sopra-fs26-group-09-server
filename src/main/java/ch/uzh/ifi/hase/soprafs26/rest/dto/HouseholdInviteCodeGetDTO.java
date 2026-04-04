package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class HouseholdInviteCodeGetDTO {

    private Long householdId;
    private String inviteCode;

    public Long getHouseholdId() {
        return householdId;
    }

    public void setHouseholdId(Long householdId) {
        this.householdId = householdId;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }
}
