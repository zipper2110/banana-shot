// Release age reminder (build-expiry-spec.md, "Release age reminder"; E10-S2).
// Each build stops 6 months after its build date. A newest build older than 4 months has less than 60 days left.
// Then this script opens one issue, so that the author makes a release or an emergency rebuild.

const INSTALLER_NAME = 'BananaShot-win-Setup.exe';
const TITLE = 'The newest release expires in less than 60 days. Make a release or an emergency rebuild.';
const MAX_AGE_MONTHS = 4;
const RELEASE_TAG = /^v\d+\.\d+\.\d+$/;

/**
 * The newest build of the published releases: the latest `created_at` of the installer asset. The upload date of the
 * installer is the build date, because the release workflow builds and uploads it in one run. Do not use the publish
 * date (a draft can wait) or the `created_at` of the release (the date of the commit). Null with no published release.
 */
function newestBuild(releases, installerName = INSTALLER_NAME) {
  let newest = null;
  for (const release of releases) {
    if (release.draft || !RELEASE_TAG.test(release.tag_name)) continue;
    for (const asset of release.assets || []) {
      if (asset.name !== installerName) continue;
      const created = new Date(asset.created_at);
      if (newest === null || created > newest.date) newest = { date: created, tag: release.tag_name };
    }
  }
  return newest;
}

/** True when more than 4 months passed from the build date to [now]. */
function isTooOld(buildDate, now) {
  const limit = new Date(buildDate.getTime());
  limit.setUTCMonth(limit.getUTCMonth() + MAX_AGE_MONTHS);
  return now > limit;
}

function issueBody(build) {
  return [
    `The newest build is \`${build.tag}\`. Its installer was built on ${build.date.toISOString().slice(0, 10)}.`,
    '',
    'Each build stops 6 months after its build date. Make a normal release, or do the emergency rebuild of',
    '`docs/release-checklist.md`. Close this issue after the new release is published.',
  ].join('\n');
}

async function run({ github, context, core, now = new Date() }) {
  const { owner, repo } = context.repo;
  const releases = await github.paginate(github.rest.repos.listReleases, { owner, repo, per_page: 100 });
  const build = newestBuild(releases);
  if (build === null) {
    core.info('No published release. Nothing to do.');
    return;
  }
  core.info(`Newest build: ${build.tag}, installer created ${build.date.toISOString()}.`);
  if (!isTooOld(build.date, now)) {
    core.info(`The build is younger than ${MAX_AGE_MONTHS} months. No issue.`);
    return;
  }
  const issues = await github.paginate(github.rest.issues.listForRepo, { owner, repo, state: 'open', per_page: 100 });
  if (issues.some((issue) => !issue.pull_request && issue.title === TITLE)) {
    core.info('An open issue with the same title exists. No second issue.');
    return;
  }
  const created = await github.rest.issues.create({ owner, repo, title: TITLE, body: issueBody(build) });
  core.warning(`Opened issue #${created.data.number}: ${TITLE}`);
}

module.exports = { INSTALLER_NAME, TITLE, newestBuild, isTooOld, run };
