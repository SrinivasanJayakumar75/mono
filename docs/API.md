# Monotron — API Contract (v1)

Base URL: `http://localhost:8080/api`
All request/response bodies are `application/json; charset=utf-8` unless stated otherwise.

## Conventions

- **Auth**: `Authorization: Bearer <accessToken>` on every endpoint except the
  `/api/auth/register`, `/api/auth/login`, `/api/auth/google`, `/api/auth/google/config`,
  `/api/auth/refresh`, `/api/auth/logout`, `/api/billing/webhook` and all
  `/api/widget/**` endpoints.
- **Paywall**: every workspace API except auth, billing and the public widget returns
  `402 Payment Required` after the 3-day trial ends. The normal error shape is used, so the
  browser can show the message and redirect to `/billing`.
- **CORS**: origin `http://localhost:3000` is allowed, credentials allowed.
- **Errors**: always
  ```json
  { "timestamp": "2026-01-01T00:00:00Z", "status": 400, "error": "Bad Request",
    "message": "human readable", "path": "/api/foo", "fieldErrors": { "email": "must be valid" } }
  ```
  `fieldErrors` is optional and only present for validation failures.
- **Dates**: ISO-8601 UTC strings, e.g. `2026-01-01T00:00:00Z`.
- **Pagination**: query params `page` (0-based) and `size` (default 20, max 100).
  Paginated responses use:
  ```json
  { "items": [], "page": 0, "size": 20, "total": 0, "totalPages": 0 }
  ```
- **Nullability**: fields not marked optional are always present; use `null` for absent values.

---

## 1. Authentication

### POST `/api/auth/register`
Creates user + workspace + messenger app + widget settings (tenant bootstrap) and starts a
3-day subscription trial for the workspace.

Request
```json
{ "email": "a@b.com", "password": "secret123", "name": "Ada", "workspaceName": "Acme Support" }
```
- `password`: min 8 chars.
- `workspaceName`: optional, defaults to `"<name>'s workspace"`.

Response `200`
```json
{
  "user": { "id": "uuid", "email": "a@b.com", "name": "Ada", "role": "ADMIN", "status": "OFFLINE" },
  "workspaces": [ { "id": "uuid", "name": "Acme Support", "slug": "acme-support-a1b2c3", "role": "ADMIN", "appId": "32-hex-chars" } ],
  "activeWorkspace": { "id": "uuid", "name": "Acme Support", "slug": "acme-support-a1b2c3", "role": "ADMIN", "appId": "32-hex-chars" },
  "accessToken": "jwt", "refreshToken": "opaque", "expiresIn": 900
}
```
Errors: `409` email already registered.

### POST `/api/auth/login`
Request `{ "email": "a@b.com", "password": "secret123" }`

Response `200`
```json
{
  "user": { "id": "uuid", "email": "a@b.com", "name": "Ada", "role": "ADMIN", "status": "OFFLINE" },
  "workspaces": [ { "id": "uuid", "name": "Acme Support", "slug": "acme-support-a1b2c3", "role": "ADMIN", "appId": "32-hex-chars" } ],
  "activeWorkspace": { "id": "uuid", "name": "Acme Support", "slug": "acme-support-a1b2c3", "role": "ADMIN", "appId": "32-hex-chars" },
  "accessToken": "jwt", "refreshToken": "opaque", "expiresIn": 900
}
```
Errors: `401` invalid credentials. `403` account disabled.

### GET `/api/auth/google/config`
Public. Tells the auth pages whether Google sign-in is configured.
```json
{ "enabled": true, "clientId": "1234567890-abc.apps.googleusercontent.com" }
```
When `GOOGLE_CLIENT_ID` is empty, `enabled` is `false` and `clientId` is `null`.

### POST `/api/auth/google`
Public. Verifies a Google Identity Services ID token, then signs in or bootstraps a
workspace. The browser obtains the token from the official Google button; no Google
client secret is sent to or stored by the browser.
```json
{
  "credential": "eyJhbGciOiJSUzI1NiIs…",
  "intent": "LOGIN",
  "workspaceName": "Acme Support"
}
```
- `intent`: `LOGIN` or `REGISTER`.
- `workspaceName`: optional; only used when `REGISTER` creates a new workspace.
- Existing accounts are matched by the verified Google email, including accounts that
  originally registered with a password.
