/**
 * The closed lists of counter keys (docs/analytics/design.md, "Counters" and "Levels"). The Worker refuses a summary
 * with another key, and an essential summary with a key that is not essential. A test checks both lists against
 * analytics-contract/v1/counter-keys.json.
 */
const TABS = ['projects', 'points', 'colors', 'crop_rotate', 'scoring', 'stats', 'export'];
const ENCODERS = ['software', 'nvenc', 'amf', 'qsv'];
const EXPORT_RESULTS = ['started', 'completed', 'failed', 'cancelled', 'interrupted', 'run_s', 'video_s'];
const FAIL_REASONS = ['source_missing', 'output_write', 'process_start', 'ffmpeg_exit', 'other'];
const EXPORT_OPTIONS = ['scoreboard', 'comments', 'stats_card', 'favorites_only', 'idle_trim'];
const RESOLUTIONS = ['720', '1080', '1440', '2160', 'other'];

export const ESSENTIAL_COUNTER_KEYS: ReadonlySet<string> = new Set([
  'unclean_exit', 'uncaught_error', 'session_n_1', 'session_n_2_5', 'session_n_6_20', 'session_n_21p',
]);

export const COUNTER_KEYS: ReadonlySet<string> = new Set([
  ...ESSENTIAL_COUNTER_KEYS,
  ...TABS.map(tab => `tab_${tab}`),
  ...TABS.map(tab => `tab_s_${tab}`),
  'project_created', 'project_opened', 'video_open_failed', 'point_added', 'point_deleted', 'point_favorited',
  'comment_added', 'score_recorded', 'color_changed', 'crop_rotate_changed', 'help_opened',
  ...EXPORT_RESULTS.flatMap(result => ENCODERS.map(encoder => `export_${result}_${encoder}`)),
  ...FAIL_REASONS.map(reason => `export_fail_${reason}`),
  ...EXPORT_OPTIONS.map(option => `export_opt_${option}`),
  ...RESOLUTIONS.map(resolution => `export_res_${resolution}`),
]);

export const MAX_COUNTER_VALUE = 1_000_000;
