# Hall Model Calorie Recommendation — Design Spec

**Date:** 2026-05-10
**Issue:** server #15 (health goal user story)
**Status:** Approved

---

## Problem

The current calorie recommendation formula (`TDEE - targetRate * 1000`) has four bugs:

1. Energy density coefficient is wrong: `×1000` instead of `7700/7 ≈ 1100 kcal/kg·week`.
2. No minimum calorie floor — outputs like 700 kcal/day are possible and dangerous.
3. No maximum deficit cap — aggressive rates destroy recommendations for lighter users.
4. TDEE is computed at current weight, not averaged over the weight-loss journey.

Additionally, `targetWeight` and `weeksToGoal` are collected in the frontend form but discarded — only the derived `targetRate` is sent to the backend, so the form cannot be restored on re-load.

---

## Solution: Hall-Inspired Steady-State Model

Based on the Kevin Hall / NIH Body Weight Planner model (Hall et al., 2012). The key insight: use the **average TDEE** across the weight-loss journey (current → target weight) rather than just current TDEE, and apply a metabolic adaptation correction.

### Algorithm (LOSE_WEIGHT branch only)

```
rate         = (weight - targetWeight) / weeksToGoal        [kg/week]
W_avg        = (weight + targetWeight) / 2
bmrAtAvg     = Mifflin-St Jeor(W_avg, height, age, sex)
tdeeAtAvg    = bmrAtAvg × activityFactor
tdeeAdapted  = tdeeAtAvg × 0.90    // ~10% adaptive thermogenesis (Hall 2012)

dailyDeficit = rate × 7700.0 / 7.0  // 7700 kcal/kg mixed tissue (75% fat, 25% lean)

tdee0        = Mifflin-St Jeor(weight) × activityFactor  // current-weight TDEE
maxDeficit   = tdee0 × 0.35                               // cap: 35% of current TDEE
floor        = sex == "MALE" ? 1500 : 1200                // absolute safety minimum

result = max(tdeeAdapted - dailyDeficit,
             tdee0 - maxDeficit,
             floor)
```

MAINTAIN and GAIN_MUSCLE branches are unchanged.

---

## Data Layer Changes

### DTO (`UserHealthGoalPutDTO`)
- **Remove** `targetRate` (now backend-derived, not a client input)
- **Add** `targetWeight` (Double, nullable — required only for LOSE_WEIGHT)
- **Add** `weeksToGoal` (Integer, nullable — required only for LOSE_WEIGHT)
- **Validation in `calculate()`**: if `goalType == LOSE_WEIGHT` and either field is null, throw `400 BAD_REQUEST`

### Entity (`UserHealthGoal`)
- **Add** `target_weight` column (nullable Double)
- **Add** `weeks_to_goal` column (nullable Integer)
- Keep `target_rate` — stored as a backend-computed field for reference
- DB: H2 in-memory, columns auto-added by Hibernate — no migration script needed

### GET Response (`UserHealthGoal` → JSON)
- `targetWeight` and `weeksToGoal` included in response so the frontend can restore the form on reload

---

## Frontend Changes

**`app/types/healthGoal.ts`**
- `HealthGoal`: add `targetWeight: number | null`, `weeksToGoal: number | null`
- `HealthGoalPutRequest`: add `targetWeight?: number | null`, `weeksToGoal?: number | null`; remove `targetRate`

**`app/users/[id]/health-goal/page.tsx`**
- `handleSave`: send `targetWeight` and `weeksToGoal` directly; remove manual `targetRate` computation
- `load` effect: restore `targetWeight` and `weeksToGoal` from GET response into form fields
- `computedRate`: keep as-is for live UI warning (purely presentational, not sent to backend)

---

## Test Changes

**`UserHealthGoalServiceTest`**
- Rewrite `calculate_femaleModerateLosingHalfKgPerWeek` to use `targetWeight` + `weeksToGoal` params
- Add: Hall model produces result above floor
- Add: very fast rate → 35% deficit cap triggered
- Add: small/light user → 1200 kcal floor triggered
- Add: MAINTAIN / GAIN_MUSCLE regression (unaffected)

**`UserHealthGoalControllerTest`**
- PUT body: replace `"targetRate": 0.5` with `"targetWeight": 57.5, "weeksToGoal": 9`

---

## Files Touched

| File | Change |
|---|---|
| `server/.../rest/dto/UserHealthGoalPutDTO.java` | Remove targetRate; add targetWeight, weeksToGoal |
| `server/.../entity/UserHealthGoal.java` | Add targetWeight, weeksToGoal columns |
| `server/.../service/UserHealthGoalService.java` | Rewrite calculate() with Hall model |
| `server/.../service/UserHealthGoalServiceTest.java` | Rewrite + add Hall tests |
| `server/.../controller/UserHealthGoalControllerTest.java` | Update PUT body |
| `client/app/types/healthGoal.ts` | Update both interfaces |
| `client/app/users/[id]/health-goal/page.tsx` | Send new fields; restore on load |
