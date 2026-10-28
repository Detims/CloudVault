# Run CloudVault on Minikube

This setup is intentionally single-node and for learning. Cassandra uses one local persistent volume, the backend has one replica, and the app depends on a real private S3 bucket. Do not use these manifests as a production template.

## Prerequisites

- Minikube with at least 4 CPU cores and 4 GiB RAM
- `kubectl` configured for the `minikube` context
- Docker or another image builder supported by Minikube
- Google OAuth web client and private S3 bucket
- Published GHCR images for the controlled deployment path, or local images for the learning path

## Build and load local images

From the repository root:

```powershell
docker build -f backend/Dockerfile -t cloudvault-api:dev .
docker build -f frontend/Dockerfile -t cloudvault-web:dev .
minikube image load cloudvault-api:dev
minikube image load cloudvault-web:dev
```

## Create local credentials

Create the namespace first, then create Secrets from your own terminal. These commands do not store secret values in the repository. Use an AWS profile with only the required bucket permissions. The application secret contains the Google client credentials and bucket name; the AWS file Secret is optional at pod start but required to use S3 unless the cluster has another AWS credential provider.

```powershell
kubectl apply -f infra/k8s/namespace.yaml
Copy-Item .env.example .env.local
# Edit .env.local locally with your OAuth values and S3 bucket name.
kubectl -n cloudvault create secret generic cloudvault-app --from-env-file=.env.local
kubectl -n cloudvault create secret generic cloudvault-aws `
  --from-file=credentials="$env:USERPROFILE\.aws\credentials"
```

For a private GHCR deployment, create a read-only package token through the hidden prompt. Local images loaded into Minikube do not need this registry secret.

```powershell
$ghcrPassword = Read-Host "GitHub token with read:packages permission" -AsSecureString
$ghcrToken = [System.Net.NetworkCredential]::new("", $ghcrPassword).Password
kubectl -n cloudvault create secret docker-registry ghcr-pull-secret `
  --docker-server=ghcr.io `
  --docker-username='your-github-username' `
  "--docker-password=$ghcrToken"
Remove-Variable ghcrToken, ghcrPassword
```

The token is entered through a hidden prompt instead of shell history. For repeat setup, add `--dry-run=client -o yaml | kubectl apply -f -` to each `create secret` command. Treat AWS credentials and the GitHub package token as secrets; do not paste them into GitHub Actions logs or commit YAML files containing values.

Alternatively, make both GHCR packages public and remove the private-pull-secret step from the deployment workflow.

## Deploy

```powershell
kubectl apply -f infra/k8s/namespace.yaml
kubectl apply -f infra/k8s/cassandra.yaml
kubectl apply -f infra/k8s/application.yaml
kubectl -n cloudvault wait --for=condition=complete job/cloudvault-cassandra-schema --timeout=5m
kubectl -n cloudvault rollout status statefulset/cloudvault-cassandra --timeout=5m
kubectl -n cloudvault rollout status deployment/cloudvault-backend --timeout=5m
kubectl -n cloudvault rollout status deployment/cloudvault-frontend --timeout=5m
kubectl -n cloudvault port-forward service/cloudvault-frontend 30080:80
```

Keep the port-forward command running and open `http://localhost:30080`. The OAuth callback registered in Google must be `http://localhost:30080/login/oauth2/code/google`. Configure S3 CORS to allow `http://localhost:30080` for `POST` requests with the `Content-Type` header.

The `cloudvault-cassandra-schema` job creates the keyspace/table. The backend init container waits for that schema. If you change the Cassandra schema, update both `infra/local/schema.cql` and the Kubernetes ConfigMap in `infra/k8s/cassandra.yaml`.

## Controlled GitHub Actions deploy

Register a self-hosted runner on the machine that can reach Minikube and label it `cloudvault-minikube`. It needs `kubectl`, a working `minikube` context, and the `ghcr-pull-secret` in the cluster. Protect the GitHub Actions environment named `local-minikube` with any approval rules you want. The workflow starts only by manual dispatch and accepts an image tag; prefer the immutable `sha-...` tag produced by CI. The runner applies these resources and waits for both app rollouts. A GitHub-hosted runner cannot reach a Minikube cluster on a personal workstation.