- Response `200` has the same shape as `/api/auth/login`.
- Errors: `401` invalid/unverified Google token, `403` account disabled,
  `404` no account for a `LOGIN` intent, `503` Google sign-in is not configured.

### POST `/api/auth/refresh`
Request `{ "refreshToken": "opaque" }`

Response `200` — `{ "accessToken": "jwt", "refreshToken": "opaque", "expiresIn": 900 }`
(the refresh token is rotated; the old one is revoked).
Errors: `401` invalid/expired/revoked refresh token.

### POST `/api/auth/logout`
Request `{ "refreshToken": "opaque" }` → `204`. Always succeeds.

### GET `/api/auth/me`
Response `200`
```json
{
  "user": { "id": "uuid", "email": "a@b.com", "name": "Ada", "role": "ADMIN", "status": "ONLINE" },
  "activeWorkspace": { "id": "uuid", "name": "Acme Support", "slug": "acme-support-a1b2c3", "role": "ADMIN", "appId": "32-hex-chars" },
  "workspaces": [ { "id": "uuid", "name": "...", "slug": "...", "role": "ADMIN", "appId": "..." } ]
}
```

### POST `/api/auth/switch-workspace`
Request `{ "workspaceId": "uuid" }` → response `200` same shape as `login` but without `password`.
Errors: `403` user is not a member of that workspace.

---

## 2. Workspace & team

### GET `/api/workspace`
```json
{ "id": "uuid", "name": "Acme Support", "slug": "acme-support-a1b2c3", "role": "ADMIN",
  "appId": "32-hex", "createdAt": "2026-01-01T00:00:00Z",
  "app": { "id": "32-hex", "name": "Acme Support", "hmacSecret": "…", "aiEnabled": true,
           "autoReply": true, "aiModel": "gpt-4o-mini", "aiTemperature": 0.2, "aiSystemPrompt": "…" } }
```

### PATCH `/api/workspace`
Request `{ "name": "New name" }` → `200` same shape as GET.

### GET `/api/workspace/members`
```json
{ "items": [ { "userId": "uuid", "email": "a@b.com", "name": "Ada", "role": "ADMIN",
               "status": "ONLINE", "joinedAt": "2026-01-01T00:00:00Z" } ] }
```

### POST `/api/workspace/members`
Request `{ "email": "agent@b.com", "role": "AGENT" }` — role is `ADMIN` | `AGENT` | `VIEWER`.
Adds an existing platform user to the workspace.
Response `200` member object. Errors: `404` no such user, `409` already a member, `403` caller is not ADMIN.

### DELETE `/api/workspace/members/{userId}` → `204`

### GET `/api/teams`
```json
{ "items": [ { "id": "uuid", "name": "Support", "description": "Tier 1", "createdAt": "…",
               "memberIds": ["uuid"] } ] }
```

### POST `/api/teams`
Request `{ "name": "Support", "description": "Tier 1" }` → `200` team object.

### POST `/api/teams/{id}/members` — request `{ "userId": "uuid" }` → `200` team object.
### DELETE `/api/teams/{id}/members/{userId}` → `200` team object.

---

## 3. Widget settings

### GET `/api/widget-settings`
```json
{ "appId": "32-hex", "workspaceName": "Acme Support", "greeting": "Hi there 👋",
  "subtitle": "We typically reply in a few minutes.", "color": "#4F46E5",
  "introMessage": "Ask us anything — we're here to help.", "inputPlaceholder": "Send a message…",
  "showBranding": true, "aiEnabled": true, "autoReply": true }
```

### PUT `/api/widget-settings`
Request — any subset of `workspaceName`, `greeting`, `subtitle`, `color`, `introMessage`,
`inputPlaceholder`, `showBranding`, `aiEnabled`, `autoReply` → `200` full settings object.

---

## 3.1 Widget help-center content (settings)

