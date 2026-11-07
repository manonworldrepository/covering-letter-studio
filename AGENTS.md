# AGENTS.md

This repository is a single-user, localhost-only Spring Boot/Kotlin app for drafting tailored cover letters with Google Gemini. The app combines a saved resume, optional supporting documents, a job description, and a selected Gemini model to generate either a full cover letter or a short motivation message.

## Project snapshot

- App package root: `src/main/kotlin/com/mosta/coveringletter`
- Main app entry point: `CoveringletterApplication.kt`
- Web/API layer: `src/main/kotlin/com/mosta/coveringletter/api`
- Business generation: `src/main/kotlin/com/mosta/coveringletter/generation`
- Persistence for resumes/documents/settings: `resume`, `supportingdocument`, `settings`, `documents`
- Local data directory: `.data` (Git-ignored), with subfolders such as `resumes` and `supporting-documents`
- Runtime settings file: root-level `.env` (also Git-ignored)
- AI integration: Google Gemini via Spring AI and `WebClient`
- MCP server: `http://127.0.0.1:8080/mcp` exposing `generate_cover_letter`

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

### Tests

```powershell
.\gradlew.bat test
```

```bash
./gradlew test
```

## Runtime configuration and secrets

- The app expects either a root `.env` file or environment variables for:
  - `GEMINI_API_KEY`
  - `GEMINI_MODEL`
- `AppSettingsService` reads `.env`, then environment variables, then Spring properties, and writes a normalized `GEMINI_*` pair back to `.env` when settings are saved.
- The default model is `gemini-3.1-flash-lite` when available.
- The app binds to `127.0.0.1` and is intentionally single-user/local-only.
- Keep `.env` private; it is excluded from Git.

## Architecture and service boundaries

### API layer

The REST API is thin and delegates to domain services. Examples:

- `GenerationController` handles `POST /api/generate`
- `ResumeController`, `SupportingDocumentsController`, `SettingsController`, `PdfController`, and `LogsController` each map to a focused store/service boundary
- API errors are surfaced as `ResponseStatusException` and mapped by `ApiExceptionHandler`

### Generation flow

The core path is:

1. `ResumeStore.extractStored()` reads the selected resume
2. `SupportingDocumentStore.extractAll()` reads selected supporting docs
3. `CoverLetterGenerationService.generate(...)` validates job description, format, and total extracted text against project limits
4. `GeminiCoverLetterService` calls Gemini and returns a `GeneratedCoverLetter`
5. `UserActionLogBuffer` records the user activity entry

`CoverLetterMcpTools` exposes the same business flow to the MCP server via `generate_cover_letter`.

### Persistence patterns

Each domain stores data under `.data` using the same pattern:

- `ResumeStore` writes to `.data/resumes` and tracks the currently selected resume in `.data/.selected-resume`
- `SupportingDocumentStore` writes to `.data/supporting-documents` and tracks selected document IDs in `.data/supporting-documents/.selected-documents`
- `DocumentTextExtractor` extracts text from PDF/DOCX input, with limits enforced by `DocumentTextExtractorLimits`

These stores are not generic repositories; they are domain-specific file stores with selection metadata, file limits, and extraction logic embedded in the store layer.

## Code conventions to follow

### Kotlin and Spring conventions

- Use constructor injection and Spring stereotype annotations (`@Service`, `@RestController`, `@Component`)
- Prefer reactive types (`Mono`, `Flux`) for web and file I/O
- Validate on entry with `ResponseStatusException(HttpStatus.BAD_REQUEST, ...)`
- Keep controllers slim; they should delegate to services and not contain business logic

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

- Keep changes aligned with the existing package layout: `api`, `generation`, `resume`, `supportingdocument`, `settings`, `documents`, `pdf`, `activity`.
- When adding features, prefer extending the existing service/store pattern instead of creating a new persistence layer.
- Preserve the localhost-only security model; do not introduce global network exposure or auth flows.
- Use the same validation style as the codebase: reject bad input early with `ResponseStatusException` and a clear user-facing message.
- For generation changes, update the shared `CoverLetterGenerationService` path rather than duplicating logic in controllers.
- If adjusting Gemini integration, preserve the existing `AppSettingsService` contract for model validation and `.env` persistence.
- When editing storage behavior, check both file selection metadata and the corresponding `*StoreReader` / `*StoreWriter` pair.

## Examples of important files

- `README.md` — authoritative runtime and setup documentation
- `build.gradle.kts` — Kotlin 2.3.21, Spring Boot 4.1.2, Spring AI MCP, Java 25 toolchain
- `src/main/kotlin/com/mosta/coveringletter/generation/CoverLetterGenerationService.kt` — validation + orchestration of document extraction + Gemini generation
- `src/main/kotlin/com/mosta/coveringletter/settings/AppSettingsService.kt` — `.env` handling and Gemini model validation
- `src/main/kotlin/com/mosta/coveringletter/resume/ResumeStore.kt` — selected-resume storage and extraction flow
- `src/main/kotlin/com/mosta/coveringletter/supportingdocument/SupportingDocumentStore.kt` — supporting document selection and extraction flow
- `src/main/kotlin/com/mosta/coveringletter/generation/CoverLetterMcpTools.kt` — MCP tool contract for external clients

This repo is not a generic CRUD app; it is a local document-processing and generation app whose domain logic is centered around selected resume content, supporting documents, Gemini prompts, and generated PDF output.

