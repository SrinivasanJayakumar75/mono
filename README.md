# Monotron

An Intercom-style, multi-tenant AI customer-support platform. Customers upload PDFs to a
knowledge base; the AI answers inbound messages with grounded answers and clickable
citations; agents work the same conversations from a shared inbox. Every tenant can also
embed a public messenger widget on its own site — including an Intercom-style help center for
blogs, FAQs and news announcements — and run its sales pipeline in a built-in CRM (leads,
deals, companies, contacts, tasks, calls, meetings, products, orders, documents).
Every workspace starts with a 3-day free trial, then subscribes monthly (₹399) or yearly
(₹3,990) through Razorpay.

```
backend/    Spring Boot 3.5 (Java 21) API, JWT auth, PDF ingestion, pgvector RAG, CRM, billing
frontend/   Next.js 15 / React 19 / Tailwind app (inbox, knowledge, contacts, CRM, settings, billing, widget)
docs/       API.md - the wire contract shared by both sides
db/         database helpers (the live schema is managed by Flyway in the backend)
```

## Architecture

| Concern | How it works |
| --- | --- |
| Auth | Email + password (BCrypt) or Google sign-in (Google Identity Services ID token verified server-side). Short-lived access JWT (15 min, claims `sub`, `wid`, `role`, `email`, `name`) plus an opaque refresh token that is stored SHA-256-hashed and rotated on every refresh. |
| Tenancy | PostgreSQL Row Level Security. The app connects as the owner (`monotron`), but every tenant-bound request runs in a transaction that does `SET LOCAL ROLE monotron_app` + `set_config('app.workspace_id', ?, true)`. The restricted role cannot see other tenants' rows, and a trigger stamps inserts with the bound workspace even if a client supplies a foreign id. |
| Public widget | `/api/widget/{appId}/**` is unauthenticated. The filter resolves `appId -> workspace_id` through the unprotected `app_workspace_lookup` bridge, then binds that tenant for the request. The messenger can auto-open its chat panel when the page loads; toggle this under **Settings → Messenger widget**. |
| Widget help center | Blogs, FAQs and announcements live in `widget_content`, separate from knowledge-base articles. Settings manages drafts and publishing through `/api/widget-content`; the public widget reads published rows from `/api/widget/{appId}/content`. |
| Knowledge base | PDF upload (max 25 MB) -> PDFBox text extraction -> overlapping chunking -> embeddings -> `content_chunks` with a pgvector column. Ingestion is asynchronous; poll the document until `READY`/`FAILED`. |
| AI | Provider `auto`: uses any OpenAI-compatible endpoint when `OPENAI_API_KEY` is set, otherwise bundled local providers (deterministic hashed embeddings + a grounded extractive answer composer) so the whole stack runs offline. Retrieval is cosine similarity via `1 - (embedding <=> ?::vector)`. |
| Human handoff | Agents can **Escalate** a conversation from the inbox. While escalated the widget still records visitor messages but skips automatic AI replies, so the assistant and a live agent never answer over each other. Escalating also claims an unassigned thread; **Resume AI** hands it back. |
| CRM | Ten resources (leads, deals, companies, contacts, tasks, calls, meetings, products, orders, documents) share one whitelisted descriptor registry, so the REST surface is `GET/POST/PATCH/DELETE /api/crm/{resource}[/{id}]` rather than ten hand-written controllers. Orders own line items and expose computed `itemCount` / `totalAmount`; the `/crm` page includes an overview plus per-module tables and forms. CRM contacts are a separate table from the widget visitor directory at `/contacts`. An inbox conversation can promote its visitor into a lead with **Add to CRM**; the action is idempotent and the thread shows **In CRM** once linked. |
| Billing | New workspaces get a 3-day trial. When it expires, a server-side MVC interceptor returns `402 Payment Required` for workspace APIs while auth, billing and the public widget stay reachable, so the user can pay. Razorpay Subscriptions handles monthly (₹399) and yearly (₹3,990) plans; checkout signatures and webhooks are verified on the server. Billing tables are global (no RLS) because webhooks arrive without a tenant, and every workspace-facing query filters by `workspace_id`. |
| Frontend | Typed API client that mirrors `docs/API.md`, tokens in `localStorage` (`monotron.access`, `monotron.refresh`), transparent single retry after a 401 refresh. A global `402` handler moves an expired workspace to the standalone `/billing` page. |

