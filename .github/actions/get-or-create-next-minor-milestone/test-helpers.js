function mockCore({ throwOnFail = false } = {}) {
  const logs = [];
  let failedMessage = null;
  return {
    info: (msg) => logs.push(msg),
    setFailed: (msg) => {
      if (throwOnFail) throw new Error(msg);
      failedMessage = msg;
    },
    logs,
    get failedMessage() { return failedMessage; },
  };
}

function mockContext() {
  return { repo: { owner: 'test-owner', repo: 'test-repo' } };
}

function mockGithub({ milestones = [], createdMilestone = null } = {}) {
  return {
    rest: {
      issues: {
        listMilestones: async ({ page }) => {
          const perPage = 100;
          const start = (page - 1) * perPage;
          return { data: milestones.slice(start, start + perPage) };
        },
        createMilestone: async ({ title }) => {
          return { data: createdMilestone || { number: 99, title } };
        },
      },
    },
  };
}

module.exports = { mockCore, mockContext, mockGithub };

