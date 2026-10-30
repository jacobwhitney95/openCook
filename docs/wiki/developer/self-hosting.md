# Self-hosting

The server is optional (only needed for photo scanning + sync) and designed for a trusted home
network. The recommended deployment is Docker; a plain Python run also works.

## Prerequisites

- A Linux machine on the same LAN/Wi-Fi as the phones.
- **[Ollama](https://ollama.com) running on the host** with the vision model pulled — it needs the
  GPU, so it stays on the host even when the server runs in a container:
  ```bash
  ollama pull qwen2.5vl:7b
  ```
  That is the default. This deployment uses LocalAI instead, at `http://127.0.0.1:8081/v1` with the
  local `gemma4-12b` model; its Compose setup, GPU backend, and model files are managed separately
  from this server. Other models and providers are covered in [Vision model](#vision-model).
  Hosted providers may receive uploaded recipe photos and may charge for inference.

## Docker (recommended)

```bash
cd server
docker compose up -d --build
docker compose ps                    # STATUS should become "healthy"
curl http://localhost:8000/health    # {"status":"ok"}
docker compose logs -f               # "Job worker started", mDNS advertise line
```

The container runs **only the server** (FastAPI + SQLite + the job worker + mDNS); it calls the
vision model over HTTP — by default the host's Ollama at `localhost:11434`, or LocalAI at
`http://127.0.0.1:8081/v1` in this deployment. `network_mode: host` is used so mDNS reaches the LAN
and the container can reach either on localhost. `restart: unless-stopped` brings it back after a
reboot.

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
| `OPENCOOK_VISION_BASE_URL` | `http://localhost:11434/v1` | OpenAI-compatible vision endpoint |
| `OPENCOOK_VISION_MODEL` | `qwen2.5vl:7b` | Vision model |
| `OPENCOOK_VISION_API_KEY` | — | Only for endpoints that require a key |
| `OPENCOOK_VISION_PARAMS` | see [Vision model](#vision-model) | Extra request fields (JSON) |
| `OPENCOOK_WORKER_POLL_INTERVAL` | `2.0` | Job worker poll interval (s) |
| `OPENCOOK_BACKUP_DIR` | `<data_dir>/backups` | Backup archive location |
| `OPENCOOK_BACKUP_KEEP` | `14` | Backups to retain (rotation) |
| `OPENCOOK_ADMIN_PASSWORD` | — | Sets the admin password on first start (see below) |

The client uses `POST /chat/completions` and sends the resized photo as a data URL. Choose aprovider and model that support vision input. API keys belong in the ignored `server/.env`, never
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
### Vision model

Photo scanning talks to the model over the **OpenAI chat-completions API**, which nearly every model
server speaks. The default is the host's Ollama (`http://localhost:11434/v1`); to use something else,
change the base URL and model:

| Where the model runs | `OPENCOOK_VISION_BASE_URL` |
|---|---|
| Ollama on this machine (default) | `http://localhost:11434/v1` |
| Ollama on another machine, e.g. a desktop with a GPU | `http://192.168.1.20:11434/v1` |
| LocalAI on this machine (this deployment, `gemma4-12b`) | `http://127.0.0.1:8081/v1` |
| llama.cpp (`llama-server`), LM Studio, vLLM | that server's address, ending in `/v1` |
| A hosted service | its OpenAI-compatible URL, plus `OPENCOOK_VISION_API_KEY` |

With an API key set, the server refuses to start unless the base URL is `https://` (or loopback,
which never leaves the machine) — the key would otherwise cross the network unencrypted. Keep the
key in `server/.env` readable only by you (`chmod 600`), never in git.

`OPENCOOK_VISION_PARAMS` adds fields to every request, as a JSON object. The default is tuned for
Ollama — near-exact reading and JSON output:

```dotenv
OPENCOOK_VISION_PARAMS={"temperature": 0.0001, "response_format": {"type": "json_object"}}
```

Setting it replaces the whole default, so repeat what you want to keep. Not every endpoint accepts
every field: LM Studio has no `json_object` mode, OpenAI's reasoning models reject a `temperature`,
and some servers take extras of their own (e.g. llama.cpp's `repeat_penalty`). If scans fail with
the endpoint rejecting the request, the server log names the field — drop it, or set `{}` to send
none. A cap such as `"max_tokens": 4000` stops a scan that gets stuck repeating itself after a
minute instead of running for many.

Only `qwen2.5vl:7b` has been validated against real cookbook pages; other models may read worse —
the console's **Prompts** view lets you adapt the instructions to yours. If
the endpoint is outside your home network, every scanned photo is sent there — the server logs a
warning at startup when that is the case.

Upgrading from an older server: `OPENCOOK_OLLAMA_BASE_URL` / `OPENCOOK_OLLAMA_MODEL` are no longer
read. If you had set them, use `OPENCOOK_VISION_BASE_URL` (add `/v1` to the old address) and
`OPENCOOK_VISION_MODEL` instead.

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
- **Prompts** — per recipe language, replace the instructions the vision model gets for reading the
  recipe text and for finding dish photos (e.g. for another model, or for your own books). Every
  language is listed with the households that use it — the *Recipe language* they have set, or for a
  household following its phones' language, the language of its recent scans — the ones in use
  first. Before any household exists, English (the default) counts as in use. The output
  format is always appended by the server and shown read-only, so an edit can't break scanning.
  Applies from the next scan; *Reset to default* (or saving the default text unchanged) returns to the
  built-in instructions, which keeps future improvements to them. Stored in the database, so it is
  part of server backups and survives updates and a full reset.
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
- The server reaches no filesystem beyond `/data` (in Docker) and contacts no third party except the
  vision endpoint — by default the host's Ollama, in this deployment LocalAI on loopback. If you
  point it outside your home network, scanned photos go there; the server logs a warning at startup
  when that is the case.
