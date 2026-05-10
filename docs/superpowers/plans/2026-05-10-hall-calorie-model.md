# Hall Model Calorie Recommendation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the simplistic `TDEE - targetRate * 1000` formula with a Hall-model-inspired calorie recommendation that uses average-weight TDEE, metabolic adaptation (×0.90), correct energy density (7700 kcal/kg), a 35%-of-TDEE deficit cap, and a sex-based calorie floor.

**Architecture:** Add `targetWeight` and `weeksToGoal` to the PUT DTO (removing `targetRate` as an input — it becomes a backend-derived field). Store both fields on the entity so the frontend can restore the form on reload. Rewrite `UserHealthGoalService.calculate()` with the new algorithm. All MAINTAIN/GAIN_MUSCLE paths are unchanged.

**Tech Stack:** Java 21 / Spring Boot (backend), TypeScript / Next.js / Ant Design (frontend). Tests: JUnit 5 + Mockito (backend), Jest + React Testing Library (frontend). Build: Maven (backend), npm (frontend).

**Issue references:** backend commits → `#129`; frontend commits → `#97`.

**Add comments** to every code change as noted in the steps below.

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `server/src/main/java/.../rest/dto/UserHealthGoalPutDTO.java` | Modify | PUT request shape: remove targetRate, add targetWeight + weeksToGoal |
| `server/src/main/java/.../rest/dto/UserHealthGoalGetDTO.java` | Modify | GET response shape: add targetWeight + weeksToGoal |
| `server/src/main/java/.../entity/UserHealthGoal.java` | Modify | DB columns: add targetWeight + weeksToGoal |
| `server/src/main/java/.../controller/UserController.java` | Modify | Mapper: copy new fields from entity → GetDTO |
| `server/src/main/java/.../service/UserHealthGoalService.java` | Modify | Hall model algorithm + upsertGoal stores new fields |
| `server/src/test/java/.../service/UserHealthGoalServiceTest.java` | Modify | Rewrite LOSE_WEIGHT test + add Hall / validation tests |
| `server/src/test/java/.../controller/UserHealthGoalControllerTest.java` | Modify | PUT body uses targetWeight/weeksToGoal instead of targetRate |
| `client/app/types/healthGoal.ts` | Modify | TS types for both interfaces |
| `client/app/users/[id]/health-goal/page.tsx` | Modify | Send new fields; restore on load |
| `client/app/users/[id]/health-goal/page.test.tsx` | Modify | Update mocks to new response shape |

---

## Task 1: Backend — Structural Changes (DTO, Entity, GetDTO, Controller mapper, upsertGoal)

These changes make the codebase compile with the new field names. The algorithm in `calculate()` is NOT changed yet — that comes in Task 3.

**Files:**
- Modify: `src/main/java/ch/uzh/ifi/hase/soprafs26/rest/dto/UserHealthGoalPutDTO.java`
- Modify: `src/main/java/ch/uzh/ifi/hase/soprafs26/rest/dto/UserHealthGoalGetDTO.java`
- Modify: `src/main/java/ch/uzh/ifi/hase/soprafs26/entity/UserHealthGoal.java`
- Modify: `src/main/java/ch/uzh/ifi/hase/soprafs26/controller/UserController.java`
- Modify: `src/main/java/ch/uzh/ifi/hase/soprafs26/service/UserHealthGoalService.java`

- [ ] **Step 1.1: Replace `UserHealthGoalPutDTO`**

Replace the entire contents of `UserHealthGoalPutDTO.java` with:

```java
package ch.uzh.ifi.hase.soprafs26.rest.dto;

public class UserHealthGoalPutDTO {

    private String goalType;
    // targetRate removed — backend derives it from targetWeight and weeksToGoal
    private Double targetWeight;  // kg; required when goalType == LOSE_WEIGHT
    private Integer weeksToGoal;  // required when goalType == LOSE_WEIGHT
    private Integer age;
    private String sex;
    private Double height;
    private Double weight;
    private String activityLevel;

    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }

    public Double getTargetWeight() { return targetWeight; }
    public void setTargetWeight(Double targetWeight) { this.targetWeight = targetWeight; }

    public Integer getWeeksToGoal() { return weeksToGoal; }
    public void setWeeksToGoal(Integer weeksToGoal) { this.weeksToGoal = weeksToGoal; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getSex() { return sex; }
    public void setSex(String sex) { this.sex = sex; }

    public Double getHeight() { return height; }
    public void setHeight(Double height) { this.height = height; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }

    public String getActivityLevel() { return activityLevel; }
    public void setActivityLevel(String activityLevel) { this.activityLevel = activityLevel; }
}
```

