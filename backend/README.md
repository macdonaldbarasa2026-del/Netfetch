# NetFetch PayPal Secure Backend

This microservice provides server-side order creation and capture for NetFetch donations using PayPal's REST APIs.

## Security Best Practice
- **Never embed your PayPal Client Secret in the Android application code.**
- Android decompilers can easily extract strings and secrets from APK binaries.
- All requests requiring PayPal authentication should be executed server-side.

## Kenya Account Restrictions (READ THIS FIRST)
The developer's PayPal account is registered in **Kenya**. This affects which PayPal features are available:

| Feature | Available? |
|---------|-----------|
| PayPal.Me link (`paypal.me/username`) | ❌ NO — Kenya not on PayPal.Me country list |
| Charity donate button (`?cmd=_donations`) | ❌ NO — requires verified nonprofit status |
| **PayPal Hosted Button** (dashboard-created) | ✅ YES — recommended client-only method |
| This backend (REST Orders API) | ✅ YES — works when deployed with real credentials |
| Receive money by email (Send Money) | ✅ YES — universal fallback |

## Quickstart: Hosted Button (No Backend Needed)

This is the **recommended approach** if you don't want to deploy a backend:

1. Log into [paypal.com](https://www.paypal.com) with your Business account
2. Go to **Business Tools** → **Accept Payments** → **Donate Button**
3. Choose **open amount** (lets donors enter any value)
4. Generate the button → copy the `hosted_button_id` from the HTML snippet
5. In `PayPalDonationManager.kt`, set: `var hostedButtonId: String? = "YOUR_ID_HERE"`
6. Rebuild and release the app

## Backend Deployment (Full API Integration)

Only needed if you want server-side order verification or capture webhooks.

1. Install dependencies:
   ```bash
   npm install
   ```

2. Configure environment variables — copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```
   Fill in your actual PayPal Client ID and Secret in `.env`.

3. Run locally for testing:
   ```bash
   npm start
   ```

4. Production Deployment:
   Deploy to Railway, Render, Fly.io, or a VPS. Add environment variables in the platform's dashboard (never commit `.env` to git).

5. Connect to NetFetch Android App:
   Set `PayPalDonationManager.backendOrderEndpoint = "https://your-server.com/api/donations/create-order"`.

## Sandbox Testing
Set `PAYPAL_ENVIRONMENT=sandbox` in `.env` and use your PayPal Sandbox Business account credentials from [developer.paypal.com](https://developer.paypal.com).
