# AGENTS.md

This repository is a multi-account Spring Boot/Kotlin app for drafting tailored cover letters and resumes with Google Gemini. Local development binds to `127.0.0.1`; Cloud Run serves authenticated Google Sign-In accounts from the `ALLOWED_EMAILS` allowlist. Each account owns its resumes, supporting documents, applied jobs, Gemini settings, and activity logs. One configured email is the administrator and can view every account's data.

## Project snapshot

- App package root: `src/main/kotlin/com/mosta/coveringletter`
- Main app entry point: `CoveringletterApplication.kt`
- Web/API layer: `src/main/kotlin/com/mosta/coveringletter/api`
- Business generation: `src/main/kotlin/com/mosta/coveringletter/generation`
- Logging and cross-cutting concerns: `src/main/kotlin/com/mosta/coveringletter/aspects`
- Applied jobs tracking: `src/main/kotlin/com/mosta/coveringletter/appliedjobs`
- PDF generation: `src/main/kotlin/com/mosta/coveringletter/pdf`
- User action logging: `src/main/kotlin/com/mosta/coveringletter/activity`
- Persistence for resumes/documents/settings: `resume`, `supportingdocument`, `settings`, `documents`
- Local data directory: `.data` (Git-ignored); root-level `resumes`, `supporting-documents`, and `applied-jobs` are the shared legacy workspace, while `accounts/<encoded-email>/` holds private account data
- Root `.env` (Git-ignored): Google OAuth credentials and initial `ALLOWED_EMAILS`; `.data/.account-access` stores the persistent allowlist and administrator assignment
- Gemini API keys and models are stored per account in `.data/accounts/<encoded-email>/.env`; do not configure a shared Gemini API key for application accounts
- AI integration: per-account Google Gemini via Spring AI and `WebClient`
- Authenticated MCP server: `http://127.0.0.1:8080/mcp` exposes `generate_cover_letter` using the active account's data
- Gradle Configuration Cache is enabled via `gradle.properties` for faster builds
- Cloud deployment: `application-cloud.yaml` binds to `0.0.0.0`; Cloud Storage persists `.data` as `data-backup.zip`, and the deploy workflow limits Cloud Run to one instance because the file-backed archive is not multi-writer storage

## Build and run

Run from the project root.

### JVM mode (Spring Boot)

Windows (PowerShell):

```powershell
.\gradlew.bat bootRun
```

Linux/macOS:

```bash
chmod +x ./gradlew
./gradlew bootRun
```

### Native executable

Requires GraalVM 25+ with Native Image and the required C/C++ build tooling for the target OS.

Windows:

```powershell
.\gradlew.bat nativeCompile
```

Linux/macOS:

```bash
./gradlew nativeCompile
```

### Cloud Run deployment

The app can be deployed to Google Cloud Run on the free tier. See `CLOUD_DEPLOYMENT.md` for a step-by-step guide. Key points:

- Activate the `cloud` Spring profile (`-Dspring.profiles.active=cloud`) to bind to `0.0.0.0` instead of `127.0.0.1`
- Each user configures their own Gemini API key/model after signing in; do not set a shared `GEMINI_API_KEY`
- Cloud Storage syncs the complete `.data` tree at startup/shutdown; generation preconditions prevent stale instance snapshots from overwriting newer archives
- Keep Cloud Run at one instance unless file-backed storage is replaced with a multi-writer-safe backend
- Google Sign-In and the allowed-email list protect application data; IAM can be added as an extra access layer

### Tests

```powershell
.\gradlew.bat test
```

```bash
./gradlew test
```

## Runtime configuration and secrets

- The root `.env` file or environment variables provide Google OAuth credentials and optionally the initial `ALLOWED_EMAILS` / `ADMIN_EMAIL` values.
- `AllowedEmailsService` persists account access and the administrator in `.data/.account-access`; the first configured address is the default administrator.
- `AppSettingsService` reads and writes Gemini settings only in the authenticated account's `.data/accounts/<encoded-email>/.env` file.
- The default model is `gemini-3.1-flash-lite` when available.
- The app binds to `127.0.0.1` by default. The `cloud` profile overrides this to `0.0.0.0` for containerized deployment.
- Keep `.env` private; it is excluded from Git.
- Allowed addresses can be changed only by the administrator in Settings. Empty allowlists cannot be saved; the administrator-only reset returns the app to first-run account setup.
- Access and refresh tokens are checked against the current allowlist so removed accounts cannot continue using existing tokens.

## Architecture and service boundaries

### API layer

The REST API is thin and delegates to domain services. Controllers contain **no logging** — all logging is handled by aspects.

