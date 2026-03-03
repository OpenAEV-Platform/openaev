const { REGEX_PATTERN } = require('./find-last-closed.js');

async function getOrCreateNextMinorMilestone({ github, context, core, lastClosed }) {
  if (!lastClosed) {
    core.setFailed('No closed "Release X.Y.Z" milestone found — cannot determine next version.');
    return;
  }

  // Increment patch version (X.Y.Z → X.Y.Z+1)
  const [, major, minor, patch] = lastClosed.title.match(REGEX_PATTERN);
  const nextTitle = `Release ${major}.${minor}.${Number(patch) + 1}`;

  core.info(`Target milestone: ${nextTitle}`);

  let page = 1;
  while (true) {
    const { data } = await github.rest.issues.listMilestones({
      owner: context.repo.owner,
      repo: context.repo.repo,
      state: 'open',
      per_page: 100,
      page,
    });

    if (data.length === 0) break;

    const found = data.find(m => m.title === nextTitle);
    if (found) {
      core.info(`Milestone already exists: #${found.number} - ${found.title}`);
      return found;
    }
    page++;
  }

  // Create if it does not exist
  core.info(`Creating milestone: ${nextTitle}`);
  const { data: created } = await github.rest.issues.createMilestone({
    owner: context.repo.owner,
    repo: context.repo.repo,
    title: nextTitle,
  });

  core.info(`Created milestone: #${created.number} - ${created.title}`);
  return created;
}

module.exports = { getOrCreateNextMinorMilestone };

