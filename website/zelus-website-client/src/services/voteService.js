import { apiFetch } from './api.js';

/**
 * Submits a vote for a given site. Creates a 'pending' vote in the database.
 * @param {number} userId
 * @param {string} siteName     - e.g. 'RUNELOCUS' | 'RSPS_LIST'
 * @param {string} gameUsername - validated in-game character name
 */
export const submitVote = (userId, siteName, gameUsername) =>
  apiFetch('/vote/submit', {
    method: 'POST',
    body: JSON.stringify({ user_id: userId, site_name: siteName, game_username: gameUsername }),
  });

/**
 * Checks if a username exists as a game character.
 * @param {string} username
 * @returns {Promise<{exists: boolean}>}
 */
export const checkGameUsername = (username) =>
  apiFetch(`/game/check-username/${encodeURIComponent(username)}`);

/**
 * Returns per-site vote states for the user.
 * Each entry: { site_name, state: 'idle'|'pending'|'cooldown', seconds_remaining, vote_id }
 * @param {number} userId
 */
export const fetchVoteStatus = (userId) => apiFetch(`/votes/status/${userId}`);
