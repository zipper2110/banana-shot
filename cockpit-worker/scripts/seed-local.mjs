// Fills the LOCAL D1 databases of `wrangler dev` with made-up data, so that the page has something to show.
// Never use it with --remote. Run: npm run dev:seed
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const DAY = 86_400_000;
const HOUR = 3_600_000;
const today = Math.floor(Date.now() / DAY) * DAY;
const releaseDay = today - 40 * DAY;
const wrangler = join('node_modules', '.bin', process.platform === 'win32' ? 'wrangler.cmd' : 'wrangler');
const dir = join('.wrangler', 'seed');
mkdirSync(dir, { recursive: true });

let seed = 7;
function random() {
  seed = (seed * 16807) % 2147483647;
  return (seed - 1) / 2147483646;
}
const pick = list => list[Math.floor(random() * list.length)];
const chance = p => random() < p;
const int = (min, max) => min + Math.floor(random() * (max - min + 1));
const quote = value => value === null ? 'NULL' : `'${String(value).replaceAll("'", "''")}'`;

function run(database, file) {
  execFileSync(wrangler, ['d1', 'execute', database, '--local', '--file', file], { stdio: ['ignore', 'ignore', 'inherit'], shell: process.platform === 'win32' });
}

for (const database of ['bananashot-analytics', 'bananashot-feedback', 'bananashot-cockpit']) {
  execFileSync(wrangler, ['d1', 'migrations', 'apply', database, '--local'], { stdio: 'inherit', shell: process.platform === 'win32' });
}

// ---------- Analytics sessions ----------

const sessions = ['DELETE FROM analytics_session;'];
let id = 0;
for (let day = today - 89 * DAY; day <= today; day += DAY) {
  const sinceRelease = (day - releaseDay) / DAY;
  const base = sinceRelease < 0 ? 0.3 : Math.min(26, 3 + sinceRelease * 0.6);
  const weekend = [0, 6].includes(new Date(day).getUTCDay()) ? 1.5 : 1;
  const count = Math.max(0, Math.round(base * weekend * (0.7 + random() * 0.6)));
  for (let index = 0; index < count; index++) {
    id++;
    const received = day + int(6, 22) * HOUR;
    const version = sinceRelease < 0 ? '1.0.0-SNAPSHOT' : (day > today - 14 * DAY && chance(0.7) ? '1.0.1' : chance(0.04) ? '1.1.0-SNAPSHOT' : '1.0.0');
    const level = chance(0.78) ? 'extended' : 'essential';
    const first = chance(sinceRelease < 7 ? 0.5 : 0.22);
    const counters = {};
    counters[first ? 'session_n_1' : pick(['session_n_2_5', 'session_n_2_5', 'session_n_6_20', 'session_n_21p'])] = 1;
    if (chance(version === '1.0.0' ? 0.04 : 0.015)) counters.unclean_exit = 1;
    if (chance(0.02)) counters.uncaught_error = int(1, 2);
    const active = Math.round(Math.exp(random() * 8.6));
    if (level === 'extended') {
      counters.tab_projects = 1;
      counters.tab_s_projects = int(5, 60);
      if (chance(first ? 0.6 : 0.4)) counters.project_created = 1;
      if (chance(0.7)) counters.project_opened = int(1, 3);
      if (chance(0.85)) { counters.tab_points = int(1, 3); counters.tab_s_points = Math.round(active * 0.5); counters.point_added = int(1, 80); }
      if (chance(0.3)) counters.point_deleted = int(1, 6);
      if (chance(0.35)) counters.point_favorited = int(1, 12);
      if (chance(0.2)) counters.comment_added = int(1, 5);
      if (chance(0.55)) { counters.tab_scoring = 1; counters.tab_s_scoring = Math.round(active * 0.3); counters.score_recorded = int(5, 120); }
      if (chance(0.25)) { counters.tab_colors = 1; counters.tab_s_colors = int(20, 300); counters.color_changed = int(1, 8); }
      if (chance(0.15)) { counters.tab_crop_rotate = 1; counters.tab_s_crop_rotate = int(10, 200); counters.crop_rotate_changed = int(1, 3); }
      if (chance(0.3)) { counters.tab_stats = 1; counters.tab_s_stats = int(10, 200); }
      if (chance(0.12)) counters.help_opened = 1;
      if (chance(0.03)) counters.video_open_failed = 1;
      if (chance(0.4)) {
        counters.tab_export = 1;
        counters.tab_s_export = int(20, 400);
        const encoder = pick(['nvenc', 'nvenc', 'software', 'qsv', 'amf']);
        const started = int(1, 2);
        counters[`export_started_${encoder}`] = started;
        const failed = chance(encoder === 'amf' ? 0.25 : 0.06) ? 1 : 0;
        const cancelled = !failed && chance(0.08) ? 1 : 0;
        const completed = started - failed - cancelled;
        if (completed > 0) {
          counters[`export_completed_${encoder}`] = completed;
          const video = completed * int(900, 3600);
          counters[`export_video_s_${encoder}`] = video;
          counters[`export_run_s_${encoder}`] = Math.round(video / (encoder === 'software' ? 1.4 : 4.5));
        }
        if (failed) {
          counters[`export_failed_${encoder}`] = 1;
          counters[pick(['export_fail_ffmpeg_exit', 'export_fail_ffmpeg_exit', 'export_fail_output_write', 'export_fail_source_missing', 'export_fail_other'])] = 1;
        }
        if (cancelled) counters[`export_cancelled_${encoder}`] = 1;
        if (chance(0.85)) counters.export_opt_scoreboard = started;
        if (chance(0.5)) counters.export_opt_idle_trim = started;
        if (chance(0.3)) counters.export_opt_favorites_only = started;
        if (chance(0.2)) counters.export_opt_stats_card = started;
        if (chance(0.15)) counters.export_opt_comments = started;
        counters[pick(['export_res_1080', 'export_res_1080', 'export_res_1080', 'export_res_720', 'export_res_2160', 'export_res_1440'])] = started;
      }
    }
    // B-44: version 1.0.1 sends schema 3 with the session attributes (extended level only).
    const schema = version === '1.0.0' || version === '1.0.0-SNAPSHOT' ? 2 : 3;
    const attributes = {};
    if (schema === 3 && level === 'extended') {
      attributes.theme = pick(['dark', 'dark', 'dark', 'mid', 'light']);
      attributes.accent = chance(0.2) ? 'custom' : 'default';
      attributes.language = 'en';
      attributes.sport = counters.project_created || counters.project_opened
        ? pick([['tennis'], ['tennis'], ['tennis'], ['padel'], ['tennis', 'padel']])
        : [];
    }
    sessions.push(`INSERT INTO analytics_session (session_id, first_received_at, last_received_at, schema_version, notice_version, level, app_version, os_family, snapshot, final, duration_s, active_s, counters, attributes) VALUES (${quote(`seed-${id}`)}, ${received}, ${received + HOUR}, ${schema}, ${schema - 1}, ${quote(level)}, ${quote(version)}, ${quote(chance(0.97) ? 'windows' : 'other')}, 3, 1, ${active + int(0, 1800)}, ${active}, ${quote(JSON.stringify(counters))}, ${quote(JSON.stringify(attributes))});`);
  }
}
sessions.push(`INSERT OR REPLACE INTO analytics_retention_status (id, ran_at, deleted_count, oldest_received_at) VALUES (1, ${today + 3 * HOUR}, 0, ${today - 89 * DAY});`);
writeFileSync(join(dir, 'analytics.sql'), sessions.join('\n'));
run('bananashot-analytics', join(dir, 'analytics.sql'));

