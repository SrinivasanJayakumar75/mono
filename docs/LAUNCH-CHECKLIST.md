# Launch checklist — WhatsApp and Instagram

This is the shortest path from local testing to a production launch on
`https://www.monotron.in` and `https://api.monotron.in`.

## Hard blockers to resolve first

1. **A Meta test number (`+1 555…`) cannot receive messages from a phone.** It is a
   sandbox number for outbound API tests only. To receive real WhatsApp messages you
   must add a real phone number to the WhatsApp Business Account.
2. **The Meta app must be Live.** While it is in Development mode, Meta only delivers
   test webhooks from the app dashboard, never real customer messages.
3. **Business verification may be required** before Meta grants messaging permissions
   in Live mode.
4. **A personal Instagram account must be converted** to a Business or Creator account
   before it can be connected.

## 1. Deploy the backend

Build the jar:

```powershell
cd backend
.\gradlew.bat test bootJar
```

Upload `backend/build/libs/monotron-backend.jar` to the server that runs
`api.monotron.in`, replace the running jar, and restart the service (systemd or Caddy
upstream, depending on the server).

Production environment:

```dotenv
APP_FRONTEND_BASE_URL=https://www.monotron.in
MONOTRON_CORS_ORIGINS=https://www.monotron.in

# Never commit these real values.
CHANNEL_CREDENTIALS_KEY=replace-with-a-long-random-value
CHANNEL_OAUTH_STATE_SECRET=replace-with-another-long-random-value

META_APP_ID=1106837821981541
META_APP_SECRET=...
META_INSTAGRAM_CONFIG_ID=1118991900897792
META_WEBHOOK_VERIFY_TOKEN=...
META_GRAPH_VERSION=v23.0

META_WHATSAPP_REDIRECT_URI=https://api.monotron.in/api/channel-connections/WHATSAPP/oauth/callback
META_INSTAGRAM_REDIRECT_URI=https://api.monotron.in/api/channel-connections/INSTAGRAM/oauth/callback

# Fill these with the real WhatsApp number, not the 555 test number.
META_WHATSAPP_WABA_ID=...
META_WHATSAPP_PHONE_NUMBER_ID=...
```

Verify:

```text
https://api.monotron.in/actuator/health
```

It must return `{"status":"UP"}`.

## 2. Meta app settings

Open:

- App dashboard: `https://developers.facebook.com/apps/1106837821981541/`
- Basic settings: `https://developers.facebook.com/apps/1106837821981541/settings/basic/`
- Permissions: `https://developers.facebook.com/apps/1106837821981541/app-review/permissions/`

Set:

- **App Domains**: `monotron.in`, `api.monotron.in`
- **Privacy Policy URL**: `https://www.monotron.in/policies`
- **Terms of Service URL**: `https://www.monotron.in/policies`
- **Data Deletion Instructions URL**: `https://www.monotron.in/policies`

Add both production redirect URIs under **Facebook Login for Business → Settings →
Valid OAuth Redirect URIs**:

```text
https://api.monotron.in/api/channel-connections/WHATSAPP/oauth/callback
https://api.monotron.in/api/channel-connections/INSTAGRAM/oauth/callback
```

## 3. Instagram configuration

1. Open **Use cases → Manage messaging & content on Instagram → Customize → API setup
   with Unified login**.
2. Edit configuration `1118991900897792`.
3. Set the redirect URL to:
   `https://api.monotron.in/api/channel-connections/INSTAGRAM/oauth/callback`
4. Add the Instagram account as an asset.
5. Request `instagram_basic` and `instagram_manage_messages`.
6. Use a **System-user access token** configuration.
7. Convert the Instagram account to **Business or Creator** in the Instagram app:
   Settings → Account type → Switch to Professional.
8. In Development mode, add the account as an **Instagram Tester** and accept the
   invite in Instagram.

## 4. Webhooks

In **WhatsApp → Configuration → Webhook** and in **Instagram → API setup with Unified
login → Configure webhooks**, set:

```text
Callback URL: https://api.monotron.in/api/channel-webhooks/meta
Verify token: the META_WEBHOOK_VERIFY_TOKEN value
```

Subscribe to `messages`.

## 5. Publish the app

Switch **App Mode** from Development to Live. If Meta blocks it, complete the listed
requirements:

- privacy policy URL
- app icon and category
- business verification
- app review for the messaging permissions

For a first launch with your own accounts, Standard Access is often enough. Advanced
Access is required to serve other businesses' accounts.

## 6. WhatsApp real number

1. Open `https://business.facebook.com/wa/manage/`.
2. Go to **Account tools → Phone numbers → Add phone number**.
3. Use a number that is not currently on WhatsApp and can receive SMS or a voice call.
4. Verify it and set the business display name.
5. Copy the new **Phone number ID** and the **WhatsApp Business Account ID**.
6. Put them in the production environment as `META_WHATSAPP_PHONE_NUMBER_ID` and
   `META_WHATSAPP_WABA_ID`, restart the backend, and connect WhatsApp in production.
7. Message the new number from another phone. It must appear in the inbox.

## 7. Test

1. Send `Hi` from another phone to the real WhatsApp business number.
2. Open `https://www.monotron.in/inbox`.
3. Reply as a human and confirm the reply arrives on the phone.
4. Repeat for an Instagram DM.
5. Check that AI auto-reply works when enabled and the knowledge base has content.

## If Meta approval is delayed

Launch with the channels that are already working (Gmail, website widget, and any
approved channel). Add WhatsApp and Instagram as soon as the real number, business
verification, and Live mode are approved. Do not promise WhatsApp inbound on the test
`+1 555` number — it cannot receive customer messages.