Blogs, FAQs and news announcements shown in the widget help screen are managed here.
They are separate from knowledge-base articles and are not indexed into the AI corpus.
Workspace members can read the list; `ADMIN` and `AGENT` can create, update and delete.
All routes are authenticated and subject to the billing paywall.

Content kinds: `BLOG`, `FAQ`, `ANNOUNCEMENT`. A `summary` is optional; `body` is plain text.

### GET `/api/widget-content`
Response `200`
```json
[
  { "id": "uuid", "kind": "FAQ", "title": "How do I reset my password?",
    "summary": "Use the security settings.", "body": "Open Settings, then Security.",
    "published": true, "createdAt": "…", "updatedAt": "…" }
]
```
Settings returns drafts too, newest updated first.

### POST `/api/widget-content`
Request
```json
{ "kind": "FAQ", "title": "How do I reset my password?", "summary": "Use the security settings.",
  "body": "Open Settings, then Security.", "published": true }
```
Response `201` — the created content object. `published` defaults to `true` when omitted.
Errors: `400` invalid kind or missing title/body, `403` caller is a `VIEWER`.

### PUT `/api/widget-content/{id}`
Same request shape as POST → `200` updated content object. `published` is left unchanged when
omitted. Errors: `404` content does not belong to the active workspace.

### DELETE `/api/widget-content/{id}` → `204`.
Errors: `404` content does not belong to the active workspace.

---

## 4. Knowledge base — documents (PDF upload)

### GET `/api/documents`
Query: `q` (title/filename contains), `status` (`PENDING|PROCESSING|READY|FAILED`), `page`, `size`.
```json
{ "items": [ { "id": "uuid", "filename": "handbook.pdf", "title": "Employee Handbook",
               "status": "READY", "error": null, "pageCount": 12, "chunkCount": 34,
               "sizeBytes": 245678, "mimeType": "application/pdf", "createdAt": "…", "updatedAt": "…" } ],
  "page": 0, "size": 20, "total": 1, "totalPages": 1 }
```

### POST `/api/documents` — `multipart/form-data`
Fields: `file` (required, `application/pdf`, max 25 MB), `title` (optional, defaults to filename).
Response `200` document object with `status: "PROCESSING"`.
The file is ingested asynchronously; poll `GET /api/documents/{id}` until `status` is `READY` or `FAILED`.
Errors: `400` empty file / not a PDF, `413` too large.

### GET `/api/documents/{id}`
```json
{ "id": "uuid", "filename": "handbook.pdf", "title": "Employee Handbook", "status": "READY",
  "error": null, "pageCount": 12, "chunkCount": 34, "sizeBytes": 245678, "mimeType": "application/pdf",
  "createdAt": "…", "updatedAt": "…",
  "chunks": [ { "id": "uuid", "chunkIndex": 0, "title": "Employee Handbook", "content": "…", "url": null } ] }
```

### GET `/api/documents/{id}/file`
Returns the raw PDF (`application/pdf`, `Content-Disposition: inline`).

### POST `/api/documents/{id}/reindex` → `200` document object (status `PROCESSING`).

### DELETE `/api/documents/{id}` → `204` (also removes its chunks).

---

## 5. Knowledge base — articles & categories

### GET `/api/articles`
Query: `q`, `categoryId`, `published` (`true|false`), `page`, `size`.
```json
{ "items": [ { "id": "uuid", "title": "How to reset your password", "slug": "how-to-reset-your-password",
               "body": "…", "published": true, "categoryId": "uuid", "categoryName": "Getting started",
               "authorName": "Ada", "createdAt": "…", "updatedAt": "…" } ],
  "page": 0, "size": 20, "total": 1, "totalPages": 1 }
```

### POST `/api/articles`
Request `{ "title": "…", "body": "…", "categoryId": "uuid?", "published": true }` → `200` article.
The body is chunked + embedded into the vector index automatically.

### GET `/api/articles/{id}` → `200` article (body included).
### PUT `/api/articles/{id}` — request `{ "title": "…", "body": "…", "categoryId": "uuid?", "published": true }` → `200`.
### DELETE `/api/articles/{id}` → `204`.

