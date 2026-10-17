/** External links shared by the home page sections. YouTube / X / forums are not linked anywhere until they exist. */
export const DISCORD_INVITE = 'sVrcatBdyG';
export const DISCORD_URL = `https://discord.gg/${DISCORD_INVITE}`;
export const YOUTUBE_URL = null;
export const TWITTER_URL = null;
export const FORUMS_URL = null;
/** Featured video on the home page: a YouTube video id, or null for the "coming soon" card. */
export const FEATURED_VIDEO_ID = null;

/** Direct downloads (same GitHub release the Download page uses). */
const RELEASES_BASE = 'https://github.com/tamerab1/zelus-clientv1.0.1/releases/download/development-latest';
export const LAUNCHER_URL = `${RELEASES_BASE}/ZelusLauncher-windows.exe`;
export const JAR_URL = `${RELEASES_BASE}/client.jar`;
