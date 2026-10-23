require('dotenv').config();
const express = require('express');
const cors = require('cors');

const app = express();
app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 3000;
const PAYPAL_ENV = process.env.PAYPAL_ENVIRONMENT || 'live';
const CLIENT_ID = process.env.PAYPAL_CLIENT_ID;
const CLIENT_SECRET = process.env.PAYPAL_CLIENT_SECRET;

const BASE_URL = PAYPAL_ENV === 'sandbox'
  ? 'https://api-m.sandbox.paypal.com'
  : 'https://api-m.paypal.com';

/**
 * Generates an OAuth 2.0 access token using client credentials strictly server-side.
 */
async function getAccessToken() {
  if (!CLIENT_ID || !CLIENT_SECRET) {
    throw new Error('Server PayPal credentials are not configured in environment variables.');
  }

  const auth = Buffer.from(`${CLIENT_ID}:${CLIENT_SECRET}`).toString('base64');
  const response = await fetch(`${BASE_URL}/v1/oauth2/token`, {
    method: 'POST',
    headers: {
      Authorization: `Basic ${auth}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: 'grant_type=client_credentials',
  });

  const data = await response.json();
  if (!response.ok) {
    throw new Error(`PayPal auth failed (${response.status}): ${JSON.stringify(data)}`);
  }

  return data.access_token;
}

/**
 * Health check endpoint
 */
app.get('/health', (req, res) => {
  res.json({ status: 'ok', service: 'netfetch-paypal-backend', environment: PAYPAL_ENV });
});

/**
 * POST /api/donations/create-order
 * Validates request and creates a PayPal checkout order securely.
 */
app.post('/api/donations/create-order', async (req, res) => {
  try {
    const { amount, currency = 'USD' } = req.body;
    const numericAmount = parseFloat(amount);

    if (isNaN(numericAmount) || numericAmount <= 0) {
      return res.status(400).json({ error: 'Invalid donation amount. Must be greater than 0.' });
    }

    const formattedAmount = numericAmount.toFixed(2);
    const accessToken = await getAccessToken();

    const orderPayload = {
      intent: 'CAPTURE',
      purchase_units: [
        {
          description: 'NetFetch Developer Appreciation Donation',
          amount: {
            currency_code: currency,
            value: formattedAmount,
          },
        },
      ],
      application_context: {
        brand_name: 'NetFetch App',
        user_action: 'PAY_NOW',
        return_url: process.env.APP_RETURN_URL || 'https://netfetch.local/donation/success',
        cancel_url: process.env.APP_CANCEL_URL || 'https://netfetch.local/donation/cancel',
      },
    };

    const response = await fetch(`${BASE_URL}/v2/checkout/orders`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(orderPayload),
    });

    const orderData = await response.json();
    if (!response.ok) {
      return res.status(response.status).json({
        error: 'Failed to create PayPal order',
        details: orderData,
      });
    }

    const approveLink = orderData.links && orderData.links.find(
      (l) => l.rel === 'approve' || l.rel === 'payer-action'
    );

    if (!approveLink || !approveLink.href) {
      return res.status(500).json({ error: 'No approval URL found in PayPal order response.' });
    }

    res.json({
      orderId: orderData.id,
      approvalUrl: approveLink.href,
      status: orderData.status,
    });
  } catch (error) {
    console.error('Donation order creation error:', error);
    res.status(500).json({ error: error.message || 'Internal server error' });
  }
});

/**
 * POST /api/donations/capture-order
 * Captures order after user approval on PayPal.
 */
app.post('/api/donations/capture-order', async (req, res) => {
  try {
    const { orderId } = req.body;
    if (!orderId) {
      return res.status(400).json({ error: 'orderId is required.' });
    }

    const accessToken = await getAccessToken();
    const response = await fetch(`${BASE_URL}/v2/checkout/orders/${orderId}/capture`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'application/json',
      },
    });

    const captureData = await response.json();
    if (!response.ok) {
      return res.status(response.status).json({
        error: 'Failed to capture PayPal order',
        details: captureData,
      });
    }

    res.json({
      orderId: captureData.id,
      status: captureData.status,
      details: captureData,
    });
  } catch (error) {
    console.error('Donation order capture error:', error);
    res.status(500).json({ error: error.message || 'Internal server error' });
  }
});

app.listen(PORT, () => {
  console.log(`NetFetch PayPal backend listening on port ${PORT} (${PAYPAL_ENV} mode)`);
});