### GET `/api/categories`
```json
{ "items": [ { "id": "uuid", "name": "Getting started", "slug": "getting-started", "sortOrder": 0 } ] }
```
### POST `/api/categories` — request `{ "name": "Support" }` → `200` category.
### DELETE `/api/categories/{id}` → `204`.

---

## 6. Semantic search (pgvector)

### POST `/api/search`
Request `{ "query": "how do I reset my password?", "limit": 8 }`
```json
{ "items": [ { "sourceType": "DOCUMENT", "sourceId": "uuid", "title": "Employee Handbook",
               "url": null, "content": "…", "score": 0.83, "chunkIndex": 4 } ] }
```
`score` is cosine similarity in `[-1, 1]`, higher is better. When no chunk clears the
similarity threshold (for example a one-word keyword query), the server falls back to a
keyword match and `score` becomes the share of query terms found in the chunk, in `[0, 1]`.

---

## 7. Inbox

### GET `/api/conversations`
Query: `status` (`OPEN|CLOSED`), `q` (subject/contact name/email contains),
`assigneeId`, `teamId`, `page`, `size`. Default sort: `last_activity_at DESC`.
```json
{ "items": [ { "id": "uuid", "subject": "Cannot log in", "status": "OPEN", "channel": "WIDGET",
               "createdAt": "…", "lastActivityAt": "…", "closedAt": null,
               "assigneeId": null, "assigneeName": null, "teamId": null, "teamName": null,
               "aiHandled": false, "escalated": false, "unreadCount": 2,
               "contact": { "id": "uuid", "name": "Guest 3437", "email": null,
                            "avatarUrl": null, "visitorId": "uuid", "online": false,
                            "browser": "Chrome", "os": "macOS", "location": "Berlin" },
               "lastMessage": { "id": "uuid", "authorType": "CONTACT", "authorName": "Guest 3437",
                                "body": "hi", "createdAt": "…" } } ],
  "page": 0, "size": 20, "total": 1, "totalPages": 1 }
```

### GET `/api/conversations/{id}`
```json
{ "conversation": { "id": "uuid", "subject": "…", "status": "OPEN", "channel": "WIDGET",
                    "assigneeId": null, "assigneeName": null, "teamId": null, "teamName": null,
                    "aiHandled": false, "escalated": false, "createdAt": "…", "lastActivityAt": "…", "closedAt": null },
  "contact": { "id": "uuid", "name": "Guest 3437", "email": null, "avatarUrl": null,
               "visitorId": "uuid", "online": false, "browser": "Chrome", "os": "macOS",
               "location": "Berlin", "customAttributes": {}, "createdAt": "…",
               "crmLeadId": null },
  "messages": [ { "id": "uuid", "authorType": "CONTACT|AGENT|AI", "authorId": null,
                  "authorName": "Guest 3437", "authorAvatarUrl": null, "body": "hi",
                  "createdAt": "…", "citations": [], "feedback": null } ] }
```

### POST `/api/conversations/{id}/reply`
Request `{ "body": "hello" }` → `200` message object (authorType `AGENT`).
Sets `last_activity_at`, clears `unread_count`.

### POST `/api/conversations/{id}/assign` — request `{ "assigneeId": "uuid" | null }` → `200` conversation.
### POST `/api/conversations/{id}/team` — request `{ "teamId": "uuid" | null }` → `200` conversation.
### POST `/api/conversations/{id}/close` → `200` conversation (`status: "CLOSED"`, `closedAt` set).
### POST `/api/conversations/{id}/reopen` → `200` conversation (`status: "OPEN"`, `closedAt` null).
### POST `/api/conversations/{id}/escalate`
Request `{ "escalated": true | false }` (omit the body or field to escalate) -> `200` conversation.
While `escalated` is `true` the widget still records visitor messages but skips automatic AI
replies, so a human agent can take over. Escalating also assigns the conversation to the
calling agent when it has no assignee. `POST .../escalate` with `{ "escalated": false }`
resumes AI auto-replies.

