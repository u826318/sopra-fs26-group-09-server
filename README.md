# Virtual Pantry

This is the backend README. You can find the frontend README [here](https://github.com/sopra-fs26-group-09/sopra-fs26-group-09-client/blob/main/README.md).

## Introduction

Virtual Pantry is a collaborative calorie and nutrition tracking app for households. Users can add products by barcode, name, or receipt scan to a shared pantry, then log consumption from existing items. This avoids repeatedly searching for the same food and helps households track products that are bought, stored, and consumed by different people.

## Technologies Used

- **Java 17** and **Spring Boot 4** for the backend REST API and application
  structure.
- **Spring Web MVC** for controllers and authenticated HTTP endpoints.
- **Spring Data JPA** and **Hibernate** for persistence of users, households,
  pantry items, consumption logs, health goals, and nutrition-related entities.
- **H2** as the local in-memory development and test database.
- **Gradle** for dependency management, builds, and test execution.
- **OpenAI API** for meal food recognition and portion estimation from food
  images.
- **Azure Document Intelligence** for receipt OCR and structured extraction of
  receipt line items.
- **OpenFoodFacts API** and a bundled **local product dataset** for product
  lookup, barcode resolution, product-name search, nutrition metadata, and
  receipt item matching.
- **RecipeAPI.io** for external recipe recommendations, with a curated local
  JSON recipe catalog and dynamic pantry-based recipe generation as fallbacks.
- **WebSocket (STOMP over SockJS)** for real-time pantry update broadcasts to
  connected frontend clients.
- **Jackson** for JSON parsing and mapping external API responses into backend
  DTOs.
- **JUnit 5**, **Mockito**, and **Spring MockMvc** for service, controller, and
  integration tests.
- **Google App Engine** for backend deployment through the GitHub Actions
  workflow.

## High-level components

### 1. Household and pantry management

This part manages the shared household pantry: households, household members, invite codes, pantry items, item consumption, item removal, calorie budgets, and household statistics.

The household endpoints are defined in [`HouseholdController`](src/main/java/ch/uzh/ifi/hase/soprafs26/controller/HouseholdController.java), and pantry actions go through [`PantryController`](src/main/java/ch/uzh/ifi/hase/soprafs26/controller/PantryController.java).

On the household side, [`HouseholdService.createHousehold`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/HouseholdService.java#L89-L109) creates a household and adds the creator as a member. [`HouseholdService.joinHouseholdByInviteCode`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/HouseholdService.java#L182-L206) lets another user join with an invite code, and [`HouseholdService.getStats`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/HouseholdService.java#L261-L380) builds the statistics view from consumption logs.

On the pantry side, [`PantryService.addItem`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/PantryService.java#L164-L181) validates and saves one pantry item, while [`PantryService.bulkAddItems`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/PantryService.java#L187-L220) saves multiple items in one request. [`PantryService.consumeItem`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/PantryService.java#L501-L618) handles the main consumption flow: it checks membership, validates the consumed amount, subtracts the correct amount from the pantry, records a `ConsumptionLog`, calculates consumed calories through [`PantryService.computeConsumedCalories`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/PantryService.java#L87-L118), and sends nutrient data to [`DailyNutrientIntakeService.recordConsumedPantryItem`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/DailyNutrientIntakeService.java#L59-L89). For unit conversion, it uses helper methods such as [`PantryService.resolveNutritionBasisMultiplier`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/PantryService.java#L628-L645), [`PantryService.resolveConsumedBasisAmount`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/PantryService.java#L647-L673), and [`PantryService.resolveInventoryAmountToSubtract`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/PantryService.java#L675-L706). If the user only removes an item without eating it, [`PantryService.removeItem`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/PantryService.java#L712-L764) updates the pantry without logging nutrition.

After pantry changes, the backend sends live update messages through [`PantryBroadcastService.broadcastPantryUpdate`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/PantryBroadcastService.java#L16-L21), so the frontend can refresh the pantry state.

### 2. Product lookup and local dataset search

This component is responsible for finding product information. A product can be looked up by barcode, by product index, or by name. The REST endpoints are in [`ProductController`](src/main/java/ch/uzh/ifi/hase/soprafs26/controller/ProductController.java).

For barcode lookup, [`ProductController.lookupByBarcode`](src/main/java/ch/uzh/ifi/hase/soprafs26/controller/ProductController.java#L32-L35) and [`ProductController.lookupByBarcodePath`](src/main/java/ch/uzh/ifi/hase/soprafs26/controller/ProductController.java#L37-L40) both go through [`ProductController.lookupLocalProductByBarcode`](src/main/java/ch/uzh/ifi/hase/soprafs26/controller/ProductController.java#L59-L70). That method calls [`LocalDatasetLookupService.findRawRowByBarcode`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/localdatasetlookup/LocalDatasetLookupService.java#L34-L55), which normalizes the barcode, finds the correct local dataset bucket, and scans that bucket for the product row.

For product-index lookup, [`ProductController.lookupByProductIndex`](src/main/java/ch/uzh/ifi/hase/soprafs26/controller/ProductController.java#L42-L49) calls [`LocalDatasetLookupService.findRawRowByProductIndex`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/localdatasetlookup/LocalDatasetLookupService.java#L57-L67). For resolving many product indices at once, the backend uses [`LocalDatasetLookupService.findRawRowsByProductIndices`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/localdatasetlookup/LocalDatasetLookupService.java#L69-L98).

For name search, [`ProductController.searchByName`](src/main/java/ch/uzh/ifi/hase/soprafs26/controller/ProductController.java#L51-L57) calls [`LocalDatasetNameSearchService.search`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/localdatasetlookup/LocalDatasetNameSearchService.java#L44-L108). That method tokenizes the query, checks which tokens exist in the local name index, selects candidate product indices, and then ranks the candidates. The actual ranking and response-building happens in [`LocalDatasetNameSearchService.rankAndAttachCandidates`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/localdatasetlookup/LocalDatasetNameSearchService.java#L110-L168). If a query is too broad, [`LocalDatasetNameSearchService.attachTooManyMatchesSample`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/localdatasetlookup/LocalDatasetNameSearchService.java#L170-L216) returns a small stable sample instead of trying to return everything.

Finally, [`LocalDatasetProductMapper.toDto`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/localdatasetlookup/LocalDatasetProductMapper.java#L77-L107) converts raw local dataset rows into the DTO format used by the frontend. This includes barcode, product name, brand, image URL, quantity fields, serving/package fields, nutrition data, and consumption options.

### 3. Receipt scanning and product matching

This component lets users upload a receipt image and turn it into structured food items. The endpoint is [`ReceiptController.uploadReceipt`](src/main/java/ch/uzh/ifi/hase/soprafs26/controller/ReceiptController.java#L24-L32). The main workflow is handled by [`ReceiptUploadService.uploadReceipt`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/ReceiptUploadService.java#L56-L62).

[`ReceiptUploadService.validateReceiptImage`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/ReceiptUploadService.java#L74-L91) checks that the uploaded file is a valid JPG or PNG receipt image, and [`ReceiptOcrService.analyzeReceipt`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/ReceiptOcrService.java#L53-L80) sends the image to Azure Document Intelligence.

Inside the OCR service, [`ReceiptOcrService.submitAnalyzeRequest`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/ReceiptOcrService.java#L82-L109) submits the image to Azure, [`ReceiptOcrService.pollAnalyzeResult`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/ReceiptOcrService.java#L111-L157) waits for the result, and [`ReceiptOcrService.mapReceiptAnalysis`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/ReceiptOcrService.java#L171-L207) maps the Azure result into our own receipt DTO.

After OCR, [`ReceiptUploadService.buildUploadResponse`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/ReceiptUploadService.java#L98-L119) builds the final response. It also calls [`ReceiptUploadService.attachLocalNameSearchCandidates`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/ReceiptUploadService.java#L121-L186), which runs local product name search for each receipt line and attaches possible product matches. So the rough flow is: receipt image -> Azure OCR -> extracted receipt lines -> local product candidates.

### 4. Nutrition, micronutrients, and daily intake tracking

This component tracks the nutrition side of the application. Users can store a personal profile, retrieve micronutrient reference values, and view daily nutrient intake.

The personal profile endpoints are in [`UserPersonalProfileController`](src/main/java/ch/uzh/ifi/hase/soprafs26/controller/UserPersonalProfileController.java). The actual profile logic is in [`UserPersonalProfileService.createOrUpdatePersonalProfile`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/UserPersonalProfileService.java#L50-L76), which saves the user birth date and life stage group. [`UserPersonalProfileService.calculateAgeInMonths`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/UserPersonalProfileService.java#L112-L120) calculates age in months, which is needed because micronutrient requirements depend on both age and life stage.

Micronutrient reference values are exposed through [`MicronutrientReferenceController.getMicronutrientRequirementsForUser`](src/main/java/ch/uzh/ifi/hase/soprafs26/controller/MicronutrientReferenceController.java#L24-L31). It calls [`MicronutrientReferenceService.getRequirementsForUser`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/MicronutrientReferenceService.java#L35-L43), which loads the user's profile and age, then calls [`MicronutrientReferenceService.findRequirements`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/MicronutrientReferenceService.java#L45-L58) to filter the reference table by life stage and age range.

Daily intake is exposed through [`UserDailyNutrientIntakeController.getDailyNutrientIntake`](src/main/java/ch/uzh/ifi/hase/soprafs26/controller/UserDailyNutrientIntakeController.java#L28-L42). It calls [`DailyNutrientIntakeService.getDailyIntakeOrEmpty`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/DailyNutrientIntakeService.java#L36-L41), which returns the stored daily intake or an empty intake object for that date.

This part connects back to the pantry through consumption. When a user consumes a pantry item, [`PantryService.consumeItem`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/PantryService.java#L501-L618) computes the nutrient multiplier with [`PantryService.resolveNutritionBasisMultiplier`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/PantryService.java#L628-L645), then passes the result to [`DailyNutrientIntakeService.recordConsumedPantryItem`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/DailyNutrientIntakeService.java#L59-L89). That method finds or creates the daily intake row and adds micronutrients through [`DailyNutrientIntakeService.addConsumedMicronutrients`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/DailyNutrientIntakeService.java#L98-L132).

### How the components work together

The household and pantry component is the center of the backend. Users add products into a shared household pantry. Product information can come from barcode lookup, product-index lookup, name search, or receipt scanning. When users consume pantry items, [`PantryService.consumeItem`](src/main/java/ch/uzh/ifi/hase/soprafs26/service/PantryService.java#L501-L618) updates the pantry state, writes a consumption log, recalculates pantry calories, broadcasts the update, and sends nutrition data to daily intake tracking.

In other words, the backend is built around one main flow: find food products, add them to a shared pantry, consume them, and use that consumption data for household statistics and personal nutrition tracking.

### Local Datasets construction

#### Local Product Dataset

The local product dataset consisting [`buckets`](src/main/resources/local-dataset/buckets) and a [`manifest`](src/main/resources/local-dataset/manifest.json) is the main product lookup source used by the backend. It is generated offline from the Open Food Facts product dump and reduced into a compact format that only keeps the fields needed by the Virtual Pantry application.

To build the dataset, we read the raw Open Food Facts data, filter and clean product rows, extract useful product information, normalize quantity and nutrition data, assign each product a stable `product_index`, and write a smaller local dataset for backend lookup.

The local product dataset records fields from the original dataset such as:

```text
code
brands
product_quantity
product_quantity_unit
package_quantity
package_quantity_unit
serving_quantity
serving_quantity_unit
nutrition_basis_unit
nutrition
```

It also combines name related columns to a list of names:

```text
name candidates
```

And also, it refactors the image column and only extract the parts needed to reconstruct the product image url. There are two image url component recording schemas in the original dataset, sometimes both schema has data stored inside, so both are recorded. 

```text
image_1
image_2
```


The dataset is sorted by barcode and split into smaller bucket files. During this process, each product receives a stable `product_index` in barcode-ascending order.

column name:

```text
product_index, code, brands, name_candidates, ...
```

Row Example:

```text
1 = smallest barcode row
2 = second-smallest barcode row
3 = third-smallest barcode row
...
```

The bucketed local dataset is stored with a manifest file:

```text
local-dataset/
  manifest.json
  buckets/
    bucket_000.csv
    bucket_001.csv
    ...
```

The manifest records both barcode ranges and product-index ranges for each bucket. This allows the backend to resolve products in two ways:

```text
barcode -> bucket -> product row
```

or:

```text
product_index -> bucket -> product row
```

This structure avoids scanning the full product dataset for every lookup. Instead, the backend reads the manifest, finds the relevant bucket, and scans only that smaller file.

Nutrition values are stored in a standardized per-100 format. The `nutrition_basis_unit` field describes whether the values in the `nutrition` cell are per `100g` or per `100ml`.

```text
nutrition_basis_unit = g  -> nutrition values are per 100g
nutrition_basis_unit = ml -> nutrition values are per 100ml
```

The `nutrition` cell stores an indexed JSON dictionary, where numeric keys refer to nutrient definitions shared between the dataset builder and the backend.

Example:

```json
{"0":533.0,"1":30.9,"13":3300.0}
```

The dataset standardize per-100 nutrition data and records package quantity when available. This allows the backend to calculate package-level or consumed nutrition later when enough quantity information exists.

#### Product Name Index

The product name index dataset [`name-index`](src/main/resources/local-dataset/name-index) supports local product search by name. It is generated from the local product dataset and uses the same `product_index` values as the local product buckets.

The name-index dataset is built from searchable product text, mainly:

```text
brands
name_candidates
product_index
```

During generation, product names and brands are normalized and tokenized, for example, if we have something like 

```text
brand: tiger kitchen
name: udon noodles
product_index: 101
```

the brand and name will be normalized (decapitalized, trimmed...), and tokenized into "tiger", "kitchen", "udon", "noodles". The index will corespond to which products are connected to each search token, so we will have 4 entries from the example product:

```text
"tiger": [101]
"kitchen": [101]
"udon": [101]
"noodles": [101]
```

Eventually, we will have:

```text
token -> a list of product_index values

yogurt -> 12 55 9001...
milk   -> 44 55 87...
noodles -> 101 10098 78239 413876...
...
```

At runtime, when a user searches for a product name, the backend normalizes and tokenizes the query, finds matching `product_index` values from the name-index, combines and ranks the candidates, and then resolves the selected product through the local product dataset.

For example "tik udon noodles" will be tokenized to "tik", "udon", "noodles", and we search each token in the name-index dataset, and then filter and rank candidates. 

The generated name-index resources are stored as streamable CSV/GZIP shards:

```text
name-index/
  manifest.json
  token-postings/
    shard_000.csv.gz
    shard_001.csv.gz
    ...
  product-metadata/
    shard_000.csv.gz
    shard_001.csv.gz
    ...
```

The token-posting shards store mappings from search tokens to product indices. The product metadata shards store minimal display and search metadata:

```text
product_index
brands
name_candidates
```

The full product details are still resolved through the local product dataset after a product candidate is selected.

This keeps the name index small.

Together, the local product dataset and the name index support the main product lookup flow of Virtual Pantry:

```text
user searches or scans product
        ↓
backend resolves product locally
        ↓
product data is returned to the client
        ↓
user adds the product to the shared pantry
        ↓
nutrition values are used for calorie and nutrition tracking
```





## Launch & Deployment

### Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes. See the Deployment section below for notes on how to deploy the project on a live system.

### Prerequisites

Make sure you have the following installed:

**Frontend**
- [Node.js 22+](https://nodejs.org/) and npm

**Backend**
- [Java 17+](https://adoptium.net/)
- An [OpenAI API key](https://platform.openai.com/api-keys) (required for meal food recognition)
- An Azure Document Intelligence endpoint and API key (required for receipt scanning): set `AZURE_DOCUMENT_INTELLIGENCE_ENDPOINT` and `AZURE_DOCUMENT_INTELLIGENCE_API_KEY`

### Installing

Clone both repositories:

```bash
git clone https://github.com/sopra-fs26-group-09/sopra-fs26-group-09-server.git
git clone https://github.com/sopra-fs26-group-09/sopra-fs26-group-09-client.git
```

Start the backend:

```bash
cd sopra-fs26-group-09-server
OPENAI_API_KEY=<your-key> ./gradlew bootRun
```

The server starts on **http://localhost:8080**. An in-memory H2 database is created automatically — no database installation needed. You can inspect it at:

- URL: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:testdb`
- User: `sa`
- Password: *(leave empty)*

Start the frontend (from the parent directory):

```bash
cd ../sopra-fs26-group-09-client
npm install
npm run dev
```

Open **http://localhost:3000** in your browser.

### Running the tests

**Frontend**

```bash
npm test                    # single run
npm run test:coverage       # with coverage report
```

**Backend**

```bash
cd ../sopra-fs26-group-09-server
./gradlew test
```

### Deployment

Every push to `main` triggers the GitHub Actions workflows automatically:

- **Frontend** is deployed to [Vercel](https://vercel.com).
- **Backend** is deployed to [Google App Engine](https://cloud.google.com/appengine).

**One-time setup**:

Frontend — add the following [repository secrets](https://docs.github.com/en/actions/security-guides/using-secrets-in-github-actions) to the client repo:
- `VERCEL_TOKEN`
- `VERCEL_ORG_ID`
- `VERCEL_PROJECT_ID`

And the following environment variable:
- `NEXT_PUBLIC_PROD_API_URL` — your backend's public URL (e.g. the App Engine URL). If not set, defaults to the hosted server URL defined in `app/utils/domain.ts`.

Backend — add the following secret to the server repo:
- `GCP_SERVICE_CREDENTIALS` (Google Cloud service account JSON)

**Build for production manually:**

```bash
# Frontend (from sopra-fs26-group-09-client)
npm run build
npm run start               # serves the build on http://localhost:3000

# Backend (from sopra-fs26-group-09-server)
./gradlew clean build
java -jar build/libs/*.jar
```

## Roadmap

New developers can contribute by adding the following features:

- **LLM diet chatbot** — A chat interface where users can ask diet and nutrition questions. Answers are personalized based on the current pantry, consumption history, and health goals.

- **Auto-generated shopping list** — Automatically build a shopping list from low-stock pantry items and missing recipe ingredients. The list can be shared across household members.

- **Weekly meal planner** — Generate a personalized weekly meal plan based on health goals and current pantry contents. The planner highlights missing ingredients and estimates daily calorie coverage for each day.

## Authors and Acknowledgment
This project was developed by Group 09 as part of the Software Praktikum (SoPra) FS26 at the University of Zurich.

### Authors

- Maxim Emelianov
- Tingting Xu    
- Tingyuan Wang
- Yifu Li

### Acknowledgment

We would like to thank the SoPra teaching team and our teaching assistant for their guidance and feedback throughout the project. We also acknowledge Open Food Facts and Azure Document Intelligence for providing datasets and external services that helped support the product lookup and receipt scanning features.
## License

This project is licensed under the [Apache License 2.0](LICENSE).
