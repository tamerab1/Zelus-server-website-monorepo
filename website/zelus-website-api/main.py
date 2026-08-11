import hashlib
import json
import logging
import math
import os
import secrets
import time

from dotenv import load_dotenv

load_dotenv()
from datetime import datetime, timedelta, timezone
from pathlib import Path

import bcrypt
import stripe as _stripe
from fastapi import Depends, FastAPI, HTTPException, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from slowapi.util import get_remote_address
from sqlalchemy.exc import OperationalError
from sqlalchemy.orm import Session

import models
from database import engine, get_db
from game_database import (
    HISCORE_SORT_COLUMNS,
    HiscoreUser,
    get_game_db,
)
from routers import checkout as checkout_router_module
from routers import webhooks as webhooks_router_module

# ── Logging ───────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)-8s %(name)s — %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
log = logging.getLogger("zelus.main")

# ── Environment config ────────────────────────────────────────────────────────
_GAME_EVENTS_SECRET  = os.getenv("GAME_EVENTS_SECRET", "")   # shared secret for /events/push
_GAME_API_PASSWORD   = os.getenv("GAME_API_PASSWORD", "")    # World.apiPassword from server properties
_VOTE_CALLBACK_SECRET = os.getenv("VOTE_CALLBACK_SECRET", "")  # shared secret for /api/vote/callback/{site}
_SITE_URL           = os.getenv("SITE_URL", "http://localhost:5173")
_CORS_ORIGINS_RAW   = os.getenv(
    "CORS_ORIGINS",
    "http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174"
)
_CORS_ORIGINS = [o.strip() for o in _CORS_ORIGINS_RAW.split(",") if o.strip()]

# ── Character file config ─────────────────────────────────────────────────────
# Path to the game server's JSON save files — used as a fallback data source
# when the MariaDB is unreachable and for username existence checks.
_CHARACTERS_DIR = Path(os.getenv("CHARACTERS_DIR") or "") or (
    Path(__file__).parent.parent / "server reasonps" / "data" / "runtime" / "saves" / "players"
)

_SKILL_ORDER = [
    'attack_xp', 'defence_xp', 'strength_xp', 'hitpoints_xp', 'ranged_xp',
    'prayer_xp', 'magic_xp', 'cooking_xp', 'woodcutting_xp', 'fletching_xp',
    'fishing_xp', 'firemaking_xp', 'crafting_xp', 'smithing_xp', 'mining_xp',
    'herblore_xp', 'agility_xp', 'thieving_xp', 'slayer_xp', 'farming_xp',
    'runecrafting_xp', 'hunter_xp', 'construction_xp',
]

# ── Game username validator ───────────────────────────────────────────────────
def _game_username_exists(username: str) -> bool:
    """
    Returns True if the given username corresponds to a known game character.
    Checks (in order):
      1. The game MariaDB hs_users table (fastest, authoritative for active players)
      2. Player JSON save files (catches characters not yet posted to hiscores)
    """
    norm = username.strip().lower()
    # 1 — MariaDB hs_users
    try:
        gdb = next(get_game_db())
        from sqlalchemy import func as sa_func
        exists = gdb.query(HiscoreUser).filter(
            sa_func.lower(HiscoreUser.username) == norm
        ).first()
        if exists:
            return True
    except Exception:
        pass
    # 2 — JSON save files fallback
    if _CHARACTERS_DIR.exists():
        for path in _CHARACTERS_DIR.rglob("*.json"):
            try:
                data = json.loads(path.read_text(encoding="utf-8"))
                info = data.get("playerInformation", {})
                dn   = (info.get("displayname") or info.get("username") or path.stem or "").lower()
                if dn == norm:
                    return True
            except Exception:
                continue
    return False

# ── XP → Level conversion ─────────────────────────────────────────────────────
def _xp_to_level(xp: float) -> int:
    if xp <= 0:
        return 1
    total = 0
    for lvl in range(1, 99):
        total += math.floor(lvl + 300 * (2 ** (lvl / 7)))
        if math.floor(total / 4) > xp:
            return lvl
    return 99

# ── Online player counter ─────────────────────────────────────────────────────
def _count_online_players() -> int | None:
    """
    Tries the game MariaDB `online_characters` table first, then falls back to
    counting active JSON sessions in the save files.
    """
    # 1 — MariaDB online_characters table
    try:
        gdb = next(get_game_db())
        from sqlalchemy import text as sa_text
        result = gdb.execute(sa_text("SELECT COUNT(*) FROM online_characters")).scalar()
        if result is not None:
            return int(result)
    except Exception:
        pass
    # 2 — JSON save file sessions fallback
    if not _CHARACTERS_DIR.exists():
        return None
    count = 0
    for path in _CHARACTERS_DIR.rglob("*.json"):
        try:
            data     = json.loads(path.read_text(encoding='utf-8'))
            sessions = data.get('loyaltyManager', {}).get('sessions', [])
            if sessions:
                last = sessions[-1]
                if 'login' in last and 'logout' not in last:
                    count += 1
        except Exception:
            continue
    return count

# ── Character file reader (fallback hiscores) ─────────────────────────────────
def _read_character_files(limit: int = 200) -> list[dict] | None:
    if not _CHARACTERS_DIR.exists():
        return None
    rows = []
    for path in _CHARACTERS_DIR.rglob("*.json"):
        try:
            data    = json.loads(path.read_text(encoding='utf-8'))
            info    = data.get('playerInformation', {})
            skills  = data.get('skills', {})
            xp_arr  = skills.get('experience', [])
            attrs   = data.get('attributes', {})
            if not xp_arr:
                continue
            username    = info.get('displayname') or info.get('username') or path.stem
            skill_xp    = {_SKILL_ORDER[i]: int(xp_arr[i]) for i in range(min(len(xp_arr), 23))}
            total_xp    = sum(skill_xp.values())
            total_level = sum(_xp_to_level(v) for v in skill_xp.values())
            kills       = int(attrs.get('pvp-kills', 0))
            deaths      = int(attrs.get('pvp-deaths', 0))
            killstreak  = int(attrs.get('current-killstreak', 0))
            kdr         = round(kills / deaths, 2) if deaths > 0 else float(kills)
            rows.append({
                "username":         username,
                "total_level":      total_level,
                "total_experience": total_xp,
                "kills":            kills,
                "deaths":           deaths,
                "kdr":              kdr,
                "killstreak":       killstreak,
                **skill_xp,
            })
        except Exception:
            continue
    return rows

# ── Hiscore row builder ───────────────────────────────────────────────────────
def _build_hiscore_row(stat: HiscoreUser) -> dict:
    """
    Converts an HiscoreUser ORM object (read from hs_users with camelCase DB
    columns) into the snake_case JSON shape the React frontend expects.
    PvP stats (kills/deaths/kdr/killstreak) are not stored in hs_users; they
    default to 0 here and are enriched by the character-file fallback when
    that path is taken instead.
    """
    return {
        "username":         stat.username         or "",
        "total_level":      stat.total_level      or 0,
        "total_experience": stat.total_experience or 0,
        "attack_xp":        stat.attack_xp        or 0,
        "defence_xp":       stat.defence_xp       or 0,
        "strength_xp":      stat.strength_xp      or 0,
        "hitpoints_xp":     stat.hitpoints_xp     or 0,
        "ranged_xp":        stat.ranged_xp        or 0,
        "prayer_xp":        stat.prayer_xp        or 0,
        "magic_xp":         stat.magic_xp         or 0,
        "cooking_xp":       stat.cooking_xp       or 0,
        "woodcutting_xp":   stat.woodcutting_xp   or 0,
        "fletching_xp":     stat.fletching_xp     or 0,
        "fishing_xp":       stat.fishing_xp       or 0,
        "firemaking_xp":    stat.firemaking_xp    or 0,
        "crafting_xp":      stat.crafting_xp      or 0,
        "smithing_xp":      stat.smithing_xp      or 0,
        "mining_xp":        stat.mining_xp        or 0,
        "herblore_xp":      stat.herblore_xp      or 0,
        "agility_xp":       stat.agility_xp       or 0,
        "thieving_xp":      stat.thieving_xp      or 0,
        "slayer_xp":        stat.slayer_xp        or 0,
        "farming_xp":       stat.farming_xp       or 0,
        "runecrafting_xp":  stat.runecrafting_xp  or 0,
        "hunter_xp":        stat.hunter_xp        or 0,
        "construction_xp":  stat.construction_xp  or 0,
        # PvP stats not in hs_users — placeholder so the frontend shape is complete
        "kills":      0,
        "deaths":     0,
        "kdr":        0.0,
        "killstreak": 0,
    }

