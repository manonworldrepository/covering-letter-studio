---
sessionId: session-261117-063104-1pr5
---

# Requirements

### Overview
Replace the current server-side session-based authentication with JWT-based authentication using HTTP-only cookies. This enables **silent session renewal** because refresh tokens are stored client-side in cookies that survive Cloud Run session expiration.

### Current Problem
- Spring Security stores the OAuth2 refresh token server-side in the session
- Cloud Run's session expires after 30 minutes of inactivity
- When the session expires, the refresh token is lost
- The heartbeat detects this and redirects to the login page
- No silent renewal is possible

### Solution
- Issue JWT access tokens (short-lived, ~15 min) stored in HTTP-only cookies
- Issue refresh tokens (long-lived, ~7 days) stored in HTTP-only cookies
- When the access token expires, the frontend silently exchanges the refresh token for a new access token
- No redirect to login page needed during active use
- Google Sign-In still used for initial authentication and email verification

### Scope

**In Scope:**
- JWT service for token generation, validation, and refresh
- HTTP-only cookie-based token storage
- Custom authentication filters replacing Spring Security session management
- Silent token refresh in the frontend heartbeat
- Google OAuth2 login for initial sign-in (preserved)
- Email-based access control (preserved)
- Logout with cookie cleanup
- Activity panel logging for auth events
- Tests for all new components

**Out of Scope:**
- Changing the Google OAuth2 client configuration
- Multi-tenant or multi-user support
- Token revocation list (refresh tokens expire naturally)

# Technical Design

### Current Implementation
- **`SecurityConfig.kt`**: Session-based OAuth2 login with `LoginSuccessHandler` (email check) and `LoginFailureHandler`. All endpoints require authentication except login pages.
- **`UserController.kt`**: `/api/user/me` extracts user info from `SecurityContext`, `/api/session/heartbeat` checks if session is active.
- **`index.html`**: `checkSession()` pings heartbeat every 2 minutes; on 401, redirects to `/login.html?error=session_expired&refresh=true`.
- **`login.html`**: Google Sign-In button linking to `/oauth2/authorization/google`.

### Key Decisions
1. **HTTP-only cookies** for both access and refresh tokens — prevents XSS access while allowing automatic inclusion in requests
2. **15-minute access token** — short enough to limit exposure, long enough to avoid frequent refreshes
3. **7-day refresh token** — matches Google's testing-mode refresh token lifetime; reduces login frequency
4. **RS256 JWT signing** — uses `spring-security-oauth2-jose` already in dependencies; key pair generated at startup
5. **Google OAuth2 preserved for initial login** — the OAuth2 flow remains the same, but instead of creating a session, we issue JWT cookies
6. **Silent refresh via heartbeat** — heartbeat endpoint returns 401 when access token expired but refresh token valid; frontend calls `/api/auth/refresh` to get new tokens

### Proposed Changes

**New files:**
- `security/JwtService.kt` — Generates, validates, and refreshes JWT tokens. Uses RSA key pair (RS256). Claims include `sub` (email), `email_verified`, `exp`, `iat`.
- `security/JwtAuthenticationFilter.kt` — Server WebFilter that reads the access token cookie, validates the JWT, and sets `SecurityContext` with an `Authentication` object.
- `security/TokenResponse.kt` — DTO for token responses (access_token, refresh_token, expires_in).

**Modified files:**
- `SecurityConfig.kt` — Replaces session-based config with JWT filter chain. Removes `LoginSuccessHandler` inner class, `ApiAuthenticationEntryPoint`. Adds JWT filter before OAuth2. Keeps OAuth2 login for initial sign-in.
- `UserController.kt` — Updates `/api/user/me` to read email from JWT claims. Adds `/api/auth/refresh` endpoint for silent token renewal. Adds `/api/auth/logout` for cookie cleanup.
- `index.html` — Updates `checkSession()` to call `/api/auth/refresh` on 401 instead of redirecting. Preserves `credentials: 'same-origin'` for cookie support.
- `login.html` — No changes needed; the Google Sign-In button URL remains the same.

### Components

**JwtService**
- `generateAccessToken(email: String): String` — Creates a JWT with 15-min expiry
- `generateRefreshToken(email: String): String` — Creates a JWT with 7-day expiry
- `validateAccessToken(token: String): Mono<String>` — Returns email if valid, empty if expired/invalid
- `validateRefreshToken(token: String): Mono<String>` — Same for refresh tokens
- Uses `JwtEncoder` (RS256) and `JwtDecoder` from Spring Security

**JwtAuthenticationFilter**
- Reads `access_token` cookie from request
- Validates JWT via `JwtService`
- Sets `SecurityContext` with `UsernamePasswordAuthenticationToken` (email as principal)
- If no/invalid token, passes through (unauthenticated)

**Token Refresh Flow**
1. Frontend heartbeat gets 401 (access token expired)
2. Frontend calls `POST /api/auth/refresh` with `credentials: 'same-origin'` (sends cookies)
3. Server reads refresh token cookie, validates it
4. If valid: issues new access + refresh tokens as HTTP-only cookies, returns 200
5. If invalid: returns 401, frontend redirects to login page

### Architecture Diagram

