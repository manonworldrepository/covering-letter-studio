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
  --set-env-vars GEMINI_API_KEY="your-api-key-here",GEMINI_MODEL="gemini-3.1-flash-lite",CLOUD_STORAGE_BUCKET="PROJECT_ID-coveringletter-data" `
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

## Data Persistence with Cloud Storage

✅ **Cloud Storage sync is built in.** The app automatically syncs the `.data` directory to Google Cloud Storage on startup and shutdown, so your data survives container restarts (scale-to-zero).

### Setup Cloud Storage Bucket

1. **Create a Cloud Storage bucket:**

```powershell
gcloud storage buckets create gs://YOUR_PROJECT_ID-coveringletter-data `
  --location=US-CENTRAL1 `
  --project=YOUR_PROJECT_ID
```

Replace `YOUR_PROJECT_ID` with your GCP project ID (e.g., `manon-world-1737941816058-coveringletter-data`). Bucket names must be globally unique.

2. **Set the bucket as a Cloud Run environment variable:**

```powershell
gcloud run services update coveringletter `
  --platform managed `
  --region us-central1 `
  --set-env-vars CLOUD_STORAGE_BUCKET="YOUR_PROJECT_ID-coveringletter-data"
```

3. **Grant the Cloud Run service account permission to read/write the bucket:**

```powershell
gcloud storage buckets add-iam-policy-binding gs://YOUR_PROJECT_ID-coveringletter-data `
  --member="serviceAccount:YOUR_PROJECT_ID-compute@developer.gserviceaccount.com" `
  --role="roles/storage.objectAdmin"
```

### How It Works

- **On startup**: The app downloads `data-backup.zip` from Cloud Storage and extracts it to `.data/`
- **On shutdown**: The app zips the entire `.data/` directory and uploads it to Cloud Storage
- **First deploy**: No data exists in Cloud Storage — the app starts with an empty `.data/` directory
- **Local development**: Cloud Storage sync is disabled by default (only active on the `cloud` profile)

### Cloud Storage Free Tier

| Resource | Free Allowance |
|----------|----------------|
| Storage | 5 GB/month |
| Reads | 50,000 operations/month |
| Writes | 2,000 operations/month |

For personal use (a few MB of resumes/documents), you'll stay well within the free tier.

### GitHub Actions CI/CD

If you use the automated deployment workflow, add the bucket name as a GitHub secret:

1. Go to: `https://github.com/YOUR_USERNAME/covering-letter-studio/settings/secrets/actions`
2. Add secret: `CLOUD_STORAGE_BUCKET` → `YOUR_PROJECT_ID-coveringletter-data`

The workflow will automatically set this environment variable on each deployment.

### Disabling Cloud Storage Sync

To disable sync (fall back to ephemeral local storage), set:

```powershell
gcloud run services update coveringletter `
  --platform managed `
  --region us-central1 `
  --set-env-vars CLOUD_STORAGE_ENABLED="false"
```

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

## Cloud Logging

Cloud Run automatically sends all `stdout`/`stderr` output to Google Cloud Logging. Your app's SLF4J server logs (including Gemini operations, retries, API errors, and user action logs) are captured without any code changes.

### Free Tier Limits

| Resource | Free Allowance |
|----------|----------------|
| Log storage | 50 GiB/project/month |
| Log ingestion (us-central1) | 4.8 GB/minute |
| Log retention | 30 days (default, auto-cleared) |

For a personal app generating a few cover letters per day, you'll stay well within these limits.

### Log Retention

The `_Default` log bucket retains logs for **30 days by default** — older logs are automatically deleted. No extra setup is needed. To change the retention period:

```powershell
gcloud logging buckets update _Default --location=global --retention-days=30
```

### Real-time Logs URL

View your app's logs in real-time:

**https://console.cloud.google.com/logs/query;query=resource.type%3D%22cloud_run_revision%22%20resource.labels.service_name%3D%22coveringletter%22?project=manon-world-1737941816058**

Or open the Logs Explorer and paste this query:

```
resource.type="cloud_run_revision" resource.labels.service_name="coveringletter"
```

### Quota Alerts

To avoid unexpected charges, set up alerting policies that notify you when usage approaches the free tier limits.

#### Step 1: Create an email notification channel

1. Go to **https://console.cloud.google.com/** → select your project
2. Search for **"Monitoring"** → click it
3. In the left sidebar, click **"Alerting"**
4. Click **"Edit notification channels"** (top right)
5. Under **"Email"**, click **"Add New"**
6. Enter your email address and click **"Save"**

#### Step 2: Create Cloud Logging storage alert (50 GiB free limit)

1. In the **Alerting** page, click **"Create Policy"**
2. Click **"Add Condition"** → **"Metric"**
3. Search for **"bytes streamed into log buckets monthly"**
4. Set the condition: **"Above"** → **"42949672960"** (40 GiB in bytes, 80% of free limit)
5. Add your email notification channel
6. Name it **"Cloud Logging Storage Alert"** and click **"Save"**

#### Step 3: Create Cloud Run request count alert (2M free limit)

1. In the **Alerting** page, click **"Create Policy"**
2. Click **"Add Condition"** → **"Metric"**
3. Search for **"Request count"** under **Cloud Run Revision**
4. Set the condition: **"Above"** → **"1600000"** (80% of 2M free limit)
5. Add your email notification channel
6. Name it **"Cloud Run Request Count Alert"** and click **"Save"**

### CLI Alternative

```powershell
# View recent logs
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
