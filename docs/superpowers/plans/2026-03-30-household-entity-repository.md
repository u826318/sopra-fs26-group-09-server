# Household Entity & Repository Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `Household` and `HouseholdMember` entities with their repositories so the pantry sub-resource has a parent to attach to.

**Architecture:** Two JPA entities (`Household`, `HouseholdMember`) with a separate `@Embeddable` composite PK class (`HouseholdMemberId`). Repositories expose typed query methods needed by the future Service layer. No Service or Controller — data layer only.

**Tech Stack:** Java 17, Spring Boot 4.0, JPA/Hibernate, H2 (test), `@DataJpaTest` for integration tests.

---

## File Map

| Action | Path |
|---|---|
| Create | `src/main/java/ch/uzh/ifi/hase/soprafs26/entity/Household.java` |
| Create | `src/main/java/ch/uzh/ifi/hase/soprafs26/entity/HouseholdMemberId.java` |
| Create | `src/main/java/ch/uzh/ifi/hase/soprafs26/entity/HouseholdMember.java` |
| Create | `src/main/java/ch/uzh/ifi/hase/soprafs26/repository/HouseholdRepository.java` |
| Create | `src/main/java/ch/uzh/ifi/hase/soprafs26/repository/HouseholdMemberRepository.java` |
| Create | `src/test/java/ch/uzh/ifi/hase/soprafs26/repository/HouseholdRepositoryIntegrationTest.java` |
| Create | `src/test/java/ch/uzh/ifi/hase/soprafs26/repository/HouseholdMemberRepositoryIntegrationTest.java` |

---

## Task 1: Household entity and repository

**Files:**
- Create: `src/main/java/ch/uzh/ifi/hase/soprafs26/entity/Household.java`
- Create: `src/main/java/ch/uzh/ifi/hase/soprafs26/repository/HouseholdRepository.java`
- Create: `src/test/java/ch/uzh/ifi/hase/soprafs26/repository/HouseholdRepositoryIntegrationTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/ch/uzh/ifi/hase/soprafs26/repository/HouseholdRepositoryIntegrationTest.java`:

```java
package ch.uzh.ifi.hase.soprafs26.repository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import ch.uzh.ifi.hase.soprafs26.entity.Household;

@DataJpaTest
class HouseholdRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private HouseholdRepository householdRepository;

    @Test
    void saveHousehold_success() {
        Household household = new Household();
        household.setName("Smith Family");
        household.setInviteCode("ABC123");
        household.setOwnerId(1L);

        entityManager.persist(household);
        entityManager.flush();

        Optional<Household> found = householdRepository.findById(household.getId());

        assertTrue(found.isPresent());
        assertNotNull(found.get().getId());
        assertEquals("Smith Family", found.get().getName());
        assertEquals("ABC123", found.get().getInviteCode());
        assertEquals(1L, found.get().getOwnerId());
        assertNotNull(found.get().getCreatedAt());
    }

    @Test
    void findByInviteCode_success() {
        Household household = new Household();
        household.setName("Jones Family");
        household.setInviteCode("XYZ789");
        household.setOwnerId(2L);

        entityManager.persist(household);
        entityManager.flush();

        Optional<Household> found = householdRepository.findByInviteCode("XYZ789");

        assertTrue(found.isPresent());
        assertEquals("Jones Family", found.get().getName());
    }

    @Test
    void findByInviteCode_notFound() {
        Optional<Household> found = householdRepository.findByInviteCode("DOESNOTEXIST");
        assertTrue(found.isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /home/tingyw/projects/sopra-fs26-group-09-server
./gradlew test --tests "ch.uzh.ifi.hase.soprafs26.repository.HouseholdRepositoryIntegrationTest"
```

Expected: compilation error — `Household` and `HouseholdRepository` do not exist yet.

- [ ] **Step 3: Create the Household entity**

Create `src/main/java/ch/uzh/ifi/hase/soprafs26/entity/Household.java`:

```java
package ch.uzh.ifi.hase.soprafs26.entity;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "households")
public class Household implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String inviteCode;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }

    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 4: Create HouseholdRepository**

Create `src/main/java/ch/uzh/ifi/hase/soprafs26/repository/HouseholdRepository.java`:

```java
package ch.uzh.ifi.hase.soprafs26.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ch.uzh.ifi.hase.soprafs26.entity.Household;

