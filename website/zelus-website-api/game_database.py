"""
game_database.py — Read-only connection to the NR 288 Kronos game MariaDB.

Database:  reason  (MariaDB / MySQL, port 3306)
Engine:    mysql+pymysql  →  pip install pymysql

The primary table for hiscores is `hs_users`, which uses camelCase column
names as written by the Java/Kotlin game server.  All ORM fields are mapped
back to snake_case for the Python/JSON API layer.

Environment variables (all optional — defaults match server.properties):
  GAME_DB_HOST      default: localhost
  GAME_DB_NAME      default: reason
  GAME_DB_USERNAME  default: root
  GAME_DB_PASSWORD  default: root
"""

import os

from sqlalchemy import BigInteger, Column, DateTime, Integer, String, create_engine
from sqlalchemy.orm import declarative_base, sessionmaker

# ── Connection ────────────────────────────────────────────────────────────────
_host     = os.getenv("GAME_DB_HOST",     "localhost")
_db_name  = os.getenv("GAME_DB_NAME",     "reason")
_db_user  = os.getenv("GAME_DB_USERNAME", "root")
_db_pass  = os.getenv("GAME_DB_PASSWORD", "root")

GAME_DB_URL = f"mysql+pymysql://{_db_user}:{_db_pass}@{_host}:3306/{_db_name}"

game_engine = create_engine(
    GAME_DB_URL,
    pool_pre_ping=True,       # detect stale connections before using them
    pool_recycle=3600,        # recycle connections every hour (MariaDB drops idle ones)
)
GameSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=game_engine)
GameBase = declarative_base()


# ── ORM model: hs_users ───────────────────────────────────────────────────────
# The game server writes camelCase column names.  SQLAlchemy `name=` kwargs
# map them to Python-friendly snake_case attribute names on this class.
#
# Column layout (matches NR 288 Kronos Hiscores.java):
#   user_id, username, difficulty (game mode), mode (ironman variant)
#   totalLevel, totalXp
#   attackXp … constructionXp  (23 skills, same order as StatType.java)
# ---------------------------------------------------------------------------
class HiscoreUser(GameBase):
    __tablename__ = "hs_users"

    # Primary key and identity
    user_id    = Column(Integer,     primary_key=True, name="user_id")
    username   = Column(String(50),  name="username")
    difficulty = Column(String(20),  name="difficulty")   # experience rate label
    mode       = Column(String(30),  name="mode")         # STANDARD | IRONMAN | …

    # Aggregate totals
    total_level      = Column(Integer,    name="totalLevel")
    total_experience = Column(BigInteger, name="totalXp")

    # 23 individual skills (camelCase in DB, snake_case on the ORM object)
    attack_xp        = Column(BigInteger, name="attackXp")
    defence_xp       = Column(BigInteger, name="defenceXp")
    strength_xp      = Column(BigInteger, name="strengthXp")
    hitpoints_xp     = Column(BigInteger, name="hitpointsXp")
    ranged_xp        = Column(BigInteger, name="rangedXp")
    prayer_xp        = Column(BigInteger, name="prayerXp")
    magic_xp         = Column(BigInteger, name="magicXp")
    cooking_xp       = Column(BigInteger, name="cookingXp")
    woodcutting_xp   = Column(BigInteger, name="woodcuttingXp")
    fletching_xp     = Column(BigInteger, name="fletchingXp")
    fishing_xp       = Column(BigInteger, name="fishingXp")
    firemaking_xp    = Column(BigInteger, name="firemakingXp")
    crafting_xp      = Column(BigInteger, name="craftingXp")
    smithing_xp      = Column(BigInteger, name="smithingXp")
    mining_xp        = Column(BigInteger, name="miningXp")
    herblore_xp      = Column(BigInteger, name="herbloreXp")
    agility_xp       = Column(BigInteger, name="agilityXp")
    thieving_xp      = Column(BigInteger, name="thievingXp")
    slayer_xp        = Column(BigInteger, name="slayerXp")
    farming_xp       = Column(BigInteger, name="farmingXp")
    runecrafting_xp  = Column(BigInteger, name="runecraftingXp")
    hunter_xp        = Column(BigInteger, name="hunterXp")
    construction_xp  = Column(BigInteger, name="constructionXp")


# ── ORM model: pklog ─────────────────────────────────────────────────────────
# Maps the PK log table written by the NR 288 Kronos server.
# Table may not exist on every installation — all queries wrap in try/except.
class PlayerKillLog(GameBase):
    __tablename__ = "pklog"

    id        = Column(Integer,   primary_key=True, autoincrement=True)
    killer    = Column(String(50),  name="killer")
    victim    = Column(String(50),  name="victim")
    timestamp = Column(DateTime,    name="time")


# ── ORM model: drop_log ───────────────────────────────────────────────────────
# Maps the rare-drop log table written by the NR 288 Kronos server.
# Table may not exist on every installation — all queries wrap in try/except.
class DropLog(GameBase):
    __tablename__ = "drop_log"

    id         = Column(Integer,     primary_key=True, autoincrement=True)
    username   = Column(String(50),  name="username")
    item_id    = Column(Integer,     name="item_id",    nullable=True)
    item_name  = Column(String(100), name="item_name")
    item_value = Column(BigInteger,  name="item_value", nullable=True)
    timestamp  = Column(DateTime,    name="timestamp")


# ── Sortable column map ────────────────────────────────────────────────────────
# Maps the ?sort= query parameter the frontend sends to the actual ORM column.
# Used by the /hiscores endpoint to push ORDER BY down to the DB instead of
# sorting in Python (far more efficient on large tables).
HISCORE_SORT_COLUMNS: dict[str, object] = {
    "total_level":      HiscoreUser.total_level,
    "total_experience": HiscoreUser.total_experience,
    "attack_xp":        HiscoreUser.attack_xp,
    "defence_xp":       HiscoreUser.defence_xp,
    "strength_xp":      HiscoreUser.strength_xp,
    "hitpoints_xp":     HiscoreUser.hitpoints_xp,
    "ranged_xp":        HiscoreUser.ranged_xp,
    "prayer_xp":        HiscoreUser.prayer_xp,
    "magic_xp":         HiscoreUser.magic_xp,
    "cooking_xp":       HiscoreUser.cooking_xp,
    "woodcutting_xp":   HiscoreUser.woodcutting_xp,
    "fletching_xp":     HiscoreUser.fletching_xp,
    "fishing_xp":       HiscoreUser.fishing_xp,
    "firemaking_xp":    HiscoreUser.firemaking_xp,
    "crafting_xp":      HiscoreUser.crafting_xp,
    "smithing_xp":      HiscoreUser.smithing_xp,
    "mining_xp":        HiscoreUser.mining_xp,
    "herblore_xp":      HiscoreUser.herblore_xp,
    "agility_xp":       HiscoreUser.agility_xp,
    "thieving_xp":      HiscoreUser.thieving_xp,
    "slayer_xp":        HiscoreUser.slayer_xp,
    "farming_xp":       HiscoreUser.farming_xp,
    "runecrafting_xp":  HiscoreUser.runecrafting_xp,
    "hunter_xp":        HiscoreUser.hunter_xp,
    "construction_xp":  HiscoreUser.construction_xp,
}


# ── Session dependency ────────────────────────────────────────────────────────
def get_game_db():
    db = GameSessionLocal()
    try:
        yield db
    finally:
        db.close()