### POST `/api/conversations/{id}/ai-reply` — request `{ "question": "optional override" }` → `200`
```json
{ "id": "uuid", "authorType": "AI", "body": "…", "citations": [ { "title": "…", "sourceType": "DOCUMENT", "sourceId": "uuid", "url": null, "score": 0.83 } ], "createdAt": "…" }
```

### GET `/api/conversations/stats`
```json
{ "open": 4, "closed": 12, "unread": 3, "aiHandled": 2, "total": 16 }
```

### GET `/api/contacts`
Query: `q`, `page`, `size`.
```json
{ "items": [ { "id": "uuid", "name": "Guest 3437", "email": null, "avatarUrl": null,
               "visitorId": "uuid", "online": false, "browser": "Chrome", "os": "macOS",
               "location": "Berlin", "locale": "en-US", "lastSeenAt": "…", "createdAt": "…",
               "customAttributes": {}, "conversationCount": 1 } ],
  "page": 0, "size": 20, "total": 1, "totalPages": 1 }
```
### GET `/api/contacts/{id}` → `200` `{ "contact": {…}, "conversations": [ … ] }`.

---

## 8. AI (RAG)

### POST `/api/ai/answer`
Request `{ "question": "How do I reset my password?", "conversationId": "uuid?" }`
```json
{ "answer": "…", "model": "gpt-4o-mini", "provider": "openai",
  "citations": [ { "title": "Account Guide", "sourceType": "DOCUMENT", "sourceId": "uuid",
                   "url": null, "score": 0.83, "snippet": "…" } ] }
```

---

## 9. Dashboard

### GET `/api/dashboard`
```json
{ "openConversations": 4, "closedConversations": 12, "unreadMessages": 3,
  "aiHandledConversations": 2, "aiResolutionRate": 0.5, "totalContacts": 9,
  "documentCount": 2, "articleCount": 5, "chunkCount": 41,
  "conversationsByDay": [ { "date": "2026-10-16", "count": 3 } ],
  "messagesByDay": [ { "date": "2026-10-16", "count": 7 } ],
  "topDocuments": [ { "id": "uuid", "title": "Handbook", "chunkCount": 34 } ] }
```

---

## 10. Public messenger widget (no auth)

### GET `/api/widget/{appId}/config`
```json
{ "appId": "32-hex", "workspaceName": "Acme Support", "greeting": "Hi there 👋",
  "subtitle": "We typically reply in a few minutes.", "color": "#4F46E5",
  "introMessage": "Ask us anything — we're here to help.",
  "inputPlaceholder": "Send a message…", "showBranding": true }
```

### GET `/api/widget/{appId}/content`
Published help-center content only, ordered announcements → FAQs → blogs, then newest first.
Drafts are never returned.
```json
[
  { "id": "uuid", "kind": "ANNOUNCEMENT", "title": "Our new help center is live",
    "summary": "Browse blogs, FAQs and news from the widget.",
    "body": "Click the help menu in the widget header.",
    "published": true, "createdAt": "…", "updatedAt": "…" }
]
```

### POST `/api/widget/{appId}/session`
Request `{ "visitorId": "uuid", "name": "Jane", "email": "jane@x.com" }`
(`visitorId` optional on first load — the server generates one and returns it.)
Response `200`
```json
{ "contactId": "uuid", "visitorId": "uuid", "name": "Jane", "email": "jane@x.com",
  "conversations": [ { "id": "uuid", "subject": "hi", "status": "OPEN", "createdAt": "…",
                       "lastActivityAt": "…",
                       "messages": [ { "id": "uuid", "authorType": "CONTACT", "authorName": "Jane",
                                       "body": "hi", "createdAt": "…" } ] } ] }
```

