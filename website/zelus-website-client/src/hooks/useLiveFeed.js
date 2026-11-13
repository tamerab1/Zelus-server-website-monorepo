import { useEffect, useRef, useState } from 'react';

const API = import.meta.env.VITE_API_URL ?? 'http://localhost:8000';
const POLL_MS = 7_000;

/**
 * Recent game events from GET /livefeed -> { events: [{ id, type, username, message, timestamp }] }.
 * The game server pushes these (rare drops, PvP kills, killstreaks, 99s, tournament wins, bounties).
 * Returns { events, connected, freshIds }: freshIds holds ids that arrived on the latest poll (for a highlight).
 */
export default function useLiveFeed(limit = 12) {
  const [events, setEvents] = useState([]);
  const [connected, setConnected] = useState(false);
  const [freshIds, setFreshIds] = useState(() => new Set());
  const known = useRef(new Set());

  useEffect(() => {
    let cancelled = false;
    let clearFresh = null;

    const load = async () => {
      try {
        const res = await fetch(`${API}/livefeed?limit=${limit}`);
        if (!res.ok) throw new Error('bad response');
        const data = await res.json();
        if (cancelled) return;
        setConnected(true);
        const incoming = data.events ?? [];
        const firstLoad = known.current.size === 0;
        const fresh = new Set(incoming.filter(e => !known.current.has(e.id)).map(e => e.id));
        incoming.forEach(e => known.current.add(e.id));
        setEvents(incoming);
        if (!firstLoad && fresh.size) {
          setFreshIds(fresh);
          clearTimeout(clearFresh);
          clearFresh = setTimeout(() => !cancelled && setFreshIds(new Set()), 2500);
        }
      } catch {
        if (!cancelled) setConnected(false);
      }
    };

    load();
    const id = setInterval(load, POLL_MS);
    return () => { cancelled = true; clearInterval(id); clearTimeout(clearFresh); };
  }, [limit]);

  return { events, connected, freshIds };
}

/** "2026-11-12T10:00:00Z" -> "4m ago" */
export function timeAgo(iso) {
  const diff = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000));
  if (diff < 60) return `${diff}s ago`;
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  return `${Math.floor(diff / 86400)}d ago`;
}