# ── DB migrations (idempotent — run on every startup) ─────────────────────────
models.Base.metadata.create_all(bind=engine)

_SKILL_COLUMNS = [
    "cooking_xp", "woodcutting_xp", "fletching_xp", "fishing_xp",
    "firemaking_xp", "crafting_xp", "smithing_xp", "mining_xp",
    "herblore_xp", "agility_xp", "thieving_xp", "slayer_xp",
    "farming_xp", "runecrafting_xp", "hunter_xp", "construction_xp",
]
with engine.connect() as _conn:
    # Ensure all skill columns exist on the local fallback table
    for _col in _SKILL_COLUMNS:
        try:
            _conn.execute(
                __import__("sqlalchemy").text(
                    f"ALTER TABLE user_skill_stats ADD COLUMN IF NOT EXISTS {_col} INTEGER DEFAULT 0"
                )
            )
        except Exception:
            pass

    # New columns added to users / votes tables
    _new_cols = [
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS is_verified BOOLEAN DEFAULT FALSE",
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_token VARCHAR(255)",
        "ALTER TABLE votes ADD COLUMN IF NOT EXISTS ip_address VARCHAR(45)",
        "ALTER TABLE votes ADD COLUMN IF NOT EXISTS game_username VARCHAR(12)",
    ]
    for _stmt in _new_cols:
        try:
            _conn.execute(__import__("sqlalchemy").text(_stmt))
        except Exception:
            pass

    # Migrate old privilege values to the NR 288 PlayerGroup equivalents.
    # These UPDATE statements are safe to re-run (no-op if already migrated).
    _privilege_migrations = [
        "UPDATE users SET privilege = 'REGISTERED' WHERE privilege = 'PLAYER'",
        "UPDATE users SET privilege = 'REGISTERED' WHERE privilege = 'MEMBER'",
        "UPDATE users SET privilege = 'ADMINISTRATOR' WHERE privilege = 'SENIOR_MODERATOR'",
        "UPDATE users SET privilege = 'OWNER' WHERE privilege = 'TRUE_DEVELOPER'",
        "UPDATE users SET privilege = 'OWNER' WHERE privilege = 'HIDDEN_ADMINISTRATOR'",
    ]
    for _stmt in _privilege_migrations:
        try:
            _conn.execute(__import__("sqlalchemy").text(_stmt))
        except Exception:
            pass

    # Migrate old game_mode values (NORMAL → STANDARD)
    try:
        _conn.execute(__import__("sqlalchemy").text(
            "UPDATE users SET game_mode = 'STANDARD' WHERE game_mode = 'NORMAL'"
        ))
    except Exception:
        pass

    # pending_claims unique constraint (idempotent)
    try:
        _conn.execute(__import__("sqlalchemy").text(
            "ALTER TABLE pending_claims ADD CONSTRAINT uq_pending_claims_transaction_id "
            "UNIQUE (transaction_id)"
        ))
    except Exception:
        pass

    # votes.user_id is no longer required — voting is keyed by game_username
    # now that website accounts are gone. Relax the legacy NOT NULL constraint.
    try:
        _conn.execute(__import__("sqlalchemy").text(
            "ALTER TABLE votes MODIFY COLUMN user_id INTEGER NULL"
        ))
    except Exception:
        pass

    _conn.commit()

# ── App setup ─────────────────────────────────────────────────────────────────
limiter = Limiter(key_func=get_remote_address)
app = FastAPI(title="Zelus Website API", description="API for Zelus RSPS (NR 288)")
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

app.add_middleware(
    CORSMiddleware,
    allow_origins=_CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Content-Type", "Authorization"],
)

app.include_router(checkout_router_module.router)
app.include_router(webhooks_router_module.router)

@app.exception_handler(_stripe.error.StripeError)
async def stripe_error_handler(request: Request, exc: _stripe.error.StripeError):
    log.error("Unhandled Stripe error: %s", exc)
    return JSONResponse(
        status_code=502,
        content={"detail": exc.user_message or "A payment processing error occurred."},
    )

@app.exception_handler(Exception)
async def generic_error_handler(request: Request, exc: Exception):
    if isinstance(exc, HTTPException):
        raise exc
    log.error("Unhandled exception on %s %s: %s", request.method, request.url.path, exc,
              exc_info=True)
    return JSONResponse(
        status_code=500,
        content={"detail": "An unexpected server error occurred. Please try again."},
    )

# ── Pydantic schemas ──────────────────────────────────────────────────────────
class UserLogin(BaseModel):
    username: str
    password: str

class VoteSubmitRequest(BaseModel):
    site_name:     str
    game_username: str = Field(..., min_length=1, max_length=12)

class PushEventItem(BaseModel):
    event_type: str = Field(..., max_length=30)
    username:   str = Field(..., max_length=50)
    message:    str = Field(..., max_length=500)

class PushEventsRequest(BaseModel):
    secret: str = Field(default="")
    events: list[PushEventItem] = Field(..., max_items=50)

# ── Vote cooldown ─────────────────────────────────────────────────────────────
VOTE_COOLDOWN_HOURS = 12

def _get_vote_state(vote: models.Vote | None) -> dict:
    if not vote:
        return {"state": "idle", "seconds_remaining": None, "vote_id": None}
    now     = datetime.now(timezone.utc)
    created = vote.created_at
    if created.tzinfo is None:
        created = created.replace(tzinfo=timezone.utc)
    elapsed   = (now - created).total_seconds()
    remaining = int(VOTE_COOLDOWN_HOURS * 3600 - elapsed)
    if remaining > 0:
        if vote.status == "pending":
            return {"state": "pending",       "seconds_remaining": remaining, "vote_id": vote.id}
        elif vote.status == "unverified":
            # Submitted by the player, but the topsite hasn't pinged /api/vote/callback yet.
            return {"state": "unverified",    "seconds_remaining": remaining, "vote_id": vote.id}
        else:
            return {"state": "cooldown",      "seconds_remaining": remaining, "vote_id": None}
    else:
        return {"state": "idle", "seconds_remaining": None, "vote_id": None}

# ── Live feed ─────────────────────────────────────────────────────────────────
# livefeed.json lives alongside main.py (not inside the players dir).
# Drop a JSON file there for a manual override that takes priority over all
# other sources.  Format: list of event objects, or {"events": [...]}
_LIVEFEED_FILE = Path(__file__).parent / "livefeed.json"

