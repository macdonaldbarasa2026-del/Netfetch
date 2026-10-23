# NetFetch PayPal Secure Backend

This microservice provides server-side order creation and capture for NetFetch donations using PayPal's REST APIs.

## Security Best Practice
- **Never embed your PayPal Client Secret in the Android application code.**
- Android decompilers can easily extract strings and secrets from APK binaries.
- All requests requiring PayPal authentication should be executed server-side.

## Setup & Deployment

1. Install dependencies:
   ```bash
   npm install
   ```

2. Configure environment variables:
   Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```
   Fill in your actual PayPal Client ID and Secret in `.env`.

3. Run locally:
   ```bash
   npm start
   ```

4. Production Deployment:
   Deploy this service to any cloud platform (e.g. Render, Railway, Vercel, Fly.io, or VPS) and add the environment variables in the platform's dashboard.

5. Connect to NetFetch Android App:
   Set `PayPalDonationManager.backendOrderEndpoint = "https://your-server.com/api/donations/create-order"`.
