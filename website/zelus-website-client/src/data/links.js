/** External links shared across the site. YouTube / X / forums are not linked anywhere until they exist. */
export const SITE_URL = 'https://karnps.com';
export const SITE_DOMAIN = 'karnps.com';

export const DISCORD_INVITE = 'sVrcatBdyG';
export const DISCORD_URL = `https://discord.gg/${DISCORD_INVITE}`;
export const YOUTUBE_URL = null;
export const TWITTER_URL = null;
export const FORUMS_URL = null;

/** Game revision shown in the status bar and hero. */
export const REVISION = 241;

/**
 * Direct downloads. These names are the real GitHub release repo and asset that the client CI uploads
 * (.github/workflows/build-client.yml in the client repo), so they keep their old spelling until that
 * workflow publishes a Karn-named asset.
 */
export const RELEASES_BASE = 'https://github.com/tamerab1/zelus-clientv1.0.1/releases/download/development-latest';
export const LAUNCHER_URL = `${RELEASES_BASE}/ZelusLauncher-windows.exe`;
export const JAR_URL = `${RELEASES_BASE}/client.jar`;