# Curated rare-drop pool for the synthetic generator.
# Items are chosen deterministically per player so results are stable across
# poll cycles (same player always drops the same item in synthetic mode).
_RARE_DROPS = [
    ("Twisted bow",            "Chambers of Xeric"),
    ("Scythe of vitur",        "Theatre of Blood"),
    ("Tumeken's shadow",       "Tombs of Amascut"),
    ("Serpentine visage",      "Zulrah"),
    ("Tanzanite fang",         "Zulrah"),
    ("Ancestral robe top",     "Chambers of Xeric"),
    ("Dragon claws",           "Chambers of Xeric"),
    ("Avernic defender hilt",  "Theatre of Blood"),
    ("Justiciar faceguard",    "Theatre of Blood"),
    ("Elder maul",             "Chambers of Xeric"),
    ("Kodai insignia",         "Chambers of Xeric"),
    ("Osmumten's fang",        "Tombs of Amascut"),
    ("Lightbearer",            "Tombs of Amascut"),
    ("Masori mask",            "Tombs of Amascut"),
    ("Magma mutagen",          "Zulrah"),
]


def _generate_synthetic_feed(rows: list[dict], limit: int) -> list[dict]:
    """
    Builds a plausible live-feed from character-file stats when the game DB
    log tables are unavailable.  Results are deterministic per username so the
    same events appear stable across poll cycles rather than shuffling.
    """
    events = []
    now    = datetime.now(timezone.utc)

    # ── PvP kills ────────────────────────────────────────────────────────────
    killers = sorted(rows, key=lambda r: r.get('kills', 0), reverse=True)
    victims = sorted(rows, key=lambda r: r.get('deaths', 0), reverse=True)
    for i, k in enumerate(killers[:5]):
        if k.get('kills', 0) == 0:
            break
        victim = victims[i % len(victims)] if victims else {'username': 'an adventurer'}
        if victim['username'] == k['username']:
            continue
        seed = int(hashlib.md5(f"{k['username']}{i}".encode()).hexdigest(), 16) % 3600
        ts   = (now - timedelta(seconds=seed)).isoformat()
        events.append({
            "id":        f"kill_{k['username']}_{i}",
            "type":      "pvp_kill",
            "timestamp": ts,
            "message":   f"{k['username']} slew {victim['username']} in the Wilderness.",
        })

    # ── Killstreaks ───────────────────────────────────────────────────────────
    streakers = sorted(rows, key=lambda r: r.get('killstreak', 0), reverse=True)
    for i, p in enumerate(streakers[:3]):
        ks = p.get('killstreak', 0)
        if ks < 1:
            break
        seed = int(hashlib.md5(f"ks_{p['username']}".encode()).hexdigest(), 16) % 1800
        ts   = (now - timedelta(seconds=seed)).isoformat()
        events.append({
            "id":        f"streak_{p['username']}",
            "type":      "killstreak",
            "timestamp": ts,
            "message":   f"{p['username']} is on a {ks}-kill streak in the Wilderness!",
        })

    # ── Level milestones ──────────────────────────────────────────────────────
    levelers = sorted(rows, key=lambda r: r.get('total_level', 0), reverse=True)
    for i, p in enumerate(levelers[:3]):
        lvl = p.get('total_level', 0)
        if lvl < 24:
            break
        seed = int(hashlib.md5(f"lvl_{p['username']}".encode()).hexdigest(), 16) % 7200
        ts   = (now - timedelta(seconds=seed)).isoformat()
        events.append({
            "id":        f"lvl_{p['username']}",
            "type":      "level_up",
            "timestamp": ts,
            "message":   f"{p['username']} reached total level {lvl}.",
        })

    # ── Rare drops (inferred from top-XP players — implies high boss KC) ─────
    top_xp = sorted(rows, key=lambda r: r.get('total_experience', 0), reverse=True)
    for i, p in enumerate(top_xp[:4]):
        if p.get('total_experience', 0) == 0:
            break
        drop_idx  = int(hashlib.md5(f"drop_{p['username']}_{i}".encode()).hexdigest(), 16) % len(_RARE_DROPS)
        item_name, source = _RARE_DROPS[drop_idx]
        seed = int(hashlib.md5(f"d_{p['username']}".encode()).hexdigest(), 16) % 5400
        ts   = (now - timedelta(seconds=seed)).isoformat()
        events.append({
            "id":        f"drop_{p['username']}_{i}",
            "type":      "rare_drop",
            "timestamp": ts,
            "message":   f"{p['username']} received a {item_name} from {source}!",
        })

    events.sort(key=lambda e: e['timestamp'], reverse=True)
    return events[:limit]

# ── API endpoints ─────────────────────────────────────────────────────────────

@app.get("/")
def read_root():
    return {"status": "Zelus API is running!", "revision": "NR 288"}


@app.get("/livefeed")
def livefeed(limit: int = 12, db: Session = Depends(get_db)):
    """
    Live game-event feed with 4-tier data source priority:

    1. livefeed.json   — manual override file (place in API directory)
    2. game_events     — website DB table that the game server pushes events to
                         via POST /events/push.  Requires game server integration.
    3. game DB tables  — queries NR 288 pklog + drop_log tables directly from the
                         read-only game MariaDB.  Degrades gracefully if absent.
    4. synthetic       — deterministic events generated from character file stats.
                         Always available; used when all live sources are absent.
    """
    from sqlalchemy import text as sa_text

    # ── 1. Manual override file ───────────────────────────────────────────────
    if _LIVEFEED_FILE.exists():
        try:
            data   = json.loads(_LIVEFEED_FILE.read_text(encoding='utf-8'))
            events = data if isinstance(data, list) else data.get('events', [])
            if events:
                events.sort(key=lambda e: e.get('timestamp', ''), reverse=True)
                return {"events": events[:limit], "source": "file"}
        except Exception:
            pass

    # ── 2. Website DB game_events (game server push model) ────────────────────
    try:
        ge_rows = (
            db.query(models.GameEvent)
            .order_by(models.GameEvent.timestamp.desc())
            .limit(limit)
            .all()
        )
        if ge_rows:
            return {
                "events": [
                    {
                        "id":        str(e.id),
                        "type":      e.event_type,
                        "timestamp": e.timestamp.isoformat() if e.timestamp else "",
                        "message":   e.message,
                    }
                    for e in ge_rows
                ],
                "source": "game_events",
            }
    except Exception:
        pass   # table does not yet exist or DB unavailable — fall through

    # ── 3. Game DB native tables (pklog + drop_log) ───────────────────────────
    try:
        gdb    = next(get_game_db())
        events = []

        # NR 288 PK log
        try:
            kills = gdb.execute(sa_text(
                "SELECT killer, victim, time FROM pklog ORDER BY time DESC LIMIT 8"
            )).fetchall()
            for row in kills:
                ts = row[2].isoformat() if hasattr(row[2], 'isoformat') else str(row[2])
                events.append({
                    "id":        f"pk_{row[0]}_{ts}",
                    "type":      "pvp_kill",
                    "timestamp": ts,
                    "message":   f"{row[0]} slew {row[1]} in the Wilderness.",
                })
        except Exception:
            pass

        # NR 288 rare drop log (only items worth 1M+ gp)
        try:
            drops = gdb.execute(sa_text(
                "SELECT username, item_name, item_value, timestamp "
                "FROM drop_log WHERE item_value >= 1000000 "
                "ORDER BY timestamp DESC LIMIT 8"
            )).fetchall()
            for row in drops:
                ts = row[3].isoformat() if hasattr(row[3], 'isoformat') else str(row[3])
                events.append({
                    "id":        f"drop_{row[0]}_{ts}",
                    "type":      "rare_drop",
                    "timestamp": ts,
                    "message":   f"{row[0]} received {row[1]} ({int(row[2] or 0):,} gp)!",
                })
        except Exception:
            pass

        if events:
            events.sort(key=lambda e: e['timestamp'], reverse=True)
            return {"events": events[:limit], "source": "game_db"}

    except Exception:
        pass

    # ── 4. Synthetic fallback from character files ────────────────────────────
    rows = _read_character_files(200) or []
    if not rows:
        return {"events": [], "source": "empty"}
    return {"events": _generate_synthetic_feed(rows, limit), "source": "synthetic"}


