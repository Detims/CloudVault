# Two-week MVP milestones

| Timebox | Deliverable | Learning checkpoint |
|---|---|---|
| Days 1–2 | Repository layout, local Cassandra, health endpoint, React shell | Follow a browser request through Vite to Spring |
| Days 3–4 | OIDC login, profile endpoint, session and CSRF setup | Explain identity, session, and CSRF boundaries |
| Days 5–7 | Cassandra document metadata, ownership rules, API tests | Design tables from access patterns and test cross-user denial |
| Days 8–9 | Private S3 bucket configuration and presigned upload/download | Explain why the API signs a URL instead of proxying file bytes |
| Days 10–11 | Upload/list/download/delete UI and failure states | Trace one document lifecycle end to end |
| Days 12–13 | Container images, Minikube manifests, health checks | Explain config, secret injection, probes, and image loading |
| Day 14 | CI, GHCR publishing, controlled self-hosted CD, README review | Explain permissions and the hosted/self-hosted runner boundary |

## MVP acceptance criteria

- A user signs in with Google OIDC and gets a server-managed session.
- A user can upload, list, download, and delete their own documents.
- Requests cannot read or mutate another user's documents, including by guessing an ID.
- File bytes live in a private S3 bucket; upload and download access uses expiring presigned URLs.
- Metadata is stored in Cassandra using owner-first access patterns.
- The UI, API, and Cassandra can run locally; the app can be deployed to Minikube with the user's configuration.
- Pull requests run automated checks. The default branch can publish an image to GHCR. A user explicitly dispatches local-cluster deployment on their configured self-hosted runner.
- No OAuth client secrets, AWS credentials, or actual customer documents are committed.

## Known MVP limits

One backend replica is required until the session store is made shared and durable. The user supplies Google OAuth and AWS setup. File type and size limits reduce accidental misuse but do not replace malware scanning. Deleting the Cassandra record and deleting the S3 object cannot be one distributed transaction; the application should surface and log partial failures.