- `GenerationController` handles `POST /api/generate`
- `ResumeController`, `SupportingDocumentsController`, `SettingsController`, `PdfController`, `LogsController`, and `AppliedJobsController` each map to a focused store/service boundary
- API errors are surfaced as `ResponseStatusException` and mapped by `ApiExceptionHandler`
- `AppliedJobsController` manages applied job entries with status tracking (`PROGRESSING` / `REJECTED`), filtering, and bulk deletion
- All user data controllers derive account identity from the authenticated principal; never accept an account email from a regular user's request to select another workspace
- `AdminDataController` exposes read-only all-account resume/document views and an owner-labeled applied-jobs listing only to the configured administrator

### Generation flow

The core path is:

1. `ResumeStore.extractStored(accountEmail)` reads the account-selected resume, including shared legacy data
2. `SupportingDocumentStore.extractAll(accountEmail)` reads the account-selected supporting docs
3. `CoverLetterGenerationService.generate(...)` validates job description, format, and total extracted text against project limits
4. `GeminiCoverLetterService` (implements `CoverLetterGenerator` interface) calls Gemini and returns a `GeneratedCoverLetter`
5. All logging (both server-side SLF4J and user-facing action entries) is handled by the `aspects` package

`CoverLetterMcpTools` exposes the same business flow to the MCP server via `generate_cover_letter` and requires the authenticated account context.

### Logging strategy

**All logging is centralized in the `aspects` package.** No SLF4J `logger` calls or `UserActionLogBuffer.record()` calls exist in regular code (controllers, services, stores).

- `ApiExceptionLoggingAspect` — `@Around` advice that logs API errors (warn/error) transparently before/after `ApiExceptionHandler` methods execute
- `ControllerActionLoggingAspect` — `@AfterReturning` advice that records user-facing action log entries after each controller method succeeds
- `GeminiServiceLoggingAspect` — `@Component` injected as a dependency; handles both SLF4J server logs and user-facing action logs for Gemini operations (model contact, reply, switch, failure, retry)

When adding new logging, always add it to the appropriate aspect class rather than inline in code.

### Persistence patterns

Each domain stores data under `.data` using the same pattern:

- Root-level `.data/resumes`, `.data/supporting-documents`, and `.data/applied-jobs` hold shared legacy data, visible to all accounts
- `AccountDataDirectories` maps authenticated emails to `.data/accounts/<encoded-email>/`; new resumes, supporting documents, applied jobs, and Gemini settings are saved there
- Each account has independent resume/document selections; deleting a shared legacy record removes it for everyone and removes matching migration copies, while private records are deletable only by their owner
- `.data/.account-access` stores the persistent allowlist and administrator role
- `DocumentTextExtractor` extracts text from PDF/DOCX input, with limits enforced by `DocumentTextExtractorLimits`

These stores are not generic repositories; they are domain-specific file stores with selection metadata, file limits, and extraction logic embedded in the store layer.

## Code conventions to follow

### Kotlin and Spring conventions

- Use constructor injection and Spring stereotype annotations (`@Service`, `@RestController`, `@Component`)
- Prefer reactive types (`Mono`, `Flux`) for web and file I/O
- Validate on entry with `ResponseStatusException(HttpStatus.BAD_REQUEST, ...)`
- Keep controllers slim; they should delegate to services and not contain business logic
- **No inline logging** — never add `logger.warn/error/info/debug` or `logs.record()` in controllers, services, or stores; use the `aspects` package instead
- The `CoverLetterGenerator` interface abstracts the Gemini generation contract, enabling testing with mock generators

### Domain-specific patterns

- Store classes are named like `ResumeStore`, `SupportingDocumentStore`, and hold both read/write logic and selection metadata
- Reader/writer collaborator classes are often split into `*Reader` and `*Writer` helper classes for the same domain
- File-size and content-length guards live in small limit classes such as:
  - `ResumeStoreLimits`
  - `SupportingDocumentStoreLimits`
  - `CoverLetterGenerationLimits`
  - `DocumentTextExtractorLimits`
  - `UserActionLogBufferLimits`
- Business output types are explicit, for example `GeneratedCoverLetter`, `GenerationFormat`, `ResumeStatus`, `ResumeOption`, `SupportingDocument`, and `AppSettingsStatus`

### Data and file handling

- PDF/DOCX uploads are accepted, but only text-based PDFs are supported; scanned/image-only docs require OCR and are not currently handled
- File uploads are limited to 10 MB each
- Extracted document text is capped at 100,000 characters total for generation
- The app writes generated PDFs and metadata to the project-local `.data` structure rather than a database

## Working rules for AI agents

