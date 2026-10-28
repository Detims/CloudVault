# Architecture and security model

## Request path

The browser uses the Spring Boot API on the same local origin through Vite's development proxy. Spring Security sends unauthenticated users through Google's OpenID Connect login. After callback, the backend keeps the login in an HTTP session and returns the browser to the app. The API derives the user ID from the authenticated OIDC `sub` claim; callers never submit an owner ID.

```text
React browser -- session cookie --> Spring MVC API
                                      |-- Cassandra: document metadata + owner
                                      |-- S3: private document bytes
Google OIDC --------------------------|
```

The backend has four conceptual parts: web/API types, application services, domain rules, and adapters for Cassandra and S3. They are packages in one deployable Spring application, not separate services.

## API contract

| Method and path | Purpose | Authentication |
|---|---|---|
| `GET /api/health` | Liveness response | Public |
| `GET /api/me` | Current user profile | Signed in |
| `GET /api/documents` | List only the current user's metadata | Signed in |
| `POST /api/documents` | Create an upload intent and a short-lived S3 PUT URL | Signed in |
| `POST /api/documents/{id}/complete` | Confirm upload and mark metadata available | Signed in |
| `GET /api/documents/{id}/download` | Create a short-lived S3 GET URL for an owned document | Signed in |
| `DELETE /api/documents/{id}` | Delete owned metadata and object | Signed in |
| `POST /logout` | End the server session | Signed in + CSRF token |

The upload client sends bytes directly to S3 with the returned URL. The API will constrain file size, allowed content types, and key generation; it will not trust a client-provided object key. URL expiry is short. A document is not listed as available until its upload is confirmed. The API must check ownership before every read, download, completion, or delete operation.

## Identity and browser security

- Google is an OIDC identity provider; the backend is an OAuth2 client. Only the verified subject claim is used as the durable owner key. Email and display name are profile attributes, not authorization keys.
- OAuth client secret and AWS credentials are runtime configuration. They do not belong in Git, frontend variables, Docker build arguments, or image layers.
- The browser receives an `HttpOnly` session cookie with `SameSite=Lax`; `Secure` must be enabled whenever the app is served over HTTPS. Session fixation protection is provided by Spring Security.
- Cookie-authenticated write requests require CSRF protection. The UI obtains a CSRF token from the backend and sends it in a request header.
- CORS is restricted to the configured frontend origin. The Vite proxy avoids cross-origin cookies in local development.
- Production deployments need TLS, durable/replicated session storage before scaling beyond one backend replica, rate limits, audit logging, malware scanning, backups, and a documented data-retention policy. Those are outside this first MVP.

## Data model

`documents` is queried by authenticated owner and newest-first creation time, so the Cassandra partition key is the owner subject. The UUID is a clustering key to support stable per-owner listing and point lookup.

```text
documents (
  owner_id text,            -- OIDC subject, partition key
  document_id timeuuid,     -- clustering key, descending
  file_name text,
  content_type text,
  size_bytes bigint,
  object_key text,          -- generated server-side; never exposed as authority
  status text,              -- PENDING_UPLOAD | AVAILABLE
  created_at timestamp,
  PRIMARY KEY ((owner_id), document_id)
)
```

Only the owner-partition query is supported. The API does not offer global listing or arbitrary object-key access. If document lookup by ID needs a separate access pattern, add a deliberate table and keep its owner check in the service layer rather than enabling filtering.

## Storage rules

The S3 bucket stays private and blocks public access. Presigned URLs grant a single object operation for a short period. The backend role should have only the bucket/object actions required for this application. For local development, use real AWS credentials from the standard AWS credential chain; never put a long-lived access key in Kubernetes YAML. Encryption at rest and TLS in transit should be enabled on the bucket.

## Deployment boundary

Minikube is the portfolio deployment target. Cassandra is deployed locally for the demo; AWS S3 remains a real external dependency. The initial Kubernetes deployment runs one backend replica because the starter session store is process-local. Do not scale it until session persistence and shared CSRF behavior are configured. GHCR publication runs on the default hosted GitHub runner. A separate, manually dispatched deployment workflow targets a user-managed self-hosted runner that can reach Minikube; it does not make a hosted runner's network reach a laptop cluster.
