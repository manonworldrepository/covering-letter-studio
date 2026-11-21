# Changelog

All notable changes to the Cover Letter Studio app are documented in this file.

## [v3.2.8] - 2026-11-21

### Added
- Loading spinner on the logout button while the logout request is processing
- Full-page dimming overlay with "Signing out..." text and spinner during logout
- Event log panel remains visible above the logout overlay so activity logs are still accessible during logout

## [v3.2.7] - 2026-11-21

### Added
- Activity panel logging for Google authentication events: "Signed in with Google: {email}" on initial sign-in and "Session refreshed via Google Sign-In" when session is renewed
- Session heartbeat endpoint `/api/session/heartbeat` that checks authentication status every 2 minutes
- Automatic redirect to login page when session expires, with `session_expired` error message on the login page
- Session refresh flow: when session expires, user is redirected to login page and can sign in again to renew the session

## [v3.2.5] - 2026-11-20

### Added
- Session heartbeat endpoint `/api/session/heartbeat` to detect expired sessions while the browser tab remains open
- Automatic redirect to login page when session expires, showing "Your session has expired. Please sign in again." message
- Heartbeat check runs every 2 minutes to detect session expiration before user actions fail

## [v3.2.4] - 2026-11-20

### Fixed
- Gemini API key and model settings were lost after Cloud Run scaled to zero because the `.env` file lived at the project root, outside the `.data/` directory synced to Cloud Storage
- Moved `.env` file from project root to `.data/.env` so it is included in the Cloud Storage backup and restored on startup

## [v3.2.3] - 2026-11-20

### Fixed
- Gemini API key and model settings were lost after Cloud Run scaled to zero because the `.env` file lived at the project root, outside the `.data/` directory synced to Cloud Storage
- Moved `.env` file from project root to `.data/.env` so it is included in the Cloud Storage backup and restored on startup

## [v3.2.2] - 2026-11-20

### Added
- Smooth scrolling throughout the UI with `scroll-behavior: smooth` CSS rule
- Auto-scroll to the generated cover letter section when generation finishes, covering both the direct "Generate" button and the setup wizard's "Generate first draft" flow

## [v3.2.1] - 2026-11-20

### Added
- Cloud Storage sync to persist `.data` directory across Cloud Run container restarts (scale-to-zero)
- `CloudStorageSyncService` automatically downloads data on startup and uploads on shutdown
- Configured via `CLOUD_STORAGE_BUCKET` environment variable, enabled by default on `cloud` profile

### Fixed
- GitHub Actions CI/CD workflow: split `--set-env-vars` into individual flags to prevent environment variable concatenation issues
- Cloud Run deployment now correctly sets all environment variables (GEMINI_MODEL, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, GEMINI_API_KEY, CLOUD_STORAGE_BUCKET)

### Changed
- Updated `CLOUD_DEPLOYMENT.md` with Cloud Storage setup instructions and free tier information

## [v3.2.0] - 2026-11-19

### Added
- Google Sign-In authentication with email-based access control (`info@manonworld.de` only)
- User info display with avatar, email, and logout button in the header bar
- Login page with blue background matching the app header
- API authentication entry point that returns proper 401 JSON responses instead of HTML login pages
- 34 comprehensive tests for security and authentication features:
  - `UserInfoResponseTests` — data class creation, equals/hashCode, toString, copy
  - `UserControllerTests` — OAuth2 user info extraction and fallbacks
  - `ApiAuthenticationEntryPointTests` — 401 JSON for API paths, redirect for non-API paths
  - `LoginSuccessHandlerTests` — email-based access control, case-insensitive matching
  - `LoginFailureHandlerTests` — failure redirect with error parameter
  - `SecurityConfigTests` — client registration, email parsing, handler instantiation
- Company name truncation to 15 characters in both the grid and exported PDF
- Blue bottom strip under section headings ("Your resumes", "Supporting documents", "Your applications", "Next actions")
- "Selected" badge labels in resume and supporting documents lists
- Hover and keyboard focus highlighting for resume and supporting document list rows
- Automatic retries (2 times) for Gemini API connection failures with user-facing retry logs
- Job status tracking (Progressing/Rejected) in the applied jobs grid with colored badges
- Status column in the exported `applied-jobs.pdf` file
- "Clear All" button in the applied jobs grid with a confirmation dialog
- Clear All button deletes only filtered/viewed jobs when filters are active
- Gradle Configuration Cache enabled via `gradle.properties`
- Cloud Run deployment support with `Dockerfile`, `.dockerignore`, and `application-cloud.yaml`
- GitHub Actions CI/CD pipeline for automatic Cloud Run deployment on push to `master`
- Cloud Logging quota alerts for storage (40 GiB) and Cloud Run request count (1.6M)
- 24 tests for applied job status tracking, controller endpoints, PDF generation, and Gemini failure messages
- 42 tests for logging aspect classes

### Fixed
- Session cookie handling with `credentials: same-origin` on all frontend fetch calls
- API endpoints now require authentication after Google Sign-In
- "Unexpected token '<'" error when generating cover letters with expired sessions

### Changed
- Header layout: brand on the left, nav menu centered, user info on the far right
- All API endpoints now require Google Sign-In authentication
- Cloud Run service deployed with `--allow-unauthenticated` at the platform level, with Spring Security handling authentication at the app level
- Moved "Clear all" buttons to the right side of section headings
- Merged Status and Actions columns in the applied jobs grid
- Moved Remove button to the end of the button sequence in the applied jobs grid
- Shortened status toggle button text to "Rejected"/"Progressing"
- Made Mark Rejected button red and Mark Progressing button green
- All logging moved to centralized aspects in the `aspects` package
- `CoverLetterGenerator` interface extracted for testable generation flow
- `GeminiCoverLetterService` now implements `CoverLetterGenerator` interface

### Removed
- Unused Lombok dependencies from `build.gradle.kts`
- Unused dependencies: `kotlin-reflect`, `kotlinx-coroutines-reactor`, `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `kotlinx-coroutines-test`
- "Selected resume" label from the resume section