@app.get("/debug/hiscores")
def debug_hiscores(db: Session = Depends(get_db)):
    """Diagnostic endpoint — shows row counts for each data source."""
    game_status = "ok"
    game_rows   = 0
    try:
        gdb       = next(get_game_db())
        game_rows = gdb.query(HiscoreUser).count()
    except Exception as e:
        game_status = str(e)

    local_status     = "ok"
    local_users      = 0
    local_skill_rows = 0
    local_rows       = 0
    try:
        local_users      = db.query(models.User).count()
        local_skill_rows = db.query(models.UserSkillStat).count()
        local_rows       = (
            db.query(models.User.username, models.UserSkillStat)
            .join(models.UserSkillStat, models.User.id == models.UserSkillStat.user_id)
            .count()
        )
    except Exception as e:
        local_status = str(e)

    char_file_rows = 0
    try:
        cf = _read_character_files(999)
        char_file_rows = len(cf) if cf else 0
    except Exception:
        pass

    return {
        "game_db":    {"status": game_status,  "hs_users_rows": game_rows},
        "local_db":   {"status": local_status, "users": local_users,
                       "skill_stats": local_skill_rows, "join_rows": local_rows},
        "char_files": {"rows": char_file_rows, "dir": str(_CHARACTERS_DIR)},
    }


@app.get("/players/online")
def get_online_players():
    """
    Returns current online player count and server status.
    Queries the NR 288 `online_characters` game DB table first;
    falls back to counting open JSON sessions in character save files.

    Response shape: {"online": int, "status": "online" | "offline"}
    """
    count = _count_online_players()
    if count is not None:
        return {"online": count, "status": "online"}
    return {"online": 0, "status": "offline"}


@app.post("/events/push", status_code=status.HTTP_201_CREATED)
@limiter.limit("120/minute")
def push_game_events(request: Request, req: PushEventsRequest, db: Session = Depends(get_db)):
    """
    Game-server → website push endpoint for live feed events.

    The NR 288 Kronos Java server should POST here whenever a notable event
    occurs (PvP kill, rare drop, level milestone, tournament win, etc.).
    Requires GAME_EVENTS_SECRET to be set in .env and passed in the `secret`
    field.  If GAME_EVENTS_SECRET is empty the endpoint accepts all requests
    (useful for local development).

    Accepted event_type values (must match frontend EVENT_STYLE keys):
      pvp_kill | killstreak | rare_drop | level_up | tournament_win | bounty_claim

    Example payload:
      {
        "secret": "your-shared-secret",
        "events": [
          {
            "event_type": "rare_drop",
            "username": "Zelus",
            "message": "Zelus received a Twisted bow from Chambers of Xeric!"
          }
        ]
      }
    """
    _VALID_TYPES = {
        "pvp_kill", "killstreak", "rare_drop",
        "level_up", "tournament_win", "bounty_claim",
    }

    if _GAME_EVENTS_SECRET and req.secret != _GAME_EVENTS_SECRET:
        raise HTTPException(status_code=403, detail="Invalid event secret.")

    inserted = 0
    for ev in req.events:
        if ev.event_type not in _VALID_TYPES:
            continue   # silently skip unknown types
        db.add(models.GameEvent(
            event_type = ev.event_type,
            username   = ev.username,
            message    = ev.message,
        ))
        inserted += 1

    if inserted:
        # Keep game_events table lean — delete rows older than 24 hours
        from sqlalchemy import text as _sa_text
        try:
            db.execute(_sa_text(
                "DELETE FROM game_events WHERE timestamp < NOW() - INTERVAL 24 HOUR"
            ))
        except Exception:
            pass
        db.commit()

    return {"inserted": inserted}


@app.get("/hiscores")
def get_hiscores(limit: int = 50, sort: str = "total_level"):
    """
    Returns up to `limit` hiscore rows ordered by `sort`.
    Data source priority:
      1. Game MariaDB hs_users table (authoritative)
      2. JSON character save files (fallback if MariaDB is unreachable)
      3. Local PostgreSQL user_skill_stats (last resort)
    """
    # Resolve sort column — reject unknown keys to prevent injection
    sort_col = HISCORE_SORT_COLUMNS.get(sort, HiscoreUser.total_level)

    # 1 — Game MariaDB
    try:
        gdb  = next(get_game_db())
        rows = (
            gdb.query(HiscoreUser)
            .order_by(sort_col.desc())
            .limit(limit)
            .all()
        )
        if rows:
            return [_build_hiscore_row(r) for r in rows]
    except Exception as e:
        log.warning("Game DB unavailable for hiscores: %s", e)

    # 2 — Character file fallback (includes PvP stats)
    all_rows = _read_character_files()
    if all_rows is not None:
        valid_sort = sort if sort in (
            'total_level', 'total_experience', 'kills', 'deaths', 'kdr', 'killstreak',
            *_SKILL_ORDER
        ) else 'total_level'
        all_rows.sort(key=lambda r: r.get(valid_sort, 0), reverse=True)
        return all_rows[:limit]

    # 3 — Local PostgreSQL fallback
    try:
        local_db = next(get_db())
        rows = (
            local_db.query(models.User.username, models.UserSkillStat)
            .join(models.UserSkillStat, models.User.id == models.UserSkillStat.user_id)
            .order_by(models.UserSkillStat.total_level.desc())
            .limit(limit)
            .all()
        )
        return [
            {
                "username":         u,
                "total_level":      s.total_level      or 0,
                "total_experience": s.total_experience or 0,
                "attack_xp":        s.attack_xp        or 0,
                "defence_xp":       s.defence_xp       or 0,
                "strength_xp":      s.strength_xp      or 0,
                "hitpoints_xp":     s.hitpoints_xp     or 0,
                "ranged_xp":        s.ranged_xp        or 0,
                "prayer_xp":        s.prayer_xp        or 0,
                "magic_xp":         s.magic_xp         or 0,
                "cooking_xp":       s.cooking_xp       or 0,
                "woodcutting_xp":   s.woodcutting_xp   or 0,
                "fletching_xp":     s.fletching_xp     or 0,
                "fishing_xp":       s.fishing_xp       or 0,
                "firemaking_xp":    s.firemaking_xp    or 0,
                "crafting_xp":      s.crafting_xp      or 0,
                "smithing_xp":      s.smithing_xp      or 0,
                "mining_xp":        s.mining_xp        or 0,
                "herblore_xp":      s.herblore_xp      or 0,
                "agility_xp":       s.agility_xp       or 0,
                "thieving_xp":      s.thieving_xp      or 0,
                "slayer_xp":        s.slayer_xp        or 0,
                "farming_xp":       s.farming_xp       or 0,
                "runecrafting_xp":  s.runecrafting_xp  or 0,
                "hunter_xp":        s.hunter_xp        or 0,
                "construction_xp":  s.construction_xp  or 0,
                "kills": 0, "deaths": 0, "kdr": 0.0, "killstreak": 0,
            }
            for u, s in rows
        ]
    except Exception:
        raise HTTPException(status_code=503, detail="Could not reach the game database.")


# ── Staff login ───────────────────────────────────────────────────────────────
# Player accounts/registration were removed — this endpoint now exists solely
# so staff can authenticate into Admin CP via the hidden /staff-login page.
# Staff accounts are created directly in the database (is_verified=True); there
# is no self-service signup path.

