import { describe, expect, it } from 'vitest';
import { loadAppUsage } from '../src/app-usage';
import { loadSite, loadWorkerHealth } from '../src/cloudflare';
import { SETUP_ASSET, dailyIncreases, loadDownloads, takeSnapshot } from '../src/downloads';
import { errorLine, loadFeedback } from '../src/feedback';
import { makePeriod } from '../src/period';
import { NOW, addReport, addSession, addSnapshot, at, makeEnv } from './fixtures';

const OTHER = 'BananaShot-1.0.0-full.nupkg';

describe('download history', () => {
  it('uses the first snapshot day as the base line and counts the increase of each later day', () => {
    const rows = [
      { day: '2026-10-01', release_tag: 'v1.0.0', asset_name: SETUP_ASSET, download_count: 10, published_at: null },
      { day: '2026-10-02', release_tag: 'v1.0.0', asset_name: SETUP_ASSET, download_count: 14, published_at: null },
      { day: '2026-10-02', release_tag: 'v1.0.0', asset_name: OTHER, download_count: 1, published_at: null },
      { day: '2026-10-04', release_tag: 'v1.0.0', asset_name: SETUP_ASSET, download_count: 20, published_at: null },
      { day: '2026-10-04', release_tag: 'v1.1.0', asset_name: SETUP_ASSET, download_count: 3, published_at: null },
    ];
    const increases = dailyIncreases(rows);
    expect(increases.has('2026-10-01')).toBe(false);
    expect(increases.get('2026-10-02')).toEqual({ setup: 4, other: 1 });
    // No snapshot on 10-03: 10-04 gets the downloads of both days. A new release counts from 0.
    expect(increases.get('2026-10-04')).toEqual({ setup: 9, other: 0 });
  });

  it('never counts a negative increase', () => {
    const increases = dailyIncreases([
      { day: '2026-10-01', release_tag: 'v1', asset_name: SETUP_ASSET, download_count: 10, published_at: null },
      { day: '2026-10-02', release_tag: 'v1', asset_name: SETUP_ASSET, download_count: 8, published_at: null },
    ]);
    expect(increases.get('2026-10-02')).toEqual({ setup: 0, other: 0 });
  });

  it('writes the totals of the app releases only, and updates the row of the same day', async () => {
    const env = makeEnv({ GITHUB_TOKEN: 'token' });
    let count = 5;
    const requests: Request[] = [];
    const fetcher = (async (input: RequestInfo, init?: RequestInit) => {
      requests.push(new Request(input, init));
      return Response.json([
        { tag_name: 'v1.0.0', draft: false, published_at: '2026-10-01T00:00:00Z', assets: [{ name: SETUP_ASSET, download_count: count }] },
        { tag_name: 'v1.1.0', draft: true, published_at: null, assets: [{ name: SETUP_ASSET, download_count: 1 }] },
        { tag_name: 'natives-2026-09', draft: false, published_at: '2026-09-26T00:00:00Z', assets: [{ name: 'ffmpeg.zip', download_count: 14 }] },
      ]);
    }) as typeof fetch;

    expect(await takeSnapshot(env, NOW, fetcher)).toBe(1);
    count = 7;
    await takeSnapshot(env, NOW + 3_600_000, fetcher);

    expect(env.cockpit.db.prepare('SELECT day, release_tag, download_count FROM download_snapshot').all()).toEqual([
      { day: '2026-10-06', release_tag: 'v1.0.0', download_count: 7 },
    ]);
    expect(requests[0].url).toBe('https://api.github.com/repos/owner/repo/releases?per_page=100');
    expect(requests[0].headers.get('authorization')).toBe('Bearer token');
    expect(env.cockpit.db.prepare('SELECT ok, message FROM job_status').get()).toEqual({ ok: 1, message: '1 release files' });
  });

  it('keeps the error of a failed snapshot for the page', async () => {
    const env = makeEnv();
    const fetcher = (async () => new Response('limit', { status: 403 })) as typeof fetch;
    await expect(takeSnapshot(env, NOW, fetcher)).rejects.toThrow('GitHub API returned 403');
    expect(env.cockpit.db.prepare('SELECT ok, message FROM job_status').get()).toEqual({ ok: 0, message: 'GitHub API returned 403' });
  });

  it('gives the daily and weekly series, the totals, and the releases', async () => {
    const env = makeEnv();
    addSnapshot(env, '2026-09-28', { [SETUP_ASSET]: 0 });
    addSnapshot(env, '2026-09-29', { [SETUP_ASSET]: 4 });
    addSnapshot(env, '2026-10-05', { [SETUP_ASSET]: 10, [OTHER]: 2 });
    addSnapshot(env, '2026-10-06', { [SETUP_ASSET]: 13, [OTHER]: 2 });

    const downloads = await loadDownloads(env, makePeriod(7, NOW));

    expect(downloads.trackingSince).toBe('2026-09-28');
    expect(downloads.daily.map(point => point.setup)).toEqual([null, null, null, null, null, 6, 3]);
    expect(downloads.total).toEqual({ setup: 9, prevSetup: 4, other: 2 });
    expect(downloads.weekly.slice(-2)).toEqual([
      { date: '2026-09-28', setup: 4, other: 0 },
      { date: '2026-10-05', setup: 9, other: 2 },
    ]);
    expect(downloads.weekly[0]).toEqual({ date: '2026-07-20', setup: null, other: null });
    expect(downloads.releases).toEqual([{ tag: 'v1.0.0', publishedAt: '2026-09-01T00:00:00Z', setup: 13, other: 2 }]);
  });
});