## Prerequisites

- Java 21 (`JAVA_HOME` set)
- Node.js 20+
- PostgreSQL 16 with the `vector` extension (this repo was developed against the
  `pgvector/pgvector:pg16` container and a database named `monotron`)

The database schema is managed by Flyway. `V1` is adopted as a baseline (the schema already
exists in the target database), `V2` adds the AI columns, `V3` creates the restricted
runtime role and grants it DML on the application tables, `V4` adds the
conversation escalation flag, `V5` removes the retired billing tables, `V8` adds the
dedicated CRM contact and document tables (tenant-scoped by the same RLS policy and insert
trigger as the other CRM entities), `V9` adds the Razorpay subscription schema plus a
3-day trial backfill for existing workspaces, `V10` links leads back to the inbox contact
they were created from, `V11` renames the restricted runtime role to `monotron_app`, and
`V12` adds tenant-scoped widget help-center content.

Applied migrations are immutable: V1-V10 keep their original historical comments and role
name so Flyway checksums stay valid. `V11` is the compatibility bridge for both fresh and
existing databases.

## Run it

### 1. Database

```powershell
docker run -d --name monotron-db -p 5432:5432 `
  -e POSTGRES_USER=monotron -e POSTGRES_PASSWORD=monotron -e POSTGRES_DB=monotron `
  pgvector/pgvector:pg16
```

If another PostgreSQL instance already owns port 5432, publish the container on 5433 instead
(`-p 5433:5432`) and point the backend at it in `backend/.env`:

```dotenv
DATABASE_URL=jdbc:postgresql://localhost:5433/monotron
DATABASE_USER=monotron
DATABASE_PASSWORD=monotron
```

`scripts/run-backend.ps1` exports that file, so `bootRun` and the built jar both use it.

### 2. Backend

```powershell
cd backend
$env:JAVA_HOME  = 'C:\Program Files\Java\jdk-21.0.10'
.\gradlew.bat bootRun
```

Migrations run on startup. API on <http://localhost:8080/api>, health at
<http://localhost:8080/actuator/health>, OpenAPI UI at <http://localhost:8080/swagger-ui.html>.

Start the backend with Gradle or `scripts/run-backend.ps1`. Avoid launching the main class from
an IDE-generated output folder such as `backend/bin/main`; it can become stale and serve an older
API without recent controllers or migrations.

Useful configuration (all optional, see `backend/src/main/resources/application.yml`):

| Variable | Default |
| --- | --- |
| `DATABASE_URL` / `DATABASE_USER` / `DATABASE_PASSWORD` | `jdbc:postgresql://localhost:5432/monotron` / `monotron` / `monotron` |
| `MONOTRON_JWT_SECRET` | dev secret (change in production) |
| `MONOTRON_TENANT_ROLE` | `monotron_app` (empty disables the RLS role downgrade) |
| `MONOTRON_AI_PROVIDER` | `auto` (`openai` to force the remote provider) |
| `OPENAI_API_KEY` / `OPENAI_BASE_URL` | empty / `https://api.openai.com/v1` |
| `GOOGLE_CLIENT_ID` | empty (Google buttons hidden until set) |
| `MONOTRON_STORAGE_DIR` | `./data/uploads` |
| `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` | empty (checkout disabled until both are set) |
| `RAZORPAY_WEBHOOK_SECRET` | empty (webhook disabled until set) |
| `RAZORPAY_MONTHLY_AMOUNT` / `RAZORPAY_YEARLY_AMOUNT` | `39900` / `399000` paise (₹399 / ₹3,990) |
| `MONOTRON_TRIAL_DAYS` | `3` |
| `BILLING_ENFORCE` | `true` (set `false` only for local development) |

#### OpenAI API key

Paste your key into `backend/.env` (created for you, ignored by git):

```dotenv
OPENAI_API_KEY=sk-...
```