- [ ] **Step 1.2: Add `targetWeight` and `weeksToGoal` to `UserHealthGoalGetDTO`**

Add the two fields, their getters and setters, immediately after the `targetRate` block (keep `targetRate` — it is still returned in the response):

```java
    // targetWeight and weeksToGoal are stored so the frontend can restore the form on reload
    private Double targetWeight;
    private Integer weeksToGoal;

    public Double getTargetWeight() { return targetWeight; }
    public void setTargetWeight(Double targetWeight) { this.targetWeight = targetWeight; }

    public Integer getWeeksToGoal() { return weeksToGoal; }
    public void setWeeksToGoal(Integer weeksToGoal) { this.weeksToGoal = weeksToGoal; }
```

- [ ] **Step 1.3: Add `targetWeight` and `weeksToGoal` columns to `UserHealthGoal` entity**

Add after the existing `targetRate` field:

```java
    // Stored so the form can be restored when the user revisits the page
    @Column
    private Double targetWeight;

    @Column
    private Integer weeksToGoal;

    public Double getTargetWeight() { return targetWeight; }
    public void setTargetWeight(Double targetWeight) { this.targetWeight = targetWeight; }

    public Integer getWeeksToGoal() { return weeksToGoal; }
    public void setWeeksToGoal(Integer weeksToGoal) { this.weeksToGoal = weeksToGoal; }
```

- [ ] **Step 1.4: Update `toHealthGoalGetDTO` mapper in `UserController`**

Add two lines to the existing `toHealthGoalGetDTO` method, after `dto.setTargetRate(...)`:

```java
        dto.setTargetWeight(goal.getTargetWeight());
        dto.setWeeksToGoal(goal.getWeeksToGoal());
```

- [ ] **Step 1.5: Update `upsertGoal` in `UserHealthGoalService`**

Replace the entire `upsertGoal` method:

```java
    public UserHealthGoal upsertGoal(Long userId, UserHealthGoalPutDTO dto) {
        UserHealthGoal goal = repository.findByUserId(userId).orElse(new UserHealthGoal());
        goal.setUserId(userId);
        goal.setGoalType(dto.getGoalType());
        goal.setTargetWeight(dto.getTargetWeight());
        goal.setWeeksToGoal(dto.getWeeksToGoal());
        // targetRate is a derived field: (currentWeight - targetWeight) / weeks
        if ("LOSE_WEIGHT".equals(dto.getGoalType())
                && dto.getTargetWeight() != null && dto.getWeeksToGoal() != null) {
            goal.setTargetRate((dto.getWeight() - dto.getTargetWeight()) / dto.getWeeksToGoal());
        } else {
            goal.setTargetRate(null);
        }
        goal.setAge(dto.getAge());
        goal.setSex(dto.getSex());
        goal.setHeight(dto.getHeight());
        goal.setWeight(dto.getWeight());
        goal.setActivityLevel(dto.getActivityLevel());
        goal.setRecommendedDailyCalories(calculate(dto));
        return repository.save(goal);
    }
```

- [ ] **Step 1.6: Verify the project compiles**

Run from `sopra-fs26-group-09-server/`:
```bash
./mvnw compile -q
```
Expected: BUILD SUCCESS with no errors. Fix any compilation errors before continuing.

- [ ] **Step 1.7: Commit structural changes**

```bash
git add src/main/java/ch/uzh/ifi/hase/soprafs26/rest/dto/UserHealthGoalPutDTO.java \
        src/main/java/ch/uzh/ifi/hase/soprafs26/rest/dto/UserHealthGoalGetDTO.java \
        src/main/java/ch/uzh/ifi/hase/soprafs26/entity/UserHealthGoal.java \
        src/main/java/ch/uzh/ifi/hase/soprafs26/controller/UserController.java \
        src/main/java/ch/uzh/ifi/hase/soprafs26/service/UserHealthGoalService.java
git commit -m "feat(#129): add targetWeight and weeksToGoal to health goal DTO and entity"
```