@app.post("/login")
@limiter.limit("10/minute")
def login_user(request: Request, user: UserLogin, db: Session = Depends(get_db)):
    db_user = db.query(models.User).filter(models.User.username == user.username).first()

    if not db_user:
        raise HTTPException(status_code=400, detail="Invalid username or password.")

    if not bcrypt.checkpw(user.password.encode("utf-8"), db_user.password.encode("utf-8")):
        raise HTTPException(status_code=400, detail="Invalid username or password.")

    if not db_user.is_verified:
        raise HTTPException(
            status_code=403,
            detail="Please verify your email address before logging in. Check your inbox."
        )

    # privilege is now stored as a plain string — no .value needed
    return {
        "message": "Login successful",
        "user": {
            "id":          db_user.id,
            "username":    db_user.username,
            "email":       db_user.email,
            "privilege":   db_user.privilege,
            "tokens":      db_user.tokens,
            "total_spent": db_user.total_spent,
        }
    }


# ── Game username validation ───────────────────────────────────────────────────
@app.get("/game/check-username/{username}")
@limiter.limit("20/minute")
def check_game_username(request: Request, username: str):
    if not username or len(username) > 12:
        raise HTTPException(status_code=400, detail="Invalid username.")
    return {"exists": _game_username_exists(username)}


# ── Vote endpoints ─────────────────────────────────────────────────────────────
#
# Two-stage verification: /vote/submit (called by our own frontend) is NOT
# trustworthy on its own — anyone could call it directly without ever voting.
# It creates a row with status="unverified" and does not grant a claimable
# vote. Only a real ping from the topsite to /api/vote/callback/{site}
# (see below) promotes that row to status="pending" (claimable in-game via
# ::claimvote). This closes the exploit where a player could claim vote
# credit without actually voting.
VALID_SITES          = {"RUNELOCUS", "RSPS_LIST"}
VOTE_POINTS_BY_SITE  = {"RUNELOCUS": 2, "RSPS_LIST": 2}

@app.post("/vote/submit", status_code=status.HTTP_201_CREATED)
@limiter.limit("10/minute")
def submit_vote(request: Request, req: VoteSubmitRequest, db: Session = Depends(get_db)):
    from sqlalchemy import func as sa_func

    try:
        if req.site_name not in VALID_SITES:
            raise HTTPException(status_code=400, detail=f"Unknown voting site: {req.site_name}")

        game_username = req.game_username.strip()
        if not _game_username_exists(game_username):
            raise HTTPException(
                status_code=400,
                detail=(
                    f"The character '{game_username}' does not exist in-game. "
                    "Please enter your exact in-game character name."
                )
            )

        client_ip = get_remote_address(request)
        cutoff    = datetime.utcnow() - timedelta(hours=VOTE_COOLDOWN_HOURS)

        recent_by_username = (
            db.query(models.Vote)
            .filter(
                sa_func.lower(models.Vote.game_username) == game_username.lower(),
                models.Vote.site_name == req.site_name,
                models.Vote.created_at >= cutoff,
            )
            .order_by(models.Vote.created_at.desc())
            .first()
        )
        if recent_by_username:
            elapsed   = (datetime.utcnow() - recent_by_username.created_at).total_seconds()
            remaining = int(VOTE_COOLDOWN_HOURS * 3600 - elapsed)
            raise HTTPException(
                status_code=429,
                detail=(
                    f"You already voted on {req.site_name}. "
                    f"Cooldown: {remaining} seconds remaining."
                )
            )

        if client_ip:
            recent_by_ip = (
                db.query(models.Vote)
                .filter(
                    models.Vote.ip_address == client_ip,
                    models.Vote.site_name  == req.site_name,
                    models.Vote.created_at >= cutoff,
                )
                .order_by(models.Vote.created_at.desc())
                .first()
            )
            if recent_by_ip:
                elapsed   = (datetime.utcnow() - recent_by_ip.created_at).total_seconds()
                remaining = int(VOTE_COOLDOWN_HOURS * 3600 - elapsed)
                raise HTTPException(
                    status_code=429,
                    detail=(
                        f"A vote from your connection was already registered for {req.site_name}. "
                        f"Cooldown: {remaining} seconds remaining."
                    )
                )

        new_vote = models.Vote(
            site_name     = req.site_name,
            vote_points   = VOTE_POINTS_BY_SITE.get(req.site_name, 2),
            status        = "unverified",
            ip_address    = client_ip,
            game_username = game_username,
        )
        db.add(new_vote)
        db.commit()
        db.refresh(new_vote)

        return {
            "message": "Cast your vote on the site that just opened — it'll unlock here automatically once confirmed.",
            "vote_id": new_vote.id,
        }

    except HTTPException:
        raise
    except OperationalError:
        raise HTTPException(
            status_code=503, detail="Database connection failed. Please try again later."
        )


# ─── GET /api/vote/callback/{site} ─────────────────────────────────────────────
# Server-to-server ping from the topsite itself, confirming a vote actually
# happened — this is the ONLY thing that makes a vote claimable in-game.
#
# Two ways to authenticate a ping, because dashboards differ in what they let
# you configure — some (RSPS-List, per their docs) accept a full URL with
# arbitrary query params; others (RuneLocus's listing settings, as configured
# 2026-08-11) only take a bare callback URL with no way to append `?secret=`.
# Both routes below run the exact same logic (_handle_vote_callback):
#
#   Query-param secret (RSPS-List):
#     https://<api-host>/api/vote/callback/rspslist?secret=<VOTE_CALLBACK_SECRET>&userid=<their-placeholder>&voted=1&userip=<their-placeholder>
#
#   Path-segment secret (RuneLocus, or any dashboard that can't add query params):
#     https://<api-host>/api/vote/callback/runelocus/<VOTE_CALLBACK_SECRET>?username=<their-placeholder>
#
# RSPS-List's postback format (confirmed against their docs):
#   userid (or postback) = the in-game username the voter typed on their site
#   voted                = "1" on a successful vote -- must equal "1" or the
#                          ping is ignored, no credit granted
#   secret                = their copy of our API secret
#   userip                = the voter's IP, stored on the Vote row if present
#
# RuneLocus's exact postback format is unconfirmed (only visible in their own
# dashboard) -- the username-param list below is deliberately tolerant of
# both formats so either site's ping works without further changes.
#
# If VOTE_CALLBACK_SECRET is unset, the secret check is skipped entirely (dev
# only -- this makes the endpoint open to anyone, so it MUST be set before
# either callback URL is registered with a real topsite).
_CALLBACK_SITE_SLUGS = {
    "runelocus":  "RUNELOCUS",
    "rspslist":   "RSPS_LIST",
    "rsps-list":  "RSPS_LIST",
    "rsps_list":  "RSPS_LIST",
}
_CALLBACK_USERNAME_PARAMS = ("userid", "postback", "id", "username", "user", "u", "pingUsername", "name")