```mermaid
graph LR
    A[Browser] -->|1. Google OAuth2 login| B[SecurityConfig]
    B -->|2. OAuth2 callback| C[LoginSuccessHandler]
    C -->|3. Issue JWT cookies| A
    A -->|4. Request with access_token cookie| D[JwtAuthenticationFilter]
    D -->|5. Validate JWT| E[JwtService]
    E -->|6. Set SecurityContext| D
    D -->|7. Authenticated request| F[Controllers]
    A -->|8. Heartbeat 401| G[/api/auth/refresh]
    G -->|9. Validate refresh cookie| E
    E -->|10. New JWT cookies| A
```

### File Structure
- `src/main/kotlin/com/mosta/coveringletter/security/JwtService.kt` (new)
- `src/main/kotlin/com/mosta/coveringletter/security/JwtAuthenticationFilter.kt` (new)
- `src/main/kotlin/com/mosta/coveringletter/security/TokenResponse.kt` (new)
- `src/main/kotlin/com/mosta/coveringletter/security/SecurityConfig.kt` (modified)
- `src/main/kotlin/com/mosta/coveringletter/api/UserController.kt` (modified)
- `src/main/resources/static/index.html` (modified)
- `src/main/resources/application.yaml` (modified — JWT config)
- `src/test/kotlin/com/mosta/coveringletter/security/` (new tests)

# Testing

### Validation Approach
Unit tests for JwtService (token generation, validation, expiry), JwtAuthenticationFilter (cookie reading, JWT validation, SecurityContext setting), and updated SecurityConfig tests. Integration via existing `CoveringletterApplicationTests`.

### Key Scenarios
- JwtService generates valid access token with correct claims and expiry
- JwtService generates valid refresh token with 7-day expiry
- JwtService rejects expired access tokens
- JwtService rejects invalid/malformed tokens
- JwtAuthenticationFilter sets SecurityContext from valid cookie
- JwtAuthenticationFilter passes through without auth when no cookie
- Token refresh endpoint issues new tokens from valid refresh token
- Token refresh endpoint rejects expired refresh tokens
- Logout clears both cookies
- Frontend heartbeat triggers silent refresh on 401

### Edge Cases
- Refresh token expired — redirect to login
- Both tokens expired — redirect to login
- Tampered JWT — rejected by signature validation
- Missing cookies — treated as unauthenticated
- Cookie injection — HTTP-only flag prevents JS access

# Delivery Steps

### ✓ Step 1: Create JwtService and TokenResponse
Implement JWT token generation, validation, and refresh with RS256 signing.
- Create `JwtService.kt` with RSA key pair generation, `generateAccessToken()` (15-min expiry), `generateRefreshToken()` (7-day expiry), and validation methods.
- Create `TokenResponse.kt` DTO for token responses.
- Add JWT configuration to `application.yaml` (key sizes, token durations).
- Add `JwtServiceTests.kt` covering token generation, validation, expiry, and malformed token rejection.

### ✓ Step 2: Create JwtAuthenticationFilter and update SecurityConfig
Replace session-based auth with JWT cookie filter chain.
- Create `JwtAuthenticationFilter.kt` that reads `access_token` HTTP-only cookie, validates JWT, and sets `SecurityContext`.
- Rewrite `SecurityConfig.kt`: remove session config, add JWT filter before OAuth2, keep OAuth2 login for initial sign-in, update `LoginSuccessHandler` to issue JWT cookies instead of relying on session.
- Update `ApiAuthenticationEntryPoint` for JWT context.
- Update all security tests to match new constructor signatures and behavior.

### ✓ Step 3: Add token refresh and logout endpoints, update frontend
Silent token refresh in backend and frontend heartbeat.
- Add `POST /api/auth/refresh` in `UserController.kt` that reads refresh token cookie, validates it, and issues new JWT cookies.
- Add `POST /api/auth/logout` that clears both token cookies.
- Update `checkSession()` in `index.html` to call `/api/auth/refresh` on 401 instead of redirecting to login.
- Update logout button handler to call `/api/auth/logout`.
- Preserve `credentials: 'same-origin'` on all fetch calls for cookie support.

### ✓ Step 4: Update tests and verify end-to-end
Comprehensive test coverage and build verification.
- Add `JwtAuthenticationFilterTests.kt` (cookie reading, JWT validation, SecurityContext, missing cookie).
- Add `TokenRefreshTests.kt` (valid refresh, expired refresh, missing refresh).
- Update `LoginSuccessHandlerTests.kt` for JWT cookie issuance.
- Update `SecurityConfigTests.kt` for new filter chain.
- Run all 263 tests to verify no regressions.

### ✓ Step 5: Fix silent refresh after PC wakes from sleep
When the PC goes to sleep, JavaScript timers are paused, so the heartbeat interval doesn't fire. When the PC wakes, the access token may have expired but the app doesn't know until the next heartbeat interval completes. Added a `visibilitychange` listener that checks the session immediately when the page becomes visible again.
- Add `document.addEventListener("visibilitychange", ...)` in `index.html` that calls `checkSession()` when the page becomes visible.
- This ensures the silent token refresh triggers immediately after the PC wakes from sleep, before the user interacts with the app.