Then start the backend. `backend/.env` is loaded automatically whether the app is started from
the repository root (IDE run button) or from `backend/` (Gradle), so no shell export is needed.
The loader script is still handy because it prints whether the key was picked up:

```powershell
.\scripts\run-backend.ps1         # Gradle bootRun
.\scripts\run-backend.ps1 -Jar    # built jar
```

With a key present, `auto` mode switches to OpenAI (`gpt-4o-mini` +
`text-embedding-3-small`). Reindex existing documents afterwards, because
embeddings produced by the offline provider are not compatible with OpenAI's.

#### Google sign-in

Google sign-in uses Google Identity Services in the browser and verifies the signed ID
token on the backend. Only a public OAuth client id is needed; no client secret is stored.

1. In [Google Cloud Console](https://console.cloud.google.com/apis/credentials), configure the
   OAuth consent screen (External is fine for testing), then create an **OAuth 2.0 Client ID**
   of type **Web application**.
2. Add `http://localhost:3000` (and your production origin) under **Authorized JavaScript
   origins**. No redirect URI is needed for the ID-token button flow. If the consent screen is
   still in **Testing**, add the Google accounts you want to use under **Test users**.
3. Put the client id in `backend/.env`:

   ```dotenv
   GOOGLE_CLIENT_ID=1234567890-abc.apps.googleusercontent.com
   ```

4. Restart the backend. The sign-in and register pages read
   `GET /api/auth/google/config` and render the official Google button when it is enabled.

On **Sign in**, Google only signs in an existing account. On **Create your workspace**, it
creates a workspace with the defaults if the verified email is new. Accounts created with a
password can also use Google later because accounts are matched by verified email. If the
variable is empty the Google button is hidden and email/password auth is unchanged.

#### Razorpay subscriptions

Monotron uses [Razorpay Subscriptions](https://razorpay.com/docs/payments/subscriptions/)
for recurring billing. The browser only receives the public key id; the key secret and webhook
secret stay on the server.

1. Create a Razorpay account, switch to **Test mode**, and generate API keys.
2. Put the keys and a webhook secret in `backend/.env`:

   ```dotenv
   RAZORPAY_KEY_ID=rzp_test_...
   RAZORPAY_KEY_SECRET=...
   RAZORPAY_WEBHOOK_SECRET=choose-a-long-random-string
   ```

3. In the Razorpay dashboard, add a webhook pointing at
   `https://<your-public-backend>/api/billing/webhook` and subscribe to the subscription and
   payment events (`subscription.authenticated`, `subscription.activated`,
   `subscription.charged`, `subscription.cancelled`, `subscription.completed`,
   `subscription.expired`, `subscription.halted`, `subscription.pending`, `payment.captured`
   and `payment.failed`). For local development, expose the backend with a tunnel such as
   ngrok.
4. Restart the backend. The first checkout creates and caches the selected monthly or yearly
   Razorpay plan in `billing_plans`; later checkouts reuse it.

Until all three values are present, the `/billing` page shows a setup notice; checkout returns
`503` until the API keys are present. The webhook is public but signature-verified and
idempotent by event id. Existing workspaces received a fresh 3-day trial in `V9`; new
workspaces get one at registration. To work offline without a paywall, set
`BILLING_ENFORCE=false` (never in production).

### 3. Frontend

```powershell
cd frontend
npm install
npm run dev
```

Open <http://localhost:3000>, register a workspace, then upload a PDF under **Knowledge** and
watch the AI answer questions about it in the **Inbox** or via the widget. **CRM** in the sidebar
opens the sales workspace: an overview, then a table and form for each module. Click a lead row to
open its profile — contact and qualification details plus every linked deal, order, task, call,
meeting and document — while the pencil icon still opens the edit form. Create a company and a
product first if you want to link leads, deals and order line items.

Every CRM module has **Import** and **Export** buttons. Export downloads a UTF-8 CSV of all
records matching the current search and filters, using the same column labels as the form and
resolving linked company/lead names. Import accepts an export, a template, or a CSV from another
tool: it previews the rows, checks required columns, and reports any row that needs attention. An
import is all-or-nothing, so a bad row never leaves a partial file behind. Relation columns
can use a name or an id; imports are limited to 2 MB and 2,000 rows.

The **Filters** button on every list opens the categorical filters for that module — status,
stage, priority, source, company, related type, category and so on. Filters combine with search,
show as removable chips, and apply to the current page, the deals board and CSV export.

**Deals** opens as a drag-and-drop pipeline board. Each stage is a column with its own count and
total value; drag a card to another column to change its stage, or click the card to edit it. The
board loads every deal matching the current search and filters, up to 500 at a time, so narrow
the search if you have a very large pipeline.

The public messenger for a workspace is at `http://localhost:3000/widget/<appId>`; the `appId`
is shown under **Settings**. The widget opens on chat; the menu icon in its header opens the
help center, where visitors can search and browse published blogs, FAQs and news. Manage that
content from the **Help center content** card in **Settings** (create, edit, publish/draft or
delete). **Auto-open on page load** is on by default, so the chat panel appears as soon as the
embedded widget loads; turn it off under **Settings → Messenger widget** if you prefer the
launcher-only experience. Backend CORS is pre-configured for `http://localhost:3000`.

**Billing** in the sidebar opens the standalone `/billing` page. It shows the trial status,
monthly (₹399) and yearly (₹3,990) cards, the Razorpay checkout, and cancellation. When a
trial expires, protected pages redirect there and the API returns `402` until a subscription
is active. The page also shows the exact setup steps when the Razorpay keys are missing.

## Tests

```powershell
cd backend
.\gradlew.bat test                                  # unit tests (chunking, vectors, JWT)
$env:MONOTRON_IT = 'true'; .\gradlew.bat test      # + RLS integration tests against the live DB
```

`TenantIsolationIntegrationTest` proves the security model end to end: tenant transactions run
as `monotron_app`, a tenant cannot read or update another tenant's documents, and an insert
carrying a foreign `workspace_id` is stamped with the bound tenant anyway.

### End-to-end smoke test

With the backend running:

```powershell
.\scripts\smoke.ps1
```

It registers a throwaway tenant, checks the public Google auth config, generates and uploads a
small PDF, waits for ingestion, asserts that semantic search and `/api/ai/answer` return
grounded citations, drives the public widget (`config` -> `session` -> `messages` with an AI
reply), exercises the agent inbox, and finally registers a second tenant to verify cross-tenant
access returns 404. Exit code is non-zero when any check fails.

The widget help center has its own end-to-end script:

```powershell
.\scripts\smoke-widget-content.ps1
```

It creates a blog, FAQ and announcement, verifies the public widget sees only published items
in help-center order, checks that drafts stay in settings, deletes an item, rejects an invalid
content type, and proves a second tenant cannot manage the first tenant's content.

The CRM has its own end-to-end script:

```powershell
.\scripts\smoke-crm.ps1
```

It creates a company, lead, deal, contact, task, call, meeting, product, order with line items
and a document; checks `itemCount` / `totalAmount`, the overview aggregates and the lead profile
payload; updates and searches records; filters lists by status, company and boolean values and
rejects unknown filters; deletes a line item; bulk-imports leads (including company/related name
resolution, all-or-nothing validation and per-row errors); and proves that a second tenant cannot
read or delete the first tenant's CRM data or link to its records.

The human-handoff contract has its own script too:

```powershell
.\scripts\smoke-handoff.ps1
```

It escalates a live widget conversation, proves the AI stays quiet while escalated, has an agent
reply, and then resumes the AI to confirm automatic replies come back.

The subscription paywall has its own script too:

```powershell
.\scripts\smoke-billing.ps1
```

It registers a throwaway tenant, checks the 3-day trial and the ₹399 / ₹3,990 plans, expires
the trial through the local Docker database, proves protected APIs (including widget settings)
return `402` while auth, billing and the public widget stay reachable, and restores the trial in
a `finally` block. It does not create a live Razorpay subscription, so it is safe to run without
keys. Use `-DbContainer`, `-DbUser` and `-DbName` if your PostgreSQL instance is not the standard
`monotron-db` container.

## API

`docs/API.md` is the source of truth for every endpoint, request/response shape and error code,
and both the backend and the frontend are written against it.
