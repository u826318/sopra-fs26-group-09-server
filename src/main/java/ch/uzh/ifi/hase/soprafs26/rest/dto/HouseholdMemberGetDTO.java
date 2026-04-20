package ch.uzh.ifi.hase.soprafs26.rest.dto;

import java.time.Instant;

public class HouseholdMemberGetDTO {

    private Long userId;
    private String username;
    private String role;
    private Instant joinedAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
}
