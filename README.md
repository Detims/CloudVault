# CloudVault

CloudVault is a small portfolio application for securely storing private documents. It is also a study reference: the docs explain why the pieces exist, and the application is organized so that each responsibility can be followed from an HTTP request to its storage adapter.

## Scope

- React, TypeScript, and Vite web client
- One modular Java Spring Boot backend
- Google OAuth 2.0 / OpenID Connect login, followed by a server-managed session
- Cassandra for document metadata and ownership
- Private Amazon S3 objects, accessed by short-lived presigned URLs
- Local development with Cassandra and local deployment to Minikube
- GitHub Actions for checks and GHCR image publishing; deployment to a local cluster is an explicit, manually approved workflow on a self-hosted runner

This is a portfolio MVP, not a production-ready document-management service. See [the architecture and security notes](docs/architecture.md), [the learning guide](docs/learning-guide.md), and [the two-week delivery plan](docs/milestones.md).

For setup, environment variables, Google redirect URIs, and S3 bucket configuration, start with [the development guide](docs/development.md) and [the API reference](docs/api.md).

## Repository map

```text
backend/                 Spring Boot API and focused tests
frontend/                React + TypeScript user interface
infra/local/             Cassandra development container setup
infra/k8s/               Kubernetes manifests for Minikube
.github/workflows/       CI, image publishing, and gated local deployment
docs/                    Architecture, setup, and learning reference
```

## Development status

The application, development environment, and local deployment resources are implemented. Follow [docs/development.md](docs/development.md) to bring up Cassandra and start the apps. Real Google and AWS credentials are intentionally not included. Keep them in an untracked local environment file or secret manager; never commit them. Builds and live OAuth, S3, and Minikube flows still need to be run with your local credentials and tools.

## License

No license has been selected yet. Add one before accepting outside contributions or publishing the repository as open source.