---

## Task 2: Backend — Write Failing Service Tests

Write all tests for the Hall model BEFORE implementing the new algorithm. They will compile (the DTO now has the right fields) but the LOSE_WEIGHT assertions will fail because `calculate()` still uses the old formula.

**Files:**
- Modify: `src/test/java/ch/uzh/ifi/hase/soprafs26/service/UserHealthGoalServiceTest.java`

- [ ] **Step 2.1: Replace `buildDto` helper to use `targetWeight` + `weeksToGoal`**

Find the existing `buildDto` helper at the bottom of the test class and replace it:

```java
    private UserHealthGoalPutDTO buildDto(String sex, int age, double height, double weight,
                                          String activity, String goalType,
                                          Double targetWeight, Integer weeksToGoal) {
        UserHealthGoalPutDTO dto = new UserHealthGoalPutDTO();
        dto.setSex(sex);
        dto.setAge(age);
        dto.setHeight(height);
        dto.setWeight(weight);
        dto.setActivityLevel(activity);
        dto.setGoalType(goalType);
        dto.setTargetWeight(targetWeight);
        dto.setWeeksToGoal(weeksToGoal);
        return dto;
    }
```

- [ ] **Step 2.2: Update existing tests to use the new `buildDto` signature**

For MAINTAIN, GAIN_MUSCLE, and OTHER tests, pass `null, null` for the last two params:

```java
    @Test
    void calculate_maleMaintain() {
        UserHealthGoalPutDTO dto = buildDto("MALE", 30, 180.0, 80.0, "ACTIVE", "MAINTAIN", null, null);
        // BMR = 10*80 + 6.25*180 - 5*30 + 5 = 1780.0
        // TDEE = 1780.0 * 1.725 = 3070.5
        assertEquals(3070.5, UserHealthGoalService.calculate(dto), 0.1);
    }

    @Test
    void calculate_gainMuscle_addThreeHundred() {
        UserHealthGoalPutDTO dto = buildDto("MALE", 25, 175.0, 70.0, "LIGHT", "GAIN_MUSCLE", null, null);
        // BMR = 10*70 + 6.25*175 - 5*25 + 5 = 1673.75
        // TDEE = 1673.75 * 1.375 = 2301.40625; +300 = 2601.40625
        assertEquals(2601.4, UserHealthGoalService.calculate(dto), 0.1);
    }

    @Test
    void calculate_otherSex_averageOfMaleAndFemale() {
        UserHealthGoalPutDTO dto = buildDto("OTHER", 28, 165.0, 62.0, "SEDENTARY", "MAINTAIN", null, null);
        // OTHER BMR = average of female (-161) and male (+5) offsets = base - 78
        double base = 10 * 62 + 6.25 * 165 - 5 * 28; // = 1511.25
        double expected = (base - 78.0) * 1.2;        // = 1433.25 * 1.2 = 1719.9
        assertEquals(expected, UserHealthGoalService.calculate(dto), 0.1);
    }
```

Also update the `upsertGoal` tests:

```java
    @Test
    void upsertGoal_createsNewWhenNotExists() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 28, 165.0, 62.0, "MODERATE", "MAINTAIN", null, null);
        when(repository.findByUserId(1L)).thenReturn(Optional.empty());

        UserHealthGoal saved = new UserHealthGoal();
        saved.setGoalId(1L);
        saved.setUserId(1L);
        saved.setGoalType("MAINTAIN");
        saved.setRecommendedDailyCalories(2092.89);
        when(repository.save(any())).thenReturn(saved);

        UserHealthGoal result = service.upsertGoal(1L, dto);
        assertEquals(1L, result.getUserId());
        assertEquals("MAINTAIN", result.getGoalType());
    }

    @Test
    void upsertGoal_updatesExisting() {
        UserHealthGoalPutDTO dto = buildDto("MALE", 30, 180.0, 80.0, "ACTIVE", "MAINTAIN", null, null);

        UserHealthGoal existing = new UserHealthGoal();
        existing.setGoalId(5L);
        existing.setUserId(2L);
        existing.setGoalType("LOSE_WEIGHT");
        when(repository.findByUserId(2L)).thenReturn(Optional.of(existing));

        UserHealthGoal updated = new UserHealthGoal();
        updated.setGoalId(5L);
        updated.setUserId(2L);
        updated.setGoalType("MAINTAIN");
        updated.setRecommendedDailyCalories(3070.5);
        when(repository.save(any())).thenReturn(updated);

        UserHealthGoal result = service.upsertGoal(2L, dto);
        assertEquals("MAINTAIN", result.getGoalType());
    }
```