### POST `/api/widget/{appId}/messages`
Request `{ "visitorId": "uuid", "conversationId": "uuid?", "body": "hi", "name": "Jane?", "email": "…" }`
Response `200`
```json
{ "conversationId": "uuid", "contactId": "uuid",
  "message": { "id": "uuid", "authorType": "CONTACT", "authorName": "Jane", "body": "hi", "createdAt": "…" },
  "aiReply": { "id": "uuid", "authorType": "AI", "body": "…", "createdAt": "…",
               "citations": [ { "title": "…", "sourceType": "DOCUMENT", "sourceId": "uuid", "url": null, "score": 0.83 } ] } }
```
`aiReply` is `null` when the workspace has AI disabled or auto-reply off.

---

## 11. CRM

Ten resources share one CRUD shape. Every record is scoped to the workspace bound to the
access token by the same row-level-security transaction as the rest of the API.

`resource` is one of:

| Resource | Table | Required fields |
| --- | --- | --- |
| `leads` | `leads` | `name` |
| `deals` | `deals` | `title` |
| `companies` | `companies` | `name` |
| `contacts` | `crm_contacts` | `firstName` |
| `tasks` | `tasks` | `title` |
| `calls` | `calls` | — (defaults apply) |
| `meetings` | `meetings` | `title`, `startsAt` |
| `products` | `products` | `name` |
| `orders` | `orders` | `orderNumber` |
| `documents` | `crm_documents` | `name` |

### GET `/api/crm/{resource}`

Query: `q` (case-insensitive search), `page` (0-based, default `0`), `size` (default `50`,
max `200`), plus any number of `filter.<fieldKey>=<value>` parameters.

Filters combine with `q` using AND. For example:

```
GET /api/crm/leads?filter.status=NEW&filter.priority=HIGH
GET /api/crm/deals?filter.companyId=<uuid>
GET /api/crm/products?filter.active=true
```

Text filters are case-insensitive and exact, so status codes (`NEW`), related types (`LEAD`) and
relation ids can be used directly. Boolean, number and date filters are exact. The field key must
be a writable field of that resource; an unknown field or invalid value returns `400`. The CRM UI
builds these parameters from its **Filters** panel, and the same filters apply to the deals board
and CSV export.

```json
{ "items": [ { "id": "uuid", "name": "Priya Sharma", "status": "QUALIFIED",
               "companyName": "Acme Corporation", "createdAt": "…" } ],
  "total": 1, "page": 0, "size": 50, "totalPages": 1 }
```

Rows include joined display fields where useful: `companyName` on leads, contacts and orders;
`leadName` on deals and orders; and `itemCount` + `totalAmount` on orders.

### GET `/api/crm/{resource}/{id}` → `200` one record.

### POST `/api/crm/{resource}` → `201` created record.

Send only the fields for that resource, for example:

```json
// POST /api/crm/leads
{ "name": "Priya Sharma", "email": "priya@acme.test", "companyId": "uuid",
  "source": "WEBSITE", "status": "NEW", "priority": "HIGH" }
```

Relation fields (`companyId`, `leadId`, `relatedId`) accept an id or `null`.
`relatedType` accepts `LEAD`, `DEAL`, `COMPANY`, `CONTACT` or `ORDER`. Dates are `YYYY-MM-DD`,
timestamps are `YYYY-MM-DDTHH:mm`; money is a decimal number.

### POST `/api/crm/leads/from-contact/{contactId}`

Promotes an inbox visitor into a CRM lead in one call. The lead copies the contact's name, email
and location, is marked `source: "WEBSITE"` and `status: "NEW"`, and stores the originating
contact in `sourceContactId`. The action is idempotent: repeating it returns the existing lead.

- `201` when a new lead is created:
  `{ "created": true, "lead": { "id": "uuid", "name": "Asha Rao", "sourceContactId": "uuid", … } }`
- `200` when the contact is already linked:
  `{ "created": false, "lead": { "id": "uuid", … } }`

`GET /api/conversations/{id}` exposes the link as `contact.crmLeadId`, which the inbox uses to
show **In CRM** instead of **Add to CRM**. A contact outside the current workspace returns `404`.

### POST `/api/crm/{resource}/import`

Bulk-create records from a spreadsheet. The CRM UI parses the CSV in the browser and sends
the mapped rows as JSON; the same contract can be called directly.

