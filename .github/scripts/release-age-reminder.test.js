// Tests of release-age-reminder.js. Run: node --test .github/scripts/release-age-reminder.test.js
const test = require('node:test');
const assert = require('node:assert');
const { INSTALLER_NAME, TITLE, newestBuild, isTooOld, run } = require('./release-age-reminder.js');

function release(tag, installerDate, { draft = false, name = INSTALLER_NAME } = {}) {
  return { tag_name: tag, draft, assets: [{ name, created_at: installerDate }, { name: 'releases.win.json', created_at: '2030-01-01T00:00:00Z' }] };
}

function fakeGitHub(releases, issues) {
  const created = [];
  const github = {
    rest: { repos: { listReleases: 'releases' }, issues: { listForRepo: 'issues', create: async (args) => { created.push(args); return { data: { number: 7 } }; } } },
    paginate: async (endpoint) => (endpoint === 'releases' ? releases : issues),
  };
  const logs = [];
  const core = { info: (m) => logs.push(m), warning: (m) => logs.push(m) };
  return { github, core, created, logs, context: { repo: { owner: 'o', repo: 'r' } } };
}

test('the newest build is the latest installer date of the published version releases', () => {
  const build = newestBuild([
    release('v1.0.0', '2026-01-10T10:00:00Z'),
    release('v1.0.1', '2026-04-02T10:00:00Z'),
    release('v1.1.0', '2026-09-01T10:00:00Z', { draft: true }),
    release('natives-2026-09', '2026-09-05T10:00:00Z'),
    release('v1.0.2', '2026-08-01T10:00:00Z', { name: 'Other.exe' }),
  ]);
  assert.strictEqual(build.tag, 'v1.0.1');
  assert.strictEqual(build.date.toISOString(), '2026-04-02T10:00:00.000Z');
});

test('no published release gives no build', () => {
  assert.strictEqual(newestBuild([]), null);
  assert.strictEqual(newestBuild([release('v1.0.0', '2026-01-10T10:00:00Z', { draft: true })]), null);
});

test('the limit is 4 months after the build date', () => {
  const build = new Date('2026-01-31T10:00:00Z');
  assert.strictEqual(isTooOld(build, new Date('2026-05-31T10:00:00Z')), false);
  assert.strictEqual(isTooOld(build, new Date('2026-05-31T10:00:01Z')), true);
});

test('an old build opens one issue with the title of the spec', async () => {
  const f = fakeGitHub([release('v1.0.0', '2026-01-10T10:00:00Z')], []);
  await run({ ...f, now: new Date('2026-06-01T00:00:00Z') });
  assert.strictEqual(f.created.length, 1);
  assert.strictEqual(f.created[0].title, TITLE);
  assert.match(f.created[0].body, /v1\.0\.0/);
});

test('a second run with the open issue opens no second issue', async () => {
  const f = fakeGitHub([release('v1.0.0', '2026-01-10T10:00:00Z')], [{ title: TITLE }]);
  await run({ ...f, now: new Date('2026-06-01T00:00:00Z') });
  assert.strictEqual(f.created.length, 0);
});

test('a pull request with the same title does not count as the issue', async () => {
  const f = fakeGitHub([release('v1.0.0', '2026-01-10T10:00:00Z')], [{ title: TITLE, pull_request: {} }]);
  await run({ ...f, now: new Date('2026-06-01T00:00:00Z') });
  assert.strictEqual(f.created.length, 1);
});

test('a young build and no release open no issue', async () => {
  const young = fakeGitHub([release('v1.0.0', '2026-03-10T10:00:00Z')], []);
  await run({ ...young, now: new Date('2026-06-01T00:00:00Z') });
  assert.strictEqual(young.created.length, 0);

  const none = fakeGitHub([], []);
  await run({ ...none, now: new Date('2026-06-01T00:00:00Z') });
  assert.strictEqual(none.created.length, 0);
});
