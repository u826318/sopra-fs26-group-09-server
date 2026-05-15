# 家庭食品储藏管理系统 — 详细设计文档（中文版）

**项目名称：** SoPra FS26 Group 09 — Household Pantry Manager
**文档版本：** v3.0
**日期：** 2026-03-26

---

## 目录

1. [项目概述](#1-项目概述)
2. [实现进度](#2-实现进度)
3. [技术栈](#3-技术栈)
4. [系统架构](#4-系统架构)
5. [数据模型](#5-数据模型)
6. [REST API 设计](#6-rest-api-设计)
7. [WebSocket 设计](#7-websocket-设计)
8. [前端页面路由](#8-前端页面路由)
9. [外部 API 集成](#9-外部-api-集成)
10. [认证机制](#10-认证机制)

---

## 1. 项目概述

本项目是一个面向家庭用户的食品储藏管理 Web 应用，允许同一家庭的成员共享一个实时同步的食品库存（Pantry）。用户可以通过条形码录入、商品名称搜索或上传商品图片的方式向共享库存添加商品，并在取用食品时记录消费份额和对应卡路里。系统提供卡路里统计和预算比较功能，帮助家庭成员追踪饮食健康目标。所有库存变更通过 WebSocket 实时推送给同一家庭的所有在线成员。

---

## 2. 实现进度

本章节记录各功能的实现状态：已完成、待开发，以及已在设计中但尚未创建对应 Issue 的功能。

### 2.1 后端（Server）

| 功能 | 状态 | 相关 Issue |
|------|------|-----------|
| User 实体与 Repository | ✅ 已完成 | #22（已关闭） |
| 注册 / 登录 / 登出接口 | ✅ 已完成 | #23（已关闭） |
| WebSocket 配置与握手拦截器 | ✅ 已完成 | — |
| PantryBroadcastService 基础设施 | ✅ 已完成 | — |
| Token 身份认证过滤器 | 🔲 待开发 | #24 |
| Household 实体与 Repository | 🔲 待开发 | #27 |
| POST /households（创建家庭） | 🔲 待开发 | #28 |
| 邀请码生成 | 🔲 待开发 | #29 |
| POST /households/join | 🔲 待开发 | #30 |
| PantryItem 实体与 Repository | 🔲 待开发 | #36 |
| POST /households/{id}/pantry | 🔲 待开发 | #37, #42 |
| GET /products/barcode/{barcode} | 🔲 待开发 | #35 |
| OpenFoodFacts API 客户端 | 🔲 待开发 | #34 |
| GET /products/search | 🔲 待开发 | #47 |
| ConsumptionLog 实体与 Repository | 🔲 待开发 | #17 |
| POST /pantry/{id}/consume | 🔲 待开发 | #18 |
| 数量归零自动删除条目 | 🔲 待开发 | #19 |
| 卡路里总量计算 | 🔲 待开发 | #50 |
| GET /households/{id}/pantry 响应包含总卡路里 | 🔲 待开发 | #51 |
| GET & PUT /households/{id}/budget 接口 | 🔲 待开发 | #53 |
| GET /households/{id}/stats 接口 | 🔲 待开发 | #58 |
| 统计中的预算比较逻辑 | 🔲 待开发 | #56 |
| WebSocket 广播配置 | 🔲 待开发 | #62 |
| 库存变更广播 | 🔲 待开发 | #63 |
| CV 服务集成（图片识别条形码） | 🔲 待开发 | #66 |
| 图片上传接口 | 🔲 待开发 | #67 |
| UserHealthGoal 实体与接口 | ⚠️ 已规划，无 Issue | — |
| 小票 OCR 接口 | ⚠️ 已规划，无 Issue | — |
| ConsumptionLog 审计字段（小票来源追踪） | ⚠️ 已规划，无 Issue | — |
| 餐食照片 CV 端点（可选） | ⚠️ 已规划，无 Issue | — |

### 2.2 前端（Client）

| 功能 | 状态 | 相关 Issue |
|------|------|-----------|
| 登录 / 注册页面 UI | ✅ 已完成 | #23（已关闭） |
| 登录 / 注册 API 对接 | ✅ 已完成 | #21（已关闭） |
| 创建 / 加入家庭页面 | 🔲 待开发 | #22, #24 |
| 家庭 API 对接 | 🔲 待开发 | #20 |
| 条形码查询页面 UI | 🔲 待开发 | #19 |
| 条形码 API 对接 | 🔲 待开发 | #18 |
| 商品名称搜索页面 UI | 🔲 待开发 | #15 |
| 商品搜索 API 对接 | 🔲 待开发 | #14 |
| 添加到库存表单 | 🔲 待开发 | #17 |
| 添加确认流程与 API 对接 | 🔲 待开发 | #16, #2 |
| 消费条目 UI | 🔲 待开发 | #25 |
| 库存页面展示总卡路里 | 🔲 待开发 | #13 |
| 预算输入与展示 UI | 🔲 待开发 | #12 |
| 预算 API 对接 | 🔲 待开发 | #11 |
| 统计日期选择与消费展示 | 🔲 待开发 | #9 |
| 统计 API 对接 | 🔲 待开发 | #7 |
| 预算比较 UI | 🔲 待开发 | #10 |
| WebSocket 实时库存更新 | 🔲 待开发 | #6 |
| WebSocket 断线通知 | 🔲 待开发 | #5 |
| 图片上传 UI（扫码） | 🔲 待开发 | #4 |
| 图片 → CV API 对接与降级处理 | 🔲 待开发 | #3 |
| 健康目标页面 UI 与 API 对接 | ⚠️ 已规划，无 Issue | — |
| 小票上传与 OCR 确认 UI | ⚠️ 已规划，无 Issue | — |
| 餐食照片上传与确认 UI（可选） | ⚠️ 已规划，无 Issue | — |

> **待处理：** 以下已规划功能在前后端均无对应 Issue，开始开发前必须先创建 Issue：
> - `UserHealthGoal`（后端实体、`GET`/`PUT /users/me/health-goal`、前端 `/health-goal` 页面）
> - 小票 OCR（后端 `POST /pantry/receipt` 接口、前端上传与确认 UI）

---

## 3. 技术栈

| 层级 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 前端框架 | Next.js | 15 | App Router，SSR/CSR 混合渲染 |
| 前端语言 | TypeScript | 5.x | 静态类型 |
| 前端 UI 库 | Ant Design | 6 | 组件库 |
| 前端 WebSocket | @stomp/stompjs | 最新版 | 原生 WebSocket + STOMP —— *已规划，尚未安装* |
| 后端框架 | Spring Boot | 4.0 | Java 17，RESTful API |
| 数据库 | H2（内存数据库） | — | 开发/测试用，JPA/Hibernate ORM |
| 后端 WebSocket | Spring WebSocket (STOMP) | — | 实时消息推送 |
| 身份认证 | UUID Token | — | 存于数据库，通过 HTTP Header 传递 |
| 外部 API | OpenFoodFacts API | v2 | 条形码/名称商品查询 |
| 外部 API | CV 服务（待定） | — | 从商品图片中识别条形码 |
| 外部 API | OCR 服务（待定） | — | 小票文字提取 |
| 前端部署 | Vercel | — | 自动 CI/CD |
| 后端部署 | Google App Engine | — | 标准环境 |

---

## 4. 系统架构

```
┌────────────────────────────────────────────────────────┐
│              Next.js 15 前端（Vercel）                   │
│                                                        │
│  页面组件（React 19）   ↔  apiService.ts               │
│                         ↔  WebSocket 客户端             │
└──────────────────────────────┬─────────────────────────┘
                               │ HTTP REST + WS/STOMP
                               ▼
┌────────────────────────────────────────────────────────┐
│       Spring Boot 4.0 后端（Google App Engine）          │
│                                                        │
│  Auth Filter → REST Controllers → Service 层 → JPA    │
│                                                        │
│  WebSocketConfig + AuthHandshakeInterceptor            │
│  PantryBroadcastService (SimpMessagingTemplate)        │
└──────────────────────────────┬─────────────────────────┘
                               │
                   ┌───────────┴──────────┐
                   │   H2 内存数据库       │
                   └──────────────────────┘
                               │
          ┌────────────────────┼────────────────────┐
          ▼                    ▼                    ▼
  OpenFoodFacts API      CV 服务（待定）       OCR 服务（待定）
  （商品营养信息）        （条形码识别）         （小票解析）
```

---

## 5. 数据模型

### 5.1 User（用户）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Long | PK | 用户唯一 ID |
| name | String | NOT NULL | 显示名称 |
| username | String | NOT NULL, UNIQUE | 登录用户名 |
| password | String | NOT NULL | BCrypt 加密密码 |
| token | String | UNIQUE | UUID 认证令牌 |
| status | UserStatus | NOT NULL | ONLINE / OFFLINE |
| createdAt | LocalDateTime | NOT NULL | 注册时间 |

### 5.2 Household（家庭）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| householdId | Long | PK | 家庭唯一 ID |
| name | String | NOT NULL | 家庭名称 |
| inviteCode | String | NOT NULL, UNIQUE | 6 位随机邀请码 |
| ownerId | Long | FK → User.id | 创建者（家庭所有者） |
| createdAt | LocalDateTime | NOT NULL | 创建时间 |

### 5.3 HouseholdMember（家庭成员，关联表）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| userId | Long | PK（联合主键），FK → User.id | 成员用户 ID |
| householdId | Long | PK（联合主键），FK → Household.householdId | 家庭 ID |
| joinedAt | LocalDateTime | NOT NULL | 加入时间 |

> 业务规则：一个用户同一时间最多只能属于一个家庭，由 Service 层强制执行。

### 5.4 PantryItem（库存条目）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| itemId | Long | PK | 条目唯一 ID |
| householdId | Long | FK, NOT NULL | 所属家庭 |
| productName | String | NOT NULL | 商品名称 |
| barcode | String | NULLABLE | 条形码（可选） |
| quantity | Double | NOT NULL, >= 0 | 剩余数量 |
| unit | String | NOT NULL | 单位：g / ml / servings / pieces |
| caloriesPerUnit | Double | NULLABLE | 每单位卡路里（可选） |
| addedByUserId | Long | FK → User.id | 添加该条目的用户 |
| addedAt | LocalDateTime | NOT NULL | 添加时间 |

> 数量归零时 Service 自动删除该条目，无需显式删除接口。

### 5.5 ConsumptionLog（消费记录）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| logId | Long | PK | 记录唯一 ID |
| pantryItemId | Long | FK, NULLABLE | 关联的库存条目（自动删除后为 null） |
| householdId | Long | FK, NOT NULL | 所属家庭（冗余存储，用于统计查询） |
| userId | Long | FK, NOT NULL | 消费用户 |
| productName | String | NOT NULL | 商品名称（冗余存储） |
| quantity | Double | NOT NULL | 消费数量 |
| unit | String | NOT NULL | 消费单位 |
| caloriesConsumed | Double | NOT NULL | quantity × caloriesPerUnit |
| consumedAt | LocalDateTime | NOT NULL | 消费时间 |
| receiptUploadedAt | LocalDateTime | NULLABLE | 触发本条记录的小票上传时间；手动录入时为 null |
| uploadedByUserId | Long | FK → User.id, NULLABLE | 上传小票的用户；手动录入时为 null |

> 审计规则：当 ConsumptionLog 条目由小票批量上传（`POST /pantry/receipt`）产生时，`receiptUploadedAt` 和 `uploadedByUserId` 同时写入。手动消费录入时两者均为 null。

### 5.6 HouseholdBudget（家庭卡路里预算）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| budgetId | Long | PK | 预算唯一 ID |
| householdId | Long | FK, NOT NULL, UNIQUE | 每个家庭一条预算 |
| dailyCalorieTarget | Double | NOT NULL | 每日卡路里目标（kcal） |
| updatedAt | LocalDateTime | NOT NULL | 最后更新时间 |

### 5.7 UserHealthGoal（个人健康目标）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| goalId | Long | PK | 目标唯一 ID |
| userId | Long | FK, NOT NULL, UNIQUE | 每个用户一条目标 |
| goalType | String | NOT NULL | LOSE_WEIGHT / MAINTAIN / GAIN_MUSCLE |
| targetRate | Double | NULLABLE | 目标速率（kg/周），用于 LOSE_WEIGHT |
| age | Integer | NOT NULL | 年龄 |
| sex | String | NOT NULL | MALE / FEMALE / OTHER |
| height | Double | NOT NULL | 身高（cm） |
| weight | Double | NOT NULL | 体重（kg） |
| activityLevel | String | NOT NULL | SEDENTARY / LIGHT / MODERATE / ACTIVE / VERY_ACTIVE |
| recommendedDailyCalories | Double | NULLABLE | 系统计算的推荐每日卡路里 |
| updatedAt | LocalDateTime | NOT NULL | 最后更新时间 |

### 5.8 实体关系图

```
User ──────────────── HouseholdMember ──────── Household
 │                      (N:M 关联表)                │
 │                                                  │
UserHealthGoal (1:1)                       HouseholdBudget (1:1)
                                                    │
                                             PantryItem (1:N)
                                                    │
                                             ConsumptionLog (1:N)
```

---

## 6. REST API 设计

### 设计原则

- **嵌套资源路由**：Pantry 是 Household 的子资源，所有库存路由均以 `/households/{householdId}` 为前缀，体现所有权关系。后端须验证已认证用户是否属于该 `householdId`，否则返回 403。
- 开发环境 Base URL：`http://localhost:8080`
- 生产环境 Base URL：`https://sopra-fs26-group-09-server.oa.r.appspot.com`
- 所有请求/响应体：`application/json`（文件上传：`multipart/form-data`）
- 受保护接口需要 Header：`Authorization: <uuid-token>`
- 错误响应格式：`{ "message": "描述" }`

---

### 6.1 身份认证

#### POST /users/register
注册新用户。

**请求体：**
```json
{
  "username": "alice",
  "name": "Alice Wang",
  "password": "securepassword123"
}
```

**响应（201 Created）：**
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
登录并获取 UUID 令牌。

**请求体：**
```json
{
  "username": "alice",
  "password": "securepassword123"
}
```

**响应（200 OK）：** 结构同注册响应。

---

#### POST /users/logout
登出——使令牌失效，将状态设为 OFFLINE。需要 `Authorization` Header。

**响应（204 No Content）**

---

### 6.2 家庭管理

#### POST /households
创建新家庭。当前用户自动成为所有者和第一个成员，系统生成 6 位随机邀请码。

**请求体：**
```json
{ "name": "王家" }
```

**响应（201 Created）：**
```json
{
  "householdId": 1,
  "name": "王家",
  "inviteCode": "A3X9KL",
  "ownerId": 1,
  "createdAt": "2026-03-26T10:00:00"
}
```

---

#### POST /households/join
使用邀请码加入家庭。若用户已在某家庭中则返回 409。

**请求体：**
```json
{ "inviteCode": "A3X9KL" }
```

**响应（200 OK）：** 返回家庭信息，结构同上。

---

#### GET /households/me
获取当前用户所在家庭的详情，包含成员列表。

**响应（200 OK）：**
```json
{
  "householdId": 1,
  "name": "王家",
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

> 若用户尚未加入任何家庭则返回 404。

---

### 6.3 库存条目

> 所有库存接口均嵌套在 `/households/{householdId}` 下。后端验证家庭成员身份，若用户不属于该家庭则返回 403。

#### GET /households/{householdId}/pantry
获取家庭所有库存条目及总卡路里。

**响应（200 OK）：**
```json
{
  "householdId": 1,
  "totalCalories": 3420.5,
  "items": [
    {
      "itemId": 10,
      "productName": "全脂牛奶",
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
向家庭库存添加新条目。数量必须 > 0；`caloriesPerUnit` 和 `barcode` 为可选字段。

**请求体：**
```json
{
  "productName": "全脂牛奶",
  "barcode": "4002359001929",
  "quantity": 3,
  "unit": "servings",
  "caloriesPerUnit": 150.0
}
```

**响应（201 Created）：**
```json
{
  "itemId": 10,
  "householdId": 1,
  "productName": "全脂牛奶",
  "barcode": "4002359001929",
  "quantity": 3,
  "unit": "servings",
  "caloriesPerUnit": 150.0,
  "addedByUserId": 1,
  "addedAt": "2026-03-26T09:00:00"
}
```

> 添加成功后，后端向 `/topic/household/1/pantry` 广播 `ITEM_ADDED` 事件。

---

#### POST /households/{householdId}/pantry/{itemId}/consume
消费库存条目的一部分。后端扣减数量并记录 ConsumptionLog。若数量归零，PantryItem 自动删除（记录保留）。

**请求体：**
```json
{ "quantity": 1.0, "unit": "servings" }
```

**响应（200 OK）：**
```json
{
  "logId": 55,
  "pantryItemId": 10,
  "productName": "全脂牛奶",
  "quantity": 1.0,
  "unit": "servings",
  "caloriesConsumed": 150.0,
  "consumedAt": "2026-03-26T12:30:00",
  "remainingQuantity": 2.0
}
```

> 消费量超过剩余数量 → 400 Bad Request。
> 消费成功后广播 `ITEM_CONSUMED`（剩余 > 0）或 `ITEM_REMOVED`（数量归零）。

---

### 6.4 商品查询

#### GET /products/barcode/{barcode}
通过条形码在 OpenFoodFacts 查询商品。

**响应（200 OK）：**
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

> 若 OpenFoodFacts 中未找到该条形码则返回 404。

---

#### GET /products/search?name={name}
通过商品名称在 OpenFoodFacts 模糊搜索，最多返回 20 条结果。

**响应（200 OK）：**
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

### 6.5 统计与预算

#### GET /households/{householdId}/budget
获取家庭每日卡路里预算。

**响应（200 OK）：**
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
设置或更新家庭每日卡路里预算。

**请求体：**
```json
{ "dailyCalorieTarget": 2000.0 }
```

**响应（200 OK）：** 结构同 GET 响应。

---

#### GET /households/{householdId}/stats?startDate={date}
从 `startDate` 到今天的消费统计。`startDate` 格式：`YYYY-MM-DD`。

**响应（200 OK）：**
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

> `status` 取值：`OVER_BUDGET` / `UNDER_BUDGET` / `ON_TARGET`（±5% 以内视为 ON_TARGET）

---

### 6.6 图片扫描

> CV 服务和 OCR 服务的具体供应商尚未确定。以下接口契约描述期望的行为；实现细节（Base URL、认证头）将在选定服务后通过环境变量配置。

#### POST /households/{householdId}/pantry/scan
上传商品图片。后端转发至 CV 服务，识别条形码并返回商品信息。
Content-Type：`multipart/form-data`，字段名：`image`。

**响应（200 OK）：**
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

> 未检测到条形码 → 422 Unprocessable Entity，引导用户手动输入条形码。

---

#### POST /households/{householdId}/pantry/receipt
上传小票照片。后端转发至 OCR 服务，提取商品名称和数量，返回结果供用户确认后批量添加。
Content-Type：`multipart/form-data`，字段名：`receipt`。

**响应（200 OK）：**
```json
{
  "extractedItems": [
    { "productName": "全脂牛奶", "quantity": 2, "unit": "pieces", "confidence": 0.95 },
    { "productName": "草莓酸奶", "quantity": 1, "unit": "pieces", "confidence": 0.87 }
  ]
}
```

> 用户确认后，前端对每条接受的条目调用 `POST /households/{id}/pantry`。

---

#### POST /households/{householdId}/pantry/meal-photo *（可选）*
上传一张已制作餐食的照片。CV 服务识别其中的食物种类并估算份量，返回建议的消费条目供用户确认后记录。
Content-Type：`multipart/form-data`，字段名：`photo`。

**响应（200 OK）：**
```json
{
  "suggestedConsumptions": [
    {
      "productName": "米饭",
      "estimatedQuantity": 200.0,
      "unit": "g",
      "estimatedCalories": 260.0,
      "confidence": 0.82
    },
    {
      "productName": "鸡胸肉",
      "estimatedQuantity": 150.0,
      "unit": "g",
      "estimatedCalories": 248.0,
      "confidence": 0.76
    }
  ]
}
```

> 用户确认后，前端对匹配到的库存条目调用 `POST /households/{id}/pantry/{itemId}/consume`；未匹配的条目直接作为消费记录写入。
> 未检测到食物 → 422 Unprocessable Entity。

---

### 6.7 个人健康目标

> **注意：** 该功能已完整规划并在下方详细描述，但后端和前端均尚未创建对应 Issue，开始开发前必须先创建。

#### GET /users/me/health-goal
获取当前用户的健康目标。

**响应（200 OK）：**
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
创建或更新健康目标。后端使用 Mifflin-St Jeor 公式自动计算 `recommendedDailyCalories`。

**请求体：**
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

**计算方法（Mifflin-St Jeor 公式）：**
- BMR（女）= 10×体重 + 6.25×身高 − 5×年龄 − 161
- BMR（男）= 10×体重 + 6.25×身高 − 5×年龄 + 5
- TDEE = BMR × 活动系数（SEDENTARY=1.2 / LIGHT=1.375 / MODERATE=1.55 / ACTIVE=1.725 / VERY_ACTIVE=1.9）
- LOSE_WEIGHT：TDEE − targetRate×1000；MAINTAIN：TDEE；GAIN_MUSCLE：TDEE + 300

---

## 7. WebSocket 设计

### 7.1 连接信息

| 属性 | 值 |
|------|----|
| URL（开发）| `ws://localhost:8080/ws` |
| URL（生产）| `wss://sopra-fs26-group-09-server.oa.r.appspot.com/ws` |
| 传输方式 | 原生 WebSocket + STOMP |
| 认证 | URL 查询参数：`?token=<uuid-token>` |
| STOMP broker 前缀 | `/topic` |
| App destination 前缀 | `/app` |

### 7.2 认证

```
ws://localhost:8080/ws?token=550e8400-e29b-41d4-a716-446655440000
```

`AuthHandshakeInterceptor.beforeHandshake()` 从查询字符串中提取令牌 → `UserRepository.findByToken()` → 无效：拒绝并返回 HTTP 401 → 有效：将 `User` 对象存入 WebSocket 会话属性，连接期间持续有效。

### 7.3 订阅 Topic

```
/topic/household/{householdId}/pantry
```

登录后，客户端获取 `householdId` 并立即订阅此 Topic，以接收该家庭的所有实时库存变更事件。

### 7.4 消息体（PantryUpdateMessage）

```json
{
  "eventType": "ITEM_ADDED",
  "householdId": 1,
  "triggeredByUserId": 2,
  "triggeredByUsername": "bob",
  "timestamp": "2026-03-26T12:30:00",
  "item": {
    "itemId": 10,
    "productName": "全脂牛奶",
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

### 7.5 事件类型

| eventType | 触发条件 |
|-----------|---------|
| `ITEM_ADDED` | POST /households/{id}/pantry 成功后 |
| `ITEM_CONSUMED` | 消费接口调用后（剩余数量 > 0） |
| `ITEM_REMOVED` | 消费接口调用后（数量归零，条目自动删除） |
| `BULK_ITEMS_ADDED` | 小票批量添加后（`item` 字段变为 `items` 数组） |

### 7.6 消息流程

```
用户 A 发送 POST /households/1/pantry
    → PantryService.addItem()
    → 保存至数据库
    → PantryBroadcastService.broadcastPantryUpdate(1, msg)
    → SimpMessagingTemplate → "/topic/household/1/pantry"
    → 所有已订阅成员收到消息，UI 更新
    → 用户 A 收到 HTTP 201 响应
```

### 7.7 前端连接管理

- **连接：** 登录后，获知 `householdId` 后立即建立连接。
- **断线：** 向用户显示警告通知。重连后调用 `GET /households/{id}/pantry` 刷新完整库存状态。
- **重连：** 由 `@stomp/stompjs` 自动处理。

---

## 8. 前端页面路由

| 路由 | 页面 | 说明 |
|------|------|------|
| `/login` | 登录 | 用户名/密码登录 |
| `/register` | 注册 | 用户注册 |
| `/household/create` | 创建家庭 | 输入名称，查看生成的邀请码 |
| `/household/join` | 加入家庭 | 输入 6 位邀请码 |
| `/pantry` | 库存总览（主页） | 库存列表、总卡路里、实时更新 |
| `/pantry/add` | 添加条目 | 选择方式：条形码 / 搜索 / 扫描 |
| `/pantry/add/barcode` | 条形码查询 | 手动输入条形码 |
| `/pantry/add/search` | 商品名称搜索 | 搜索并选择商品 |
| `/pantry/add/scan` | 图片扫描 | 上传图片识别条形码 |
| `/pantry/add/receipt` | 小票上传 | 确认 OCR 结果，批量添加条目 |
| `/pantry/consume/meal-photo` | 餐食照片（可选） | 上传餐食照片，确认 CV 份量估算，记录消费 |
| `/pantry/consume/{itemId}` | 消费 | 输入消费数量和单位 |
| `/stats` | 统计 | 日期选择、卡路里图表、预算比较 |
| `/budget` | 预算设置 | 设置家庭每日卡路里预算 |
| `/health-goal` | 健康目标 | 输入身体信息，查看推荐卡路里 |

**路由保护规则：**
- `/login` 和 `/register` 为公开路由；其他路由均需要 `localStorage` 中有效的令牌。
- 登录后，若用户尚未加入家庭（`GET /households/me` → 404），自动跳转至家庭选择页面（创建或加入）。

---

## 9. 外部 API 集成

### 9.1 OpenFoodFacts API

| 属性 | 值 |
|------|----|
| Base URL | `https://world.openfoodfacts.org` |
| 认证 | 无需 API Key（完全免费开放） |
| 调用方向 | 后端 → OpenFoodFacts |

**条形码查询：**
```
GET https://world.openfoodfacts.org/api/v2/product/{barcode}.json
```

**名称搜索：**
```
GET https://world.openfoodfacts.org/cgi/search.pl?search_terms={name}&search_simple=1&action=process&json=1&page_size=20
```

提取字段：`product_name`、`brands`、`nutriments.energy-kcal_100g`、`serving_size`、`nutriments.energy-kcal_serving`、`image_front_url`

> 部分商品缺少卡路里数据，此时卡路里字段返回 `null`，允许用户手动输入。

### 9.2 CV 服务（条形码图片识别）

具体服务供应商尚未确定。确定后，在后端配置以下环境变量：

| 环境变量 | 用途 |
|---------|------|
| `CV_SERVICE_URL` | CV 服务 Base URL |
| `CV_SERVICE_API_KEY` | CV 服务 API 密钥 |

**期望请求：** `POST {CV_SERVICE_URL}/detect-barcode`，字段 `image`（multipart）

**期望响应：**
```json
{ "success": true, "barcode": "4002359001929", "confidence": 0.98, "barcodeType": "EAN-13" }
```

### 9.3 OCR / 小票解析服务

具体服务供应商尚未确定。确定后，在后端配置以下环境变量：

| 环境变量 | 用途 |
|---------|------|
| `OCR_SERVICE_URL` | OCR 服务 Base URL |
| `OCR_SERVICE_API_KEY` | OCR 服务 API 密钥 |

**期望请求：** `POST {OCR_SERVICE_URL}/parse-receipt`，字段 `receipt`（multipart）

**期望响应：**
```json
{ "items": [{ "productName": "全脂牛奶 3.5%", "quantity": 2, "unit": "pieces", "confidence": 0.95 }] }
```

流程：后端接收小票图片 → 转发至 OCR 服务 → 返回提取条目列表给前端 → 用户确认 → 前端对每条接受的条目调用 `POST /households/{id}/pantry`。

---

## 10. 认证机制

### 10.1 REST API 认证

1. 登录成功 → 后端生成 UUID → 存入 `User.token` → 返回给前端
2. 前端存入 `localStorage["token"]`
3. 每个请求添加 Header：`Authorization: <token>`
4. `AuthFilter` 验证令牌 → 有效：将用户注入请求上下文 → 无效：401
5. 登出 → `User.token = null`；后续所有请求返回 401

### 10.2 WebSocket 认证

令牌通过 URL 查询参数传递：`/ws?token=<uuid>`

`AuthHandshakeInterceptor.beforeHandshake()` 验证令牌并将 `User` 存入 WebSocket 会话属性，连接期间持续有效。

### 10.3 公开接口

| 接口 | 用途 |
|------|------|
| `POST /users/register` | 注册 |
| `POST /users/login` | 登录 |

---

*文档版本 v3.0 — 新增实现进度追踪，标记健康目标与小票 OCR 功能的缺失 Issue，注明 CV/OCR 外部服务供应商待定。*
