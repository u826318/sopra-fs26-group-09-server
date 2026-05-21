# Virtual Pantry

## Introduction

## Technologies Used

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
