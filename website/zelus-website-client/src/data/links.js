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
 * Direct downloads: the rolling "development-latest" release of the client repo, published by its
 * .github/workflows/build-client.yml (beta 2026-11-18: Karn Beta launchers for Windows, macOS and Linux). The repo
 * keeps its old name until it is renamed at launch; GitHub redirects the old URL after a rename.
 */
export const RELEASES_BASE = 'https://github.com/tamerab1/zelus-clientv1.0.1/releases/download/development-latest';
export const LAUNCHER_URL = `${RELEASES_BASE}/KarnBeta-windows.exe`;
export const MAC_ARM_URL = `${RELEASES_BASE}/KarnBeta-macos-arm64.dmg`;
export const MAC_INTEL_URL = `${RELEASES_BASE}/KarnBeta-macos-x64.dmg`;
export const LINUX_APPIMAGE_URL = `${RELEASES_BASE}/KarnBeta-linux-x86_64.AppImage`;
export const LINUX_TAR_URL = `${RELEASES_BASE}/KarnBeta-linux-x64.tar.gz`;
export const LAUNCHER_JAR_URL = `${RELEASES_BASE}/karn-beta-launcher.jar`;
export const JAR_URL = `${RELEASES_BASE}/client.jar`;