@Repository("householdRepository")
public interface HouseholdRepository extends JpaRepository<Household, Long> {
    Optional<Household> findByInviteCode(String inviteCode);
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew test --tests "ch.uzh.ifi.hase.soprafs26.repository.HouseholdRepositoryIntegrationTest"
```

Expected: 3 tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ch/uzh/ifi/hase/soprafs26/entity/Household.java \
        src/main/java/ch/uzh/ifi/hase/soprafs26/repository/HouseholdRepository.java \
        src/test/java/ch/uzh/ifi/hase/soprafs26/repository/HouseholdRepositoryIntegrationTest.java
git commit -m "Add Household entity, repository and integration test #27"
```

---

## Task 2: HouseholdMember entity and repository

**Files:**
- Create: `src/main/java/ch/uzh/ifi/hase/soprafs26/entity/HouseholdMemberId.java`
- Create: `src/main/java/ch/uzh/ifi/hase/soprafs26/entity/HouseholdMember.java`
- Create: `src/main/java/ch/uzh/ifi/hase/soprafs26/repository/HouseholdMemberRepository.java`
- Create: `src/test/java/ch/uzh/ifi/hase/soprafs26/repository/HouseholdMemberRepositoryIntegrationTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/ch/uzh/ifi/hase/soprafs26/repository/HouseholdMemberRepositoryIntegrationTest.java`:

```java
package ch.uzh.ifi.hase.soprafs26.repository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMember;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMemberId;

@DataJpaTest
class HouseholdMemberRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private HouseholdMemberRepository householdMemberRepository;

    @Test
    void saveHouseholdMember_success() {
        HouseholdMemberId memberId = new HouseholdMemberId(10L, 20L);
        HouseholdMember member = new HouseholdMember();
        member.setId(memberId);

        entityManager.persist(member);
        entityManager.flush();

        Optional<HouseholdMember> found = householdMemberRepository.findById(memberId);

        assertTrue(found.isPresent());
        assertEquals(10L, found.get().getId().getUserId());
        assertEquals(20L, found.get().getId().getHouseholdId());
        assertNotNull(found.get().getJoinedAt());
    }

    @Test
    void findByIdUserId_success() {
        HouseholdMemberId memberId = new HouseholdMemberId(11L, 21L);
        HouseholdMember member = new HouseholdMember();
        member.setId(memberId);

        entityManager.persist(member);
        entityManager.flush();

        Optional<HouseholdMember> found = householdMemberRepository.findByIdUserId(11L);

        assertTrue(found.isPresent());
        assertEquals(21L, found.get().getId().getHouseholdId());
    }

    @Test
    void findByIdUserId_notFound() {
        Optional<HouseholdMember> found = householdMemberRepository.findByIdUserId(999L);
        assertTrue(found.isEmpty());
    }

    @Test
    void findByIdHouseholdId_returnsAllMembers() {
        HouseholdMember member1 = new HouseholdMember();
        member1.setId(new HouseholdMemberId(101L, 50L));

        HouseholdMember member2 = new HouseholdMember();
        member2.setId(new HouseholdMemberId(102L, 50L));

        entityManager.persist(member1);
        entityManager.persist(member2);
        entityManager.flush();

        List<HouseholdMember> members = householdMemberRepository.findByIdHouseholdId(50L);

        assertEquals(2, members.size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests "ch.uzh.ifi.hase.soprafs26.repository.HouseholdMemberRepositoryIntegrationTest"
```

Expected: compilation error — `HouseholdMember`, `HouseholdMemberId`, and `HouseholdMemberRepository` do not exist yet.

- [ ] **Step 3: Create HouseholdMemberId (composite PK)**

Create `src/main/java/ch/uzh/ifi/hase/soprafs26/entity/HouseholdMemberId.java`:

```java
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
```

- [ ] **Step 4: Create HouseholdMember entity**

Create `src/main/java/ch/uzh/ifi/hase/soprafs26/entity/HouseholdMember.java`:

```java
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
```

- [ ] **Step 5: Create HouseholdMemberRepository**

Create `src/main/java/ch/uzh/ifi/hase/soprafs26/repository/HouseholdMemberRepository.java`:

```java
package ch.uzh.ifi.hase.soprafs26.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMember;
import ch.uzh.ifi.hase.soprafs26.entity.HouseholdMemberId;

@Repository("householdMemberRepository")
public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, HouseholdMemberId> {
    Optional<HouseholdMember> findByIdUserId(Long userId);
    List<HouseholdMember> findByIdHouseholdId(Long householdId);
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
./gradlew test --tests "ch.uzh.ifi.hase.soprafs26.repository.HouseholdMemberRepositoryIntegrationTest"
```

Expected: 4 tests pass.

- [ ] **Step 7: Run the full test suite to check nothing is broken**

```bash
./gradlew test
```

Expected: all tests pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/ch/uzh/ifi/hase/soprafs26/entity/HouseholdMemberId.java \
        src/main/java/ch/uzh/ifi/hase/soprafs26/entity/HouseholdMember.java \
        src/main/java/ch/uzh/ifi/hase/soprafs26/repository/HouseholdMemberRepository.java \
        src/test/java/ch/uzh/ifi/hase/soprafs26/repository/HouseholdMemberRepositoryIntegrationTest.java
git commit -m "Add HouseholdMember entity, repository and integration test #27"
```
