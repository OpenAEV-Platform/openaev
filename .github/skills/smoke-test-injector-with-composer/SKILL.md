---
name: smoke-test-injector-with-composer
description: >-
  Stands up an isolated OpenAEV + XTM Composer Docker stack to smoke-test an injector
  end-to-end (catalog contract → Composer deploy → container startup → RabbitMQ inject
  → actual payload delivery). Use when asked to test a PR that adds/changes an injector
  by actually running it, or to verify a catalog entry is correctly wired at runtime.
---

# Smoke-test an injector with XTM Composer

## When to use this

- A PR adds or changes an entry in `openaev-api/src/main/resources/resources/catalog/catalog-integrators.json`
  (or any other injector catalog file) and you want proof it works at runtime, not just that
  the JSON is valid.
- You need to verify Composer can pull the injector image, deploy it, and that it
  registers with OpenAEV and processes an inject from RabbitMQ.

This spins up a **throwaway, isolated** stack (own Docker network, own volumes) —
it does not touch any existing project containers/volumes on the machine.

## Prerequisites

- Docker (or Podman) running locally, with enough resources for Postgres + Elasticsearch +
  RabbitMQ + OpenAEV + Composer (~6-8GB RAM recommended).
- The PR branch checked out locally with a buildable `Dockerfile` at the repo root.
- Network access to pull base images (`postgres`, `rabbitmq`, `elasticsearch`, `mailpit`,
  `filigran/xtm-composer`, and the injector image referenced in the catalog entry's
  `catalog_connector_container_image`, e.g. `openaev/injector-email-smtp:rolling`).

## Procedure

### Step 1 — Build the OpenAEV image from the PR branch

```bash
docker build --tag openaev-smoke:latest .
```

