# Rev 241 migration dry run -- production copy (2026-10-25)

Read-only snapshot taken with tar / mysqldump / mongodump, no restart. Stored outside the repo
and outside OneDrive: `C:\Users\tamer\zelus-prod-snapshots\20261025\` (contains player data).
Full per-path reports (`saves_dryrun.md`, `db_dryrun.md`) are in that folder.

## Saves -- READY
- 3,069 JSON files (372 player saves, 2,697 attribute / friend-list / global files); 0 unreadable.
- Every path holding a colliding id is classified (31 paths; rules in `rules.json`). 0 invariant violations.
- 721 item id fields change, plus 690 item-id map keys (`uniqueDrops`, `weaponAutocastIds`).
- **183 of 372 accounts change**; 189 hold no colliding id.
- Custom varp slots 4828-4836 and custom-varbit savedConfigs keys: 0 values on prod (checked directly as well).
- Collection logs: all 372 `collected` maps are empty (progress lives in `uniqueDrops`).
- `.json.bak-*` backups are not migrated (left as rev 231 backups).

## MariaDB (`reason`) -- READY
- Item-id tables: tradepost_offers 11 rows / 0 colliding, tradepost_collections 1 / 0, tradepost_history 64 / **2 rows (1 id)**; the rest are empty.
- No remapped id is already present in its column.

## Mongo (`reason.players`) -- rebuild
- 373 documents mirroring the player saves (same fields); rebuilt from the migrated files rather than migrated separately.
