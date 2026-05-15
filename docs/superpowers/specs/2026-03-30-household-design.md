# Household Entity & Repository — Design Spec

**Date:** 2026-03-30
**Scope:** `Household` entity, `HouseholdMember` entity, and their repositories

---

## 1. Context

The pantry is a sub-resource of a Household (`/households/{id}/pantry`). Before pantry endpoints can be built, the Household data layer must exist. This spec covers the entity and repository layer only — no Service or Controller.

---

## 2. Business Rules

- One user can belong to **at most one household at a time** (enforced in the Service layer, not the DB).
- Households are identified by a **unique 6-character invite code** for joining.
- The user who creates a household is the **owner** (`ownerId`).
- A member can leave a household; the owner cannot leave (must delete the household instead) — enforced in Service layer.

---

## 3. Entities

### 3.1 Household

**Table:** `households`

| Field | Type | Column constraint | Notes |
|---|---|---|---|
| `id` | `Long` | PK, auto-generated | |
| `name` | `String` | NOT NULL | Display name of the household |
| `inviteCode` | `String` | NOT NULL, UNIQUE | 6-char random code; generated in Service layer |
| `ownerId` | `Long` | NOT NULL | FK → `users.id`; stored as raw Long, not `@ManyToOne` |
| `createdAt` | `Instant` | NOT NULL, not updatable | Set via `@PrePersist` |

```java
@Entity
@Table(name = "households")
public class Household implements Serializable {
    @Id @GeneratedValue
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

    // getters and setters
}
```

### 3.2 HouseholdMember

**Table:** `household_members`

Composite primary key (`userId` + `householdId`) via `@Embeddable` + `@EmbeddedId`.

| Field | Type | Column constraint | Notes |
|---|---|---|---|
| `userId` | `Long` | PK (composite) | FK → `users.id` |
| `householdId` | `Long` | PK (composite) | FK → `households.id` |
| `joinedAt` | `Instant` | NOT NULL, not updatable | Set via `@PrePersist` |

```java
@Embeddable
public class HouseholdMemberId implements Serializable {
    private Long userId;
    private Long householdId;
    // equals, hashCode, getters, setters
}

@Entity
@Table(name = "household_members")
public class HouseholdMember implements Serializable {
    @EmbeddedId
    private HouseholdMemberId id;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist
    private void prePersist() {
        this.joinedAt = Instant.now();
    }

    // getters and setters
}
```

---

## 4. Repositories

### 4.1 HouseholdRepository

```java
@Repository
public interface HouseholdRepository extends JpaRepository<Household, Long> {
    Optional<Household> findByInviteCode(String inviteCode);
}
```

`findByInviteCode` is needed when a user joins via invite code.

### 4.2 HouseholdMemberRepository

```java
@Repository
public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, HouseholdMemberId> {
    Optional<HouseholdMember> findByIdUserId(Long userId);
    List<HouseholdMember> findByIdHouseholdId(Long householdId);
}
```

- `findByIdUserId` — check if a user already belongs to a household (enforces one-household rule)
- `findByIdHouseholdId` — list all members of a household

---

## 5. Design Decisions

| Decision | Rationale |
|---|---|
| FK stored as `Long`, not `@ManyToOne` | Consistent with existing `User` and `PantryItem` entities in this codebase |
| `Instant` instead of `LocalDateTime` | Consistent with existing entities; timezone-safe |
| Composite PK via `@EmbeddedId` | Natural key for a join table; avoids a surrogate PK that adds no value |
| One-household rule in Service, not DB | DB constraint would be complex (partial unique index); Service layer is simpler and sufficient |

---

## 6. Out of Scope

- `inviteCode` generation logic — belongs in Service layer
- Household create/join/leave endpoints — separate issues (#28, #29, #30)
- Relationship to `PantryItem` — `PantryItem` will add `householdId` FK when that entity is updated