- Keep changes aligned with the existing package layout: `api`, `generation`, `resume`, `supportingdocument`, `settings`, `documents`, `pdf`, `activity`, `aspects`, `appliedjobs`.
- When adding features, prefer extending the existing service/store pattern instead of creating a new persistence layer.
- Keep local development bound to localhost by default; preserve Google Sign-In, JWT allowlist checks, and admin-only cross-account APIs.
- Derive account ownership from `Principal`; do not accept client-supplied owner emails for regular account operations.
- Shared legacy records are visible to all and deletable by any user; private account records must remain isolated from other non-admin accounts.
- Use the same validation style as the codebase: reject bad input early with `ResponseStatusException` and a clear user-facing message.
- For generation changes, update the shared `CoverLetterGenerationService` path rather than duplicating logic in controllers.
- If adjusting Gemini integration, preserve `AppSettingsService` model validation and save account settings only under the authenticated account directory.
- When editing storage behavior, check both file selection metadata and the corresponding `*StoreReader` / `*StoreWriter` pair.
- **Logging must go through aspects** — never add inline `logger` or `logs.record()` calls in regular code; instead, extend the appropriate aspect class in the `aspects` package.
- The Gemini service supports automatic retries (2 attempts with exponential backoff) for connection failures, rate limits, and server errors before falling back to alternative models.
- Applied jobs support status tracking (`PROGRESSING` / `REJECTED`) with filter-based bulk deletion via `AppliedJobsQuery`.

## Examples of important files

- `README.md` — authoritative runtime and setup documentation
- `build.gradle.kts` — Kotlin 2.3.21, Spring Boot 4.1.2, Spring AI MCP, Java 25 toolchain, AspectJ for logging
- `gradle.properties` — Gradle Configuration Cache enabled for faster builds
- `src/main/kotlin/com/mosta/coveringletter/generation/CoverLetterGenerationService.kt` — validation + orchestration of document extraction + Gemini generation
- `src/main/kotlin/com/mosta/coveringletter/generation/CoverLetterGenerator.kt` — interface that abstracts the Gemini generation contract
- `src/main/kotlin/com/mosta/coveringletter/generation/GeminiCoverLetterService.kt` — implements `CoverLetterGenerator`; handles retries and model fallback
- `src/main/kotlin/com/mosta/coveringletter/settings/AppSettingsService.kt` — `.env` handling and Gemini model validation
- `src/main/kotlin/com/mosta/coveringletter/resume/ResumeStore.kt` — selected-resume storage and extraction flow
- `src/main/kotlin/com/mosta/coveringletter/supportingdocument/SupportingDocumentStore.kt` — supporting document selection and extraction flow
- `src/main/kotlin/com/mosta/coveringletter/generation/CoverLetterMcpTools.kt` — MCP tool contract for external clients
- `src/main/kotlin/com/mosta/coveringletter/aspects/GeminiServiceLoggingAspect.kt` — centralized logging for Gemini operations (both SLF4J and user action logs)
- `src/main/kotlin/com/mosta/coveringletter/aspects/ControllerActionLoggingAspect.kt` — `@AfterReturning` aspects that record user action logs after controller methods
- `src/main/kotlin/com/mosta/coveringletter/aspects/ApiExceptionLoggingAspect.kt` — `@Around` aspects that log API errors transparently
- `src/main/kotlin/com/mosta/coveringletter/appliedjobs/AppliedJobStore.kt` — applied job persistence with status tracking and filter-based deletion
- `src/main/kotlin/com/mosta/coveringletter/api/controller/AdminDataController.kt` — administrator-only all-account data endpoints
- `src/main/kotlin/com/mosta/coveringletter/persistence/AccountDataDirectories.kt` — email-derived private account storage paths
- `src/main/kotlin/com/mosta/coveringletter/persistence/LegacyDataAccountMigration.kt` — one-time cloud archive copy task implementation
- `src/main/kotlin/com/mosta/coveringletter/appliedjobs/AppliedJob.kt` — applied job data model with `AppliedJobStatus` enum (`PROGRESSING` / `REJECTED`)
- `src/main/resources/application-cloud.yaml` — Spring profile for cloud deployment (binds to `0.0.0.0`)
- `Dockerfile` — container image definition for Cloud Run deployment
- `CLOUD_DEPLOYMENT.md` — step-by-step guide for deploying to Google Cloud Run, including CI/CD setup
- `.github/workflows/deploy.yml` — GitHub Actions workflow for automatic Cloud Run deployment on push to `master`

This repo is not a generic CRUD app; it is a multi-account document-processing and generation app whose domain logic centers around account-selected resume content, supporting documents, Gemini prompts, and generated PDF output, with shared legacy data retained for migration.

