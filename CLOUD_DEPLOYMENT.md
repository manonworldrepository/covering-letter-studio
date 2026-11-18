# Cloud Run Deployment Guide

This guide covers deploying the Cover Letter Studio app to Google Cloud Run on the free tier.

## Prerequisites

- Google Cloud account with billing enabled (free tier still requires billing setup)
- Google Cloud SDK (`gcloud`) installed locally
- Docker installed (for building the container image)

## Step 1: Build the JAR

```powershell
.\gradlew.bat bootJar
```

This creates `build/libs/coveringletter-0.0.1-SNAPSHOT.jar`.

## Step 2: Build and Push the Container Image

### Option A: Google Artifact Registry (Recommended)

```powershell
# Create an Artifact Registry repository
gcloud artifacts repositories create coveringletter-repo --repository-format=docker --location=us-central1

# Build and push the image
docker build -t us-central1-docker.pkg.dev/PROJECT_ID/coveringletter-repo/coveringletter:latest .
docker push us-central1-docker.pkg.dev/PROJECT_ID/coveringletter-repo/coveringletter:latest
```

Replace `PROJECT_ID` with your Google Cloud project ID.

### Option B: Docker Hub

```powershell
docker build -t yourusername/coveringletter:latest .
docker push yourusername/coveringletter:latest
```

## Step 3: Deploy to Cloud Run

```powershell
gcloud run deploy coveringletter `
  --image us-central1-docker.pkg.dev/PROJECT_ID/coveringletter-repo/coveringletter:latest `
  --platform managed `
  --region us-central1 `
  --allow-unauthenticated `
  --set-env-vars GEMINI_API_KEY="your-api-key-here",GEMINI_MODEL="gemini-3.1-flash-lite" `
  --memory 512Mi `
  --cpu 1 `
  --max-instances 2
```

### Security Note

The `--allow-unauthenticated` flag makes the service publicly accessible. For a personal app with sensitive data (resumes, API keys), use one of these instead:

**Option 1: IAM-protected service (Recommended)**

```powershell
gcloud run deploy coveringletter `
  --image us-central1-docker.pkg.dev/PROJECT_ID/coveringletter-repo/coveringletter:latest `
  --platform managed `
  --region us-central1 `
  --no-allow-unauthenticated `
  --set-env-vars GEMINI_MODEL="gemini-3.1-flash-lite" `
  --memory 512Mi
```

Then access the service by authenticating with `gcloud auth print-identity-token` or by setting up IAM policies for your Google account.

**Option 2: API Key Header**

Add a simple API key check in the app by setting an environment variable `APP_API_KEY` and checking it in a Spring filter.

## Step 4: Configure Environment Variables

The app requires these environment variables on Cloud Run:

| Variable | Required | Description |
|----------|----------|-------------|
| `GEMINI_API_KEY` | Yes | Your Google Gemini API key |
| `GEMINI_MODEL` | No | Model ID (default: `gemini-3.1-flash-lite`) |

Set them via `gcloud run services update`:

```powershell
gcloud run services update coveringletter `
  --platform managed `
  --region us-central1 `
  --set-env-vars GEMINI_API_KEY="your-new-key"
```

## Data Persistence Warning

⚠️ **Cloud Run is stateless.** The `.data` directory is ephemeral — all resumes, supporting documents, and applied jobs will be lost on restart or redeployment.

### Options for Persistence

1. **Accept ephemeral data** — Upload your resume and documents each session. Applied jobs are lost on restart.
2. **Google Cloud Storage** — Replace the file-based stores with Cloud Storage API calls. This requires code changes to `ResumeStore`, `SupportingDocumentStore`, and `AppliedJobStore`.
3. **Cloud SQL** — Use a managed PostgreSQL database for structured data (applied jobs, settings).
4. **Google Cloud Filestore** — Mount a persistent NFS volume (has minimum cost of ~$20/month).

For personal use, Option 1 may be acceptable if you keep local backups of your resumes and documents.

## Free Tier Limits

| Resource | Free Allowance |
|----------|----------------|
| Requests | 2 million/month |
| vCPU-seconds | 180,000/month |
| Memory-seconds | 360,000 GiB/month |
| Network egress | 1 GB/month (North America) |

For generating 10-20 cover letters per day, you'll stay well within these limits.

## Native Image Deployment (Optional)

For faster cold starts and lower memory usage, you can build a GraalVM native image:

```powershell
.\gradlew.bat nativeCompile
```

Then update the Dockerfile to use the native executable:

```dockerfile
FROM gcr.io/distroless/cc-debian12
WORKDIR /app
COPY build/native/nativeCompile/coveringletter coveringletter
ENTRYPOINT ["/app/coveringletter"]
```

This requires GraalVM 25+ with Native Image tooling installed locally.

## Monitoring

View logs and metrics:

```powershell
# View logs
gcloud logging read "resource.type=cloud_run_revision AND resource.labels.service_name=coveringletter" --limit 50