- [ ] **Step 2.3: Replace and add LOSE_WEIGHT tests**

Replace the old `calculate_femaleModerateLosingHalfKgPerWeek` and add four new Hall model tests:

```java
    // Hall model: normal path — no cap, no floor triggered
    // rate = 10kg / 30 weeks = 0.333 kg/week
    // W_avg = (62 + 52) / 2 = 57 kg
    // BMR_avg = 10*57 + 6.25*165 - 5*28 - 161 = 1300.25
    // TDEE_avg = 1300.25 * 1.55 = 2015.39; adapted = * 0.90 = 1813.85
    // dailyDeficit = (10/30) * 7700 / 7 = 366.67
    // raw = 1813.85 - 366.67 = 1447.18
    // TDEE0 = 1350.25 * 1.55 = 2092.89; 35% cap floor = 2092.89 * 0.65 = 1360.38
    // result = max(1447.18, 1360.38, 1200) = 1447.18
    @Test
    void calculate_loseWeight_normalHallPath() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 28, 165.0, 62.0, "MODERATE", "LOSE_WEIGHT", 52.0, 30);
        assertEquals(1447.18, UserHealthGoalService.calculate(dto), 0.5);
    }

    // Hall model: 35% deficit cap triggered
    // rate = 10kg / 20 weeks = 0.5 kg/week
    // raw Hall result = 1813.85 - 550 = 1263.85 < cap 1360.38 → cap wins
    // result = max(1263.85, 1360.38, 1200) = 1360.38
    @Test
    void calculate_loseWeight_capTriggered() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 28, 165.0, 62.0, "MODERATE", "LOSE_WEIGHT", 52.0, 20);
        assertEquals(1360.38, UserHealthGoalService.calculate(dto), 0.5);
    }

    // Hall model: 1200 kcal floor triggered for small sedentary female
    // rate = 5kg / 8 weeks = 0.625 kg/week
    // W_avg = 42.5 kg; BMR_avg = 1086.5; TDEE_avg = 1303.8; adapted = 1173.42
    // dailyDeficit = 0.625 * 7700/7 = 687.5
    // raw = 1173.42 - 687.5 = 485.92
    // TDEE0 = 1111.5 * 1.2 = 1333.8; cap floor = 1333.8 * 0.65 = 866.97
    // result = max(485.92, 866.97, 1200) = 1200
    @Test
    void calculate_loseWeight_floorTriggered() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 23, 150.0, 45.0, "SEDENTARY", "LOSE_WEIGHT", 40.0, 8);
        assertEquals(1200.0, UserHealthGoalService.calculate(dto), 0.1);
    }

    // Validation: targetWeight null for LOSE_WEIGHT must throw 400
    @Test
    void calculate_loseWeight_nullTargetWeight_throws400() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 28, 165.0, 62.0, "MODERATE", "LOSE_WEIGHT", null, 20);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> UserHealthGoalService.calculate(dto));
        assertEquals(400, ex.getStatusCode().value());
    }

    // Validation: weeksToGoal null for LOSE_WEIGHT must throw 400
    @Test
    void calculate_loseWeight_nullWeeksToGoal_throws400() {
        UserHealthGoalPutDTO dto = buildDto("FEMALE", 28, 165.0, 62.0, "MODERATE", "LOSE_WEIGHT", 52.0, null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> UserHealthGoalService.calculate(dto));
        assertEquals(400, ex.getStatusCode().value());
    }
```

