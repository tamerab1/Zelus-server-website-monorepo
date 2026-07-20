/**
 * voteSites.js — Static configuration for each voting topsite.
 *
 * buildUrl(userId) embeds the player's website user ID into the topsite URL.
 * The user ID is used to match the vote callback back to the correct account.
 *
 * SECURITY NOTE: The user ID alone is not a secret — anyone who knows it can
 * open the vote URL.  The real protection is the 12-hour per-user + per-IP
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
     * Replace "zelus-rsps" with the slug RuneLocus assigns to your listing.
     * The callback= param tells RuneLocus to POST back to our /vote/callback
     * endpoint with the user ID when the vote is confirmed.
     */
    buildUrl: (userId) =>
      `https://www.runelocus.com/top-rsps-list/zelus-rsps/vote?callback=${userId}`,
    rewards: [
      '2 Vote Points',
      '2× Tome of Experience',
      '100 Donator Points',
    ],
  },
  {
    id:       'RSPS_LIST',
    name:     'RSPS-List',
    tagline:  'RS Private Server Rankings',
    icon:     '📜',
    /**
     * Replace "zelus" in u=zelus with the username/slug your listing uses.
     * The id= param passes the user ID through as the callback identifier.
     */
    buildUrl: (userId) =>
      `https://www.rsps-list.com/index.php?a=in&u=zelus&id=${userId}`,
    rewards: [
      '2 Vote Points',
      '2× Tome of Experience',
      '50 Donator Points',
    ],
  },
];

export default VOTE_SITES;
