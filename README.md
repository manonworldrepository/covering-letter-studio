# Cover Letter Studio

[![Cloud Run deployment](https://github.com/manonworldrepository/covering-letter-studio/actions/workflows/deploy.yml/badge.svg?branch=master)](https://github.com/manonworldrepository/covering-letter-studio/actions/workflows/deploy.yml?query=branch%3Amaster)

A green badge means the latest `master` commit was deployed to Cloud Run and the serving revision was verified against its image digest.

Cover Letter Studio is a multi-account application that uses Google Gemini to draft tailored cover letters and resumes from a job description, a saved resume, and optional supporting documents. Local development binds to `127.0.0.1`; Cloud Run can host the app for multiple Google Sign-In accounts. Each account has private resumes, supporting documents, applied jobs, Gemini settings, and activity logs.

## Requirements

- **Spring Boot mode:** Java 25 JDK.
- **Native executable mode:** GraalVM 25+ with its Native Image compiler installed and configured. Native image compilation uses the JDK that runs Gradle.
- Internet access to download Gradle dependencies and contact the Gemini API.
- A Gemini API key. Google AI Studio may offer free-tier usage in supported regions, subject to model availability and quotas; unlimited free usage is not guaranteed.

## Google Sign-In configuration

On first launch, if no allowed emails are configured, a one-time setup screen appears before Google Sign-In. Enter one Google account email address per field and select exactly one as administrator; the first address is selected by default. If `ALLOWED_EMAILS` is already set in the environment or root `.env`, setup is skipped and the first configured address is the default administrator unless `ADMIN_EMAIL` is set. Sign-in is restricted to the configured list; the app does not automatically allow every Gmail account.

Only the administrator can edit the allowed-account list or reassign the administrator from **Settings**. The Settings form saves the administrator's personal Gemini key/model and account changes together, and it cannot save without at least one allowed address and an administrator. The administrator can view every account's resumes, supporting documents, and applied jobs; the all-applications grid labels each row with its owner. Cross-account private data is read-only to the admin view, but shared legacy records can be deleted by any user.

Existing legacy resumes, supporting documents, and applied jobs remain visible to all accounts as shared data. Any user can delete a shared legacy record; that deletion removes it for everyone. New uploads and applications are saved privately and remain deletable only by their owner. Each account has its own document selections, so selecting a shared legacy document does not change another user's selections. On Cloud Run, the existing shared dataset can also be copied into an owner's account workspace with the one-time migration task in `CLOUD_DEPLOYMENT.md`; the application does not special-case any email address.

**Reset application settings** is available to the administrator in **Settings** and requires confirmation. It clears the administrator's saved Gemini API key and the global allowed-account/admin list, preserves the selected Gemini model and Google OAuth credentials, signs out the administrator, and returns to first-run account setup. It does not delete resumes, supporting documents, or applied jobs.

Create a Google OAuth client for your app and provide `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in the root `.env` file or as environment variables. For local development, add `http://localhost:8080/login/oauth2/code/google` as an authorized redirect URI for that client. The callback URI is generated from the current host, so the same setup works with a Cloud Run URL when that URL is registered with the OAuth client. Gemini keys and model choices are saved in each user's `.data/accounts/<encoded-email>/.env`, not in a shared account setting.

## Start the application

Run commands from the project root. After sign-in, each user sets a Gemini API key and model in their own setup/settings and adds a resume; supporting documents are optional. Gemini 3.1 Flash-Lite (`gemini-3.1-flash-lite`) is the default model. To allow sign-in, you can configure Google OAuth and an initial allowlist in the root `.env` before starting:

```dotenv
GOOGLE_CLIENT_ID=your-oauth-client-id
GOOGLE_CLIENT_SECRET=your-oauth-client-secret
ALLOWED_EMAILS=your.email@gmail.com
ADMIN_EMAIL=your.email@gmail.com
```

`ADMIN_EMAIL` is optional; when omitted, the first address in `ALLOWED_EMAILS` becomes administrator. Keep `.env` private; it is excluded from Git. In-app changes persist in `.data` alongside each account's data.

### Spring Boot with Gradle

Java 25 is required. Start the app with the Gradle wrapper:

**Windows (PowerShell):**

```powershell
.\gradlew.bat bootRun
```

**Linux or macOS:**

```bash
chmod +x ./gradlew
./gradlew bootRun
```

Open [http://localhost:8080](http://localhost:8080). Press `Ctrl+C` in the terminal to stop the app.

### Build and run a native executable

Install and configure GraalVM 25 or later with Native Image before building. The native-image plugin compiles with the JDK running Gradle, so make sure `JAVA_HOME`, `PATH`, and `java -version` all refer to that GraalVM installation.

Native compilation also needs the platform's C/C++ build tools. On Windows, install Visual Studio 2022 version 17.6 or later with the **Desktop development with C++** workload and x64/x86 build tools. On Linux, install the C compiler, linker, and required system development libraries for your distribution. On macOS, install the Xcode Command Line Tools.

Build the executable:

**Windows:**

```powershell
.\gradlew.bat nativeCompile
```

**Linux or macOS:**

```bash
./gradlew nativeCompile
```

Run it from the project root so it finds `.env` and stores files in the expected local `.data` directory:

**Windows (PowerShell):**

```powershell
.\build\native\nativeCompile\coveringletter.exe
```

**Linux or macOS:**

```bash
./build/native/nativeCompile/coveringletter
```

Open [http://localhost:8080](http://localhost:8080). Native executables are platform-specific; build separately on each target operating system. Native compilation may require additional platform C/C++ build tools provided by GraalVM’s Native Image setup.

## Setup and launch scripts

The scripts create a starter `.env` file if missing and start the application. They do not install Java, GraalVM, Native Image, or platform build tools; install the prerequisites manually before running them. All platforms accept an optional mode argument: `bootRun` (the default) or `native`.

**Windows (PowerShell or Command Prompt):**

```powershell
.\install-and-run-windows.bat
```

By default, the script starts with Spring Boot `bootRun`; Java 25 must already be installed and available on `PATH`. To build and run a native executable instead, use:

```powershell
.\install-and-run-windows.bat native
```

Native mode uses the Java/GraalVM and Visual Studio configuration already available to Gradle; the script does not pre-check Native Image or Visual Studio. `nativeCompile` reports any missing compiler or build-tool prerequisites. The script then runs the generated executable. To explicitly select JVM mode, use `.\install-and-run-windows.bat bootRun`.

**Linux:**

```bash
chmod +x ./install-and-run-linux.sh
./install-and-run-linux.sh
```

By default, the script starts with Spring Boot `bootRun`; install Java 25 manually first. To build and run a native executable, use `./install-and-run-linux.sh native` as your regular user (do not use `sudo`). Native mode uses the Java/GraalVM configuration already available to Gradle and does not pre-check Native Image; `nativeCompile` reports missing compiler or platform build-tool prerequisites. For JVM mode, the script selects Java 25 from installed JDKs or `PATH`.

**macOS (Terminal):**

```bash
chmod +x ./install-and-run-macos.sh
./install-and-run-macos.sh
```

By default, the script starts with Spring Boot `bootRun`; install Java 25 manually first and make it available on `PATH`. To build and run a native executable, use `./install-and-run-macos.sh native`. Native mode uses the Java/GraalVM configuration already available to Gradle and does not pre-check Native Image; `nativeCompile` reports missing compiler or platform build-tool prerequisites.

The Gradle wrapper downloads Gradle and project dependencies on first use, so an internet connection is required. The setup scripts do not install system prerequisites.

## Use the application

1. Upload one or more PDF or DOCX resumes. Choose one saved resume as the source for each generated letter; remove your own resumes or delete shared legacy resumes for all accounts.
2. Optionally save up to five supporting PDF or DOCX documents in your account. Select which documents should contribute to generation; remove your own documents or delete shared legacy documents for all accounts. Selections remain private to your account.
3. Paste the job description and choose whether to generate a complete covering letter or a short motivation message for an application form. Both options use the selected resume and supporting documents; Gemini identifies the applicant’s full name from the resume and the job title from the description. Make sure the resume contains the applicant’s name.
4. Review and edit the draft. Download the complete covering letter as a PDF, or copy either draft to paste into an application form. PDF filenames include the applicant's first and last names, a shortened job title when needed, and a `_Cover_Letter` or `_Resume` suffix.

The app binds to `127.0.0.1` by default. Per-account data and the shared legacy workspace are stored under `.data`, which is excluded from Git. Extracted document text and the job description are sent to Google Gemini for generation. Only text-based PDFs are supported; scanned/image-only documents require OCR and cannot currently be extracted. Each file can be up to 10 MB, with extracted document text limited to 100,000 characters total.

The app also exposes an MCP Streamable HTTP server at `http://127.0.0.1:8080/mcp`. It provides a `generate_cover_letter` tool that uses the authenticated account's selected resume, supporting documents, and Gemini settings. MCP calls require an authenticated app session; they are not an unauthenticated route. The supplied job description and selected document text are sent to Gemini through Gemini's API.

The resizable **User activity** panel shows actions including the selected Gemini model when a request is sent and when a reply arrives. It does not show technical application logs. If Gemini reports that a model is busy or another Gemini error occurs, use **Settings** to select another available model.

## Tests

Run the test suite with the Gradle wrapper:

**Windows:**

```powershell
.\gradlew.bat test
```

**Linux or macOS:**

```bash
./gradlew test
```
