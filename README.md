# Read Me First
The following was discovered as part of building this project:

* The JVM level was changed to '25' as the Kotlin version does not support a later Java version yet.

# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.2/gradle-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.1.2/gradle-plugin/packaging-oci-image.html)
* [Coroutines section of the Spring Framework Documentation](https://docs.spring.io/spring-framework/reference/7.0.10/languages/kotlin/coroutines.html)
* [GraalVM Native Image Support](https://docs.spring.io/spring-boot/4.1.2/reference/packaging/native-image/introducing-native-image-support.html)
* [Testcontainers support](https://docs.spring.io/spring-boot/4.1.2/reference/testing/testcontainers.html#testing.testcontainers)
* [Reactive Web Applications](https://docs.spring.io/spring-boot/4.1.2/reference/web/reactive.html)
* [Reactive HTTP Client](https://docs.spring.io/spring-boot/4.1.2/reference/io/rest-client.html#io.rest-client.webclient)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a Reactive RESTful Web Service](https://spring.io/guides/gs/reactive-rest-service/)

### Additional Links
These additional references should also help you:

* [Gradle Build Scans – insights](https://scans.gradle.com#gradle)
* [Configure AOT settings in Build Plugin](https://docs.spring.io/spring-boot/4.1.2/how-to/aot.html)

## GraalVM Native Support

This project has been configured to let you generate either a lightweight container or a native executable.
You can also run your existing tests in a native image.

### Lightweight Container with Cloud Native Buildpacks
If you're already familiar with container images, this is the easiest way to get started.
Docker should be installed and configured on your machine prior to creating the image.

To create the image, run the following goal:

```
$ ./gradlew bootBuildImage
```

Then, run it like any other container:
```
$ docker run --rm coveringletter:0.0.1-SNAPSHOT
```

### Executable with Native Build Tools
The GraalVM `native-image` compiler should be installed and configured on your machine.

NOTE: GraalVM 25+ is required.

To build the native executable, run:
```
$ ./gradlew nativeCompile
```

Then, run the executable:
```
$ build/native/nativeCompile/coveringletter
```

You can also run your existing test suite in a native image. This is an efficient way to validate compatibility:
```
$ ./gradlew nativeTest
```

### Gradle Toolchain support

There are some limitations regarding Native Build Tools and Gradle toolchains.
Native Build Tools disable toolchain support by default. Effectively, native image compilation is done with the JDK used to execute Gradle.
Read more in the [Native Build Tools documentation](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#configuration-toolchains).

### Testcontainers support

This project uses [Testcontainers at development time](https://docs.spring.io/spring-boot/4.1.2/reference/features/dev-services.html#features-dev-services-testcontainers).

Testcontainers has been configured to use the following Docker images:

Please review the tags of the used images and set them to the same as you're running in production.

## Cover Letter Studio instructions

The application extracts text from a resume and supporting documents, uses Google's Gemini API to draft a tailored cover letter, and lets you edit and download it as a PDF.

### Requirements

- Java 25 (the project Gradle toolchain is configured for Java 25).
- A Gemini API key. Google AI Studio may offer free-tier usage in supported regions, subject to current model availability and quotas. Unlimited free usage is not guaranteed.

### Run locally

Create a `.env` file in the project root and configure your Gemini API key and model:

```dotenv
GEMINI_API_KEY=your-api-key
GEMINI_MODEL=gemini-3.1-flash-lite
```

On a first run, the setup wizard can collect the key and available model and save them in `.env`. You can also create the file yourself as shown above. Keep `.env` private; it is excluded from Git. Start the application from the project root:

```powershell
.\gradlew.bat bootRun
```

Open [http://localhost:8080](http://localhost:8080). The app reads `GEMINI_MODEL` from `.env` on startup; change that value to select a different supported model.

### Install prerequisites and run

The convenience scripts check for Java 25, attempt to install it if needed using an available package manager, and then start the app with the Gradle wrapper (which downloads Gradle and the project dependencies as needed). You need an internet connection; installing Java may require administrator/root privileges. If `.env` is missing, the script creates an empty template and the app's first-run wizard will request the API key and model.

On Windows, run from PowerShell or Command Prompt:

```powershell
.\install-and-run-windows.bat
```

The Windows script uses `winget` or Chocolatey to install Java 25 when needed.

On Linux, run from the project directory:

```bash
chmod +x ./install-and-run-linux.sh
./install-and-run-linux.sh
```

The Linux script supports apt, dnf, zypper, and pacman. If Java 25 is not available in the configured distribution repositories, install a Java 25 JDK manually and rerun the script.

On macOS, run from Terminal in the project directory:

```bash
chmod +x ./install-and-run-macos.sh
./install-and-run-macos.sh
```

The macOS script uses an existing Java 25 installation or installs Eclipse Temurin 25 with Homebrew. If Homebrew is not installed, install it from [brew.sh](https://brew.sh) and rerun the script. The script creates `.env` if needed and starts the app; open [http://localhost:8080](http://localhost:8080) in your browser.

### Run on Linux

Alternatively, install Java 25 manually and make sure `java -version` reports Java 25. Create `.env` in the project root with your key and model:

```dotenv
GEMINI_API_KEY=your-new-key
GEMINI_MODEL=gemini-3.1-flash-lite
```

Then run the Gradle wrapper from the project root:

```bash
chmod +x ./gradlew
./gradlew bootRun
```

Open [http://localhost:8080](http://localhost:8080) in your browser. Change `GEMINI_MODEL` in `.env` to select a different supported model; restart the app for the change to take effect.

### Use the application

On the first launch, enter your Gemini API key in the setup wizard, load the models available for that key, and select one. The wizard saves both settings to `.env`. Later, use **Settings** in the top-right to change the key or model.

1. Save your resume as a PDF or DOCX; it is kept locally under its uploaded filename and reused for future letters. Choose another resume at any time to replace it.
2. Save up to five supporting PDF or DOCX documents for reuse. Remove an individual file or clear all saved files from the Supporting documents section.
3. Paste the job description. Gemini reads your full name from the saved resume, identifies the job title from the description, and drafts the letter. Make sure your resume includes your name.
4. Review and edit the letter, then download the PDF. Its filename uses the capitalized resume name, detected job title, and current date separated by underscores.

The saved resume and supporting files are stored under `.data` on the application machine and excluded from Git. Settings and files are shared by all clients connected to this single-user, localhost-only app. Extracted document text and the job description are sent to Google's Gemini API for generation. Only text-based PDFs can be extracted; scanned/image-only documents need OCR and are not supported. Each file can be up to 10 MB, and combined extracted content is limited to 100,000 characters. The bottom-right User activity panel shows recent actions, including the selected Gemini model when a request is sent and when a reply arrives; it does not display technical application logs.

### Build and test

```powershell
.\gradlew.bat test
```

On Linux, run the tests with:

```bash
./gradlew test
```