def _handle_vote_callback(site: str, provided_secret: str | None, request: Request, db: Session):
    from sqlalchemy import func as sa_func

    site_name = _CALLBACK_SITE_SLUGS.get(site.lower())
    if not site_name:
        raise HTTPException(status_code=404, detail=f"Unknown vote callback site: {site}")

    if _VOTE_CALLBACK_SECRET and provided_secret != _VOTE_CALLBACK_SECRET:
        log.warning("vote_callback: rejected %s ping with bad/missing secret from %s",
                    site_name, get_remote_address(request))
        raise HTTPException(status_code=403, detail="Invalid callback secret.")

    # "voted" (RSPS-List): if the topsite tells us explicitly whether the vote
    # succeeded, believe it -- only "1" counts. Sites that don't send this
    # param at all (unconfirmed for RuneLocus) aren't blocked by its absence.
    voted_param = request.query_params.get("voted")
    if voted_param is not None and voted_param != "1":
        log.info("vote_callback: %s reported voted=%s (not 1) — ignoring, no credit granted.",
                  site_name, voted_param)
        return {"status": "ignored", "reason": "vote not marked successful by site"}

    game_username = None
    for param in _CALLBACK_USERNAME_PARAMS:
        value = request.query_params.get(param)
        if value:
            game_username = value.strip()
            break

    if not game_username:
        raise HTTPException(
            status_code=400,
            detail=f"No username found in callback query params (expected one of {_CALLBACK_USERNAME_PARAMS})."
        )

    if not _game_username_exists(game_username):
        log.warning("vote_callback: %s pinged for unknown character '%s' — ignoring.",
                    site_name, game_username)
        # 200, not 4xx/5xx -- an error response could make the topsite retry indefinitely.
        return {"status": "ignored", "reason": "unknown in-game character"}

    # Prefer the topsite's own reported voter IP (RSPS-List's "userip") over
    # the request's own remote address, which is the TOPSITE's server IP, not
    # the voter's -- more useful for any future anti-abuse review.
    reported_ip = request.query_params.get("userip") or get_remote_address(request)

    try:
        now    = datetime.utcnow()
        cutoff = now - timedelta(hours=VOTE_COOLDOWN_HOURS)

        # 1. Promote the matching "unverified" row from /vote/submit, if one exists.
        unverified = (
            db.query(models.Vote)
            .filter(
                sa_func.lower(models.Vote.game_username) == game_username.lower(),
                models.Vote.site_name  == site_name,
                models.Vote.status     == "unverified",
                models.Vote.created_at >= cutoff,
            )
            .order_by(models.Vote.created_at.desc())
            .first()
        )
        if unverified:
            unverified.status = "pending"
            db.commit()
            log.info("vote_callback: %s confirmed vote #%d for '%s'.",
                      site_name, unverified.id, game_username)
            return {"status": "confirmed", "vote_id": unverified.id}

        # 2. No unverified row (callback arrived before/without a /vote/submit call,
        #    or the topsite re-pinged) -- avoid creating a duplicate if one's already
        #    pending/claimed for this site+username within the cooldown window.
        existing = (
            db.query(models.Vote)
            .filter(
                sa_func.lower(models.Vote.game_username) == game_username.lower(),
                models.Vote.site_name  == site_name,
                models.Vote.status.in_(["pending", "claimed"]),
                models.Vote.created_at >= cutoff,
            )
            .order_by(models.Vote.created_at.desc())
            .first()
        )
        if existing:
            return {"status": "duplicate", "vote_id": existing.id}

        # 3. The callback itself is authoritative proof a vote happened -- create it.
        new_vote = models.Vote(
            site_name     = site_name,
            vote_points   = VOTE_POINTS_BY_SITE.get(site_name, 2),
            status        = "pending",
            ip_address    = reported_ip,
            game_username = game_username,
        )
        db.add(new_vote)
        db.commit()
        db.refresh(new_vote)
        log.info("vote_callback: %s created fresh confirmed vote #%d for '%s' (no prior submit).",
                  site_name, new_vote.id, game_username)
        return {"status": "confirmed", "vote_id": new_vote.id}

    except OperationalError:
        raise HTTPException(status_code=503, detail="Database connection failed.")


@app.get("/api/vote/callback/{site}")
@limiter.limit("120/minute")
def vote_callback_query_secret(site: str, request: Request, db: Session = Depends(get_db)):
    return _handle_vote_callback(site, request.query_params.get("secret"), request, db)


@app.get("/api/vote/callback/{site}/{secret}")
@limiter.limit("120/minute")
def vote_callback_path_secret(site: str, secret: str, request: Request, db: Session = Depends(get_db)):
    return _handle_vote_callback(site, secret, request, db)


# ─── GET /voting/claim/{username} ──────────────────────────────────────────────
# Called by the game server's ::claimvote/::voted/::claimed command
# (CommandHandlerRegular.java -- EventWorker.submitHttpGetRequest("voting/claim/" +
# player.getName(), ...)). Must return a bare JSON array of strings; the Java
# side only inspects its length (claimedVotes.size()/isEmpty()), so the exact
# string content isn't load-bearing -- each entry is the site_name of one
# newly-claimed vote, for anyone reading logs/responses later.
#
# Every "pending" (server-verified, see /api/vote/callback/{site}) vote for
# this username is atomically marked "claimed" and returned in one call, so a
# retried/duplicate request from the game server can't double-claim -- once
# marked "claimed" here, a vote won't be picked up by a second call.
@app.get("/voting/claim/{username}")
@limiter.limit("30/minute")
def claim_votes_for_game(username: str, request: Request, db: Session = Depends(get_db)):
    from sqlalchemy import func as sa_func

    try:
        norm = username.strip().lower()
        pending = (
            db.query(models.Vote)
            .filter(
                sa_func.lower(models.Vote.game_username) == norm,
                models.Vote.status == "pending",
            )
            .all()
        )

        if not pending:
            return []

        now = datetime.utcnow()
        claimed_sites = []
        for vote in pending:
            vote.status     = "claimed"
            vote.claimed_at = now
            claimed_sites.append(vote.site_name)

        db.commit()
        log.info("claim_votes_for_game: '%s' claimed %d vote(s): %s",
                  username, len(claimed_sites), claimed_sites)
        return claimed_sites

    except OperationalError:
        raise HTTPException(status_code=503, detail="Database connection failed.")


@app.get("/votes/status/{username}")
def get_vote_status(username: str, db: Session = Depends(get_db)):
    from sqlalchemy import func as sa_func

    try:
        norm = username.strip().lower()
        result = []
        for site_name in VALID_SITES:
            cutoff = datetime.utcnow() - timedelta(hours=VOTE_COOLDOWN_HOURS)
            recent = (
                db.query(models.Vote)
                .filter(
                    sa_func.lower(models.Vote.game_username) == norm,
                    models.Vote.site_name  == site_name,
                    models.Vote.created_at >= cutoff,
                )
                .order_by(models.Vote.created_at.desc())
                .first()
            )
            state_info = _get_vote_state(recent)
            result.append({"site_name": site_name, **state_info})
        return result
    except OperationalError:
        raise HTTPException(
            status_code=503, detail="Database connection failed. Please try again later."
        )


@app.get("/votes/pending/{username}")
def get_pending_votes(username: str, db: Session = Depends(get_db)):
    from sqlalchemy import func as sa_func

    try:
        norm = username.strip().lower()
        pending = (
            db.query(models.Vote)
            .filter(
                sa_func.lower(models.Vote.game_username) == norm,
                models.Vote.status == "pending",
            )
            .all()
        )
        return [
            {"vote_id": v.id, "site_name": v.site_name, "vote_points": v.vote_points}
            for v in pending
        ]
    except OperationalError:
        raise HTTPException(status_code=503, detail="Database connection failed.")


@app.post("/votes/claim/{vote_id}")
def claim_vote(vote_id: int, db: Session = Depends(get_db)):
    try:
        vote = db.query(models.Vote).filter(models.Vote.id == vote_id).first()
        if not vote:
            raise HTTPException(status_code=404, detail="Vote not found.")
        if vote.status == "claimed":
            raise HTTPException(status_code=400, detail="Vote already claimed.")
        vote.status     = "claimed"
        vote.claimed_at = datetime.utcnow()
        db.commit()
        return {"message": f"Vote {vote_id} marked as claimed."}
    except HTTPException:
        raise
    except OperationalError:
        raise HTTPException(status_code=503, detail="Database connection failed.")