- [ ] **Step 2.4: Run tests and confirm the right ones fail**

```bash
./mvnw test -pl . -Dtest=UserHealthGoalServiceTest -q
```

Expected:
- `calculate_loseWeight_normalHallPath` — FAIL (old formula gives different value)
- `calculate_loseWeight_capTriggered` — FAIL
- `calculate_loseWeight_floorTriggered` — FAIL
- `calculate_loseWeight_nullTargetWeight_throws400` — FAIL (no validation yet)
- `calculate_loseWeight_nullWeeksToGoal_throws400` — FAIL
- All other tests (MAINTAIN, GAIN_MUSCLE, OTHER, getGoal, upsertGoal) — PASS

If any unexpected test fails, fix it before continuing.

- [ ] **Step 2.5: Commit failing tests**

```bash
git add src/test/java/ch/uzh/ifi/hase/soprafs26/service/UserHealthGoalServiceTest.java
git commit -m "test(#129): write failing Hall model tests for health goal calorie calculation"
```

---

## Task 3: Backend — Implement Hall Model in `calculate()`

**Files:**
- Modify: `src/main/java/ch/uzh/ifi/hase/soprafs26/service/UserHealthGoalService.java`

- [ ] **Step 3.1: Replace `calculate()` and add `mifflinBmr()` helper**

Replace the entire `calculate` static method and add the helper below it:

```java
    static double calculate(UserHealthGoalPutDTO dto) {
        double activityFactor = switch (dto.getActivityLevel()) {
            case "SEDENTARY"   -> 1.2;
            case "LIGHT"       -> 1.375;
            case "MODERATE"    -> 1.55;
            case "ACTIVE"      -> 1.725;
            case "VERY_ACTIVE" -> 1.9;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid activity level: " + dto.getActivityLevel());
        };

        // TDEE at current body weight — used as reference for cap and non-loss goals
        double tdee0 = mifflinBmr(dto.getWeight(), dto.getHeight(), dto.getAge(), dto.getSex())
                * activityFactor;

        return switch (dto.getGoalType()) {
            case "LOSE_WEIGHT" -> {
                // targetWeight and weeksToGoal are required inputs for weight-loss calculation
                if (dto.getTargetWeight() == null || dto.getWeeksToGoal() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "targetWeight and weeksToGoal are required for LOSE_WEIGHT");
                }

                // Hall (2012): use TDEE at the average weight across the loss journey,
                // then apply ~10% metabolic adaptation factor.
                double wAvg = (dto.getWeight() + dto.getTargetWeight()) / 2.0;
                double tdeeAtAvg = mifflinBmr(wAvg, dto.getHeight(), dto.getAge(), dto.getSex())
                        * activityFactor;
                double tdeeAdapted = tdeeAtAvg * 0.90;

                // Energy density of mixed tissue (≈75% fat + 25% lean): 7700 kcal/kg
                double rate = (dto.getWeight() - dto.getTargetWeight()) / dto.getWeeksToGoal();
                double dailyDeficit = rate * 7700.0 / 7.0;

                // Safety: cap deficit at 35% of current TDEE; enforce sex-based calorie floor
                double maxDeficit = tdee0 * 0.35;
                double floor = "MALE".equals(dto.getSex()) ? 1500.0 : 1200.0;

                yield Math.max(
                        Math.max(tdeeAdapted - dailyDeficit, tdee0 - maxDeficit),
                        floor
                );
            }
            case "MAINTAIN"     -> tdee0;
            case "GAIN_MUSCLE"  -> tdee0 + 300.0;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid goal type: " + dto.getGoalType());
        };
    }

    // Mifflin-St Jeor BMR. "OTHER" uses the average of male (+5) and female (-161) offsets = -78.
    private static double mifflinBmr(double weight, double height, int age, String sex) {
        double base = 10.0 * weight + 6.25 * height - 5.0 * age;
        return switch (sex) {
            case "FEMALE" -> base - 161.0;
            case "MALE"   -> base + 5.0;
            default       -> base - 78.0;
        };
    }
```

- [ ] **Step 3.2: Run all service tests and confirm they pass**

```bash
./mvnw test -pl . -Dtest=UserHealthGoalServiceTest -q
```

