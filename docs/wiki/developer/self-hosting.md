# Self-hosting

The server is optional (only needed for photo scanning + sync) and designed for a trusted home
network. The recommended deployment is Docker; a plain Python run also works.

## Prerequisites

- A Linux machine on the same LAN/Wi-Fi as the phones.
- A **vision-capable OpenAI-compatible Chat Completions API**. This deployment uses LocalAI,
  configured at `http://127.0.0.1:8081/v1` with the local `gemma4-12b` model. Its Compose setup,
  GPU backend, and model files are managed separately from this server. Ollama or another
  compatible provider can be selected with the `OPENCOOK_AI_*` environment variables. Hosted
  providers may receive uploaded recipe photos and may charge for inference.

## Docker (recommended)

```bash
cd server
docker compose up -d --build
docker compose ps                    # STATUS should become "healthy"
curl http://localhost:8000/health    # {"status":"ok"}
docker compose logs -f               # "Job worker started", mDNS advertise line
```

The container runs **only the server** (FastAPI + SQLite + the job worker + mDNS); it calls the
configured Chat Completions endpoint over HTTP. The default is LocalAI at
`http://127.0.0.1:8081/v1`. `network_mode: host` is used so mDNS reaches the LAN and the container
can reach LocalAI on localhost. `restart: unless-stopped` brings it back after a reboot.

Hardening (already set in `docker-compose.yml`): non-root user (uid 10001), `read_only` root
filesystem with a `/tmp` tmpfs, `cap_drop: ALL`, `no-new-privileges`. Only `/data` is writable. No
Docker socket, no `--privileged`.

### Connecting the app

On the same LAN the app **auto-discovers** the server via mDNS and lists it as "openCook". Give it a
friendlier name with `OPENCOOK_SERVER_NAME=Kueche docker compose up -d`. No discovery (VPN/emulator)?
Enter `http://<server-lan-ip>:8000` by hand in onboarding.

## Plain Python

```bash
cd server
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
uvicorn app.main:app            # add --reload for development
```

## Configuration

Environment variables, all prefixed `OPENCOOK_` (a `server/.env` file is read too):

| Variable | Default | Purpose |
|---|---|---|
| `OPENCOOK_DATA_DIR` | `./data` | DB + images + backups root |
| `OPENCOOK_HOST` | `0.0.0.0` | HTTP bind address |
| `OPENCOOK_PORT` | `8000` | HTTP port |
| `OPENCOOK_SERVER_NAME` | hostname | Name advertised over mDNS |
| `OPENCOOK_AI_BASE_URL` | `http://127.0.0.1:8081/v1` | OpenAI-compatible API base URL |
| `OPENCOOK_AI_MODEL` | `gemma4-12b` | Vision model name served by that API |
| `OPENCOOK_AI_API_KEY` | unset | Optional API key; set this in the ignored `server/.env` for providers that require one |
| `OPENCOOK_WORKER_POLL_INTERVAL` | `2.0` | Job worker poll interval (s) |
| `OPENCOOK_BACKUP_DIR` | `<data_dir>/backups` | Backup archive location |
| `OPENCOOK_BACKUP_KEEP` | `14` | Backups to retain (rotation) |
| `OPENCOOK_ADMIN_PASSWORD` | — | Sets the admin password on first start (see below) |

The client uses `POST /chat/completions` and sends the resized photo as a data URL. Choose a
provider and model that support vision input. API keys belong in the ignored `server/.env`, never
in a committed Compose file. For the local LocalAI endpoint, leave the API key unset.

### VS Code with Continue

Install the Continue extension in the VS Code environment that hosts this checkout, then configure
its OpenAI-compatible provider in `~/.continue/config.yaml`:

```yaml
name: LocalAI
version: 0.0.1
schema: v1
models:
  - name: Gemma4 local
    provider: openai
    model: gemma4-12b
    apiBase: http://127.0.0.1:8081/v1
    apiKey: localai
    roles:
      - chat
      - edit
      - apply
```

The LocalAI endpoint is bound to loopback, so the placeholder key is not used for authentication.
Use another model name if you install a different model in LocalAI. This URL is for a VS Code
extension host running on the NAS. Don't expose the unauthenticated API to the LAN or public
internet.

