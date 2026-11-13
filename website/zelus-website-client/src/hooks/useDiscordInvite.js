import { useSyncExternalStore } from 'react';
import { DISCORD_INVITE } from '../data/links.js';

/**
 * Live Discord counts from the public invite endpoint ({ online, members, name }), refreshed every 5 minutes.
 * Stays null if Discord can't be reached, so callers simply hide the counter.
 * One shared fetch for every component that uses the hook.
 */
let snapshot = null;
const listeners = new Set();
let timer = null;

async function load() {
  try {
    const res = await fetch(`https://discord.com/api/v10/invites/${DISCORD_INVITE}?with_counts=true`);
    if (!res.ok) return;
    const d = await res.json();
    snapshot = {
      online:  d.approximate_presence_count ?? null,
      members: d.approximate_member_count ?? null,
      name:    d.guild?.name ?? 'Karn',
    };
    listeners.forEach(fn => fn());
  } catch { /* offline or blocked: no counter */ }
}

function subscribe(fn) {
  listeners.add(fn);
  if (!timer) {
    load();
    timer = setInterval(load, 300_000);
  }
  return () => {
    listeners.delete(fn);
    if (listeners.size === 0) { clearInterval(timer); timer = null; }
  };
}

export default function useDiscordInvite() {
  return useSyncExternalStore(subscribe, () => snapshot);
}
