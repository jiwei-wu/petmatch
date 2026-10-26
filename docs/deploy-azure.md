# Deploying PetMatch to Azure Container Apps

The API runs on **Azure Container Apps**. The database stays on **Neon** (serverless PostgreSQL + PostGIS).
Every push to `main` runs **test → build image → deploy**. GitHub Actions logs in to Azure with **OIDC**, so no Azure password or key is stored in GitHub.

```
push to main ─► test (Testcontainers) ─► build image ─► ghcr.io/jiwei-wu/petmatch:<sha>
                                                        │
                       GitHub OIDC token ─► Azure managed identity ─► az containerapp update
                                                        │
                                   Azure Container Apps (HTTPS, scale to zero) ─► Neon PostgreSQL
```

You do the one-time setup below by hand, in about an hour. After that, deploys are automatic.

---

## 0. Before you start

- An **Azure for Students** subscription (sign up with your university email at https://azure.microsoft.com/free/students).
- Your **Neon** connection details: host, database, user and password, from the Neon console → *Connect*.
- Use **Azure Cloud Shell** (the `>_` icon at the top of the Azure portal, Bash mode). The `az` CLI is already installed there. If it asks for storage, choose the option with no storage account.

> The nightly cleanup job assumes **one running instance** (see README → Limitations).
> When the Azure deployment works, **suspend the Render service** so that only one copy of the app uses the database.

## 1. Set variables

```bash
RG=petmatch-rg
LOC=germanywestcentral   # must be one of your subscription's allowed regions (see below)
ENV=petmatch-env
APP=petmatch
ID=petmatch-github-deploy
GH_REPO=jiwei-wu/petmatch
SUB=$(az account show --query id -o tsv)
TENANT=$(az account show --query tenantId -o tsv)
```

Azure for Students only allows a few regions, and the list is set per subscription. To see yours:

```bash
az policy assignment list --query "[].parameters" -o json   # look for listOfAllowedLocations
```

## 2. Create the Container Apps environment

```bash
az extension add --name containerapp --upgrade
az provider register --namespace Microsoft.App --wait
az provider register --namespace Microsoft.OperationalInsights --wait

az group create -n $RG -l $LOC
az containerapp env create -n $ENV -g $RG -l $LOC
```

## 3. Create the app, with its secrets

The secrets are stored in Container Apps and passed to the app as environment variables.
They are not in the image or in the Git repository.

```bash
# Paste each line on its own. Pasting several `read` lines at once feeds the next line in as the answer.
read -rp "Neon host: " NEON_HOST            # e.g. ep-xxxx.c-2.eu-west-2.aws.neon.tech (no -pooler)
read -rp "Neon database: " NEON_DB
read -rp "Neon user: " NEON_USER
read -rsp "Neon password: " DB_PASSWORD; echo
echo "host=$NEON_HOST db=$NEON_DB user=$NEON_USER pw_starts=${DB_PASSWORD:0:4} pw_length=${#DB_PASSWORD}"
# Expect pw_starts=npg_ and a length of about 16.

JWT_SECRET=$(openssl rand -base64 48)      # a random 64-character signing key

az containerapp create -n $APP -g $RG --environment $ENV \
  --image mcr.microsoft.com/k8se/quickstart:latest \
  --ingress external --target-port 8080 \
  --cpu 0.5 --memory 1.0Gi \
  --min-replicas 0 --max-replicas 1 \
  --secrets db-password="$DB_PASSWORD" jwt-secret="$JWT_SECRET" \
  --env-vars \
    SPRING_PROFILES_ACTIVE=prod \
    SPRING_DATASOURCE_URL="jdbc:postgresql://${NEON_HOST}/${NEON_DB}?sslmode=require" \
    SPRING_DATASOURCE_USERNAME="$NEON_USER" \
    SPRING_DATASOURCE_PASSWORD=secretref:db-password \
    JWT_SECRET=secretref:jwt-secret \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
```

- **Neon URL:** Neon gives you `postgresql://user:pass@HOST/DB?sslmode=require...`. Keep only the host and database name in the JDBC URL above. Drop `channel_binding` if it is there.
- **Placeholder image:** the app starts with a Microsoft sample image, which does not listen on port 8080. It shows as unhealthy until the first real deploy. This is expected.
- **Scaling:** `--max-replicas 1` keeps a single instance, as the cleanup job needs. `--min-replicas 0` lets the app scale to zero when idle, which keeps it inside the monthly free grant.

## 4. Let GitHub deploy without a stored key (OIDC)

This uses a **user-assigned managed identity**, not an app registration. University tenants often block students from creating app registrations, but you can create a managed identity in your own subscription.

```bash
az identity create -n $ID -g $RG
CLIENT_ID=$(az identity show -n $ID -g $RG --query clientId -o tsv)
PRINCIPAL_ID=$(az identity show -n $ID -g $RG --query principalId -o tsv)

# Trust only workflow jobs that run in this repo's "production" environment.
# GitHub now puts the owner and repo IDs in the subject, so register that form.
# (Find the IDs in a failed azure/login log, or: gh api repos/$GH_REPO --jq '"\(.owner.id) \(.id)"')
az identity federated-credential create --name github-production-ids \
  --identity-name $ID -g $RG \
  --issuer https://token.actions.githubusercontent.com \
  --subject "repo:jiwei-wu@234907513/petmatch@1398878289:environment:production" \
  --audiences api://AzureADTokenExchange

# Least privilege: the identity can change this resource group only, not the whole subscription.
az role assignment create \
  --assignee-object-id $PRINCIPAL_ID --assignee-principal-type ServicePrincipal \
  --role Contributor --scope /subscriptions/$SUB/resourceGroups/$RG

echo "AZURE_CLIENT_ID=$CLIENT_ID"
echo "AZURE_TENANT_ID=$TENANT"
echo "AZURE_SUBSCRIPTION_ID=$SUB"
```

## 5. Configure GitHub

In the repo, open **Settings**.

1. **Environments → New environment → `production`**. Under *Deployment branches and tags*, choose **Selected branches** and add `main`.
2. **Secrets and variables → Actions → Variables** (these are IDs, not secrets). Add:

   | Name | Value |
   |---|---|
   | `AZURE_CLIENT_ID` | from step 4 |
   | `AZURE_TENANT_ID` | from step 4 |
   | `AZURE_SUBSCRIPTION_ID` | from step 4 |
   | `AZURE_RESOURCE_GROUP` | `petmatch-rg` |
   | `AZURE_CONTAINERAPP` | `petmatch` |

## 6. First deploy

1. Push to `main`. The *test* and *build-image* jobs push `ghcr.io/jiwei-wu/petmatch:<sha>`.
2. **First time only:** GitHub creates the image package as **private**, so Azure cannot pull it, and the *deploy* job fails.
   Open your GitHub profile → **Packages → petmatch → Package settings → Change visibility → Public**.
   The image has no secrets in it, and the source code is already public.
3. In the Actions run, click **Re-run failed jobs**. The smoke test prints the live URL:
   `https://petmatch.<random>.germanywestcentral.azurecontainerapps.io/swagger-ui.html`

From now on, each push to `main` deploys by itself.

## 7. Guardrails

- **Budget alert:** Azure portal → *Cost Management → Budgets*. Set a small amount, for example €5, with an email alert.
- **Free grant:** the Container Apps consumption plan includes a monthly free grant of vCPU-seconds, GiB-seconds and requests. With scale to zero and light demo traffic, usage should stay inside it.
- **Logs:** `az containerapp logs show` does not work with *Express* environments yet, so query Log Analytics:
  ```bash
  WS=$(az containerapp env show -n $ENV -g $RG --query properties.appLogsConfiguration.logAnalyticsConfiguration.customerId -o tsv)
  az monitor log-analytics query -w $WS -o tsv --analytics-query \
    "ContainerAppConsoleLogs_CL | where ContainerAppName_s == 'petmatch' | sort by TimeGenerated desc | take 80 | project TimeGenerated, Log_s"
  ```
  Logs take a few minutes to arrive.

## Troubleshooting

| What you see | What it means |
|---|---|
| `RequestDisallowedByPolicy` / `RequestDisallowedByAzure` | The region is not allowed for your subscription. Pick one from `listOfAllowedLocations` (step 1). |
| `ContainerAppSecretInvalid` for `db-password` | `DB_PASSWORD` is empty, usually because several `read` lines were pasted at once. |
| App exits with `Could not resolve placeholder 'JWT_SECRET'` (or a datasource value) | A variable is missing. This is the `prod` profile's fail-fast check working. Fix the variable with `az containerapp update --set-env-vars ...`. |
| `azure/login` fails with `AADSTS700213` / `AADSTS70021` (no matching federated identity) | The subject in step 4 does not match the one in the log. Copy the exact subject from the error into a new federated credential. |
| Smoke test keeps getting `503`; logs show `password authentication failed` | Wrong Neon password. Fix it with `az containerapp secret set -n $APP -g $RG --secrets db-password="$DB_PASSWORD"`, then wake the app. |
| Deploy succeeds but pulling the image fails | The ghcr package is still private (see step 6). |
| First request takes 20–40 s | Cold start after scaling to zero. Open the link once before a demo or an interview. |