Home Assistant's built-in Ollama integration is Ollama-specific, and its built-in OpenAI integration
does not accept arbitrary compatible endpoints. The Apache-2.0 licensed LeonCode LLM Conversation
custom integration can use LocalAI. Install it, add a config entry with base URL
`http://127.0.0.1:8081/v1`, leave the API key blank, and create a conversation profile for
`gemma4-12b` using Chat Completions. Expose only the entities you want the assistant to control;
leave allowlisted service tools disabled unless needed. Immich's face-recognition and Smart Search
use Immich's separate machine-learning API, not this OpenAI-compatible chat endpoint.

## Data

Everything lives under `/data` (the Docker named volume `opencook-data`):

```
/data/opencook.db (+ -wal/-shm)   # SQLite
/data/images/                      # uploaded photos + AI crops
/data/backups/                     # *.tar.gz backups (default)
```

It survives restarts and `docker compose up --build`, but **not** deleting the volume or losing the
disk — so keep durable backups off the data volume.

## Web admin console

Open **`http://<server>:8000/admin/`** in a browser and enter the admin password. It's a built-in,
self-contained page (no extra service) for inspecting and maintaining the server, and it is the
**only** place server maintenance happens — the Android app has no admin screen.

**Setting the password on a fresh server.** Either set `OPENCOOK_ADMIN_PASSWORD` before the first
start (applied only while no password exists, so a later change in the console survives a restart),
or just open `/admin/` — with no password configured the gate offers to set one instead of asking
for one. Change it later in **Maintenance**.

The console offers:

- **Tables** — browse the SQLite tables read-only (paginated, sortable; password/PIN hashes masked).
- **Sync log** — the append-only message log shown as reconstructed entities per dataset, scoped by
  household, with an added/edited/deleted breakdown and filter. Dish crops and AI original-scan
  photos render inline.
- **Households** — rename, set/change/clear a PIN, or delete a household.
- **Maintenance** — create/download/restore backups, change the admin password, full reset.

The web hostname is protected by the Authentik forward-auth outpost. After login, the page loads
the sole household's invite code automatically; Android and the browser extension continue using
the direct household-code API on port 8000. Keep both paths on the trusted LAN/VPN.

## Backup & restore

A backup is one portable `.tar.gz` with a consistent `opencook.db` snapshot (SQLite online-backup —
safe while running) plus the whole `images/` tree. It captures the complete message log, so a
restore rolls the server back to an exact point in time.

This is separate from, and complementary to, the **on-device backup** in the app
([Backup & restore](../user/backup-and-restore.md)), which exports readable schema.org recipes to a
ZIP and can only ever add data back. Server archives are complete and unattended; phone backups are
portable and work without a server at all.

**From the web console:** create, download, upload and restore backups (admin password required).

**From the CLI:**
```bash
# Create (safe while the server runs)
PYTHONPATH=server server/.venv/bin/python server/scripts/backup.py

# Restore — STOP THE SERVER FIRST (a safety snapshot is taken before replacing anything)
PYTHONPATH=server server/.venv/bin/python server/scripts/restore.py <archive.tar.gz>
```

**Daily cron** (rotation keeps the newest `OPENCOOK_BACKUP_KEEP`):
```cron
15 3 * * * cd /path/to/openCook && PYTHONPATH=server server/.venv/bin/python server/scripts/backup.py >> server/data/backups/backup.log 2>&1
```

To survive an OS reinstall, point `OPENCOOK_BACKUP_DIR` at an external/network drive (or copy the
archives off-box). For Docker, mount a host dir / NAS at the backup path and `chown` it to uid
`10001`, or bind-mount all of `/data`.

## App updates

openCook has no in-app updater. Get the app from F-Droid (auto-updates) or from the GitHub
releases. Release steps are in [Building → Releasing](building.md#releasing).

## Security notes

- Most endpoints are **unauthenticated by design** (trusted LAN/VPN). **Never expose port 8000 to
  the internet.** Admin endpoints are password-protected; households can also set a join PIN.
- The server reaches no filesystem beyond `/data` (in Docker) and sends recipe photos only to the
  configured AI provider. The default provider is the local LocalAI service.
