# API reference

The browser sends application requests to the same origin as the frontend. During Vite development, Vite proxies API and login paths to Spring Boot. In Minikube, Nginx proxies those paths to the backend service.

## Authentication

Start sign-in with `GET /oauth2/authorization/google`. After Google returns to Spring Security, the backend creates an HTTP session and redirects to the configured frontend origin. The browser session cookie is HttpOnly and SameSite=Lax. The UI first calls `GET /api/csrf`, which sets the readable `XSRF-TOKEN` cookie; it copies that value into the `X-XSRF-TOKEN` header on every POST and DELETE. `POST /logout` ends the session and requires that CSRF header.

Unauthenticated API requests return `401`. OAuth login is a browser navigation, not a fetch call. The API uses the verified OIDC subject as the document owner.

## Endpoints

### `GET /api/health`

Public liveness response: `{"status":"ok"}`.

### `GET /api/csrf`

Public, returns `204` and sets the CSRF cookie.

### `GET /api/me`

Returns the signed-in profile:

```json
{"id":"google-subject","name":"Example User","email":"user@example.test","picture":"https://..."}
```

### `GET /api/documents`

Returns metadata belonging to the current user. It never returns S3 object keys.

### `POST /api/documents`

Creates a pending metadata row and returns a short-lived upload URL. Supported content types are PDF, PNG, JPEG, and plain text. The maximum upload size defaults to 25,000,000 bytes.

Request:

```json
{"fileName":"notes.txt","contentType":"text/plain","sizeBytes":2048}
```

Response (`201`):

```json
{"documentId":"uuid","uploadUrl":"https://...","fields":{"key":"users/...","policy":"...","x-amz-algorithm":"AWS4-HMAC-SHA256"},"expiresAt":"2026-01-01T00:05:00Z","contentType":"text/plain","maxFileBytes":25000000}
```

Create a `multipart/form-data` POST to `uploadUrl`. Add every name/value pair in the returned `fields` object, then append the file under the field name `file` as the final part. Do not set the multipart `Content-Type` manually; the browser adds its boundary. The S3 POST policy restricts the upload to the generated object key, requested content type, and exact declared file size. The S3 bucket CORS policy must permit POST from the frontend origin. Amazon S3's POST policy supports a `content-length-range` condition for this limit. [AWS POST policy documentation](https://docs.aws.amazon.com/AmazonS3/latest/developerguide/sigv4-HTTPPOSTConstructPolicy.html)

### `POST /api/documents/{id}/complete`

Checks that the object exists and its size matches the upload intent, then marks it available. Ownership is checked before S3 is queried.

### `GET /api/documents/{id}/download`

Returns a short-lived download URL only for an available document owned by the caller. The URL sets attachment disposition using a sanitized filename.

### `DELETE /api/documents/{id}`

Deletes the owned S3 object and then its metadata. S3 and Cassandra cannot participate in one transaction; if deletion fails partway, retry or inspect the record and object before cleanup.

## Error behavior

- `400`: malformed metadata, invalid size, or upload size mismatch
- `401`: no valid session
- `404`: the document does not exist in the current user's partition
- `409`: upload has not been completed
- `413`: request size limits may be enforced by the proxy (document bytes go directly to S3)
- `415`: file type is not in the allowlist
- `503`: S3 bucket configuration is missing

The service deliberately returns the same `404` for a missing ID and an ID owned by another user.
