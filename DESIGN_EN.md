# Household Pantry Manager — Detailed Design Document (English)

**Project:** SoPra FS26 Group 09 — Household Pantry Manager
**Document Version:** v3.0
**Date:** 2026-03-26

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Implementation Status](#2-implementation-status)
3. [Tech Stack](#3-tech-stack)
4. [System Architecture](#4-system-architecture)
5. [Data Model](#5-data-model)
6. [REST API Design](#6-rest-api-design)
7. [WebSocket Design](#7-websocket-design)
8. [Frontend Page Routes](#8-frontend-page-routes)
9. [External API Integration](#9-external-api-integration)
10. [Authentication](#10-authentication)

---

## 1. Project Overview

This application is a household food pantry management system that allows members of the same household to share a real-time synchronized food inventory (Pantry). Users can add items via barcode entry, product name search, or product image scanning. Consumption is recorded with calorie tracking. The system provides calorie statistics and budget comparison to help household members monitor dietary goals. All pantry changes are pushed in real time via WebSocket to all online members of the same household.

---

## 2. Implementation Status

This section tracks what has been implemented, what is in progress, and what is planned but not yet covered by implementation issues.

### 2.1 Backend (Server)

| Feature | Status | Related Issues |
|---------|--------|----------------|
| User entity & repository | ✅ Done | #22 (closed) |
| Register / Login / Logout endpoints | ✅ Done | #23 (closed) |
| WebSocket config & handshake interceptor | ✅ Done | — |
| PantryBroadcastService infrastructure | ✅ Done | — |
| Token-based auth filter | 🔲 Pending | #24 |
| Household entity & repository | 🔲 Pending | #27 |
| POST /households (create) | 🔲 Pending | #28 |
| Invite code generation | 🔲 Pending | #29 |
| POST /households/join | 🔲 Pending | #30 |
| PantryItem entity & repository | 🔲 Pending | #36 |
| POST /households/{id}/pantry | 🔲 Pending | #37, #42 |
| GET /products/barcode/{barcode} | 🔲 Pending | #35 |
| OpenFoodFacts API client | 🔲 Pending | #34 |
| GET /products/search | 🔲 Pending | #47 |
| ConsumptionLog entity & repository | 🔲 Pending | #17 |
| POST /pantry/{id}/consume | 🔲 Pending | #18 |
| Auto-remove item at quantity zero | 🔲 Pending | #19 |
| Calorie total calculation | 🔲 Pending | #50 |
| Total calories in GET /households/{id}/pantry response | 🔲 Pending | #51 |
| GET & PUT /households/{id}/budget endpoints | 🔲 Pending | #53 |
| GET /households/{id}/stats endpoint | 🔲 Pending | #58 |
| Budget comparison in stats | 🔲 Pending | #56 |
| WebSocket broadcast setup | 🔲 Pending | #62 |
| Pantry change broadcast | 🔲 Pending | #63 |
| CV service integration (barcode from image) | 🔲 Pending | #66 |
| Image upload endpoint | 🔲 Pending | #67 |
| UserHealthGoal entity & endpoints | ⚠️ Planned, no issues | — |
| Receipt OCR endpoint | ⚠️ Planned, no issues | — |
| Audit fields in ConsumptionLog (receipt source tracking) | ⚠️ Planned, no issues | — |
| Meal photo CV endpoint (optional) | ⚠️ Planned, no issues | — |

### 2.2 Frontend (Client)

| Feature | Status | Related Issues |
|---------|--------|----------------|
| Login / Register page UI | ✅ Done | #23 (closed) |
| Login / Register API connection | ✅ Done | #21 (closed) |
| Household create / join pages | 🔲 Pending | #22, #24 |
| Household API connection | 🔲 Pending | #20 |
| Barcode lookup page UI | 🔲 Pending | #19 |
| Barcode API connection | 🔲 Pending | #18 |
| Product name search page UI | 🔲 Pending | #15 |
| Product search API connection | 🔲 Pending | #14 |
| Add to pantry form | 🔲 Pending | #17 |
| Add to pantry confirmation & API | 🔲 Pending | #16, #2 |
| Consume item UI | 🔲 Pending | #25 |
| Total calories on pantry page | 🔲 Pending | #13 |
| Budget input & display UI | 🔲 Pending | #12 |
| Budget API connection | 🔲 Pending | #11 |
| Stats date picker & consumption display | 🔲 Pending | #9 |
| Stats API connection | 🔲 Pending | #7 |
| Budget comparison UI | 🔲 Pending | #10 |
| WebSocket real-time pantry update | 🔲 Pending | #6 |
| WebSocket disconnect notification | 🔲 Pending | #5 |
| Image upload UI (barcode scan) | 🔲 Pending | #4 |
| Image → CV API connection & fallback | 🔲 Pending | #3 |
| Health goal page UI & API | ⚠️ Planned, no issues | — |
| Receipt upload & OCR confirmation UI | ⚠️ Planned, no issues | — |
| Meal photo upload & confirmation UI (optional) | ⚠️ Planned, no issues | — |

> **Action required:** The following planned features have no implementation issues in either repository and must have issues created before work begins:
> - `UserHealthGoal` (backend entity, `GET`/`PUT /users/me/health-goal`, frontend `/health-goal` page)
> - Receipt OCR (backend `POST /pantry/receipt` endpoint, frontend receipt upload & review UI)

---

## 3. Tech Stack

| Layer | Technology | Version | Notes |
|-------|-----------|---------|-------|
| Frontend framework | Next.js | 15 | App Router, SSR/CSR hybrid |
| Frontend language | TypeScript | 5.x | Static typing |
| Frontend UI | Ant Design | 6 | Component library |
| Frontend WebSocket | @stomp/stompjs | latest | Native WebSocket + STOMP — *planned, not yet installed* |
| Backend framework | Spring Boot | 4.0 | Java 17, RESTful API |
| Database | H2 (in-memory) | — | Dev/test; JPA/Hibernate ORM |
| Backend WebSocket | Spring WebSocket (STOMP) | — | Real-time message broadcast |
| Authentication | UUID Token | — | Stored in DB, passed via HTTP Header |
| External API | OpenFoodFacts API | v2 | Barcode / name product lookup |
| External API | CV Service (TBD) | — | Barcode detection from product image |
| External API | OCR Service (TBD) | — | Receipt text extraction |
| Frontend deploy | Vercel | — | Automatic CI/CD |
| Backend deploy | Google App Engine | — | Standard environment |

---

## 4. System Architecture

```
┌────────────────────────────────────────────────────────┐
│              Next.js 15 Frontend (Vercel)               │
│                                                        │
│  Page Components (React 19)  ↔  apiService.ts         │
│                              ↔  WebSocket Client       │
└──────────────────────────────┬─────────────────────────┘
                               │ HTTP REST + WS/STOMP
                               ▼
┌────────────────────────────────────────────────────────┐
│        Spring Boot 4.0 Backend (Google App Engine)      │
│                                                        │
│  Auth Filter → REST Controllers → Service Layer → JPA  │
│                                                        │
│  WebSocketConfig + AuthHandshakeInterceptor            │
│  PantryBroadcastService (SimpMessagingTemplate)        │
└──────────────────────────────┬─────────────────────────┘
                               │
                   ┌───────────┴──────────┐
                   │   H2 In-Memory DB    │
                   └──────────────────────┘
                               │
          ┌────────────────────┼────────────────────┐
          ▼                    ▼                    ▼
  OpenFoodFacts API       CV Service (TBD)    OCR Service (TBD)
  (product nutrition)    (barcode detection)  (receipt parsing)
```

---

## 5. Data Model

### 5.1 User

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| id | Long | PK | Unique user ID |
| name | String | NOT NULL | Display name |
| username | String | NOT NULL, UNIQUE | Login username |
| password | String | NOT NULL | BCrypt-hashed password |
| token | String | UNIQUE | UUID auth token |
| status | UserStatus | NOT NULL | ONLINE / OFFLINE |
| createdAt | LocalDateTime | NOT NULL | Registration time |

### 5.2 Household

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| householdId | Long | PK | Unique household ID |
| name | String | NOT NULL | Household name |
| inviteCode | String | NOT NULL, UNIQUE | 6-character random invite code |
| ownerId | Long | FK → User.id | Creator (household owner) |
| createdAt | LocalDateTime | NOT NULL | Creation time |

### 5.3 HouseholdMember (join table)

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| userId | Long | PK (composite), FK → User.id | Member user ID |
| householdId | Long | PK (composite), FK → Household.householdId | Household ID |
| joinedAt | LocalDateTime | NOT NULL | Time of joining |

> Business rule: one user can belong to at most one household at a time — enforced in the Service layer.

### 5.4 PantryItem

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| itemId | Long | PK | Unique item ID |
| householdId | Long | FK, NOT NULL | Owning household |
| productName | String | NOT NULL | Product name |
| barcode | String | NULLABLE | Barcode (optional) |
| quantity | Double | NOT NULL, >= 0 | Remaining quantity |
| unit | String | NOT NULL | Unit: g / ml / servings / pieces |
| caloriesPerUnit | Double | NULLABLE | Calories per unit (optional) |
| addedByUserId | Long | FK → User.id | User who added this item |
| addedAt | LocalDateTime | NOT NULL | Time of addition |

> When quantity reaches 0 the Service automatically deletes the item (no explicit delete API needed).

### 5.5 ConsumptionLog

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| logId | Long | PK | Unique log ID |
| pantryItemId | Long | FK, NULLABLE | Associated pantry item (null if auto-deleted) |
| householdId | Long | FK, NOT NULL | Household (denormalized for stats queries) |
| userId | Long | FK, NOT NULL | User who consumed |
| productName | String | NOT NULL | Product name (denormalized) |
| quantity | Double | NOT NULL | Amount consumed |
| unit | String | NOT NULL | Unit consumed |
| caloriesConsumed | Double | NOT NULL | quantity × caloriesPerUnit |
| consumedAt | LocalDateTime | NOT NULL | Consumption time |
| receiptUploadedAt | LocalDateTime | NULLABLE | Timestamp of the receipt upload that triggered this entry; null if manually entered |
| uploadedByUserId | Long | FK → User.id, NULLABLE | User who uploaded the receipt; null if manually entered |

> Audit rule: `receiptUploadedAt` and `uploadedByUserId` are set together when a ConsumptionLog entry originates from a receipt bulk-add (`POST /pantry/receipt`). Both are null for manually entered consumption.

### 5.6 HouseholdBudget

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| budgetId | Long | PK | Unique budget ID |
| householdId | Long | FK, NOT NULL, UNIQUE | One budget per household |
| dailyCalorieTarget | Double | NOT NULL | Daily calorie target (kcal) |
| updatedAt | LocalDateTime | NOT NULL | Last updated |

### 5.7 UserHealthGoal

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| goalId | Long | PK | Unique goal ID |
| userId | Long | FK, NOT NULL, UNIQUE | One goal per user |
| goalType | String | NOT NULL | LOSE_WEIGHT / MAINTAIN / GAIN_MUSCLE |
| targetRate | Double | NULLABLE | Target rate (kg/week), used for LOSE_WEIGHT |
| age | Integer | NOT NULL | Age |
| sex | String | NOT NULL | MALE / FEMALE / OTHER |
| height | Double | NOT NULL | Height (cm) |
| weight | Double | NOT NULL | Weight (kg) |
| activityLevel | String | NOT NULL | SEDENTARY / LIGHT / MODERATE / ACTIVE / VERY_ACTIVE |
| recommendedDailyCalories | Double | NULLABLE | System-calculated recommendation |
| updatedAt | LocalDateTime | NOT NULL | Last updated |

### 5.8 Entity Relationship Diagram

```
User ──────────────── HouseholdMember ──────── Household
 │                      (N:M join table)            │
 │                                                  │
UserHealthGoal (1:1)                       HouseholdBudget (1:1)
                                                    │
                                             PantryItem (1:N)
                                                    │
                                             ConsumptionLog (1:N)
```

---

## 6. REST API Design

### Design Principles

- **Nested resource routing**: The Pantry is a sub-resource of a Household. All pantry routes are prefixed with `/households/{householdId}`, reflecting the ownership relationship. The backend must verify that the authenticated user belongs to the given `householdId`, returning 403 otherwise.
- Base URL (dev): `http://localhost:8080`
- Base URL (prod): `https://sopra-fs26-group-09-server.oa.r.appspot.com`
- All request/response bodies: `application/json` (file uploads: `multipart/form-data`)
- Protected endpoints require Header: `Authorization: <uuid-token>`
- Error response format: `{ "message": "description" }`

---

### 6.1 Authentication

#### POST /users/register
Register a new user.

**Request body:**
```json
{
  "username": "alice",
  "name": "Alice Wang",
  "password": "securepassword123"
}
```

**Response (201 Created):**
```json
{
  "id": 1,
  "username": "alice",
  "name": "Alice Wang",
  "token": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ONLINE"
}
```

---

#### POST /users/login
Login and receive a UUID token.

**Request body:**
```json
{
  "username": "alice",
  "password": "securepassword123"
}
```

**Response (200 OK):** Same structure as register response.

---

#### POST /users/logout
Logout — invalidate token, set status to OFFLINE. Requires `Authorization` header.

**Response (204 No Content)**

---

### 6.2 Household Management

#### POST /households
Create a new household. The current user automatically becomes the owner and first member. The system generates a 6-character random invite code.

**Request body:**
```json
{ "name": "Wang Family" }
```

**Response (201 Created):**
```json
{
  "householdId": 1,
  "name": "Wang Family",
  "inviteCode": "A3X9KL",
  "ownerId": 1,
  "createdAt": "2026-03-26T10:00:00"
}
```

---

#### POST /households/join
Join a household using an invite code. Returns 409 if the user is already in a household.

**Request body:**
```json
{ "inviteCode": "A3X9KL" }
```

**Response (200 OK):** Household info, same structure as above.

---

#### GET /households/me
Get the current user's household details, including member list.

**Response (200 OK):**
```json
{
  "householdId": 1,
  "name": "Wang Family",
  "inviteCode": "A3X9KL",
  "ownerId": 1,
  "createdAt": "2026-03-26T10:00:00",
  "members": [
    {
      "userId": 1,
      "username": "alice",
      "name": "Alice Wang",
      "joinedAt": "2026-03-26T10:00:00"
    }
  ]
}
```

> Returns 404 if the user has not joined any household.

---

### 6.3 Pantry Items

> All pantry endpoints are nested under `/households/{householdId}`. The backend verifies household membership and returns 403 if the user does not belong to the given household.

#### GET /households/{householdId}/pantry
Get all pantry items for the household along with the total calories.

**Response (200 OK):**
```json
{
  "householdId": 1,
  "totalCalories": 3420.5,
  "items": [
    {
      "itemId": 10,
      "productName": "Whole Milk",
      "barcode": "4002359001929",
      "quantity": 2.5,
      "unit": "servings",
      "caloriesPerUnit": 150.0,
      "addedByUserId": 1,
      "addedAt": "2026-03-26T09:00:00"
    }
  ]
}
```

---

#### POST /households/{householdId}/pantry
Add a new item to the household pantry. Quantity must be > 0. `caloriesPerUnit` and `barcode` are optional.

**Request body:**
```json
{
  "productName": "Whole Milk",
  "barcode": "4002359001929",
  "quantity": 3,
  "unit": "servings",
  "caloriesPerUnit": 150.0
}
```

**Response (201 Created):**
```json
{
  "itemId": 10,
  "householdId": 1,
  "productName": "Whole Milk",
  "barcode": "4002359001929",
  "quantity": 3,
  "unit": "servings",
  "caloriesPerUnit": 150.0,
  "addedByUserId": 1,
  "addedAt": "2026-03-26T09:00:00"
}
```

> After successful addition, the backend broadcasts an `ITEM_ADDED` event to `/topic/household/1/pantry`.

---

#### POST /households/{householdId}/pantry/{itemId}/consume
Consume a portion of a pantry item. The backend deducts the quantity and records a ConsumptionLog. If quantity reaches 0, the PantryItem is automatically deleted (log is preserved).

**Request body:**
```json
{ "quantity": 1.0, "unit": "servings" }
```

**Response (200 OK):**
```json
{
  "logId": 55,
  "pantryItemId": 10,
  "productName": "Whole Milk",
  "quantity": 1.0,
  "unit": "servings",
  "caloriesConsumed": 150.0,
  "consumedAt": "2026-03-26T12:30:00",
  "remainingQuantity": 2.0
}
```

> Consuming more than the remaining quantity → 400 Bad Request.
> Successful consumption broadcasts `ITEM_CONSUMED` (quantity > 0) or `ITEM_REMOVED` (quantity = 0).

---

### 6.4 Product Lookup

#### GET /products/barcode/{barcode}
Look up a product on OpenFoodFacts by barcode.

**Response (200 OK):**
```json
{
  "barcode": "4002359001929",
  "productName": "Vollmilch 3.5%",
  "brand": "Weihenstephan",
  "caloriesPer100g": 64.0,
  "caloriesPerServing": 150.0,
  "servingSize": "250ml",
  "imageUrl": "https://images.openfoodfacts.org/..."
}
```

> Returns 404 if the barcode is not found on OpenFoodFacts.

---

#### GET /products/search?name={name}
Search OpenFoodFacts by product name (fuzzy), up to 20 results.

**Response (200 OK):**
```json
{
  "results": [
    {
      "barcode": "4002359001929",
      "productName": "Vollmilch 3.5%",
      "brand": "Weihenstephan",
      "caloriesPer100g": 64.0,
      "caloriesPerServing": 150.0,
      "servingSize": "250ml",
      "imageUrl": "https://images.openfoodfacts.org/..."
    }
  ]
}
```

---

### 6.5 Stats & Budget

#### GET /households/{householdId}/budget
Get the household's daily calorie budget.

**Response (200 OK):**
```json
{
  "budgetId": 1,
  "householdId": 1,
  "dailyCalorieTarget": 2000.0,
  "updatedAt": "2026-03-26T08:00:00"
}
```

---

#### PUT /households/{householdId}/budget
Set or update the household's daily calorie budget.

**Request body:**
```json
{ "dailyCalorieTarget": 2000.0 }
```

**Response (200 OK):** Same structure as GET response.

---

#### GET /households/{householdId}/stats?startDate={date}
Consumption statistics from `startDate` to today. `startDate` format: `YYYY-MM-DD`.

**Response (200 OK):**
```json
{
  "startDate": "2026-03-20",
  "endDate": "2026-03-26",
  "dailyCalorieTarget": 2000.0,
  "averageDailyCalories": 1750.5,
  "totalCaloriesConsumed": 12253.5,
  "dailyBreakdown": [
    { "date": "2026-03-20", "caloriesConsumed": 1800.0 },
    { "date": "2026-03-21", "caloriesConsumed": 1650.0 }
  ],
  "comparisonToBudget": {
    "status": "UNDER_BUDGET",
    "differenceFromTarget": -249.5,
    "percentageOfTarget": 87.5
  }
}
```

> `status` values: `OVER_BUDGET` / `UNDER_BUDGET` / `ON_TARGET` (±5% of target counts as ON_TARGET)

---

### 6.6 Image Scanning

> The exact external service providers for CV and OCR have not yet been selected. The endpoint contracts below describe the expected interface; implementation details (base URL, auth headers) will be filled in once a service is chosen and configured via environment variables.

#### POST /households/{householdId}/pantry/scan
Upload a product image. The backend forwards it to the CV service, which detects the barcode and returns barcode + product info.
Content-Type: `multipart/form-data`, field name: `image`.

**Response (200 OK):**
```json
{
  "barcode": "4002359001929",
  "product": {
    "productName": "Vollmilch 3.5%",
    "caloriesPer100g": 64.0,
    "caloriesPerServing": 150.0,
    "servingSize": "250ml"
  }
}
```

> No barcode detected → 422 Unprocessable Entity. The user is prompted to enter the barcode manually.

---

#### POST /households/{householdId}/pantry/receipt
Upload a receipt photo. The backend forwards it to the OCR service, which extracts item names and quantities. The extracted list is returned for the user to review before bulk-adding.
Content-Type: `multipart/form-data`, field name: `receipt`.

**Response (200 OK):**
```json
{
  "extractedItems": [
    { "productName": "Vollmilch", "quantity": 2, "unit": "pieces", "confidence": 0.95 },
    { "productName": "Strawberry Yogurt", "quantity": 1, "unit": "pieces", "confidence": 0.87 }
  ]
}
```

> After review, the user confirms and the frontend calls `POST /households/{id}/pantry` for each accepted item.

---

#### POST /households/{householdId}/pantry/meal-photo *(optional)*
Upload a photo of a prepared meal. The CV service identifies food items and estimates portion sizes. The result is returned as suggested consumption entries for the user to confirm before being recorded.
Content-Type: `multipart/form-data`, field name: `photo`.

**Response (200 OK):**
```json
{
  "suggestedConsumptions": [
    {
      "productName": "Steamed Rice",
      "estimatedQuantity": 200.0,
      "unit": "g",
      "estimatedCalories": 260.0,
      "confidence": 0.82
    },
    {
      "productName": "Chicken Breast",
      "estimatedQuantity": 150.0,
      "unit": "g",
      "estimatedCalories": 248.0,
      "confidence": 0.76
    }
  ]
}
```

> After confirmation, the frontend calls `POST /households/{id}/pantry/{itemId}/consume` for each matched item (or records unmatched items directly as consumption logs).
> No food detected → 422 Unprocessable Entity.

---

### 6.7 User Health Goal

> **Note:** This feature is planned and fully specified below, but no backend or frontend implementation issues have been created yet. Issues must be created before implementation begins.

#### GET /users/me/health-goal
Get the current user's health goal.

**Response (200 OK):**
```json
{
  "goalId": 1,
  "userId": 1,
  "goalType": "LOSE_WEIGHT",
  "targetRate": 0.5,
  "age": 28,
  "sex": "FEMALE",
  "height": 165.0,
  "weight": 62.0,
  "activityLevel": "MODERATE",
  "recommendedDailyCalories": 1750.0,
  "updatedAt": "2026-03-26T09:00:00"
}
```

---

#### PUT /users/me/health-goal
Create or update the health goal. The backend automatically calculates `recommendedDailyCalories` using the Mifflin-St Jeor formula.

**Request body:**
```json
{
  "goalType": "LOSE_WEIGHT",
  "targetRate": 0.5,
  "age": 28,
  "sex": "FEMALE",
  "height": 165.0,
  "weight": 62.0,
  "activityLevel": "MODERATE"
}
```

**Calculation (Mifflin-St Jeor):**
- BMR (female) = 10×weight + 6.25×height − 5×age − 161
- BMR (male) = 10×weight + 6.25×height − 5×age + 5
- TDEE = BMR × activity factor (SEDENTARY=1.2 / LIGHT=1.375 / MODERATE=1.55 / ACTIVE=1.725 / VERY_ACTIVE=1.9)
- LOSE_WEIGHT: TDEE − targetRate×1000; MAINTAIN: TDEE; GAIN_MUSCLE: TDEE + 300

---

## 7. WebSocket Design

### 7.1 Connection Info

| Property | Value |
|----------|-------|
| URL (dev) | `ws://localhost:8080/ws` |
| URL (prod) | `wss://sopra-fs26-group-09-server.oa.r.appspot.com/ws` |
| Transport | Native WebSocket + STOMP |
| Auth | URL query parameter: `?token=<uuid-token>` |
| STOMP broker prefix | `/topic` |
| App destination prefix | `/app` |

### 7.2 Authentication

```
ws://localhost:8080/ws?token=550e8400-e29b-41d4-a716-446655440000
```

`AuthHandshakeInterceptor.beforeHandshake()` extracts the token from the query string → `UserRepository.findByToken()` → invalid: reject with HTTP 401 → valid: store the `User` object in WebSocket session attributes for the lifetime of the connection.

### 7.3 Subscription Topic

```
/topic/household/{householdId}/pantry
```

After login, the client obtains its `householdId` and immediately subscribes to this topic to receive all real-time pantry change events for the household.

### 7.4 Message Payload (PantryUpdateMessage)

```json
{
  "eventType": "ITEM_ADDED",
  "householdId": 1,
  "triggeredByUserId": 2,
  "triggeredByUsername": "bob",
  "timestamp": "2026-03-26T12:30:00",
  "item": {
    "itemId": 10,
    "productName": "Whole Milk",
    "barcode": "4002359001929",
    "quantity": 3.0,
    "unit": "servings",
    "caloriesPerUnit": 150.0,
    "addedByUserId": 2,
    "addedAt": "2026-03-26T12:30:00"
  },
  "newTotalCalories": 3570.5
}
```

### 7.5 Event Types

| eventType | Trigger |
|-----------|---------|
| `ITEM_ADDED` | After POST /households/{id}/pantry succeeds |
| `ITEM_CONSUMED` | After consume endpoint (remaining quantity > 0) |
| `ITEM_REMOVED` | After consume endpoint (remaining quantity = 0, item auto-deleted) |
| `BULK_ITEMS_ADDED` | After receipt bulk-add (`item` field becomes `items` array) |

### 7.6 Message Flow

```
User A sends POST /households/1/pantry
    → PantryService.addItem()
    → Save to DB
    → PantryBroadcastService.broadcastPantryUpdate(1, msg)
    → SimpMessagingTemplate → "/topic/household/1/pantry"
    → All subscribed members receive the message, UI updates
    → User A receives HTTP 201 response
```

### 7.7 Frontend Connection Management

- **Connect:** After login, once the `householdId` is known, connect immediately.
- **Disconnect:** Show a warning notification to the user. On reconnect, call `GET /households/{id}/pantry` to refresh full pantry state.
- **Reconnect:** Handled automatically by `@stomp/stompjs`.

---

## 8. Frontend Page Routes

| Route | Page | Description |
|-------|------|-------------|
| `/login` | Login | Username/password login |
| `/register` | Register | User registration |
| `/household/create` | Create Household | Enter name, view generated invite code |
| `/household/join` | Join Household | Enter 6-character invite code |
| `/pantry` | Pantry Overview (home) | Inventory list, total calories, real-time updates |
| `/pantry/add` | Add Item | Choose method: barcode / search / scan |
| `/pantry/add/barcode` | Barcode Lookup | Manually enter barcode |
| `/pantry/add/search` | Product Name Search | Search and select a product |
| `/pantry/add/scan` | Photo Scan | Upload image for barcode detection |
| `/pantry/add/receipt` | Receipt Upload | Confirm OCR results, bulk-add items |
| `/pantry/consume/meal-photo` | Meal Photo *(optional)* | Upload meal photo, confirm CV portion estimates, record consumption |
| `/pantry/consume/{itemId}` | Consume | Enter quantity and unit to consume |
| `/stats` | Statistics | Date picker, calorie chart, budget comparison |
| `/budget` | Budget Settings | Set household daily calorie budget |
| `/health-goal` | Health Goal | Enter body info, view recommended calories |

**Route protection rules:**
- `/login` and `/register` are public; all other routes require a valid token in `localStorage`.
- After login, if the user has not joined a household (`GET /households/me` → 404), redirect to the household selection page (create or join).

---

## 9. External API Integration

### 9.1 OpenFoodFacts API

| Property | Value |
|----------|-------|
| Base URL | `https://world.openfoodfacts.org` |
| Auth | No API key required (free and open) |
| Call direction | Backend → OpenFoodFacts |

**Barcode lookup:**
```
GET https://world.openfoodfacts.org/api/v2/product/{barcode}.json
```

**Name search:**
```
GET https://world.openfoodfacts.org/cgi/search.pl?search_terms={name}&search_simple=1&action=process&json=1&page_size=20
```

Extracted fields: `product_name`, `brands`, `nutriments.energy-kcal_100g`, `serving_size`, `nutriments.energy-kcal_serving`, `image_front_url`

> Some products lack calorie data. In that case return `null` for calorie fields and allow the user to enter values manually.

### 9.2 CV Service (Barcode Image Detection)

The specific CV service provider has not yet been selected. Once chosen, configure the following environment variables on the backend:

| Env Var | Purpose |
|---------|---------|
| `CV_SERVICE_URL` | Base URL of the CV service |
| `CV_SERVICE_API_KEY` | API key for the CV service |

**Expected request:** `POST {CV_SERVICE_URL}/detect-barcode`, field `image` (multipart)

**Expected response:**
```json
{ "success": true, "barcode": "4002359001929", "confidence": 0.98, "barcodeType": "EAN-13" }
```

### 9.3 OCR / Receipt Parsing Service

The specific OCR service provider has not yet been selected. Once chosen, configure the following environment variables on the backend:

| Env Var | Purpose |
|---------|---------|
| `OCR_SERVICE_URL` | Base URL of the OCR service |
| `OCR_SERVICE_API_KEY` | API key for the OCR service |

**Expected request:** `POST {OCR_SERVICE_URL}/parse-receipt`, field `receipt` (multipart)

**Expected response:**
```json
{ "items": [{ "productName": "Vollmilch 3.5%", "quantity": 2, "unit": "pieces", "confidence": 0.95 }] }
```

Flow: Backend receives receipt image → forwards to OCR service → returns extracted item list to frontend → user reviews and confirms → frontend calls `POST /households/{id}/pantry` for each accepted item.

---

## 10. Authentication

### 10.1 REST API Authentication

1. Login succeeds → backend generates UUID → stores in `User.token` → returns to frontend
2. Frontend stores in `localStorage["token"]`
3. Every request adds header: `Authorization: <token>`
4. `AuthFilter` validates token → valid: inject user into request context → invalid: 401
5. Logout → `User.token = null`; all subsequent requests return 401

### 10.2 WebSocket Authentication

Token is appended as a URL query parameter: `/ws?token=<uuid>`

`AuthHandshakeInterceptor.beforeHandshake()` validates the token and stores the `User` in WebSocket session attributes, available for the duration of the connection.

### 10.3 Public Endpoints

| Endpoint | Purpose |
|----------|---------|
| `POST /users/register` | Registration |
| `POST /users/login` | Login |

---

*Document version v3.0 — added implementation status tracking, flagged missing issues for health goal and receipt OCR features, noted unselected external service providers for CV/OCR.*
