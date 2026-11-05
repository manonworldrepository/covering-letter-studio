# Cover Letter Studio

Cover Letter Studio is a single-user, localhost-only application that uses Google Gemini to draft a tailored cover letter from a job description, a saved resume, and optional supporting documents. Review and edit the draft, then download it as a PDF.

## Requirements

- **Spring Boot mode:** Java 25 JDK.
- **Native executable mode:** GraalVM 25+ with its Native Image compiler installed and configured. Native image compilation uses the JDK that runs Gradle.
- Internet access to download Gradle dependencies and contact the Gemini API.
- A Gemini API key. Google AI Studio may offer free-tier usage in supported regions, subject to model availability and quotas; unlimited free usage is not guaranteed.

## Start the application

Run commands from the project root. On first launch, the setup wizard asks for a Gemini API key and a model available to that key. Saving the settings creates or updates the root `.env` file. You can also create `.env` before starting:

```dotenv
GEMINI_API_KEY=your-api-key
GEMINI_MODEL=gemini-3.1-flash-lite
```

Keep `.env` private; it is excluded from Git. When both values exist, the app opens directly to the workspace. Change them later from **Settings** in the application.

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

1. Upload one or more PDF or DOCX resumes. Choose one saved resume as the source for each generated letter; remove individual resumes or clear all saved resumes from the Your resumes section.
2. Optionally save up to five supporting PDF or DOCX documents. Select which documents should contribute to generation, and remove individual files or clear all saved files from the Supporting documents section.
3. Paste the job description. Gemini identifies the applicant’s full name from the selected resume and the job title from the description, then drafts the letter. Make sure the resume contains the applicant’s name.
4. Review and edit the draft, then download the PDF. The filename combines the title-cased applicant name, detected job title, and current date, separated by underscores.

The app is configured for single-user local use and binds to `127.0.0.1`. The saved resume and supporting documents are stored under `.data`, which is excluded from Git. Extracted document text and the job description are sent to Google Gemini for generation. Only text-based PDFs are supported; scanned/image-only documents require OCR and cannot currently be extracted. Each file can be up to 10 MB, with extracted document text limited to 100,000 characters total.

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
