"""
net_utils.py — shared request-level helpers used across main.py and routers/*.
"""
from fastapi import Request
from slowapi.util import get_remote_address


# ── Real client IP (Cloudflare / reverse proxy aware) ──────────────────────────
# The API sits behind Cloudflare (and possibly an nginx hop) on the VPS, so
# request.client.host / slowapi's get_remote_address() resolve to the proxy's
# IP, not the caller's -- that's what was causing unrelated players to collide
# on the same IP-based vote cooldown. Cloudflare sets CF-Connecting-IP itself
# at the edge (not spoofable as long as the origin firewall only accepts
# connections from Cloudflare's IP ranges, which is how this box is deployed),
# so it's preferred; X-Forwarded-For's left-most entry is the fallback for any
# hop that isn't behind Cloudflare (e.g. local/dev).
def get_real_client_ip(request: Request) -> str | None:
    cf_ip = request.headers.get("CF-Connecting-IP")
    if cf_ip:
        return cf_ip.strip()
    xff = request.headers.get("X-Forwarded-For")
    if xff:
        first = xff.split(",")[0].strip()
        if first:
            return first
    return get_remote_address(request)