```json
{
  "rows": [
    { "name": "Priya Sharma", "email": "priya@acme.test", "companyId": "Acme Corporation" },
    { "name": "Rahul Verma", "email": "rahul@acme.test", "companyId": "uuid" }
  ]
}
```

- Up to 2,000 rows per request.
- All rows are validated before anything is inserted. If any row fails, nothing is imported.
- A successful response is `{ "imported": 2, "total": 2, "errors": [] }`.
- A validation failure returns `200` with
  `{ "imported": 0, "total": 2, "errors": [ { "row": 3, "message": "name is required" } ] }`.
  `row` is the spreadsheet row number; the header is row 1.
- Relation fields accept a record id or a name. `companyId` and `leadId` resolve by the linked
  record's name. `relatedId` resolves by display name when `relatedType` is present:
  `LEAD` -> lead name, `DEAL` -> title, `COMPANY` -> company name, `CONTACT` -> full name,
  `ORDER` -> order number. Ambiguous names must be replaced with the id.
- Empty optional cells are skipped so database defaults apply. Unknown field keys are ignored.
- Names are resolved inside the current workspace; a link to another workspace's record fails
  validation instead of being stored.

The CRM **Export** button downloads a UTF-8 CSV of every record matching the current search and
filters, with human-readable column labels and linked company/lead names. **Import** previews a
file, checks required columns, and shows per-row errors; an export can be edited and re-imported.
The template button downloads a header-only CSV for the selected module.

### PATCH `/api/crm/{resource}/{id}` → `200` updated record.

Send any subset of that resource's fields; only those keys are updated.

### DELETE `/api/crm/{resource}/{id}` → `204`.

### GET `/api/crm/overview`

```json
{ "leads": 12, "deals": 4, "companies": 7, "contacts": 9, "products": 8,
  "orders": 3, "documents": 4, "openTasks": 3, "upcomingMeetings": 2,
  "pipelineValue": 250000, "wonValue": 0, "orderValue": 11997 }
```

`pipelineValue` sums deals not `WON`/`LOST`, `wonValue` sums `WON` deals, `openTasks` excludes
`DONE`/`CANCELLED`, `upcomingMeetings` counts scheduled meetings starting in the future, and
`orderValue` is the sum of every order line (`quantity * unit_price`).

### GET `/api/crm/leads/{id}/profile`

Returns the lead plus every record linked to it in one tenant-bound response. Deals and orders
are matched by `leadId`; tasks, calls, meetings and documents are matched by
`relatedType = "LEAD"` and `relatedId`.

```json
{ "lead": { "id": "uuid", "name": "Priya Sharma", "status": "QUALIFIED" },
  "deals": [ { "id": "uuid", "title": "Acme annual plan", "valueAmount": 250000 } ],
  "orders": [ { "id": "uuid", "orderNumber": "ORD-1001", "itemCount": 2,
                "totalAmount": 11997 } ],
  "tasks": [ { "id": "uuid", "title": "Send the proposal", "status": "OPEN" } ],
  "calls": [ { "id": "uuid", "subject": "Discovery call", "outcome": "CONNECTED" } ],
  "meetings": [],
  "documents": [ { "id": "uuid", "name": "Acme signed contract" } ] }
```

A lead outside the current workspace returns `404`, exactly like the plain record route.

### Order line items

- `GET /api/crm/orders/{orderId}/items` → `200` array of
  `{ "id": "uuid", "orderId": "uuid", "productId": "uuid", "productName": "Starter plan",
     "quantity": 2, "unitPrice": 4999, "lineNo": 1 }`
- `POST /api/crm/orders/{orderId}/items` - request `{ "productId": "uuid", "quantity": 2,
  "unitPrice": 4999, "productName": "Starter plan?" }` → `201` line item. `productName` and
  `unitPrice` default to the product's name and price. A line without `productId` needs a
  `productName`.
- `DELETE /api/crm/orders/{orderId}/items/{itemId}` → `204`.

A resource that does not exist, or that belongs to another workspace, returns `404`.
Unknown field names are ignored rather than interpolated into SQL.

---

## 12. Billing

