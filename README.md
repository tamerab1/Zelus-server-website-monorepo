# Zelus — Developer Setup

Onboarding guide for the Zelus OSRS private server monorepo: cloning, opening in IntelliJ,
running the server/client/website locally, and rebuilding the cache. If you're a new
co-owner/dev, read this top to bottom once — it covers everything needed to get from a
fresh clone to a logged-in local client.

## 1. Project layout

This repo (`server zelus/`) is one of **three sibling folders** on disk, only one of which
is inside git:

```
osrs server/
├── server zelus/        ← THIS REPO (git monorepo: server + website)
│   └── website/
│       ├── zelus-website-api/       (Python/FastAPI backend)
│       └── zelus-website-client/    (React/Vite frontend)
├── client zelus/client/  ← separate git repo (RuneLite-based client), NOT a submodule
└── data zelus/           ← NOT in git at all — cache, player saves, all runtime data
```

- **`client zelus/client/`** is its own independent git checkout
  (`github.com/tamerab1/zelus-clientv1.0.1`). It is not referenced by this repo in any
  way (no submodule, no build dependency) — you clone it separately as a true sibling
  directory.
- **`data zelus/`** is never committed anywhere — it's a large tree (the packed OSRS
  cache, thousands of TOML cache-source files, player saves, runtime state). There is
  **no automated way to fetch it**. You need an existing owner to hand you a copy
  out-of-band (rsync, zip transfer, shared drive — whatever's convenient). Get this
  early since almost nothing runs without it.
- Everything must sit at the same directory level as shown above — `server.properties`
  resolves `data zelus/` via a relative path (`../data zelus/data`), so if your folder
  names or nesting differ, fix the paths in `server.properties` rather than
  restructuring.

## 2. Prerequisites

| Tool | Version | Used for |
|---|---|---|
| JDK | **21** (Temurin recommended) | Server (`server zelus/`) — built with `--enable-preview`, matches CI |
| JDK | **11** | Client only (`client zelus/client/`) — separate JDK, don't reuse 21 for it |
| Docker Desktop | any recent | Local MariaDB + MongoDB (server never runs in Docker locally, only its DBs) |
| Node.js | 22 | Website frontend (`zelus-website-client`) |
| Python | 3.12 | Website backend (`zelus-website-api`) |
| IntelliJ IDEA | any recent | Server + client development |
| Git | any recent | |

Gradle itself doesn't need installing — both the server and client repos ship their own
wrapper (`./gradlew`), which will download the right Gradle version on first run
(server: Gradle 9.0.0; client: Gradle 8.8 — different, that's expected, they're separate
projects).

## 3. Clone everything

```bash
cd "osrs server"
git clone https://github.com/tamerab1/Zelus-server-website-monorepo.git "server zelus"
git clone https://github.com/tamerab1/zelus-clientv1.0.1.git "client zelus/client"
```

Then get a copy of `data zelus/` from an existing owner and place it as the third
sibling folder (see §1). Check out whichever branch you're meant to be working on
(`git checkout peaks` etc.) in the server repo.

## 4. Open in IntelliJ

**File → Open** on the `server zelus/` folder — it's a standard Gradle Kotlin-DSL
multi-module project and will auto-import.

A few things that aren't obvious from a plain import:

- **No `.idea/` is committed** (gitignored) — every developer does a clean import, there's
  nothing to inherit.
- **Install the Lombok plugin** in IntelliJ (Settings → Plugins) and make sure annotation
  processing is enabled (Settings → Build → Compiler → Annotation Processors). Every
  module uses Lombok; without this you'll see false "unresolved method" errors.
- **`--enable-preview` is required.** This is a preview-features build — the Gradle
  `run`/`run_hotswap` tasks already pass this flag, but if you create your own IntelliJ
  run configuration by hand instead of using the Gradle task, you must add it yourself
  or the server will fail to start with a class-version/preview-feature error.
- Recommended IntelliJ **Application** run configuration (matches what the Gradle `run`
  task does — either works, this one gives you a normal IntelliJ debug/stop toolbar):
  - Main class: `boot.Boot`
  - Module: `kronos-boot.main`
  - Working directory: the repo root (`server zelus/`) — required, `server.properties`
    and the cache/data paths resolve relative to this
  - VM options:
    ```
    -XX:-OmitStackTraceInFastThrow -Xms1g -XX:AutoBoxCacheMax=65535 --enable-preview --add-opens=java.base/jdk.internal.vm=ALL-UNNAMED --add-opens=java.base/jdk.internal.loader=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --enable-native-access=ALL-UNNAMED -XX:CompileThreshold=1500 -Dslf4j.internal.verbosity=warn -XX:+UseCodeCacheFlushing -XX:ReservedCodeCacheSize=128M
    ```

## 5. Local databases (Docker)

The server itself always runs directly (via Gradle/IntelliJ, never in a container
locally) — Docker is only used for its two databases.

1. Copy the env template and fill in local credentials (any values are fine locally,
   these aren't shared/production secrets):
   ```bash
   cp docker-compose.env.example docker-compose.env
   ```
   At minimum set `DB_SQL_ROOT_PASSWORD`, `DB_SQL_USER`, `DB_SQL_USER_PASSWORD`, and set
   `IMAGE_REGISTRY=kronos` / `IMAGE_TAG=latest` (matches the locally-built image tag the
   next step produces — the real registry values are only for production).
2. Start the databases:
   ```powershell
   .\.dev\deploy_local.ps1
   ```
   (bash equivalent: `.dev/deploy_local.sh`). This builds a local `db_sql` image and
   brings up:

   | Service | Port | Local login |
   |---|---|---|
   | MariaDB (`db_sql`) | `3306` | whatever you set in `docker-compose.env` |
   | Adminer (DB admin UI) | `8080` | same MariaDB creds |
   | MongoDB (`db_mongo`) | `27017` | **hardcoded** `root` / `password` (local-only default, ignores `docker-compose.env`'s Mongo vars) |
   | mongo-express (Mongo admin UI) | `8081` | no auth |

   First boot auto-creates the `reason` (game) schema and a `zelusrsps_db` +
   `website_api` user for the website API.

## 6. `server.properties`

```bash
cp server.properties.example server.properties
```

Then edit:

- `cache_path` / `data_path` — leave as-is (`../data zelus/data/...`) if your sibling
  folders match §1's layout.
- `database_host=localhost`, `database_user` / `database_password` — your local MariaDB
  creds from `docker-compose.env`.
- `game_db_host=localhost`, `game_db_username` / `game_db_password` — same MariaDB
  instance; in practice the game server connects as `root` (no dedicated DB grant exists
  even in production), so pointing these at your root creds is the simplest option.
- `mongo_host=127.0.0.1`, `mongo_username=root`, `mongo_password=password` — **must**
  match the hardcoded local Mongo creds from §5's table, not whatever's in
  `docker-compose.env`.
- `api_password`, `login_master_password` — any placeholder value, local-only.
- `RSA_EXPONENT` / `RSA_MODULUS` — **generate your own, never reuse the example values**:
  ```bash
  ./gradlew :kronos-api:classes
  java -cp kronos-api/build/classes/java/main io.ruin.api.utils.RSAKeyGen
  ```
- Leave the `discord_hook_*` and `sentry_*` fields blank — fine locally.
- `world_port=43594` and `world_address=127.0.0.1` are already correct for local dev.

## 7. Run the server

```powershell
.\gradlew :kronos-boot:run
```

Hot-swap variant (requires a JRebel license/agent — see §11 below):
```powershell
.\gradlew :kronos-boot:run_hotswap
```

Or use the IntelliJ run configuration from §4. Either way:

- Entry point: `boot.Boot` → `Server.startCore()`.
- Game port: **43594**. World-list/API port: **9292** (serves `GET /worlds.ws`, which the
  client's `jav_config.ws` depends on).
- **The server goes quiet in the log after boot finishes** — its main thread exits
  normally once workers start; the world runs on background threads that don't log at
  idle. Several minutes of silence is normal, not a hang. Confirm it's actually up via
  `Get-NetTCPConnection -LocalPort 43594` (should show `State=Listen`).

## 8. Cache

The packed binary OSRS cache lives at `data zelus/data/cache/`, generated from a large
TOML source tree at `data zelus/data/cache/toml/`. You won't normally need to rebuild
it — the cache you got from §1's handoff already has it packed. You only need this if
you're actually editing cache content (items, NPCs, objects, etc.) via the TOML source.

**⚠️ Don't run the raw Gradle task directly.** `:kronos-boot:build_cache` shells out to
`.dev/tool-cache-packer.exe`, which rebuilds the *entire* cache from TOML on every run —
discarding any direct binary patches — and has a known reproducible race condition that
can non-deterministically corrupt sprite archives on a given run.

**Use the safe wrapper instead:**
```powershell
.\.dev\safe_build_cache.ps1
```
This backs up the current cache, runs the real packer, then diffs the result against a
known-good snapshot (`data zelus/data/cache_golden`) and restores anything the packer
corrupted. **Stop the running dev server first** — it holds cache files open — and
restart it after the script finishes.

## 9. Client

The client is a separate repo/checkout at `client zelus/client/` (RuneLite fork). It has
its **own JDK requirement — 11, not 21** — don't reuse the server's toolchain.

Point it at your local server before building — edit
`runelite-client/src/main/resources/jav_config.ws`:
```
codebase=http://127.0.0.1/
param=17=http://127.0.0.1:9292/worlds.ws
```
(Both currently point at production — change both or you'll connect to the live server
instead of your local one.)

Build the client jar:
```powershell
.\gradlew :client:shadowJar
```

Run it (must be the `-all.jar` fat jar — the plain jar is missing bundled deps and fails
immediately):
```powershell
cd runelite-client\build\libs
java --add-opens=java.base/java.lang=ALL-UNNAMED -jar "client-<version>-all.jar"
```

**Never use `runelite-client-launcher` for local testing** — it downloads a prebuilt jar
from GitLab Packages and ignores your local build entirely.

**Order matters: always start the server first**, wait for it to finish booting (see
§7's note on the log going quiet — that's your signal it's ready), *then* launch the
client.

## 10. Website

### Backend (`website/zelus-website-api/`)

```bash
cd website/zelus-website-api
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

Set these env vars for local dev (defaults exist for most, but explicitly setting them
avoids surprises):

| Var | Local value |
|---|---|
| `DB_HOST` / `DB_PORT` | `localhost` / `3306` |
| `DB_USER` / `DB_PASS` / `DB_NAME` | your `zelusrsps_db` creds (auto-created by `init_website.sh` in §5) |
| `GAME_DB_HOST` / `GAME_DB_NAME` | `localhost` / `reason` |
| `GAME_DB_USERNAME` / `GAME_DB_PASSWORD` | your MariaDB root creds (matches `server.properties`) |
| `GAME_API_PASSWORD` | must exactly match `server.properties`'s `api_password` |
| `CHARACTERS_DIR` | point explicitly at `<path to>/data zelus/data/runtime/saves/players` |
| `SITE_URL` | `http://localhost:5173` |
| `CORS_ORIGINS` | `http://localhost:5173` |

Payment provider secrets (`STRIPE_*`, `PAYPAL_*`, `TEBEX_SECRET`, `NOWPAYMENTS_*`,
`VOTE_CALLBACK_SECRET`) can all stay blank locally — each feature just disables/503s
rather than needing fake credentials.

### Frontend (`website/zelus-website-client/`)

```bash
cd website/zelus-website-client
npm install
npm run dev
```
Serves at `http://localhost:5173`. Create a `.env` there with:
```
VITE_API_URL=http://localhost:8000
```
(`VITE_TURNSTILE_SITE_KEY` can stay blank — disables the CAPTCHA widget locally.)

> ⚠️ This folder has its own `README.md` that is **stale and inaccurate** — it describes
> a standalone repo, PostgreSQL, and a plain nginx/Certbot deployment, none of which
> match the actual current setup (this monorepo, MariaDB, Docker Swarm + Caddy). Ignore
> it; this file and `README-beta.md` are correct.

## 11. JRebel hot-swap (optional)

Only needed if you want to use `:kronos-boot:run_hotswap` for class-reload-on-save
during development. Requires a JRebel license. In
`<user home>/.jrebel/jrebel.properties`, add:
```
idea.outpath=\\out\\production\\classes
gradle.resources=\\build\\resources\\main
gradle.java.main=\\build\\classes\\java\\main
gradle.kotlin.main=\\build\\classes\\kotlin\\main

reason.api=C\:\\Users\\..\\kronos-api
reason.server=C\:\\Users\\..\\kronos-server
reason.common=C\:\\Users\\..\\common
```
- Replace `..` with the correct path for your machine.
- `\\` **must** be used as the path separator — not `/` or a single `\`.

## 12. Start-up order, summary

1. `.\.dev\deploy_local.ps1` — databases
2. `.\gradlew :kronos-boot:run` — game server, wait for it to go quiet (booted)
3. `uvicorn main:app --reload --port 8000` — website API (optional, only if working on the site)
4. `npm run dev` — website frontend (optional)
5. Build + launch the client, pointed at `127.0.0.1` (§9)

## 13. Deployment

Not covered here — this file is local dev only. For how production/beta is actually
hosted (Docker Swarm, Caddy, CI/CD tag-based deploy flow), see `README-beta.md`. It's
written for the beta VPS (now retired), but its explanation of the DB architecture,
cache/data conventions, and env vars is still accurate for production today.
