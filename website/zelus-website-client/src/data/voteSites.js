/**
 * voteSites.js — Static configuration for each voting topsite.
 *
 * buildUrl(username) embeds the player's in-game username into the topsite
 * URL. The username is used to match the vote callback back to the player.
 *
 * SECURITY NOTE: The username alone is not a secret — anyone who knows it can
 * open the vote URL.  The real protection is the 12-hour per-username + per-IP
 * cooldown enforced server-side in /vote/submit.
 *
 * HOW TO UPDATE URLS:
 *   After submitting Zelus to each topsite, replace the listing slug/ID below
 *   with the slug/ID assigned to your server's listing.
 *   - RuneLocus: the slug in the URL of your listing page
 *   - RSPS-List: the `u=` parameter from your listing's vote link
 */

const VOTE_SITES = [
  {
    id:       'RUNELOCUS',
    name:     'RuneLocus',
    tagline:  'Top 100 RSPS Network',
    icon:     '👑',
    /**
     * Zelus's actual RuneLocus listing slug/vote link (confirmed live 2026-08-11):
     * https://www.runelocus.com/top-rsps-list/zelus/vote/?id={username}
     * The id= param passes the in-game username through as the callback identifier.
     */
    buildUrl: (username) =>
      `https://www.runelocus.com/top-rsps-list/zelus/vote/?id=${encodeURIComponent(username)}`,
    // Matches CommandHandlerRegular.java's ::claimvote reward logic exactly.
    votePoints: 2,
    rewards: [
      '2 Vote Points',
      'Donator rank bonus points',
    ],
  },
  {
    id:       'RSPS_LIST',
    name:     'RSPS-List',
    tagline:  'RS Private Server Rankings',
    icon:     '📜',
    /**
     * u=mrboolt is Zelus's actual RSPS-List listing account username (confirmed
     * live 2026-08-11 after registering the server there).
     * The id= param passes the in-game username through as the callback identifier.
     */
    buildUrl: (username) =>
      `https://www.rsps-list.com/index.php?a=in&u=mrboolt&id=${encodeURIComponent(username)}`,
    // Matches CommandHandlerRegular.java's ::claimvote reward logic exactly.
    votePoints: 1,
    rewards: [
      '1 Vote Point',
      'Donator rank bonus points',
    ],
  },
];

export default VOTE_SITES;
