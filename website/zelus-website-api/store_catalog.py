"""
store_catalog.py — Server-side store catalog (single source of truth for pricing).

Frontend prices are NEVER trusted; every checkout validates the package_id and
its price against this file before creating a Stripe/PayPal session.

Donor rank slugs are aligned with the NR 288 Kronos server's SecondaryGroup enum:
  DONATOR → SUPER_DONATOR → ELITE_DONATOR → NOBLE_DONATOR →
  GOLD_DONATOR → PLATINUM_DONATOR → LEGENDARY_DONATOR → SUPREME_DONATOR

The slug field MUST exactly match the `slug` / `id` sent by the React frontend
(storeItems.js / storePackages.js) — case-insensitive lookup via get_item().

tokens field:
  - For 'dp_*' items: the Donator Points balance credited to the player.
  - For ranks: Donator Points awarded on purchase (matches storePackages.js).
  - For packages, boosters, misc: 0 (handled server-side per slug).
"""

from dataclasses import dataclass


@dataclass(frozen=True)
class StoreItem:
    slug:      str
    name:      str
    price_usd: float
    tokens:    int          # Donator Points credited; 0 for non-dp items


# ── Catalog ────────────────────────────────────────────────────────────────────
CATALOG: dict[str, StoreItem] = {

    # ── Donator Points ─────────────────────────────────────────────────────────
    # tokens = exact DP credited to player's in-game balance.

    "dp_500":   StoreItem("dp_500",   "500 Donator Points",    5.00,    500),
    "dp_1200":  StoreItem("dp_1200",  "1,200 Donator Points", 10.00,   1200),
    "dp_3000":  StoreItem("dp_3000",  "3,000 Donator Points", 20.00,   3000),
    "dp_8000":  StoreItem("dp_8000",  "8,000 Donator Points", 50.00,   8000),
    "dp_20000": StoreItem("dp_20000", "20,000 Donator Points",100.00, 20000),

    # ── Donor Ranks (8 tiers — matches NR 288 SecondaryGroup enum) ────────────
    # Slug must match SecondaryGroup name in lowercase with underscores.
    # tokens = Donator Points awarded on rank purchase (mirrors storePackages.js).
    #
    #  Rank               Price     DP       Drop boost (server-side)
    #  ──────────────     ───────   ──────   ────────────────────────
    #  DONATOR            $10       100      +2.50 %
    #  SUPER_DONATOR      $25       275      +2.75 %
    #  ELITE_DONATOR      $50       600      +3.00 %
    #  NOBLE_DONATOR      $75       950      +3.20 %
    #  GOLD_DONATOR       $100      1 300    +3.50 %
    #  PLATINUM_DONATOR   $150      2 000    +4.00 %
    #  LEGENDARY_DONATOR  $200      2 750    +5.00 %
    #  SUPREME_DONATOR    $300      4 500    +7.00 %

    "donator":           StoreItem("donator",           "Donator",           10.00,   100),
    "super_donator":     StoreItem("super_donator",     "Super Donator",     25.00,   275),
    "elite_donator":     StoreItem("elite_donator",     "Elite Donator",     50.00,   600),
    "noble_donator":     StoreItem("noble_donator",     "Noble Donator",     75.00,   950),
    "gold_donator":      StoreItem("gold_donator",      "Gold Donator",     100.00,  1300),
    "platinum_donator":  StoreItem("platinum_donator",  "Platinum Donator", 150.00,  2000),
    "legendary_donator": StoreItem("legendary_donator", "Legendary Donator",200.00,  2750),
    "supreme_donator":   StoreItem("supreme_donator",   "Supreme Donator",  300.00,  4500),

    # ── Packages ───────────────────────────────────────────────────────────────
    # Themed bundles — server applies bundle contents by slug.

    "pkg_starter_pvm":       StoreItem("pkg_starter_pvm",       "Starter PvM Package",     15.00,  0),
    "pkg_ironman_headstart":  StoreItem("pkg_ironman_headstart",  "Ironman Head-Start",      20.00,  0),
    "pkg_ultimate_skiller":   StoreItem("pkg_ultimate_skiller",   "Ultimate Skiller Bundle", 35.00,  0),
    "pkg_wilderness_warlord": StoreItem("pkg_wilderness_warlord", "Wilderness Warlord Pack", 40.00,  0),
    "pkg_raiders_arsenal":    StoreItem("pkg_raiders_arsenal",    "Raider's Arsenal",        60.00,  0),
    "pkg_rng_god":            StoreItem("pkg_rng_god",            "RNG God Bundle",          90.00,  0),
    "pkg_supreme_endgame":    StoreItem("pkg_supreme_endgame",    "Supreme Endgame Bundle", 150.00,  0),

    # ── Boosters ───────────────────────────────────────────────────────────────
    # Timed scrolls — server activates the buff by slug for the named duration.

    "scroll_xp_1h":      StoreItem("scroll_xp_1h",      "Double XP Scroll (1 hr)",       3.00,  0),
    "scroll_xp_24h":     StoreItem("scroll_xp_24h",     "Double XP Scroll (24 hr)",      7.00,  0),
    "scroll_xp_7d":      StoreItem("scroll_xp_7d",      "Double XP Scroll (7 days)",    20.00,  0),
    "scroll_dr_1h":      StoreItem("scroll_dr_1h",      "+50% Drop Rate Scroll (1 hr)",  4.00,  0),
    "scroll_dr_24h":     StoreItem("scroll_dr_24h",     "+50% Drop Rate Scroll (24 hr)", 10.00,  0),
    "scroll_pk_1h":      StoreItem("scroll_pk_1h",      "2× PK Points Scroll (1 hr)",    3.00,  0),
    "scroll_pk_24h":     StoreItem("scroll_pk_24h",     "2× PK Points Scroll (24 hr)",   8.00,  0),
    "scroll_pet_1h":     StoreItem("scroll_pet_1h",     "Pet Bonus Scroll (1 hr)",        5.00,  0),
    "scroll_slayer_24h": StoreItem("scroll_slayer_24h", "3× Slayer Points Scroll (24 hr)", 8.00, 0),

    # ── Miscellaneous ──────────────────────────────────────────────────────────

    "mystery_box":       StoreItem("mystery_box",       "Mystery Box",           5.00,   0),
    "super_mystery_box": StoreItem("super_mystery_box", "Super Mystery Box",    15.00,   0),
    "grand_promo_box":   StoreItem("grand_promo_box",   "Grand Promo Box",      30.00,   0),
    "pet_box":           StoreItem("pet_box",           "Exclusive Pet Box",    18.00,   0),
}


def get_item(slug: str) -> StoreItem | None:
    """
    Case-insensitive lookup by slug.  Returns None if the slug is not in the
    catalog — the checkout endpoint must reject unknown slugs with a 400 error.
    """
    if not slug:
        return None
    return CATALOG.get(slug.lower().strip())
