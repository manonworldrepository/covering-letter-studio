# Changelog

All notable changes to the Cover Letter Studio app are documented in this file.

## [Unreleased]

### Added
- Added per-account storage for resumes, supporting documents, applied jobs, Gemini settings, and activity logs while keeping legacy data visible to all accounts
- Added an administrator-only, read-only view of all account resumes and supporting documents, plus an applications grid that identifies each application's owner
- Added an offline legacy-data archive migration task and Cloud Storage generation checks to prevent stale instance snapshots overwriting newer account data
- Configured the Spring Boot application entry point explicitly so the migration CLI does not make container builds ambiguous

### Changed
- Allowed accounts and administrator assignment are now managed together in Settings; the first address defaults to administrator and account-list changes require an administrator
- The configured allowlist is checked on access and refresh tokens so removed users cannot continue using existing sessions
- Gemini API keys and models are stored per account; any user can delete a shared legacy record globally, while private records remain owner-only
- Added a confirmed administrator-only settings reset that clears the personal Gemini API key and global allowed-account list, preserves the selected model and Google OAuth credentials, signs out, and returns to first-run setup

## [v3.3.17] - 2026-11-27

### Fixed
- Cloud Run deployment no longer fails when the optional `ALLOWED_EMAILS` GitHub secret is unset; existing allowlist configuration is preserved, and fresh services can use first-run setup

## [v3.3.16] - 2026-11-27

### Added
- Added a one-time pre-login screen to configure allowed Google accounts and authenticated Settings controls to review, add, and remove allowed email addresses

### Changed
- Google Sign-In access now uses `ALLOWED_EMAILS` from the root `.env` file locally or the environment on Cloud Run, and the OAuth callback follows the current app host
- Activity logs are cleared automatically when the user signs out

## [v3.3.15] - 2026-11-27

### Fixed
- Upgraded GitHub Actions used by the Cloud Run deployment workflow to Node.js 24-compatible releases, removing Node.js 20 deprecation warnings

## [v3.3.14] - 2026-11-27

### Changed
- PDF filenames now use a document-type suffix instead of the date and shorten job titles longer than 25 characters to their first two words

## [v3.3.13] - 2026-11-27

### Fixed
- Cloud Run deployment verification now checks the revision created by that deployment and accepts its platform-specific digest only when it belongs to the exact pushed image

## [v3.3.12] - 2026-11-27

### Changed
- Cloud Run deployment now deploys the exact pushed image digest and verifies the latest commit's serving revision and traffic; added a deployment-status badge to the README

## [v3.3.11] - 2026-11-27

### Changed
- Added horizontal dividers between experience entries in tailored resume PDFs

## [v3.3.10] - 2026-11-27

### Added
- Clear-all button for the user activity panel, backed by a dedicated API operation

## [v3.3.9] - 2026-11-27

### Changed
- Documented the release history for versions `v3.3.1` through `v3.3.8`

## [v3.3.8] - 2026-11-27

### Changed
- Cover-letter and tailored-resume PDF filenames now include only the applicant's first and last names
- Tailored-resume downloads use the server-provided filename, matching the cover-letter naming convention

## [v3.3.7] - 2026-11-27

### Added
- Duplicate applied-job prevention for matching company, job title, and job description, with an explanatory dialog in both generation workflows

## [v3.3.6] - 2026-11-27

### Added
- “Add to applied jobs” action on the tailored-resume page

## [v3.3.5] - 2026-11-27

### Added
- Formatted PDF export for edited, tailored resumes

## [v3.3.4] - 2026-11-27

### Changed
- Separated API controllers, request models, and response models into dedicated packages

## [v3.3.3] - 2026-11-27

### Added
- Resume-tailoring workflow with a dedicated page that uses the selected resume, supporting documents, and job description

## [v3.3.2] - 2026-11-25

### Fixed
- Corrected misleading user-facing logs for Gemini token-refresh failures

## [v3.3.1] - 2026-11-25

### Fixed
- Hardened Cloud Run JWT key configuration and token refresh handling, including shared-key validation, environment-variable parsing, and refresh-loop fixes
- Fixed API authentication and session-heartbeat issues encountered during JWT-based sign-in and silent refresh
- Cloud Run deploy now supplies all environment variables in a single `--set-env-vars` assignment, preventing later flags from replacing earlier settings
- Cloud Run requires a valid shared JWT key pair and fails startup for missing, invalid, or mismatched keys instead of generating per-instance keys

## [v3.3.0] - 2026-11-21

### Added
- JWT-based authentication replacing server-side session management for silent session renewal on Cloud Run
- `JwtService` — generates and validates RS256-signed JWT access tokens (15 min) and refresh tokens (7 days), using a configured shared key pair on Cloud Run
- `JwtAuthenticationConverter` — reads `access_token` HTTP-only cookie, validates JWT, and sets `SecurityContext`
- `TokenResponse` DTO for token responses
- `POST /api/auth/refresh` endpoint for silent token renewal — validates refresh token cookie and issues new JWT cookies
- `POST /api/auth/logout` endpoint for cookie cleanup — clears both access and refresh token cookies
- Silent token refresh in the frontend heartbeat — calls `/api/auth/refresh` on 401 before redirecting to login
- JWT configuration in `application.yaml`: `access-token-validity-seconds` (900) and `refresh-token-validity-seconds` (604800)
- 25 new tests for JWT authentication:
  - `JwtServiceTests` (14 tests) — token generation, validation, type mismatch, malformed/tampered token rejection
  - `JwtAuthenticationFilterTests` (6 tests) — cookie reading, JWT validation, missing/invalid cookie handling
  - `TokenRefreshTests` (5 tests) — valid refresh, missing cookie, invalid token, wrong token type, logout cookie clearing

### Changed
- `SecurityConfig` rewritten: `LoginSuccessHandler` now issues JWT cookies on successful Google Sign-In instead of relying on server-side session
- `UserController` updated with token refresh and logout endpoints
- `index.html` updated: `checkSession()` calls `/api/auth/refresh` silently on heartbeat 401; logout button uses `/api/auth/logout`
- All security tests updated to match new constructor signatures and JWT-based behavior
- Authentication no longer depends on Cloud Run session lifetime — refresh tokens survive session expiration via HTTP-only cookies

### Notes
- Local development generates an ephemeral JWT key pair when no keys are configured; Cloud Run uses the configured shared key pair so tokens remain valid across instances and restarts.
- Cookies use `SameSite=None; Secure` for cross-site compatibility on Cloud Run HTTPS.

## [v3.2.9] - 2026-11-21

### Added
- Privacy Policy page (`/privacy.html`) for Google OAuth app verification
- Terms of Service page (`/terms.html`) for Google OAuth app verification
- `access_type=offline` parameter in Google OAuth2 registration for silent token refresh
- Both privacy and terms pages are publicly accessible without authentication

### Fixed
- Authentication using Google user ID (`sub`) instead of email address — added `user-name-attribute: email` to OAuth2 provider configuration
- `LoginSuccessHandler` reading `allowedEmails` at bean creation time before `@Value` injection — changed to lazy getter
- "Access denied" error after sign-in caused by empty `allowedEmails` set at initialization

### Changed
- Removed custom `ClientRegistration` bean from `SecurityConfig` — now relies on Spring Boot auto-configuration from `application.yaml`
- Simplified `SecurityConfig` constructor to accept only `UserActionLogBuffer`; OAuth2 credentials read from `application.yaml`
- Updated tests to match new `SecurityConfig` constructor signature

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
- GitHub Actions deployment supplies Cloud Run environment variables using a single `--set-env-vars` list, avoiding replacement of previously specified variables

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