describe('app usage', () => {
  it('counts the sessions of the period and of the previous period, without smoke and development rows', async () => {
    const env = makeEnv();
    addSession(env, { receivedAt: at('2026-10-06'), counters: { session_n_1: 1, unclean_exit: 1 } });
    addSession(env, { receivedAt: at('2026-10-05'), level: 'essential', counters: { session_n_2_5: 1 } });
    addSession(env, { receivedAt: at('2026-09-29'), counters: { session_n_1: 1 } });
    addSession(env, { receivedAt: at('2026-10-06'), version: 'synthetic-smoke' });
    addSession(env, { receivedAt: at('2026-10-06'), version: '1.0.1-SNAPSHOT' });

    const usage = await loadAppUsage(env, makePeriod(7, NOW), false);

    expect(usage.totals).toMatchObject({ sessions: 2, first_sessions: 1, extended_sessions: 1, unclean_exits: 1 });
    expect(usage.prevTotals).toMatchObject({ sessions: 1, first_sessions: 1 });
    expect(usage.daily.at(-1)).toMatchObject({ date: '2026-10-06', sessions: 1 });
    expect(usage.daily[0]).toMatchObject({ date: '2026-09-30', sessions: 0 });
    expect(usage.weekly.at(-1)).toMatchObject({ date: '2026-10-05', sessions: 2 });
    expect(usage.weekly.at(-2)).toMatchObject({ date: '2026-09-28', sessions: 1 });
    expect(usage.versions.map(row => row.version)).toEqual(['1.0.0']);

    const withDev = await loadAppUsage(env, makePeriod(7, NOW), true);
    expect(withDev.totals.sessions).toBe(3);
  });

  it('counts the features and the exports of the extended sessions only', async () => {
    const env = makeEnv();
    addSession(env, { receivedAt: at('2026-10-06'), counters: {
      tab_points: 1, point_added: 12, export_started_nvenc: 2, export_completed_nvenc: 1, export_failed_nvenc: 1,
      export_fail_ffmpeg_exit: 1, export_run_s_nvenc: 60, export_video_s_nvenc: 300,
    } });
    addSession(env, { receivedAt: at('2026-10-06'), counters: { tab_points: 1, point_added: 3 } });
    addSession(env, { receivedAt: at('2026-10-06'), level: 'essential', counters: { unclean_exit: 1 } });

    const usage = await loadAppUsage(env, makePeriod(7, NOW), false);

    expect(usage.counters.find(row => row.key === 'point_added')).toEqual({ key: 'point_added', sessions: 2, total: 15 });
    expect(usage.counters.some(row => row.key === 'unclean_exit')).toBe(false);
    expect(usage.encoders[0]).toEqual({ encoder: 'nvenc', started: 2, completed: 1, failed: 1, cancelled: 0, interrupted: 0, speed: 5 });
    expect(usage.totals).toMatchObject({ exports_started: 2, exports_completed: 1, exports_failed: 1, unclean_exits: 1 });
    expect(usage.sessionShape).toMatchObject({ sessions: 3, extended_sessions: 2 });
  });
});