// ---------- Feedback ----------

const messages = {
  problem: ['The export stops at 87% every time.', 'The video is black in the preview after I rotate it.', 'The app did not start after the update.', 'Scoreboard shows the wrong set after a tiebreak.'],
  idea: ['Please add padel scoring.', 'A keyboard shortcut to jump to the next point would be great.', 'Can the scoreboard show player photos?', 'Export straight to YouTube, please!'],
  question: ['Does it work on a Mac?', 'How do I undo a deleted point?', 'Can I use a GoPro video with 4K 60 fps?'],
  other: ['Love the app, thanks!', 'Great tool for our club.'],
};
const errors = ['Export failed\n\nffmpeg exited with code 1', 'Export failed\n\nffmpeg exited with code -22', 'Cannot open the video\n\nNo video stream', 'Autosave failed\n\nAccessDeniedException'];
const reports = ['DELETE FROM feedback_report;'];
for (let index = 0; index < 34; index++) {
  const topic = pick(['problem', 'problem', 'idea', 'idea', 'question', 'other']);
  const received = releaseDay + Math.floor(random() * (today + DAY - releaseDay));
  const error = topic === 'problem' && chance(0.6) ? pick(errors) : null;
  reports.push(`INSERT INTO feedback_report (report_id, received_at, topic, message, email, app_version, os_name, os_version, java_version, error, has_log, delivered) VALUES (${quote(`00000000-0000-4000-8000-${String(index).padStart(12, '0')}`)}, ${received}, ${quote(topic)}, ${quote(pick(messages[topic]))}, ${chance(0.4) ? quote('someone@example.test') : 'NULL'}, ${quote(chance(0.5) ? '1.0.1' : '1.0.0')}, 'Windows 11', '10.0.26100', '25.0.1', ${quote(error)}, ${error ? 1 : 0}, ${index === 3 ? 0 : 1});`);
}
writeFileSync(join(dir, 'feedback.sql'), reports.join('\n'));
run('bananashot-feedback', join(dir, 'feedback.sql'));

// ---------- Download snapshots ----------

const snapshots = ['DELETE FROM download_snapshot;', 'DELETE FROM job_status;'];
let setup = 0, other = 0;
for (let day = releaseDay - DAY; day <= today; day += DAY) {
  const sinceRelease = (day - releaseDay) / DAY;
  if (sinceRelease >= 0) {
    setup += Math.round((sinceRelease < 3 ? 25 : 8 + sinceRelease * 0.4) * (0.6 + random() * 0.8));
    other += chance(0.3) ? 1 : 0;
  }
  if (sinceRelease === 17) continue; // A missed day: the next day gets both days.
  const date = new Date(day).toISOString().slice(0, 10);
  snapshots.push(`INSERT INTO download_snapshot (day, release_tag, asset_name, download_count, published_at, taken_at) VALUES ('${date}', 'v1.0.0', 'BananaShot-win-Setup.exe', ${setup}, '${new Date(releaseDay).toISOString()}', ${day + 23 * HOUR});`);
  snapshots.push(`INSERT INTO download_snapshot (day, release_tag, asset_name, download_count, published_at, taken_at) VALUES ('${date}', 'v1.0.0', 'BananaShot-1.0.0-full.nupkg', ${other}, '${new Date(releaseDay).toISOString()}', ${day + 23 * HOUR});`);
}
snapshots.push(`INSERT INTO job_status (name, ran_at, ok, message) VALUES ('download_snapshot', ${Date.now() - 20 * 60000}, 1, '2 release files');`);
writeFileSync(join(dir, 'cockpit.sql'), snapshots.join('\n'));
run('bananashot-cockpit', join(dir, 'cockpit.sql'));

console.log(`Seeded ${id} sessions, 34 reports, and the download snapshots.`);
