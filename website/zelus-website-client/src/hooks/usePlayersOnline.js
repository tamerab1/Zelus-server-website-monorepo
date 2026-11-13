import { useSyncExternalStore } from 'react';

const API = import.meta.env.VITE_API_URL ?? 'http://localhost:8000';
const POLL_MS = 30_000;

/**
 * Live player count from GET /players/online -> { online, status }.
 * Returns { count, status }: status is 'checking' | 'online' | 'offline', count stays null until the first answer.
 * One shared poller for every component that uses the hook; it stops when none are mounted.
 */
let snapshot = { count: null, status: 'checking' };
const listeners = new Set();
let timer = null;

async function poll() {
  try {
    const res = await fetch(`${API}/players/online`);
    if (!res.ok) throw new Error('bad response');
    const data = await res.json();
    snapshot = { count: Number(data.online ?? 0), status: data.status === 'offline' ? 'offline' : 'online' };
  } catch {
    snapshot = { count: snapshot.count, status: 'offline' };
  }
  listeners.forEach(fn => fn());
}

function subscribe(fn) {
  listeners.add(fn);
  if (!timer) {
    poll();
    timer = setInterval(poll, POLL_MS);
  }
  return () => {
    listeners.delete(fn);
    if (listeners.size === 0) { clearInterval(timer); timer = null; }
  };
}

export default function usePlayersOnline() {
  return useSyncExternalStore(subscribe, () => snapshot);
}