# ═══════════════════════════════════════════════════════════════════════════════
# GAME-SERVER CLAIM SYSTEM
# ═══════════════════════════════════════════════════════════════════════════════
#
# Flow:
#   1. On boot, game server POSTs to /authenticate/login with World.apiPassword.
#      API returns a session token stored as cookie r_auth=<token>.
#   2. Player types ::claim in-game → server GETs /store/claim?username=X
#      (with Cookie: r_auth=<token>).  API returns all unclaimed purchases
#      formatted as DonationRequestClaim JSON.
#   3. Game server gives items/bonds to the player, then POSTs to
#      /store/claim/confirm to mark those rows as claimed.
#
# Security layers:
#   • Shared password (GAME_API_PASSWORD env var) required at login.
#   • Session tokens: 256-bit random hex, 6-hour TTL, refreshed on use.
#   • All game-server endpoints reject requests without a valid r_auth token.
#   • Per-player rate limit: only unclaimed rows returned (idempotent).
#   • DB-level unique constraint on transaction_id prevents double-delivery.
#   • All claim actions are logged with timestamp for audit.
# ───────────────────────────────────────────────────────────────────────────────

# In-memory session store: token → expiry (Unix epoch float)
# Fine for a single-process server; swap for Redis if you run multiple workers.
_game_sessions: dict[str, float] = {}
_SESSION_TTL_SECONDS = 6 * 3600  # 6 hours


def _require_game_session(request: Request) -> str:
    """
    FastAPI dependency.  Validates the r_auth cookie sent by the game server.
    Raises 403 if the token is missing, unknown, or expired.
    Returns the token string so callers can refresh it.
    """
    # OkHttp sends cookies in the Cookie header; FastAPI also parses them.
    token = request.cookies.get("r_auth")
    if not token:
        # Fallback: parse the raw Cookie header (some HTTP clients set it differently)
        raw = request.headers.get("cookie", "") or request.headers.get("Cookie", "")
        for part in raw.split(";"):
            part = part.strip()
            if part.startswith("r_auth="):
                token = part[7:]
                break

    if not token:
        raise HTTPException(status_code=403, detail="Missing game-server auth token.")

    expiry = _game_sessions.get(token)
    if expiry is None or time.time() > expiry:
        _game_sessions.pop(token, None)
        raise HTTPException(status_code=403, detail="Invalid or expired game-server session.")

    # Sliding expiry — active servers keep their token alive indefinitely
    _game_sessions[token] = time.time() + _SESSION_TTL_SECONDS
    return token


# ─── Slug → in-game item delivery ─────────────────────────────────────────────
#
# Maps each store slug to a list of (item_id, amount) tuples.
# These are the physical items placed in the player's inventory / bank via ::claim.
#
# Bond item IDs and their DP / rank-credit values — rebalanced 2026-08-11,
# amounts confirmed live in-game (::claim tested against each bond):
#   30464 = $5  bond  →  350 DP, updateTotalDonated(  5)
#   30497 = $10 bond  →  770 DP, updateTotalDonated( 10)
#   30466 = $25 bond  → 2180 DP, updateTotalDonated( 25)
#   30467 = $50 bond  → 4350 DP, updateTotalDonated( 50)
#   30468 = $100 bond → 9450 DP, updateTotalDonated(100)
#
# Claim scroll IDs (update totalDonated only — no DP awarded):
#   30575 = $10 donated scroll
#   30576 = $25 donated scroll
#   30573 = $50 donated scroll
#   30574 = $100 donated scroll
#
# Rank thresholds (totalDonated $) — equal to the store price, no bridge math:
#   DONATOR≥10 | SUPER≥50 | ELITE≥100 | NOBLE≥250 | GOLD≥400
#   PLATINUM≥700 | LEGENDARY≥1000 | SUPREME≥1750
#
# Booster scrolls are stackable — amounts represent boost duration in 30-min units.
#   30460 = Double exp scroll (30 min)
#   30459 = Double drops scroll (30 min)
#   30572 = 5% Drop rate scroll
#   30458 = Slayer task skip scroll
#   22092 = Pet bonus token (24 h)

SLUG_TO_ITEMS: dict[str, list[tuple[int, int]]] = {

    # ── Donator Points (bond items — player redeems in-game for DP + rank credit)
    "dp_500":            [(30464, 1)],        # $5   bond → 350 DP
    "dp_1200":           [(30497, 1)],        # $10  bond → 770 DP
    "dp_3000":           [(30466, 1)],        # $25  bond → 2,180 DP
    "dp_8000":           [(30467, 1)],        # $50  bond → 4,350 DP
    "dp_20000":          [(30468, 1)],        # $100 bond → 9,450 DP

    # ── Miscellaneous boxes
    "mystery_box":       [(30444, 1)],        # Donator mystery box
    "super_mystery_box": [(30447, 1)],        # Advanced donator mystery box
    "grand_promo_box":   [(30451, 1)],        # High tier mystery box
    "pet_box":           [(30185, 1)],        # Legendary pet mystery box

    # ── XP boosters  (Double exp scroll, 30 min each, stackable)
    "scroll_xp_1h":      [(30460,   2)],      #   2 × 30 min = 1 h
    "scroll_xp_24h":     [(30460,  48)],      #  48 × 30 min = 24 h
    "scroll_xp_7d":      [(30460, 336)],      # 336 × 30 min = 7 days

    # ── Drop-rate boosters  (5% Drop rate scroll, stackable)
    "scroll_dr_1h":      [(30572,   2)],      #   2 × 30 min = 1 h
    "scroll_dr_24h":     [(30572,  48)],      #  48 × 30 min = 24 h

    # ── PK / Double-drops boosters  (Double drops scroll, 30 min each, stackable)
    "scroll_pk_1h":      [(30459,   2)],      #   2 × 30 min = 1 h
    "scroll_pk_24h":     [(30459,  48)],      #  48 × 30 min = 24 h

    # ── Pet bonus  (Pet bonus token — 24 h per token, stackable not needed)
    "scroll_pet_1h":     [(22092,   1)],      # 1 token = 24 h pet bonus

    # ── Slayer skip scrolls  (Slayer task skip scroll, stackable)
    "scroll_slayer_24h": [(30458,   5)],      # 5 skip scrolls

    # ── Donor ranks  (claim scrolls — triggers updateTotalDonated + auto-rank)
    # Non-stackable scrolls go to bank automatically if inventory is full.
    "donator":           [(30575,  1)],                      # $10   donated
    "super_donator":     [(30573,  1)],                      # $50   donated
    "elite_donator":     [(30574,  1)],                      # $100  donated
    "noble_donator":     [(30574,  2), (30573, 1)],          # $250  donated ($200 + $50)
    "gold_donator":      [(30574,  4)],                      # $400  donated
    "platinum_donator":  [(30574,  7)],                      # $700  donated
    "legendary_donator": [(30574, 10)],                      # $1 000 donated
    "supreme_donator":   [(30574, 17), (30573, 1)],          # $1 750 donated ($1 700 + $50)

    # ── Packages  (DP bond + key items; physical gear must be granted server-side)
    # pkg_starter_pvm ($15):   donator scroll + $5 bond (500 DP) + 24 h XP
    "pkg_starter_pvm":        [(30575, 1), (30464, 1), (30460, 48)],

    # pkg_ironman_headstart ($20):  $10 bond (1 000 DP)
    "pkg_ironman_headstart":  [(30497, 1)],

    # pkg_ultimate_skiller ($35):  $25 bond (3 000 DP) + 7-day XP
    "pkg_ultimate_skiller":   [(30466, 1), (30460, 336)],

    # pkg_wilderness_warlord ($40):  $25 bond (3 000 DP) + 24 h drop boost
    "pkg_wilderness_warlord": [(30466, 1), (30459, 48)],

    # pkg_raiders_arsenal ($60):  2× $10 bond (2 000 DP) + 5 mystery boxes
    "pkg_raiders_arsenal":    [(30497, 2), (30444, 5)],

    # pkg_rng_god ($90):  $50 scroll (rank) + 3 grand boxes + 5 super + 10 std + 24 h DR
    "pkg_rng_god":            [(30573, 1), (30451, 3), (30447, 5), (30444, 10), (30572, 48)],

    # pkg_supreme_endgame ($150):  $100 bond + $50 scroll + 5 grand boxes + 7-day XP + 24 h DR
    "pkg_supreme_endgame":    [(30468, 1), (30573, 1), (30451, 5), (30460, 336), (30572, 48)],
}