describe('feedback', () => {
  it('lists the reports without the email address and groups the errors by their first line', async () => {
    const env = makeEnv();
    addReport(env, { receivedAt: at('2026-10-06'), email: 'user@example.test', error: 'Export failed\n\nffmpeg exited with code 1', delivered: false });
    addReport(env, { receivedAt: at('2026-10-05'), error: '\nExport failed\nother details' });
    addReport(env, { receivedAt: at('2026-10-04'), topic: 'idea', message: 'x'.repeat(700) });
    addReport(env, { receivedAt: at('2026-09-29'), topic: 'question' });
    addReport(env, { receivedAt: at('2026-10-06'), version: 'synthetic-smoke' });

    const feedback = await loadFeedback(env, makePeriod(7, NOW), false);

    expect(feedback.total).toBe(3);
    expect(feedback.prevTotal).toBe(1);
    expect(feedback.undelivered).toBe(1);
    expect(feedback.byTopic).toEqual({ problem: 2, idea: 1, question: 0, other: 0 });
    expect(feedback.topErrors).toEqual([{ error: 'Export failed', reports: 2 }]);
    expect(feedback.reports[0]).toMatchObject({ hasEmail: true, errorLine: 'Export failed', delivered: false });
    expect(JSON.stringify(feedback)).not.toContain('user@example.test');
    expect(feedback.reports[2]).toMatchObject({ truncated: true });
    expect(feedback.reports[2].message).toHaveLength(600);
  });

  it('counts the reports of the site form and marks them', async () => {
    const env = makeEnv();
    addReport(env, { receivedAt: at('2026-10-06'), version: 'website', topic: 'question' });
    addReport(env, { receivedAt: at('2026-10-05') });

    const feedback = await loadFeedback(env, makePeriod(7, NOW), false);

    expect(feedback.byTopic).toMatchObject({ problem: 1, question: 1 });
    expect(feedback.reports.map(report => report.fromSite)).toEqual([true, false]);
  });

  it('takes the first line with text as the error line', () => {
    expect(errorLine('\n  \n Title \nmore')).toBe('Title');
  });
});