Run from the repo root (frontend must already be built if the Dockerfile expects it —
check `openaev-front/builder/prod/build/` or run `yarn build` first if the multi-stage
build doesn't do it for you).

### Step 2 — Create an isolated compose stack

Create `/tmp/openaev-smoke/compose.yml` (keep it outside the repo — it's throwaway
infra, not something to commit). Use a **unique `-p` project name** per session so
stacks never collide.

Minimal working topology (adapt image tags/ports if they've changed):

```yaml
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_USER: openaev
      POSTGRES_PASSWORD: openaev-smoke-password
      POSTGRES_DB: openaev
    volumes: [postgres-data:/var/lib/postgresql/data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U openaev -d openaev"]
      interval: 5s
      timeout: 5s
      retries: 20
  minio:
    # minio/minio:RELEASE.* tags get delisted from Docker Hub over time and may 403/404.
    # pgsty/silo:latest is a drop-in S3-compatible replacement that speaks the same API.
    image: pgsty/silo:latest
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    command: server /data
    volumes: [minio-data:/data]
    healthcheck:
      test: ["CMD", "mc", "ready", "local"]
      interval: 5s
      timeout: 5s
      retries: 20
  rabbitmq:
    image: rabbitmq:4.2-management
    environment:
      RABBITMQ_DEFAULT_USER: openaev
      RABBITMQ_DEFAULT_PASS: rabbitmq-smoke-password
    volumes: [rabbitmq-data:/var/lib/rabbitmq]
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 10s
      timeout: 10s
      retries: 12
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.19.12
    environment:
      discovery.type: single-node
      xpack.ml.enabled: "false"
      xpack.security.enabled: "false"
      ES_JAVA_OPTS: -Xms1g -Xmx1g -XX:+IgnoreUnrecognizedVMOptions -XX:UseSVE=0
    volumes: [elasticsearch-data:/usr/share/elasticsearch/data]
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://localhost:9200/_cluster/health?wait_for_status=yellow >/dev/null"]
      interval: 10s
      timeout: 5s
      retries: 30
  mailpit:
    # Only needed for email-family injectors. Expose ports if you want to eyeball
    # the web UI / send test SMTP from the host — see the port-mapping gotcha below.
    image: axllent/mailpit:latest
    ports: ["8026:8025", "1026:1025"]
  rsa-key-generator:
    # Composer requires an RSA keypair for its manager identity.
    image: alpine/openssl:3.5.5
    volumes: [rsa-keys:/keys]
    entrypoint: ["/bin/ash"]
    command: ["-c", "if [ ! -f /keys/private_key.pem ]; then openssl genpkey -algorithm RSA -out /keys/private_key.pem -pkeyopt rsa_keygen_bits:4096; fi && tail -f /dev/null"]
    healthcheck:
      test: ["CMD", "test", "-f", "/keys/private_key.pem"]
      interval: 5s
      timeout: 5s
      retries: 20
  openaev:
    image: openaev-smoke:latest
    environment:
      OPENAEV_BASE-URL: http://localhost:8081
      OPENAEV_AUTH-LOCAL-ENABLE: "true"
      # Must be a real-looking TLD — admin.email validation rejects .local / .test etc.
      OPENAEV_ADMIN_EMAIL: admin@openaev.io
      OPENAEV_ADMIN_PASSWORD: OpenAEVSmokeTest2026
      OPENAEV_ADMIN_TOKEN: 8c23fc1c-0196-4aa1-8aa1-6e21aae6a1b5
      OPENAEV_HEALTHCHECK_KEY: healthcheck-smoke-key
      OPENAEV_ADMIN_ENCRYPTION_KEY: 0OhSkm3ZvFgu3KLgGmKbVNafRuwryqeg
      OPENAEV_ADMIN_ENCRYPTION_SALT: i4TJssA4vjxqXiLO
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/openaev
      SPRING_DATASOURCE_USERNAME: openaev
      SPRING_DATASOURCE_PASSWORD: openaev-smoke-password
      MINIO_ENDPOINT: minio
      MINIO_ACCESS-KEY: minioadmin
      MINIO_ACCESS-SECRET: minioadmin
      OPENAEV_RABBITMQ_HOSTNAME: rabbitmq
      OPENAEV_RABBITMQ_USER: openaev
      OPENAEV_RABBITMQ_PASS: rabbitmq-smoke-password
      ENGINE_URL: http://elasticsearch:9200
      SPRING_ELASTICSEARCH_URIS: http://elasticsearch:9200
      # Email-family injectors only — point at mailpit's INTERNAL port (1025), not the
      # host-published one.
      SPRING_MAIL_HOST: mailpit
      SPRING_MAIL_PORT: "1025"
      SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH: "false"
      SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE: "false"
      SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE: "false"
      OPENAEV_MAIL_IMAP_ENABLED: "false"
      OPENAEV_XTM_OPENCTI_ENABLE: "false"
    ports: ["8081:8080"]
    depends_on:
      postgres: {condition: service_healthy}
      minio: {condition: service_healthy}
      rabbitmq: {condition: service_healthy}
      elasticsearch: {condition: service_healthy}
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/api/health?health_access_key=healthcheck-smoke-key"]
      interval: 10s
      timeout: 5s
      retries: 30
  xtm-composer:
    image: filigran/xtm-composer:latest
    platform: linux/amd64
    environment:
      MANAGER__ID: 99c2e494-0b37-42f2-ad42-d690e2424e78
      MANAGER__NAME: OpenAEV Smoke XTM Composer
      MANAGER__CREDENTIALS_KEY_FILEPATH: /keys/private_key.pem
      OPENCTI__ENABLE: "false"
      OPENAEV__ENABLE: "true"
      OPENAEV__URL: http://openaev:8080
      OPENAEV__TOKEN: 8c23fc1c-0196-4aa1-8aa1-6e21aae6a1b5
      OPENAEV__DAEMON__SELECTOR: docker
      # MUST match this compose project's default network (see Step 3 gotcha).
      OPENAEV__DAEMON__DOCKER__NETWORK_MODE: <project-name>_default
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
      - rsa-keys:/keys:ro
    depends_on:
      rsa-key-generator: {condition: service_healthy}
      rabbitmq: {condition: service_healthy}
      openaev: {condition: service_healthy}
volumes:
  postgres-data: {}
  minio-data: {}
  rabbitmq-data: {}
  elasticsearch-data: {}
  rsa-keys: {}
```

Start it with a project name so container/network names are predictable:

```bash
docker compose -p smoke-test -f /tmp/openaev-smoke/compose.yml up -d
```

Set `OPENAEV__DAEMON__DOCKER__NETWORK_MODE` to `<project-name>_default` (e.g.
`smoke-test_default`) — this is Composer's setting for which Docker network to
attach injector containers to. It must match the project name.

### Step 3 — Fix the fresh-tenant health-check 503 (one-time)

On a brand-new MinIO/Silo bucket, `/api/health` can 503 forever because
`MinioService.isTenantPathExists()` does a `statObject` on the tenant's root S3
"directory" marker, which doesn't exist until something is actually uploaded.
Work around it by creating an empty marker object once, using the default seed
tenant ID `2cffad3a-0001-4078-b0e2-ef74274022c3`:

```bash
docker exec smoke-test-minio-1 sh -c \
  "echo -n '' | mc pipe local/openaev/2cffad3a-0001-4078-b0e2-ef74274022c3/"
```

(Adjust the bucket name / mc alias if your compose uses different minio env vars.)
After this, `docker compose -p smoke-test ps` should show `openaev` as `healthy`.

### Step 4 — Verify Composer registered

```bash
docker logs smoke-test-xtm-composer-1 | grep -i "manager registered"
```

A single early `ERROR ... Failed to fetch connector instances: status 400` right
before `Manager registered: <uuid>` is a benign startup ordering artifact — ignore it.

### Step 5 — Deploy the injector via the OpenAEV UI/API

Log in at `http://localhost:8081` with the admin credentials from Step 2, then
enable/deploy the injector connector from the catalog (via UI or API). Composer
will pull the image referenced by the catalog entry's `catalog_connector_container_image`
and create a new container.

**Gotcha — wrong Docker network on the spawned container:** Composer-created
injector containers can sometimes land on the default Docker `bridge` network
instead of the compose project's network, breaking hostname resolution
(`Failed to resolve 'openaev'`). If the injector container keeps restarting with a
`NameResolutionError`, fix it directly:

```bash
docker network connect <project-name>_default <injector-container-name>
docker restart <injector-container-name>
```

Find the injector container name/id with:

```bash
docker ps -a --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'
```

### Step 6 — Watch the injector's own logs (not just Composer's)

```bash
docker logs -f <injector-container-name>
```

Healthy startup looks like:

```
[..._MAIN] ... injector configuration initialized successfully.
Starting PingAlive thread
Starting ListenQueue thread
ListenQueue connecting to RabbitMQ.
```

### Step 7 — Trigger a real inject and confirm delivery

Send a test inject/payload from an exercise or scenario using this injector, then
watch the same injector log for:

```
Received email inject message ...
Crafting email ... smtp_host=..., smtp_port=...
```

For email-family injectors, double-check the SMTP host/port configured on the
injector matches the **internal** Docker service+port (`mailpit:1025`), not the
host-published port you might use to browse Mailpit's web UI (`localhost:8026` /
`mailpit:1026` in this example) — using the host-published port from inside the
Docker network causes `Connection refused`, since it's a host-side mapping only.

Confirm delivery in Mailpit's web UI at `http://localhost:8026` (or your host port).

## Cleanup

Always tear the stack down when done — it's throwaway infra:

```bash
docker compose -p smoke-test down -v --remove-orphans
# Composer-spawned injector containers aren't part of the compose project; remove them too:
docker ps -a --format '{{.Names}}' | grep -E '<injector-name-pattern>' | xargs -r docker rm -f
docker network rm smoke-test_default 2>/dev/null || true
docker rmi openaev-smoke:latest <injector-image>:<tag> 2>/dev/null || true
rm -rf /tmp/openaev-smoke
```

If `docker compose down` reports the network is "still in use", it's almost always
because a Composer-spawned injector container is still attached to it — remove
that container first, then retry the network removal.

## Known environment gotchas (reference)

| Symptom | Cause | Fix |
|---|---|---|
| `pull access denied for minio/minio` | Old `minio/minio:RELEASE.*` tags delisted from Docker Hub | Use `pgsty/silo:latest` |
| `admin.email should be a valid email address` on startup | `.local`/`.test` TLDs rejected by validator | Use a real-looking TLD, e.g. `admin@openaev.io` |
| `/api/health` returns 503 forever on a fresh stack | No S3 object yet under the tenant's root prefix | Create an empty marker object (Step 3) |
| Injector container crash-loops with `NameResolutionError` for `openaev` | Composer attached the container to the wrong Docker network | `docker network connect <project>_default <container>` + restart |
| SMTP `Connection refused` from an email injector | Using the host-published Mailpit port instead of its internal port | Use `mailpit:1025` (internal), not `mailpit:1026`/`localhost:1026` (host-published) |
