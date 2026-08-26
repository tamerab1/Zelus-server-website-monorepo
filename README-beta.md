# Beta Deployment Runbook

How the public beta is hosted, what it costs, and how to stand it up or
recover it. This documents the `develop` → `zelus_dev` Docker Swarm stack
that already existed in `.github/workflows/ci-cd.yml` before beta work
started — beta reuses it rather than adding a parallel environment.

## Architecture

Single VPS, Docker Swarm (single-node). Everything colocated — no external
managed services, no cross-network database calls. Currently running on a
Hetzner CX-series box (4GB RAM) — cheap rather than literally $0 (the
original plan targeted Oracle Cloud's Always Free ARM tier, but Hetzner was
chosen instead for simpler provisioning with no capacity/verification
friction).

**4GB is tight** for game server + MariaDB + MongoDB + FastAPI + nginx +
Caddy all on one box. Current per-service memory budget (see the `deploy.
resources.limits` blocks in `docker-compose-prod.yml`):

| Service | Limit | Notes |
|---|---|---|
| game_server | 2g (heap `-Xmx1536m`) | JVM heap + metaspace/thread-stack overhead |
| db_sql | 768m | MariaDB |
| db_mongo | 512m | |
| website_api | 512m | gunicorn, 2 workers (see Dockerfile — `WEB_CONCURRENCY` env var is a no-op once `--workers` is passed explicitly, don't try to tune via env) |
| website_client | 64m | static nginx |
| reverse_proxy | 128m | Caddy |
| adminer | 128m | |

A 2-4GB swap file (see setup below) is a safety net against transient
spikes — **not** a substitute for RAM. Sustained swap use means the JVM is
under real memory pressure, which shows up to players as lag/stutter; if
`docker stats` shows the game server consistently near its limit, upsize
the VPS (Hetzner resizes are quick) and raise the heap + limit together
rather than leaning on swap long-term.

**Networks**: no custom overlay network is defined. `docker stack deploy`
on Swarm mode automatically creates one default overlay network per stack
and attaches every service to it — service-to-service DNS by service name
(`db_sql`, `db_mongo`, etc.) already works without any extra config, which
is exactly what's validated by `docker compose config` showing `networks:
default: null` per service. Segmenting into a public-tier/data-tier network
pair is a reasonable future hardening step (so a compromised website_client
container can't reach db_mongo directly) but adds complexity beyond what a
single-node beta box needs right now.

```
Internet
   │
   ├── :80/:443 ──▶ reverse_proxy (Caddy, automatic Let's Encrypt)
   │                    ├─▶ website_client (nginx, static React build)
   │                    └─▶ website_api   (FastAPI / gunicorn)
   │
   └── :43594 ────▶ game_server (Java, raw TCP passthrough — not proxied)

Internal only (no published ports, reached via Swarm overlay DNS by service name):
   db_sql   (MariaDB — schemas: `reason` [game], `zelusrsps_db` [website])
   db_mongo (MongoDB — player saves)
   adminer  (DB admin UI — loopback-only via SSH tunnel, see docker-compose-prod.yml)
```

**Why this shape, not the originally-proposed Vercel/Render/MongoDB Atlas
hybrid:**
- `player-mongo/src/main/java/player/mongo/Connection.java` builds a bare
  `mongodb://user:pass@host:port` connection string — no TLS, no
  `authSource=admin`, no SRV/replica-set support. MongoDB Atlas requires
  those. Using Atlas would need a code change first; the in-Swarm `db_mongo`
  container needs none.
- `website/zelus-website-api` connects to MariaDB (`pymysql`), not MongoDB
  or PostgreSQL — its `.env.example` still shows a stale
  `DATABASE_URL=postgresql://...` comment, but `database.py` and
  `requirements.txt` confirm Postgres was removed. Hosting the API on
  Render/Koyeb would mean either exposing MariaDB publicly (real risk) or
  running everything colocated anyway — so colocated from the start.
- `develop` already auto-deploys to a `zelus_dev` Swarm stack with every
  push, no manual gate. That's exactly the "team + early players,
  continuous deploys" behavior beta needs — a second parallel environment
  would just double the secrets/stacks to maintain for no benefit.

## What's deployed vs. what's deliberately not

| In beta | Not in beta (and why) |
|---|---|
| Game server (MariaDB `reason` + MongoDB saves) | MongoDB **Atlas** — needs a `Connection.java` code change first (see above) |
| Website frontend (React, static, Caddy-served) | **Vercel/Cloudflare Pages** — architecturally fine, but a second build pipeline outside existing CI/CD; skipped for one-less-moving-part |
| Website API (FastAPI, MariaDB `zelusrsps_db`) | **Render/Koyeb** — would need public MariaDB exposure or its own colocation anyway |
| Caddy reverse proxy, automatic HTTPS | Legacy donation "store" DB (`Donation.java`, hardcoded `DATABASE="store"`) — never provisioned in this pipeline; `donation_shop_enabled=false` for beta rather than standing up a 4th schema for a feature nobody asked to test |

## Files

| File | Purpose |
|---|---|
| `docker-compose.yml` | Base services shared by every environment (db_sql, db_mongo) |
| `docker-compose-prod.yml` | Full stack overlay: game_server, website_api, website_client, reverse_proxy, adminer, plus the two DB services' prod-specific env |
| `Caddyfile` | Domain → service routing; Caddy auto-issues Let's Encrypt certs for whatever `BETA_DOMAIN`/`BETA_API_DOMAIN` resolve to |
| `docker/db_sql/docker-entrypoint-initdb.d/init.sql` | Pre-existing — creates the `reason` schema + game tables |
| `docker/db_sql/docker-entrypoint-initdb.d/init_website.sh` | New — creates `zelusrsps_db` + a least-privilege `website_api` DB user (full access to `zelusrsps_db`, read-only `SELECT` on `reason` for hiscores). **Must be a `.sh` file, not `.sql`** — MariaDB's official entrypoint only does `${VAR}` substitution for sourced shell scripts, not raw SQL files piped to the client. |
| `docker-compose.env.example` | Template for the `DOCKER_COMPOSE_ENV` GitHub Environment secret — documents every variable the stack needs |
| `.github/workflows/ci-cd.yml` | CI/CD — builds/pushes `server`, `website-client`, `website-api` images to GHCR; `develop` push → `deploy-development` → `zelus_dev` stack |
| `server.properties` | Game server config — **gitignored, host-only**, never committed |

## One-time setup

### 1. VPS + Docker Swarm
Provision a free-tier ARM VPS (Oracle Cloud Always Free 24GB is the
reference target). Install Docker, init a single-node Swarm, generate a
deploy SSH keypair.

### 2. GitHub Environment secrets (`development`)
Settings → Environments → `development`:
- `DEPLOY_HOST` — VPS public IP
- `DEPLOY_HOST_USER` — SSH user (e.g. `root`)
- `DEPLOY_SSH_KEY` — private half of the deploy keypair
- `DOCKER_COMPOSE_ENV` — full contents matching `docker-compose.env.example`, real values

### 3. DNS
Point both at the VPS's public IP **before** the stack first deploys —
Caddy's ACME HTTP-01 challenge fails (and backs off) if the domain doesn't
resolve yet:
```
beta.yourdomain.com       A   <VPS_IP>
api.beta.yourdomain.com   A   <VPS_IP>
```

### 4. Repo variables (not secrets — public-safe values)
Settings → Secrets and variables → Actions → **Variables** tab:
- `VITE_API_URL` = `https://api.beta.yourdomain.com`
- `VITE_TURNSTILE_SITE_KEY` = your Turnstile site key, or blank

These get baked into the `website-client` image at **build time** (Vite
env vars aren't runtime-configurable after `npm run build`) — see the
`build-args` step in `ci-cd.yml`'s `release-dev` job.

### 5. RSA keypair (login encryption)
Never reuse the example/dev keypair for a real deployment:
```bash
./gradlew :kronos-api:classes
java -cp kronos-api/build/classes/java/main io.ruin.api.utils.RSAKeyGen
```
Copy the **PRIVATE** exponent/modulus into `server.properties`.

### 6. `server.properties` and `Caddyfile` on the VPS
Both are bind-mounted from **absolute paths** (`/opt/zelus/server.properties`,
`/opt/zelus/Caddyfile`) in `docker-compose-prod.yml` — deliberately not
`./server.properties`. `docker stack deploy` resolves relative bind sources
on whichever machine issues the command, and `deploy-development` runs it
with `DOCKER_HOST=ssh://...` from a GitHub Actions runner, not the VPS
itself. A relative path would resolve to a runner-local path that doesn't
exist on the VPS; Docker's fallback for a missing bind source is to
silently mount an empty directory, booting the game server with no real
config and no error thrown. Fixed by pinning both to a path that means the
same thing regardless of which machine issued the deploy.

`server.properties` is gitignored — created directly on the VPS, never
committed:
```bash
mkdir -p /opt/zelus
cp server.properties.example /opt/zelus/server.properties
# (Caddyfile is committed to the repo — copy it from a checkout, or scp it
#  from your own machine: scp Caddyfile root@<VPS_IP>:/opt/zelus/Caddyfile)
```

**Also required — the OSRS cache itself.** `cache_path`/`data_path` in
`server.properties.example` point at `../data zelus/data`, a folder that
lives *outside this git repo entirely* (never committed, never built into
the `server` image — CI's `:kronos-boot:build_cache` step is deliberately
excluded from CI builds). `docker-compose-prod.yml` mounts
`/opt/zelus/data:/data` into `game_server`, so on the VPS:
```bash
mkdir -p /opt/zelus/data
```
then, from your local machine, transfer the actual cache/data contents —
this is a large tree (hundreds of thousands of small TOML files plus the
packed binary cache), so `rsync` over SSH rather than `scp`/`tar`:
```bash
rsync -avz --progress "path/to/data zelus/data/" root@<VPS_IP>:/opt/zelus/data/
```
This can take a long time on the first run depending on your upload speed
and file count — it's resumable, so it's safe to re-run if interrupted.
Then in `/opt/zelus/server.properties` on the VPS, point both paths at the
container-internal mount instead of the repo-relative default:
```
cache_path=/data/cache
data_path=/data
```

**This same mount is also where player saves live** — `ServerWrapper.java`
resolves `data_path` into `dataFolder`, and `CentralSaves.java`/
`IPBans.java`/`PresetManager.java`/etc. all write under
`${dataFolder}/runtime/...`. An earlier version of `docker-compose-prod.yml`
had a separate `data_server:/app/data/runtime/` volume that didn't
correspond to this at all (wrong container path) — removed, since nothing
ever wrote there; `/opt/zelus/data` covers both cache and runtime data now.

**Carrying over a specific local test account to beta**: local dev saves
live under `runtime/saves/players/<stage>/<type>/<username>.json` where
`<stage>` is `World.stage.name().toLowerCase()` — i.e. whatever
`world_stage` resolves to, **lowercased**. A local `DEV`-stage save (folder
`players/dev/...`) needs its stage folder renamed to `players/beta/...`
when copied to a box running `world_stage=BETA`, or the beta server won't
find it. A player's non-core state (friend list, collection log, chat
filter settings, group-ironman bank, etc.) is scattered across
`runtime/saves/attributes/<module>/<username>.json` — one file per module,
no stage/type component, copied as-is. For excluding everything else from
a fresh transfer: `cache_backup_*` (stale map-editing backups), `groups/`
(local group-ironman team data), the top-level `saves/` folder (per-player
log files, unrelated to `runtime/saves/`), `banned_hwids.dat`, and
`referral_claimed_ips.json` are all local dev state, not game content.
Key fields to set for beta:
- `world_address=beta.yourdomain.com`, `world_stage=BETA`
- `database_host` / `database_user` / `database_password` and
  `game_db_*` → MariaDB creds (this legacy path connects as `root` in
  practice — no dedicated grants exist for it in `init.sql`)
- `mongo_host=db_mongo`, `mongo_username`/`mongo_password` → Mongo creds
- `RSA_EXPONENT` / `RSA_MODULUS` → from step 5
- `donation_shop_enabled=false`, `api_enabled=false` (legacy store DB not provisioned)
- `discord_hook_*` → optional, a test webhook if you want beta activity pinged

## Deploying

```bash
git push origin develop
```
CI runs `release-dev` (builds/tags `:latest-dev` for all three images) then
`deploy-development` (SSHes to `DEPLOY_HOST`, `docker stack deploy` as
`zelus_dev`). First boot is slower — Caddy issuing certs, MariaDB running
both init scripts.

## Verifying

```bash
curl -I https://beta.yourdomain.com          # frontend, expect 200
curl -I https://api.beta.yourdomain.com/     # backend, expect 200
```
Then connect a game client to `beta.yourdomain.com:43594`.

## Known gaps / follow-ups

- `docker-compose-prod.yml` sets `MARIADB_DATABASE: zelus_prod`, but
  `init.sql` creates a schema called `reason` — `zelus_prod` ends up an
  empty, unused schema. Harmless, just confusing; worth cleaning up
  eventually.
- No `GRANT` statements exist for the game server's own DB access — it
  appears to connect as `root` today. `init_website.sh` gives the new
  `website_api` user least-privilege access instead; consider doing the
  same for the game server's connection at some point.
- MongoDB Atlas support requires updating `Connection.java` to build a
  proper Atlas connection string (TLS, `authSource=admin`, SRV or explicit
  replica-set hosts) — not done, since colocated Mongo needs no code change.
