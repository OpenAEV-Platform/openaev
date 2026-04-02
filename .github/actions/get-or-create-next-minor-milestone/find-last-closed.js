const REGEX_PATTERN = /^Release (\d+)\.(\d+)\.(\d+)$/;

async function findLastClosedMilestone({ github, context, core }) {
  let page = 1;
  let lastClosed = null;

  while (true) {
    const { data } = await github.rest.issues.listMilestones({
      owner: context.repo.owner,
      repo: context.repo.repo,
      state: 'closed',
      per_page: 100,
      page,
    });

    if (data.length === 0) break;

    for (const m of data) {
      if (!REGEX_PATTERN.test(m.title)) continue;
      const [, major, minor, patch] = m.title.match(REGEX_PATTERN).map(Number);
      if (
        !lastClosed ||
        major > lastClosed.major ||
        (major === lastClosed.major && minor > lastClosed.minor) ||
        (major === lastClosed.major && minor === lastClosed.minor && patch > lastClosed.patch)
      ) {
        lastClosed = { milestone: m, major, minor, patch };
      }
    }

    page++;
  }

  if (!lastClosed) {
    core.info('No closed milestone matching "Release X.Y.Z" found.');
    return null;
  }

  core.info(`Last closed milestone: #${lastClosed.milestone.number} - ${lastClosed.milestone.title}`);
  return lastClosed.milestone;
}

module.exports = { findLastClosedMilestone, REGEX_PATTERN };

