package ch.uzh.ifi.hase.soprafs26.entity;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "household_members")
public class HouseholdMember implements Serializable {

    private static final long serialVersionUID = 1L;

    @EmbeddedId
    private HouseholdMemberId id;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist
    private void prePersist() {
        this.joinedAt = Instant.now();
    }

    public HouseholdMemberId getId() { return id; }
    public void setId(HouseholdMemberId id) { this.id = id; }

    public Instant getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Instant joinedAt) { this.joinedAt = joinedAt; }
}