# View metrics
gcloud run services describe coveringletter --platform managed --region us-central1
```

## Rolling Back

To roll back to a previous revision:

```powershell
gcloud run services revisions list coveringletter --platform managed --region us-central1
gcloud run services update-traffic REVISION_ID --to-revision=REVISION_ID --platform managed --region us-central1
```

## CI/CD with GitHub Actions

The app includes a GitHub Actions workflow (`.github/workflows/deploy.yml`) that automatically builds and deploys to Cloud Run on every push to the `master` branch.

### How it works

1. You push code to `master` on GitHub
2. GitHub Actions authenticates to GCP using **OIDC** (no service account keys stored in secrets)
3. The workflow builds the Gradle JAR, then builds and pushes the Docker image to Artifact Registry
4. Cloud Run is automatically redeployed with the new image

### Setup steps

#### 1. Enable required GCP APIs

```powershell
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com
```

#### 2. Create an Artifact Registry repository (if not already created)

```powershell
gcloud artifacts repositories create coveringletter-repo --repository-format=docker --location=us-central1
```

#### 3. Set up Workload Identity Federation

This allows GitHub Actions to authenticate to GCP without storing service account keys.

```powershell
# Create a workload identity pool
gcloud iam workload-identity-pools create "github-pool" `
  --project="PROJECT_ID" `
  --location="global" `
  --display-name="GitHub Actions Pool"

# Create a provider for your GitHub repository
# IMPORTANT: You must include --attribute-condition to restrict which repos can authenticate
gcloud iam workload-identity-pools providers create-oidc "github-provider" `
  --project="PROJECT_ID" `
  --location="global" `
  --workload-identity-pool="github-pool" `
  --display-name="GitHub Provider" `
  --attribute-mapping="google.subject=assertion.sub,attribute.actor=assertion.actor,attribute.repository=assertion.repository,attribute.repository_owner=assertion.repository_owner" `
  --attribute-condition="assertion.repository == 'GITHUB_USERNAME/GITHUB_REPO'" `
  --issuer-uri="https://token.actions.githubusercontent.com"
```

#### 4. Create a service account and grant permissions

```powershell
# Create a dedicated service account for deployments
gcloud iam service-accounts create coveringletter-deploy `
  --display-name="Cover Letter Deploy Service Account"

# Grant Cloud Run deploy permissions
gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:coveringletter-deploy@PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/run.admin"

# Grant Artifact Registry push permissions
gcloud artifacts repositories add-iam-policy-binding coveringletter-repo \
  --location=us-central1 \
  --member="serviceAccount:coveringletter-deploy@PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/artifactregistry.repoAdmin"

# Grant Cloud Build permissions
gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="serviceAccount:coveringletter-deploy@PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/cloudbuild.builds.editor"

# Allow GitHub Actions to impersonate the service account
# Note: This binding is on the SERVICE ACCOUNT, not the pool
gcloud iam service-accounts add-iam-policy-binding "coveringletter-deploy@PROJECT_ID.iam.gserviceaccount.com" \
  --project="PROJECT_ID" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/attribute.repository/GITHUB_USERNAME/GITHUB_REPO"
```

Replace `PROJECT_ID`, `PROJECT_NUMBER`, `GITHUB_USERNAME`, and `GITHUB_REPO` with your actual values.

#### 5. Add GitHub repository secrets

Go to your GitHub repository → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**, and add:

| Secret name | Value | Example |
|-------------|-------|---------|
| `GCP_PROJECT_ID` | Your GCP project ID | `my-project-123456` |
| `GCP_REGION` | Cloud Run region | `us-central1` |
| `GCP_SERVICE` | Cloud Run service name | `coveringletter` |
| `WIF_PROVIDER` | Full Workload Identity Provider resource name | `projects/123456789/locations/global/workloadIdentityPools/github-pool/providers/github-provider` |

#### 6. Configure `GEMINI_API_KEY` on Cloud Run

The workflow sets `GEMINI_MODEL` automatically. For `GEMINI_API_KEY`, set it once on the Cloud Run service:

```powershell
gcloud run services update coveringletter `
  --platform managed `
  --region us-central1 `
  --set-env-vars GEMINI_API_KEY="your-api-key-here"
```

The key persists across redeployments and is not overwritten by the workflow.

### First deployment

Before enabling the automated workflow, deploy the service once manually (see Step 3 above) so the Cloud Run service exists. After that, every push to `main` will trigger an automatic redeployment.

### Triggering manually

You can also trigger the workflow manually from GitHub:
**Actions** → **Deploy to Cloud Run** → **Run workflow**.
