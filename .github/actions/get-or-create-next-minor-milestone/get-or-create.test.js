const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { getOrCreateNextMinorMilestone } = require('./get-or-create.js');
const { mockCore, mockContext, mockGithub } = require('./test-helpers.js');

// -- getOrCreateNextMinorMilestone --

describe('getOrCreateNextMinorMilestone', () => {
  it('should fail when lastClosed is null', async () => {
    await assert.rejects(
      () => getOrCreateNextMinorMilestone({
        github: mockGithub(),
        context: mockContext(),
        core: mockCore(),
        lastClosed: null,
      }),
      { message: /cannot determine next version/ },
    );
  });

  it('should increment patch version (v2.2.0 → v2.2.1)', async () => {
    const result = await getOrCreateNextMinorMilestone({
      github: mockGithub({
        createdMilestone: { number: 10, title: 'Release 2.2.1' },
      }),
      context: mockContext(),
      core: mockCore(),
      lastClosed: { number: 5, title: 'Release 2.2.0' },
    });

    assert.equal(result.title, 'Release 2.2.1');
  });

  it('should increment patch version (v2.2.9 → v2.2.10)', async () => {
    const result = await getOrCreateNextMinorMilestone({
      github: mockGithub({
        createdMilestone: { number: 11, title: 'Release 2.2.10' },
      }),
      context: mockContext(),
      core: mockCore(),
      lastClosed: { number: 5, title: 'Release 2.2.9' },
    });

    assert.equal(result.title, 'Release 2.2.10');
  });

  it('should return existing open milestone instead of creating', async () => {
    const existingMilestone = { number: 7, title: 'Release 2.2.1' };

    const result = await getOrCreateNextMinorMilestone({
      github: mockGithub({
        milestones: [existingMilestone],
      }),
      context: mockContext(),
      core: mockCore(),
      lastClosed: { number: 5, title: 'Release 2.2.0' },
    });

    assert.equal(result.number, 7);
    assert.equal(result.title, 'Release 2.2.1');
  });

  it('should create milestone when none exists', async () => {
    const core = mockCore();

    const result = await getOrCreateNextMinorMilestone({
      github: mockGithub({
        milestones: [],
        createdMilestone: { number: 12, title: 'Release 1.0.1' },
      }),
      context: mockContext(),
      core,
      lastClosed: { number: 1, title: 'Release 1.0.0' },
    });

    assert.equal(result.number, 12);
    assert.equal(result.title, 'Release 1.0.1');
    assert.ok(core.logs.some(l => l.includes('Creating milestone')));
  });
});