Expected: all 11 tests PASS. If any fail, check the arithmetic in the test comments against the algorithm — fix the implementation, not the comments.

- [ ] **Step 3.3: Commit Hall model implementation**

```bash
git add src/main/java/ch/uzh/ifi/hase/soprafs26/service/UserHealthGoalService.java
git commit -m "feat(#129): implement Hall-model calorie recommendation with deficit cap and floor"
```

---

## Task 4: Backend — Update Controller Test

**Files:**
- Modify: `src/test/java/ch/uzh/ifi/hase/soprafs26/controller/UserHealthGoalControllerTest.java`

- [ ] **Step 4.1: Update PUT body and `buildGoal` helper**

In `putHealthGoal_200_createsGoal`, replace the JSON body:

```java
        String body = """
                {
                  "goalType": "LOSE_WEIGHT",
                  "targetWeight": 57.5,
                  "weeksToGoal": 9,
                  "age": 28,
                  "sex": "FEMALE",
                  "height": 165.0,
                  "weight": 62.0,
                  "activityLevel": "MODERATE"
                }
                """;
```

Also update `buildGoal` to set the new entity fields (so GET tests can assert on them if needed):

```java
    private UserHealthGoal buildGoal(Long userId, String goalType, Double targetRate, double kcal) {
        UserHealthGoal goal = new UserHealthGoal();
        goal.setGoalId(1L);
        goal.setUserId(userId);
        goal.setGoalType(goalType);
        goal.setTargetRate(targetRate);
        // targetWeight and weeksToGoal stored so the frontend can restore the form
        goal.setTargetWeight(targetRate != null ? 57.5 : null);
        goal.setWeeksToGoal(targetRate != null ? 9 : null);
        goal.setAge(28);
        goal.setSex("FEMALE");
        goal.setHeight(165.0);
        goal.setWeight(62.0);
        goal.setActivityLevel("MODERATE");
        goal.setRecommendedDailyCalories(kcal);
        goal.setUpdatedAt(Instant.now());
        return goal;
    }
```

- [ ] **Step 4.2: Run controller tests**

```bash
./mvnw test -pl . -Dtest=UserHealthGoalControllerTest -q
```

Expected: all 4 controller tests PASS.

- [ ] **Step 4.3: Run the full backend test suite**

```bash
./mvnw test -q
```

Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 4.4: Commit controller test update**

```bash
git add src/test/java/ch/uzh/ifi/hase/soprafs26/controller/UserHealthGoalControllerTest.java
git commit -m "test(#129): update controller test PUT body to use targetWeight and weeksToGoal"
```

---

## Task 5: Frontend — Types and Page

**Files:**
- Modify: `client/app/types/healthGoal.ts`
- Modify: `client/app/users/[id]/health-goal/page.tsx`
- Modify: `client/app/users/[id]/health-goal/page.test.tsx`

- [ ] **Step 5.1: Update `healthGoal.ts`**

Replace the file contents:

```ts
export type GoalType = "LOSE_WEIGHT" | "MAINTAIN" | "GAIN_MUSCLE";
export type Sex = "MALE" | "FEMALE" | "OTHER";
export type ActivityLevel = "SEDENTARY" | "LIGHT" | "MODERATE" | "ACTIVE" | "VERY_ACTIVE";

export interface HealthGoal {
  goalId: number;
  userId: number;
  goalType: GoalType;
  targetRate: number | null;
  // targetWeight and weeksToGoal are stored so the form can be restored on reload
  targetWeight: number | null;
  weeksToGoal: number | null;
  age: number;
  sex: Sex;
  height: number;
  weight: number;
  activityLevel: ActivityLevel;
  recommendedDailyCalories: number;
  updatedAt: string;
}

export interface HealthGoalPutRequest {
  goalType: GoalType;
  // targetWeight and weeksToGoal replace targetRate — the backend derives targetRate from them
  targetWeight?: number | null;
  weeksToGoal?: number | null;
  age: number;
  sex: Sex;
  height: number;
  weight: number;
  activityLevel: ActivityLevel;
}
```

- [ ] **Step 5.2: Update `handleSave` in `page.tsx`**

In `handleSave`, replace the block that computes `targetRate` and builds the request body:

```tsx
      // Send targetWeight and weeksToGoal directly — backend derives targetRate
      const body: HealthGoalPutRequest = {
        goalType: values.goalType as HealthGoalPutRequest["goalType"],
        age: values.age,
        sex: values.sex as HealthGoalPutRequest["sex"],
        height: values.height,
        weight: values.weight,
        activityLevel: values.activityLevel as HealthGoalPutRequest["activityLevel"],
        targetWeight: values.goalType === "LOSE_WEIGHT" ? (values.targetWeight ?? null) : null,
        weeksToGoal: values.goalType === "LOSE_WEIGHT" ? (values.weeksToGoal ?? null) : null,
      };
```

Remove the three lines that declared and computed `targetRate` (the `let targetRate` block).

- [ ] **Step 5.3: Update the `load` effect in `page.tsx`**

In the `load` async function, update `form.setFieldsValue` to also restore `targetWeight` and `weeksToGoal`:

```tsx
        form.setFieldsValue({
          age: goal.age,
          sex: goal.sex,
          height: goal.height,
          weight: goal.weight,
          activityLevel: goal.activityLevel,
          goalType: goal.goalType,
          // Restore loss-specific fields now that they are persisted on the backend
          targetWeight: goal.targetWeight ?? undefined,
          weeksToGoal: goal.weeksToGoal ?? undefined,
        });
```

(Remove the old comment that said "targetWeight and weeksToGoal are not stored on the backend".)

- [ ] **Step 5.4: Update `page.test.tsx` mocks to match new response shape**

In the test `"shows recommendation when goal already exists"`, add the new fields to the mock:

```tsx
    getMock.mockResolvedValueOnce({
      goalId: 1,
      userId: 1,
      goalType: "MAINTAIN",
      targetRate: null,
      targetWeight: null,   // new fields now returned by backend
      weeksToGoal: null,
      age: 28,
      sex: "FEMALE",
      height: 165,
      weight: 62,
      activityLevel: "MODERATE",
      recommendedDailyCalories: 2092,
      updatedAt: "2026-04-27T10:00:00Z",
    });
```

In the test `"shows target weight and weeks fields when lose weight is selected"`, update the mock:

```tsx
    getMock.mockResolvedValueOnce({
      goalId: 1,
      userId: 1,
      goalType: "LOSE_WEIGHT",
      targetRate: 0.5,
      targetWeight: 75.0,   // new fields now returned by backend
      weeksToGoal: 20,
      age: 30,
      sex: "MALE",
      height: 175,
      weight: 85,
      activityLevel: "LIGHT",
      recommendedDailyCalories: 1900,
      updatedAt: "2026-04-27T10:00:00Z",
    });
```

- [ ] **Step 5.5: Run frontend tests**

From `sopra-fs26-group-09-client/`:
```bash
npm test -- --testPathPattern="health-goal" --watchAll=false
```

Expected: all 5 tests in `page.test.tsx` PASS.

- [ ] **Step 5.6: Run TypeScript build**

```bash
npm run build
```

Expected: no TypeScript errors, BUILD successful.

- [ ] **Step 5.7: Commit frontend changes**

```bash
git add app/types/healthGoal.ts \
        "app/users/[id]/health-goal/page.tsx" \
        "app/users/[id]/health-goal/page.test.tsx"
git commit -m "feat(#97): send targetWeight and weeksToGoal to backend; restore form on load"
```

---

## Self-Review Checklist (run before calling this plan complete)

- [ ] Every DTO field added in Task 1 has a corresponding test assertion in Task 2
- [ ] `mifflinBmr("OTHER")` returns `base - 78` — matches existing `calculate_otherSex_averageOfMaleAndFemale` expected value
- [ ] Three Hall edge-case tests cover: normal path, 35% cap, 1200 floor — all three branches of the `Math.max` chain
- [ ] Both null-validation tests cover each required field independently
- [ ] Frontend mock in Task 5.4 includes `targetWeight` and `weeksToGoal` (avoids TypeScript strict-mode errors in tests)
- [ ] All commits reference `#129` (backend) or `#97` (frontend)
- [ ] No push to remote — user asked to be notified before push