Every workspace gets a 3-day free trial. When it expires, workspace APIs return
`402 Payment Required`; auth, billing and the public widget remain available so the workspace
can subscribe. Amounts are in the smallest currency unit (paise for INR): monthly is `39900`
(₹399) and yearly is `399000` (₹3,990).

`status` is one of `TRIALING`, `PENDING`, `ACTIVE`, `PAST_DUE`, `CANCELLED` or `EXPIRED`.
`access` is the server's answer to "may this workspace use the app right now?", and
`canManage` is true only for workspace admins. `configured` is false until
`RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET` are set; `webhookConfigured` is false until
`RAZORPAY_WEBHOOK_SECRET` is set. `testMode` is true for `rzp_test_` keys.

### GET `/api/billing`

Any workspace member can read the summary. Response `200`:

```json
{
  "workspaceId": "uuid",
  "status": "TRIALING",
  "interval": null,
  "provider": "razorpay",
  "trialEndsAt": "2026-11-01T12:00:00Z",
  "currentPeriodEnd": "2026-11-01T12:00:00Z",
  "cancelAtPeriodEnd": false,
  "access": true,
  "daysLeft": 3,
  "configured": false,
  "webhookConfigured": false,
  "testMode": false,
  "canManage": true,
  "shortUrl": null,
  "trialDays": 3,
  "plans": [
    { "interval": "MONTHLY", "label": "Monthly", "amount": 39900, "currency": "INR",
      "description": "Billed every month", "recommended": false },
    { "interval": "YEARLY", "label": "Yearly", "amount": 399000, "currency": "INR",
      "description": "Billed once a year", "recommended": true }
  ]
}
```

### POST `/api/billing/checkout`

Workspace admin only. Starts a Razorpay subscription checkout and stores it as `PENDING`.
Request `{ "interval": "MONTHLY" }` or `{ "interval": "YEARLY" }`. Response `200`:

```json
{
  "subscriptionId": "sub_...",
  "keyId": "rzp_test_...",
  "shortUrl": "https://rzp.io/i/...",
  "name": "Monotron",
  "description": "Monotron Monthly subscription",
  "prefillName": "Ada",
  "prefillEmail": "a@b.com",
  "interval": "MONTHLY",
  "amount": 39900,
  "currency": "INR"
}
```

Errors: `400` invalid interval, `403` not an admin, `409` a subscription is already active,
`503` Razorpay keys are not configured, `502` Razorpay could not be reached.

### POST `/api/billing/verify`

Workspace admin only. Verifies the Razorpay Checkout success payload, activates the local
subscription, records the payment and returns the same shape as `GET /api/billing`.

```json
{
  "razorpay_payment_id": "pay_...",
  "razorpay_subscription_id": "sub_...",
  "razorpay_signature": "hex-hmac"
}
```

The signature is `HMAC-SHA256(payment_id + "|" + subscription_id, key_secret)`. Errors: `400`
invalid signature or a subscription that belongs to another workspace, `503` Razorpay keys are
not configured.

### POST `/api/billing/cancel`

Workspace admin only. Cancels at the end of the current billing period and returns the summary.
`access` stays true until `currentPeriodEnd`; `status` becomes `CANCELLED` and
`cancelAtPeriodEnd` becomes true. Errors: `400` no active subscription, `403` not an admin,
`503` Razorpay keys are not configured, `502` Razorpay could not be reached.

### POST `/api/billing/webhook`

Public Razorpay webhook. Send the raw event body with the `X-Razorpay-Signature` header (and
optionally `X-Razorpay-Event-Id`). The server verifies
`HMAC-SHA256(raw_body, RAZORPAY_WEBHOOK_SECRET)`, ignores duplicate event ids, updates the
subscription from the `subscription` entity, and records `payment` entities in
`billing_payments`. Response `200` `{ "status": "ok" }`.

Errors: `400` invalid signature or malformed payload, `503` `RAZORPAY_WEBHOOK_SECRET` is not
configured. The webhook is intentionally outside the tenant transaction because Razorpay has no
JWT; it matches rows by the subscription id or the `workspace_id` note written at checkout.