# ─── POST /authenticate/login ──────────────────────────────────────────────────
# Called once at game server boot (HttpClient.authenticate()).
# Body: GSON.toJson(password) → a bare JSON string e.g. "mysecret"
# Response: a bare JSON string (the session token), consumed as:
#   authCookieValue = gson.fromJson(response.body().string(), String.class)
#   → Cookie: r_auth=<token>

@app.post("/authenticate/login")
@limiter.limit("10/minute")
async def game_server_login(request: Request):
    try:
        body = await request.body()
        password = json.loads(body)          # unwrap the JSON string
        if not isinstance(password, str):
            raise ValueError
    except (json.JSONDecodeError, ValueError):
        raise HTTPException(status_code=400, detail="Expected a JSON-encoded string body.")

    if not _GAME_API_PASSWORD:
        log.error("GAME_API_PASSWORD is not configured — rejecting game-server login.")
        raise HTTPException(status_code=503, detail="Auth not configured.")

    # Constant-time comparison to prevent timing attacks
    if not secrets.compare_digest(password, _GAME_API_PASSWORD):
        log.warning("Game server sent incorrect API password.")
        raise HTTPException(status_code=403, detail="Invalid credentials.")

    token = secrets.token_hex(32)            # 256-bit random token
    _game_sessions[token] = time.time() + _SESSION_TTL_SECONDS
    log.info("Game server authenticated — session issued.")
    # Return the token as a bare JSON string (matches GSON.fromJson(..., String.class))
    return JSONResponse(content=token)


# ─── GET /store/claim ──────────────────────────────────────────────────────────
# Called by the game server when a player types ::claim.
# Returns DonationRequestClaim JSON consumed by DonationCommandHandler.handleClaim().

@app.get("/store/claim")
@limiter.limit("30/minute")
def store_claim(
    request:  Request,
    username: str,
    db:       Session = Depends(get_db),
    _token:   str     = Depends(_require_game_session),
):
    if not username or len(username) > 12:
        raise HTTPException(status_code=400, detail="Invalid username.")

    # Fetch all unclaimed rows for this player
    pending = (
        db.query(models.PendingClaim)
        .filter(
            models.PendingClaim.username       == username,
            models.PendingClaim.claimed_status == "unclaimed",
        )
        .order_by(models.PendingClaim.created_at)
        .all()
    )

    view_models = []
    total_spent_this_claim_cents = 0

    for claim in pending:
        slug = claim.package_id
        items = SLUG_TO_ITEMS.get(slug)

        if items is None:
            # Unknown slug — log and skip rather than crashing the whole claim
            log.warning("store/claim: unknown slug '%s' for user '%s' (claim #%d) — skipped.",
                        slug, username, claim.id)
            continue

        # Build item list in DonationIngameItems.IngameItem format (camelCase for GSON)
        ingame_items = [{"itemId": item_id, "amount": amount} for item_id, amount in items]

        # Basket model mirrors StoreBasketModel fields GSON will parse
        basket_model = {
            "id":               str(claim.id),   # used by /store/claim/confirm
            "username":         username,
            "paymentStatus":    "paid",
            "hasClaimed":       False,
            "transactionAmount": int(claim.tokens_to_give or 0),
        }

        view_models.append({
            "basketModel":  basket_model,
            "inGameItems":  {"items": ingame_items},
        })

        # Accumulate USD cents for DonationBossHandler (price from catalog, or 0)
        from store_catalog import get_item as _catalog_get
        catalog_item = _catalog_get(slug)
        if catalog_item:
            total_spent_this_claim_cents += int(catalog_item.price_usd * 100)

    # Overall lifetime spent (sum of all completed transactions for this user)
    from sqlalchemy import func as _func
    overall_cents_row = (
        db.query(_func.sum(models.Transaction.amount_usd))
        .filter(
            models.Transaction.username == username,
            models.Transaction.status   == "completed",
        )
        .scalar()
    )
    overall_spent_cents = int((overall_cents_row or 0.0) * 100)

    response_payload = {
        "username":             username,
        "totalSpentThisClaim":  total_spent_this_claim_cents,
        "overallSpent":         overall_spent_cents,
        "viewModels":           view_models,
    }

    log.info("store/claim: returning %d pending claim(s) for '%s'.", len(view_models), username)
    return JSONResponse(content=response_payload)


# ─── POST /store/claim/confirm ─────────────────────────────────────────────────
# Called by the game server after successfully delivering items to the player.
# Body: List<String> of basket IDs (pending_claim.id values, as strings).
# Query: ?ipAddress=<player_ip>

@app.post("/store/claim/confirm")
@limiter.limit("60/minute")
async def store_claim_confirm(
    request:    Request,
    ipAddress:  str = "",
    db:         Session = Depends(get_db),
    _token:     str    = Depends(_require_game_session),
):
    try:
        body = await request.body()
        claim_ids_raw: list = json.loads(body)
        if not isinstance(claim_ids_raw, list):
            raise ValueError
    except (json.JSONDecodeError, ValueError):
        raise HTTPException(status_code=400, detail="Expected a JSON array of claim IDs.")

    if not claim_ids_raw:
        return {"confirmed": 0}

    # Convert to integers (the game server sends them as strings from basketModel.id)
    try:
        claim_ids = [int(cid) for cid in claim_ids_raw]
    except (ValueError, TypeError):
        raise HTTPException(status_code=400, detail="Claim IDs must be numeric strings.")

    now = datetime.utcnow()
    confirmed = 0

    for cid in claim_ids:
        claim = db.query(models.PendingClaim).filter(models.PendingClaim.id == cid).first()

        if claim is None:
            log.warning("store/claim/confirm: claim #%d not found — skipped.", cid)
            continue

        if claim.claimed_status == "claimed":
            # Idempotent: already confirmed (e.g. duplicate webhook delivery)
            log.info("store/claim/confirm: claim #%d already claimed — skipped.", cid)
            continue

        claim.claimed_status = "claimed"
        claim.claimed_at     = now
        confirmed += 1
        log.info("store/claim/confirm: claim #%d (slug=%s, user=%s, ip=%s) marked claimed.",
                 cid, claim.package_id, claim.username, ipAddress or "unknown")

    db.commit()
    return {"confirmed": confirmed}


# ── Admin endpoints ────────────────────────────────────────────────────────────
# Note: these endpoints are protected at the frontend via privilege checks
# (ranks.js ADMIN_GROUPS).  The backend exposes them without a token for now —
# add an Authorization header check here if you want server-side enforcement.

@app.get("/admin/users")
def get_all_users(db: Session = Depends(get_db)):
    users = db.query(models.User).order_by(models.User.id.desc()).all()
    return [
        {
            "id":          u.id,
            "username":    u.username,
            "privilege":   u.privilege,   # plain string — no .value needed
            "total_spent": u.total_spent,
        }
        for u in users
    ]

@app.get("/admin/donations")
def get_all_donations(db: Session = Depends(get_db)):
    donations = db.query(models.Donation).order_by(models.Donation.id.desc()).all()
    return [
        {
            "id":           d.id,
            "package_name": d.package_name,
            "usd_amount":   d.usd_amount,
            "status":       d.status,
        }
        for d in donations
    ]
