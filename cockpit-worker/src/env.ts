export interface Env {
  /** The database of the analytics Worker. Read only. */
  ANALYTICS_DB: D1Database;
  /** The database of the feedback Worker. Read only. */
  FEEDBACK_DB: D1Database;
  /** The own database of the cockpit: the download snapshots. */
  COCKPIT_DB: D1Database;
  /** The static files of the page. */
  ASSETS: Fetcher;
  /** The password of the page (secret). Without it, the Worker refuses each request. */
  COCKPIT_PASSWORD?: string;
  /** Only in `.dev.vars`: "true" turns off the password for requests to localhost. Never set it for a deploy. */
  COCKPIT_DEV_NO_AUTH?: string;
  /** The GitHub repository with the releases, for example `zipper2110/banana-shot`. */
  GITHUB_REPO: string;
  /** Optional GitHub token (secret). Without it, the GitHub API allows 60 requests in one hour. */
  GITHUB_TOKEN?: string;
  /** Cloudflare API token with "Account Analytics: Read" (secret). */
  CF_API_TOKEN?: string;
  CF_ACCOUNT_ID?: string;
  /** Optional site tag of Web Analytics. Without it, the site numbers are filtered by SITE_HOST. */
  CF_WEB_ANALYTICS_SITE_TAG?: string;
  /** The host of the site. Default `banana-shot-editor.app`. */
  SITE_HOST?: string;
  /** Comma-separated Worker names for the health section. */
  WORKER_SCRIPTS?: string;
}

/** The result of one data source. A failure in one source does not stop the other sources. */
export type Section<T> = { ok: true; data: T } | { ok: false; error: string };

export async function section<T>(load: () => Promise<T>): Promise<Section<T>> {
  try {
    return { ok: true, data: await load() };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    if (!(error instanceof NotConfigured)) console.error(`Section failed: ${message}`);
    return { ok: false, error: message };
  }
}

export class NotConfigured extends Error {}
