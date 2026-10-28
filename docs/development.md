# Local development

## Requirements

- Java 21 and Maven 3.6.3 or newer
- Node.js 20.19+ and npm
- Docker Compose for Cassandra
- Google OAuth client credentials for the login flow
- An AWS account, private S3 bucket, and AWS credentials for document storage

Spring Boot 3.5 targets Java 17+; this project compiles against Java 21. Cassandra and local tools are intended for learning, not production sizing.

## Configure local secrets

Copy `.env.example` to `.env.local`, add the Google OAuth client ID and secret, and set the real S3 bucket name and AWS region. Keep `.env.local` untracked. The Spring backend reads environment variables; it does not read `.env.local` by itself. In PowerShell, load the file into the current shell with `scripts/load-env.ps1` before starting Maven. AWS credentials can use the normal AWS shared profile or role credential chain on the host. Never add credentials to a tracked file.

In Google Cloud Console, configure the OAuth consent screen and create a Web application OAuth client. Add these exact callback URLs for the local mode you plan to use:

| Mode | Authorized redirect URI |
|---|---|
| Vite dev server | `http://localhost:5173/login/oauth2/code/google` |
| Minikube service tunnel | `http://localhost:30080/login/oauth2/code/google` |

Use the matching frontend origin: `http://localhost:5173` or `http://localhost:30080`.

## Start Cassandra and the API/UI

```powershell
docker compose -f infra/local/compose.yaml up -d cassandra cassandra-schema
.\scripts\load-env.ps1 .env.local
mvn -f backend/pom.xml spring-boot:run
```

In a second terminal:

```powershell
npm --prefix frontend install
npm --prefix frontend run dev
```

Open `http://localhost:5173`. Ensure `FRONTEND_ORIGIN=http://localhost:5173` in the environment before starting Spring. The Vite proxy routes login callbacks and API requests through the same browser origin.

## S3 bucket setup

Create a private bucket in the configured region. Choose a DNS-compatible bucket name without periods so the virtual-hosted S3 upload URL works with HTTPS. Keep S3 Block Public Access enabled, enable default encryption, and grant the application role only `s3:PutObject`, `s3:GetObject`, and `s3:DeleteObject` for that bucket's objects. Configure bucket CORS to permit the exact frontend origins you use with `POST`, `GET`, and `HEAD`; allow the `Content-Type` request header. For Vite, use `http://localhost:5173`; for the Minikube port-forward, use `http://localhost:30080`. The app signs upload policies for a specific key, content type, and file size. A starting CORS file is in [infra/aws/s3-cors.example.json](../infra/aws/s3-cors.example.json). Do not make the bucket public.

The file size and type checks limit accidental misuse; they are not a malware scanner. The S3 bytes are never proxied through Spring.

## Minikube

See [the Kubernetes setup guide](../infra/k8s/README.md). The deployment is one backend replica until a shared session store is added. Create the Google and AWS Kubernetes secrets on your machine and do not commit them. Configure the local S3 CORS origin as `http://localhost:30080`.

## Common commands

```powershell
mvn -f backend/pom.xml test
npm --prefix frontend run typecheck
npm --prefix frontend test
npm --prefix frontend run build
```

The GitHub Actions workflow runs these checks automatically on pull requests.
