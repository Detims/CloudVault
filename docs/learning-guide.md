# Learning guide

Use this repository as a reference in small passes. Start with one request and trace it through the layers before moving to the next feature.

## Suggested order

1. **HTTP and UI** — follow the sign-in button and the `GET /api/me` request. Identify which work belongs in React and which belongs on the server.
2. **Spring structure** — trace a controller into an application service, then into a repository or storage adapter. The controller should translate HTTP; it should not own policy.
3. **OIDC and sessions** — inspect the Google client configuration, callback, principal mapping, session cookie, and CSRF token. Distinguish login (who the user is) from authorization (which document they may access).
4. **Cassandra** — map each query to a table key. In Cassandra, choose the table around the reads the feature needs rather than treating it like a relational database.
5. **S3 presigning** — follow upload intent, direct browser POST, completion, and download. The signed POST policy restricts the key, MIME type, and file size; AWS credentials remain on the server.
6. **Containers and Kubernetes** — read the Dockerfile, ConfigMap, Secret references, probes, and service. Trace how a local image reaches Minikube.
7. **GitHub Actions** — compare pull-request checks, GHCR publishing, and manually dispatched local deployment. Notice which permissions each workflow receives.

## Study questions

- Why does the API derive the owner from the authenticated principal instead of accepting `ownerId` in JSON?
- What happens if a user guesses another user's document UUID?
- Why is an upload marked `PENDING_UPLOAD` before S3 receives bytes?
- Which Cassandra query does the partition key make efficient?
- Why can a GitHub-hosted runner publish to GHCR but not normally deploy into a laptop's Minikube cluster?
- What must change before multiple backend replicas can safely share user sessions?

For each feature, read its tests and then change one behavior in a small branch. Avoid copying credentials from a local file into a test fixture or commit.
