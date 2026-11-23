---
sessionId: session-261123-101351-pwdc
---

# Deployment Steps

### Step 1: Generate an RSA key pair locally

**PowerShell (Windows):**
```powershell
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl pkey -in jwt-private.pem -pubout -out jwt-public.pem
$privateKey = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes("jwt-private.pem"))
$publicKey = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes("jwt-public.pem"))
Write-Host "=== Private Key Base64 ==="
Write-Host $privateKey
Write-Host ""
Write-Host "=== Public Key Base64 ==="
Write-Host $publicKey
```

**Linux/macOS:**
```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl pkey -in jwt-private.pem -pubout -out jwt-public.pem
echo "=== Private Key Base64 ==="
base64 -w 0 jwt-private.pem
echo ""
echo "=== Public Key Base64 ==="
base64 -w 0 jwt-public.pem
```

Copy both base64 strings.

### Step 2: Set as GitHub Secrets (for CI/CD)

1. GitHub repo → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**
2. Add `JWT_PRIVATE_KEY_BASE64` and `JWT_PUBLIC_KEY_BASE64`
3. Push to `main` or re-run the workflow

### Step 3: OR set on Cloud Run manually

```powershell
gcloud run services update CoveringLetter \
  --region YOUR_REGION \
  --set-env-vars JWT_PRIVATE_KEY_BASE64="..." \
  --set-env-vars JWT_PUBLIC_KEY_BASE64="..."
```

### Step 4: Clean up

Delete `jwt-private.pem` and `jwt-public.pem` locally.

# Delivery Steps

### ✓ Step 1: Generate RSA key pair and encode to base64
Run OpenSSL commands locally to create a 2048-bit RSA key pair and encode both keys to base64.

**Generated keys:**
- Private Key Base64: `MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCVljU8eHkRoLw3SQkPdD3EH3bq5B/LBu9x/dJSRWf6uqoCH7R+75yjnrGA8+kdjlErXDvpYke5QAGaGhFNvbX+t+AWBqTMSDqLL9e/sCnw1+mBbyHPkg+WppTPuW1BO9nkS4pqwKZGs8ET67os8u8L46AQKCnaCaJswwTKxfQ46eclEQ57Whpse3i7AjiXeEWHaMZR9skPeogXPqlQXr1ZNAo2xX7xPAKzisYuOe+rUhIGtVQyO1zjWoQxWkrsnlTlVfvPAWpUecHLVsadKjRXwdQPUK1TUu2CAbEyCOl6ywqHUI8Tk9GmcD7bUHFbeH4DKzGeKSiUUljJMGmKfeslAgMBAAECggEACHfnTflAsAufoX5G1Iikods+0gprIFp6qMM7/8CtI8fN2G6aEHZD3GF0tBOwNqZNbb0m084RYxPpY04m0ktd/RcaHl/EPvUMghUJy5t6AbKqJf6NdUupX+0l+eKJ+bTyihbwCvNwFmBBGyhvsvFn22OeC/wcw8Vi3BxoQ4sptHph8N5ZJNKl/FNL+yLnYwV25JeqqEilILoXOTRk9xwh1djbREEEIck3slj9i/FvKGM+jdkRpaTDVH9u9wO5IKep9el0ra+J0sRssUZlXljZvEMsxNff8KHDZf+VOsnyPBQ2Ii0tFfcX913sLskf/jkhNhNkpNaooNv+VRcHa7DSSwKBgQDI+P5p44Kp+j0DqcAQIXvSZhe5c0mGpSZ+TptXQ08u68DERkIpBRb/zFk/dg60snqLLF5NpxHevlyZyRslCgYwcB4vG1buKnFtF/iJAb1GcAuikhrL5M93Q1DKIPckbzpNfe97JhhVTnpgSB3TE+DqGvC01AT4zfmFMCDNLCBPGwKBgQC+i11D4N50tuzSNcFy/0J0qs5BfEYlSImzSKuK8N4iKDgNXRxCE2GYHDfeuLL92cadSGuZauZEFhqw6UBeYZdIj/vsuxpbEb00X0Bjb8eWzu9+zUk9CimfUOtxzJkiQBnNjusGQYjUkuTM0+Xq4TMhYGsmyeP2N2T4ADyHYKkSvwKBgHmelPengq8yjDHnTB924+PTfI/xwJEG6GCPWVpZlVWjvagJjIgeWGi8VVYeL5ASYi5Oz1LWw+2z7X3zQcYMZupxLxL6zISckr+ePf1LXCuAKspa+HRjJ1nZFs77XBRkazfx+jbgOuGE+HHDsqjF4tpJK1mC5O9cVp/PHEqNZY/5AoGBAKZGq36ebsHfF05hRoz5n557pz2SGwxR/ogLNFcs1ktsWW2gARbQHuxEQ6UB/IB1WFLMbckVdheYSnZzNY6nDwNtUmRE6jzqf/eB34hmdP9vWaOHtQ+lHKlAThWie0deNKYiv4lYCvWlsi/kAdEz8BCCOT9CEsI7mMqqOifQ+/m1AoGALHrZgZ0Rz4HoMWNShTnHOUGw4ahVu7oQBwt5Y0ADcP791LzQi3zDMbCiDmfLsJ+FkfZoVDUWWVrJVyORe7NAtaHUmWkrmy9zIgqIh/kW/J/Dhd38zDkIxss2FHIJ1w4FBR5XEOY4O0hSe6jlEq36mh4JN7Gd+L7Mn3WFdVSdlKs=`
- Public Key Base64: `MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlZY1PHh5EaC8N0kJD3Q9xB926uQfywbvcf3SUkVn+rqqAh+0fu+co56xgPPpHY5RK1w76WJHuUABmhoRTb21/rfgFgakzEg6iy/Xv7Ap8NfpgW8hz5IPlqaUz7ltQTvZ5EuKasCmRrPBE+u6LPLvC+OgECgp2gmibMMEysX0OOnnJREOe1oabHt4uwI4l3hFh2jGUfbJD3qIFz6pUF69WTQKNsV+8TwCs4rGLjnvq1ISBrVUMjtc41qEMVpK7J5U5VX7zwFqVHnBy1bGnSo0V8HUD1CtU1LtggGxMgjpessKh1CPE5PRpnA+21BxW3h+AysxnikolFJYyTBpin3rJQIDAQAB`

### ✓ Step 2: Deploy keys to Cloud Run via GitHub Secrets or gcloud
Set JWT_PRIVATE_KEY_BASE64 and JWT_PUBLIC_KEY_BASE64 as environment variables on Cloud Run, either through GitHub Actions secrets or manual gcloud command.

**Completed:** Keys added as GitHub Actions secrets. Push to `main` to trigger deployment.