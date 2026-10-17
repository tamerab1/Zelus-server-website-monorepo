import { useEffect, useState } from 'react';
import { DISCORD_INVITE } from '../data/links.js';

/**
 * Live Discord counts from the public invite endpoint ({ online, members, name }), refreshed every 5 minutes.
 * Stays null if Discord can't be reached, so callers simply hide the counter.
 */
export default function useDiscordInvite() {
  const [info, setInfo] = useState(null);
  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const res = await fetch(`https://discord.com/api/v10/invites/${DISCORD_INVITE}?with_counts=true`);
        if (!res.ok) return;
        const d = await res.json();
        if (!cancelled) {
          setInfo({
            online:  d.approximate_presence_count ?? null,
            members: d.approximate_member_count ?? null,
            name:    d.guild?.name ?? 'Zelus',
          });
        }
      } catch { /* offline or blocked: no counter */ }
    };
    load();
    const id = setInterval(load, 300_000);
    return () => { cancelled = true; clearInterval(id); };
  }, []);
  return info;
}
