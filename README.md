# Virtual Pantry

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
- **OpenAI API** for receipt OCR and structured extraction of receipt line
  items.
- **OpenFoodFacts API** and a bundled **local product dataset** for product
  lookup, barcode resolution, product-name search, nutrition metadata, and
  receipt item matching.
- **RecipeAPI.io** for external recipe recommendations, with a curated local
  JSON recipe catalog and dynamic pantry-based recipe generation as fallbacks.
- **Jackson** for JSON parsing and mapping external API responses into backend
  DTOs.
- **JUnit 5**, **Mockito**, and **Spring MockMvc** for service, controller, and
  integration tests.
- **Google App Engine** for backend deployment through the GitHub Actions
  workflow.

## High-Level Components

## Launch & Deployment

### Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes. See the Deployment section below for notes on how to deploy the project on a live system.

### Prerequisites

Make sure you have the following installed:

- [Java 17+](https://adoptium.net/)
- An [OpenAI API key](https://platform.openai.com/api-keys) (required for receipt scanning and meal recognition)

### Installing

Clone the repository:

```bash
git clone https://github.com/sopra-fs26-group-09/sopra-fs26-group-09-server.git
cd sopra-fs26-group-09-server
```

Start the backend:

```bash
OPENAI_API_KEY=<your-key> ./gradlew bootRun
```

The server starts on **http://localhost:8080**. An in-memory H2 database is created automatically — no database installation needed. You can inspect it at:

- URL: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:testdb`
- User: `sa`
- Password: *(leave empty)*

### Running the tests

```bash
./gradlew test
```

### Deployment

Every push to `main` triggers the GitHub Actions workflow, which automatically deploys the backend to [Google App Engine](https://cloud.google.com/appengine).

To release a new version, merge your changes into `main`. The CI/CD pipeline will automatically build and deploy the backend.

**One-time setup** (one team member):

Add the following [repository secret](https://docs.github.com/en/actions/security-guides/using-secrets-in-github-actions) to the server repo:
- `GCP_SERVICE_CREDENTIALS` (Google Cloud service account JSON)

**Build for production manually:**

```bash
./gradlew clean build
java -jar build/libs/*.jar
```

## Illustrations

## Roadmap

## Authors and Acknowledgment

## License

This project is licensed under the [Apache License 2.0](LICENSE).
