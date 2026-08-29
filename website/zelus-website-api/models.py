import enum

from sqlalchemy import (
    Boolean,
    Column,
    DateTime,
    Float,
    ForeignKey,
    Integer,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.sql import func

Base = declarative_base()

# ---------------------------------------------------------------------------
# Privilege levels — aligned with the NR 288 server's PlayerGroup enum.
# Stored as VARCHAR(30) in the DB (no native PG enum) so future renames never
# require ALTER TYPE migrations.  Values are kept as a Python enum purely for
# IDE auto-complete and documentation; the DB column is a plain string.
# ---------------------------------------------------------------------------
class ApiPrivilege(enum.Enum):
    REGISTERED      = "REGISTERED"       # default new account (was PLAYER)
    BETA_TESTER     = "BETA_TESTER"
    YOUTUBER        = "YOUTUBER"
    FORUM_MODERATOR = "FORUM_MODERATOR"
    SUPPORT         = "SUPPORT"
    MODERATOR       = "MODERATOR"
    COMMUNITY_ADMIN = "COMMUNITY_ADMIN"  # new — mid-tier staff
    ADMINISTRATOR   = "ADMINISTRATOR"
    HEAD_ADMIN      = "HEAD_ADMIN"       # new — above ADMINISTRATOR
    DEVELOPER       = "DEVELOPER"
    OWNER           = "OWNER"            # new — highest rank (was TRUE_DEVELOPER)
    BANNED          = "BANNED"

# Convenience set used by /admin/* endpoints to validate staff access
ADMIN_PRIVILEGE_VALUES = {
    ApiPrivilege.COMMUNITY_ADMIN.value,
    ApiPrivilege.ADMINISTRATOR.value,
    ApiPrivilege.HEAD_ADMIN.value,
    ApiPrivilege.DEVELOPER.value,
    ApiPrivilege.OWNER.value,
}

# ---------------------------------------------------------------------------
# Game modes — aligned with the NR 288 server's GameMode enum.
# GROUP_IRONMAN and HARDCORE_GROUP_IRONMAN are new additions.
# STANDARD replaces the old NORMAL value.
# ---------------------------------------------------------------------------
class GameMode(enum.Enum):
    STANDARD               = "STANDARD"               # was NORMAL
    IRONMAN                = "IRONMAN"
    HARDCORE_IRONMAN       = "HARDCORE_IRONMAN"
    ULTIMATE_IRONMAN       = "ULTIMATE_IRONMAN"
    GROUP_IRONMAN          = "GROUP_IRONMAN"           # new
    HARDCORE_GROUP_IRONMAN = "HARDCORE_GROUP_IRONMAN"  # new


# ---------------------------------------------------------------------------
# User — website account table (PostgreSQL, not the game MariaDB)
# privilege and game_mode are stored as VARCHAR so enum changes never require
# an ALTER TYPE in production.
# ---------------------------------------------------------------------------
class User(Base):
    __tablename__ = "users"

    id                 = Column(Integer, primary_key=True, index=True, autoincrement=True)
    username           = Column(String(12), unique=True, index=True, nullable=False)
    password           = Column(String(128), nullable=False)
    password_at_risk   = Column(Boolean, default=False)
    email              = Column(String(255), unique=True, index=True, nullable=True)

    # Stored as plain VARCHAR — matches ApiPrivilege.value strings
    privilege          = Column(String(30), default=ApiPrivilege.REGISTERED.value, nullable=False)

    tokens             = Column(Integer, default=0)       # store token balance
    votes              = Column(Integer, default=0)
    total_spent        = Column(Float, default=0.0)       # USD — used for donor-rank display

    last_login         = Column(DateTime, nullable=True)
    join_date          = Column(DateTime, default=func.now())

    two_factor_secret    = Column(String(50), nullable=True)
    two_factor_activated = Column(Boolean, default=False)

    # Stored as plain VARCHAR — matches GameMode.value strings
    game_mode          = Column(String(30), default=GameMode.STANDARD.value, nullable=False)

    is_verified        = Column(Boolean, default=False)
    verification_token = Column(String(255), nullable=True, index=True)


# ---------------------------------------------------------------------------
# UserSkillStat — mirrors the website PostgreSQL user stats (fallback only).
# Primary hiscore data comes from the game MariaDB via game_database.py.
# ---------------------------------------------------------------------------
class UserSkillStat(Base):
    __tablename__ = "user_skill_stats"

    user_id          = Column(Integer, ForeignKey("users.id"), primary_key=True)

    total_level      = Column(Integer, default=32)
    total_experience = Column(Integer, default=1154)

    attack_xp        = Column(Integer, default=0)
    defence_xp       = Column(Integer, default=0)
    strength_xp      = Column(Integer, default=0)
    hitpoints_xp     = Column(Integer, default=1154)
    ranged_xp        = Column(Integer, default=0)
    prayer_xp        = Column(Integer, default=0)
    magic_xp         = Column(Integer, default=0)
    cooking_xp       = Column(Integer, default=0)
    woodcutting_xp   = Column(Integer, default=0)
    fletching_xp     = Column(Integer, default=0)
    fishing_xp       = Column(Integer, default=0)
    firemaking_xp    = Column(Integer, default=0)
    crafting_xp      = Column(Integer, default=0)
    smithing_xp      = Column(Integer, default=0)
    mining_xp        = Column(Integer, default=0)
    herblore_xp      = Column(Integer, default=0)
    agility_xp       = Column(Integer, default=0)
    thieving_xp      = Column(Integer, default=0)
    slayer_xp        = Column(Integer, default=0)
    farming_xp       = Column(Integer, default=0)
    runecrafting_xp  = Column(Integer, default=0)
    hunter_xp        = Column(Integer, default=0)
    construction_xp  = Column(Integer, default=0)


# ---------------------------------------------------------------------------
# Vote — tracks vote submissions through the website portal.
# Identity is the validated in-game username (game_username) — voting no
# longer requires a website account.  user_id is kept only for legacy rows
# from before accounts were removed.
# The game server reads this table (or a mirror) to award in-game rewards.
# ---------------------------------------------------------------------------
class Vote(Base):
    __tablename__ = "votes"

    id            = Column(Integer, primary_key=True, index=True, autoincrement=True)
    user_id       = Column(Integer, ForeignKey("users.id"), index=True, nullable=True)
    site_name     = Column(String(50), nullable=False)    # e.g. RUNELOCUS, RSPS_LIST
    vote_points   = Column(Integer, default=2)             # points awarded on claim
    status        = Column(String(20), default="pending")  # pending | claimed
    created_at    = Column(DateTime, default=func.now())
    claimed_at    = Column(DateTime, nullable=True)
    ip_address    = Column(String(45), nullable=True)      # IPv4 or IPv6
    game_username = Column(String(12), nullable=True, index=True)  # validated in-game character name


# ---------------------------------------------------------------------------
# Donation — legacy table kept for backward compatibility.
# New purchases go through Transaction + PendingClaim.
# ---------------------------------------------------------------------------
class Donation(Base):
    __tablename__ = "donations"

    id             = Column(Integer, primary_key=True, index=True, autoincrement=True)
    user_id        = Column(Integer, ForeignKey("users.id"), index=True)
    package_name   = Column(String(100), nullable=False)
    usd_amount     = Column(Float, nullable=False)
    tokens_to_give = Column(Integer, nullable=False)
    status         = Column(String(20), default="pending")
    created_at     = Column(DateTime, default=func.now())
    claimed_at     = Column(DateTime, nullable=True)


# ---------------------------------------------------------------------------
# Payment system models
# ---------------------------------------------------------------------------

class TransactionStatus(enum.Enum):
    PENDING   = "pending"
    COMPLETED = "completed"
    FAILED    = "failed"
    REFUNDED  = "refunded"


class PaymentProvider(enum.Enum):
    STRIPE  = "stripe"
    PAYPAL  = "paypal"
    OSRS_GP = "osrs_gp"   # manual trade, fulfilled by staff after Discord ticket
    CRYPTO  = "crypto"    # placeholder — Coinbase/NowPayments (future)
    TEBEX   = "tebex"     # webstore hosted entirely on Tebex -- no /api/checkout
                           # session, fulfillment happens purely off the webhook


class Transaction(Base):
    """
    Audit log for every payment attempt.
    Created before the player is redirected to Stripe/PayPal; updated by the
    webhook once the provider confirms or rejects the charge.
    """
    __tablename__ = "transactions"

    id                  = Column(Integer, primary_key=True, index=True, autoincrement=True)
    username            = Column(String(12), nullable=False, index=True)
    package_id          = Column(String(50), nullable=False)
    package_name        = Column(String(100), nullable=False)
    amount_usd          = Column(Float, nullable=False)
    # Stores PaymentProvider.<X>.value ("stripe"/"paypal"/"osrs_gp"/"crypto"/"tebex") --
    # NEVER assign the raw enum member here (str(enum_member) is "PaymentProvider.X",
    # which overflows String(10) and raises pymysql.err.DataError at flush/commit time.
    # Every call site MUST use .value explicitly; this column is a plain String, not a
    # SQLAlchemy Enum type, so nothing coerces it automatically).
    provider            = Column(String(10), nullable=False)
    provider_session_id = Column(String(255), unique=True, index=True, nullable=True)
    # Same footgun as `provider` above -- TransactionStatus is also a plain enum.Enum,
    # so every read (`txn.status == ...`) and write must use .value on both sides.
    # Comparing against the bare enum member silently always evaluates False once the
    # column actually holds a string, which breaks every COMPLETED idempotency check.
    status              = Column(String(20), default=TransactionStatus.PENDING.value, nullable=False)
    created_at          = Column(DateTime, default=func.now())
    completed_at        = Column(DateTime, nullable=True)
    raw_webhook_payload = Column(Text, nullable=True)


class PendingClaim(Base):
    """
    Written by the webhook handler once a payment is verified.
    The in-game ::claimbond command reads this table to deliver items/tokens.
    Flow: unclaimed → claimed
    """
    __tablename__ = "pending_claims"

    id             = Column(Integer, primary_key=True, index=True, autoincrement=True)
    username       = Column(String(12), nullable=False, index=True)
    package_id     = Column(String(50), nullable=False)
    package_name   = Column(String(100), nullable=False)
    tokens_to_give = Column(Integer, nullable=False)
    transaction_id = Column(Integer, ForeignKey("transactions.id"), nullable=True, index=True)
    claimed_status = Column(String(20), default="unclaimed", nullable=False)
    created_at     = Column(DateTime, default=func.now())
    claimed_at     = Column(DateTime, nullable=True)

    # DB-level safety net: one claim per transaction prevents duplicate fulfillment
    # even if two webhook deliveries race past the application-level idempotency check.
    __table_args__ = (
        UniqueConstraint("transaction_id", name="uq_pending_claims_transaction_id"),
    )


# ---------------------------------------------------------------------------
# GameSession — the game server's r_auth token (see main.py's
# _require_game_session / POST /authenticate/login).
#
# DB-backed rather than an in-memory dict: website-api runs multiple gunicorn
# worker processes, each its own Python interpreter. A dict would only be
# visible to whichever worker happened to handle the login request, so a
# /store/claim request routed to a *different* worker would always see the
# token as unknown and 403 — confirmed live 2026-08-29, a real Tebex
# purchase's ::claim failed this way even seconds after a fresh, successful
# re-login. All workers share one DB, so this is worker-count-proof.
# ---------------------------------------------------------------------------
class GameSession(Base):
    __tablename__ = "game_sessions"

    token      = Column(String(64), primary_key=True)
    expires_at = Column(DateTime, nullable=False)


# ---------------------------------------------------------------------------
# GameEvent — live feed events pushed by the game server to the website DB.
#
# The Java game server connects to zelusrsps_db and INSERTs rows here whenever
# a notable event happens (rare drop, PvP kill, level milestone, etc.).
# The /livefeed endpoint reads this table as its first data source.
#
# event_type values mirror the frontend EVENT_STYLE keys:
#   pvp_kill | killstreak | rare_drop | level_up | tournament_win | bounty_claim
# ---------------------------------------------------------------------------
class GameEvent(Base):
    __tablename__ = "game_events"

    id         = Column(Integer,    primary_key=True, index=True, autoincrement=True)
    event_type = Column(String(30), nullable=False, index=True)
    username   = Column(String(50), nullable=False, index=True)
    message    = Column(String(500), nullable=False)
    timestamp  = Column(DateTime, default=func.now(), index=True)
