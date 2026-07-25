"""
database.py — Website database connection (MySQL via mysql+pymysql).

Connection is built dynamically from split environment variables:
    DB_HOST  — hostname or IP of the MySQL server  (default: localhost)
    DB_PORT  — MySQL port                           (default: 3306)
    DB_USER  — database username                    (default: zelus_web)
    DB_PASS  — database password                    (required)
    DB_NAME  — database/schema name                 (default: zelusrsps_db)

The old single-key DATABASE_URL (PostgreSQL) is no longer used.
Requires: pip install pymysql
"""

import os

from dotenv import load_dotenv

load_dotenv()

from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

# ── Read split env vars ────────────────────────────────────────────────────────
_DB_HOST = os.getenv("DB_HOST", "localhost")
_DB_PORT = os.getenv("DB_PORT", "3306")
_DB_USER = os.getenv("DB_USER", "zelus_web")
_DB_PASS = os.getenv("DB_PASS", "")
_DB_NAME = os.getenv("DB_NAME", "zelusrsps_db")

if not _DB_PASS:
    raise RuntimeError(
        "DB_PASS environment variable is not set. "
        "Add it to your .env file before starting the API."
    )

# ── Build connection URL ───────────────────────────────────────────────────────
SQLALCHEMY_DATABASE_URL = (
    f"mysql+pymysql://{_DB_USER}:{_DB_PASS}@{_DB_HOST}:{_DB_PORT}/{_DB_NAME}"
)

# ── Engine ─────────────────────────────────────────────────────────────────────
engine = create_engine(
    SQLALCHEMY_DATABASE_URL,
    pool_pre_ping=True,    # detect and discard stale connections before use
    pool_recycle=3600,     # recycle connections every hour (MySQL drops idle ones)
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

# ── Session dependency (used by FastAPI Depends) ───────────────────────────────
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