describe('Cloudflare numbers', () => {
  // A pasted token often ends with a line break.
  const configured = { CF_API_TOKEN: 'abcdefghijklmnopqrstuvwxyz0123456789ABCD\r\n', CF_ACCOUNT_ID: 'account1', CF_WEB_ANALYTICS_SITE_TAG: 'site1', WORKER_SCRIPTS: 'bananashot-analytics, bad name' };

  it('refuses a token with an invalid format before it calls Cloudflare', async () => {
    const fetcher = (async () => { throw new Error('must not be called'); }) as typeof fetch;
    await expect(loadSite(makeEnv({ ...configured, CF_API_TOKEN: 'x' }), makePeriod(7, NOW), fetcher)).rejects.toThrow('CF_API_TOKEN has an invalid format (1 characters)');
  });

  it('tells that the source is not configured', async () => {
    await expect(loadSite(makeEnv(), makePeriod(7, NOW))).rejects.toThrow('Not configured');
  });

  it('reads the site numbers', async () => {
    const bodies: string[] = [];
    const fetcher = (async (_input: RequestInfo, init?: RequestInit) => {
      bodies.push(String(init?.body));
      return Response.json({ data: { viewer: { accounts: [{
        daily: [{ count: 9, sum: { visits: 4 }, dimensions: { date: '2026-10-06' } }],
        pages: [{ count: 6, sum: { visits: 3 }, dimensions: { requestPath: '/' } }],
        referrers: [{ count: 2, sum: { visits: 2 }, dimensions: { refererHost: '' } }],
        countries: [], devices: [],
        prev: [{ count: 3, sum: { visits: 2 } }],
      }] } } });
    }) as typeof fetch;

    const site = await loadSite(makeEnv(configured), makePeriod(7, NOW), fetcher);

    expect(site.totals).toEqual({ visits: 4, pageViews: 9 });
    expect(site.prevTotals).toEqual({ visits: 2, pageViews: 3 });
    expect(site.daily.at(-1)).toEqual({ date: '2026-10-06', visits: 4, pageViews: 9 });
    expect(site.pages).toEqual([{ name: '/', visits: 3, pageViews: 6 }]);
    expect(site.referrers[0].name).toBe('(none)');
    expect(site.clampedToDays).toBeNull();
    expect(bodies[0]).toContain('siteTag: \\"site1\\"');
  });

  it('filters by the host of the site when no site tag is set', async () => {
    const bodies: string[] = [];
    const fetcher = (async (_input: RequestInfo, init?: RequestInit) => {
      bodies.push(String(init?.body));
      return Response.json({ data: { viewer: { accounts: [{ daily: [], pages: [], referrers: [], countries: [], devices: [], prev: [] }] } } });
    }) as typeof fetch;
    await loadSite(makeEnv({ ...configured, CF_WEB_ANALYTICS_SITE_TAG: undefined }), makePeriod(7, NOW), fetcher);
    expect(bodies[0]).toContain('requestHost: \\"banana-shot-editor.app\\"');
  });

  it('falls back to 30 days when Cloudflare refuses the long range', async () => {
    let calls = 0;
    const fetcher = (async () => {
      calls++;
      if (calls === 1) return Response.json({ data: null, errors: [{ message: 'time range is too large' }] });
      return Response.json({ data: { viewer: { accounts: [{ daily: [], pages: [], referrers: [], countries: [], devices: [] }] } } });
    }) as typeof fetch;

    const site = await loadSite(makeEnv(configured), makePeriod(90, NOW), fetcher);

    expect(site.clampedToDays).toBe(30);
    expect(site.prevTotals).toBeNull();
  });

  it('tries a lost connection once more, and tells the time of the failure', async () => {
    let calls = 0;
    const lost = (async () => { calls++; throw new Error('Network connection lost.'); }) as typeof fetch;
    await expect(loadSite(makeEnv(configured), makePeriod(7, NOW), lost)).rejects.toThrow(/Network connection lost\. \(after \d+\.\d s, try 2\)/);
    expect(calls).toBe(2);

    calls = 0;
    const flaky = (async () => {
      calls++;
      if (calls === 1) throw new Error('Network connection lost.');
      return Response.json({ data: { viewer: { accounts: [{ daily: [], pages: [], referrers: [], countries: [], devices: [], prev: [] }] } } });
    }) as typeof fetch;
    expect((await loadSite(makeEnv(configured), makePeriod(7, NOW), flaky)).totals).toEqual({ visits: 0, pageViews: 0 });
  });

  it('shows the error text of a refused request', async () => {
    const refused = (async () => Response.json({ success: false, errors: [{ code: 9106, message: 'Authentication failed (status: 400)' }] }, { status: 400 })) as typeof fetch;
    await expect(loadSite(makeEnv(configured), makePeriod(7, NOW), refused)).rejects.toThrow('Cloudflare API returned 400: Authentication failed (status: 400)');
  });

  it('sums the Worker requests and errors, and ignores invalid script names', async () => {
    const bodies: string[] = [];
    const fetcher = (async (_input: RequestInfo, init?: RequestInit) => {
      bodies.push(String(init?.body));
      return Response.json({ data: { viewer: { accounts: [{ groups: [
        { count: 0, sum: { requests: 10, errors: 1 }, dimensions: { date: '2026-10-06', scriptName: 'bananashot-analytics' } },
        { count: 0, sum: { requests: 5, errors: 0 }, dimensions: { date: '2026-10-05', scriptName: 'bananashot-analytics' } },
      ] }] } } });
    }) as typeof fetch;

    const health = await loadWorkerHealth(makeEnv(configured), makePeriod(7, NOW), fetcher);

    expect(health.scripts).toEqual([{ name: 'bananashot-analytics', requests: 15, errors: 1 }]);
    expect(health.daily.at(-1)).toEqual({ date: '2026-10-06', requests: 10, errors: 1 });
    expect(bodies[0]).not.toContain('bad name');
  });
});
