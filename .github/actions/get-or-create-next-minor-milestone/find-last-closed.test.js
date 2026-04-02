const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { findLastClosedMilestone, REGEX_PATTERN } = require('./find-last-closed.js');
const { mockCore, mockContext, mockGithub } = require('./test-helpers.js');

// -- REGEX_PATTERN --

describe('REGEX_PATTERN', () => {
  it('should match Release X.Y.Z format', () => {
    assert.ok(REGEX_PATTERN.test('Release 2.2.0'));
    assert.ok(REGEX_PATTERN.test('Release 0.0.1'));
    assert.ok(REGEX_PATTERN.test('Release 10.20.30'));
  });

  it('should not match other formats', () => {
    assert.ok(!REGEX_PATTERN.test('v2.2.0'));
    assert.ok(!REGEX_PATTERN.test('Release 2.2'));
    assert.ok(!REGEX_PATTERN.test('2.2.0'));
    assert.ok(!REGEX_PATTERN.test('Release 2.2.0-beta'));
    assert.ok(!REGEX_PATTERN.test('Next Minor'));
  });

  it('should capture major, minor, patch groups', () => {
    const [, major, minor, patch] = 'Release 2.3.1'.match(REGEX_PATTERN);
    assert.equal(major, '2');
    assert.equal(minor, '3');
    assert.equal(patch, '1');
  });
});

// -- findLastClosedMilestone --

describe('findLastClosedMilestone', () => {
  it('should return null when no milestones exist', async () => {
    const result = await findLastClosedMilestone({
      github: mockGithub(),
      context: mockContext(),
      core: mockCore({ throwOnFail: true }),
    });

    assert.equal(result, null);
  });

  it('should return null when no milestone matches the pattern', async () => {
    const result = await findLastClosedMilestone({
      github: mockGithub({ milestones: [
        { number: 1, title: 'Next Minor' },
        { number: 2, title: 'Backlog' },
      ] }),
      context: mockContext(),
      core: mockCore({ throwOnFail: true }),
    });

    assert.equal(result, null);
  });

  it('should return the only matching milestone', async () => {
    const result = await findLastClosedMilestone({
      github: mockGithub({ milestones: [
        { number: 1, title: 'Next Minor' },
        { number: 2, title: 'Release 2.2.0' },
      ] }),
      context: mockContext(),
      core: mockCore({ throwOnFail: true }),
    });

    assert.equal(result.title, 'Release 2.2.0');
  });

  it('should return the highest semver milestone regardless of order', async () => {
    const result = await findLastClosedMilestone({
      github: mockGithub({ milestones: [
        { number: 5, title: 'Release 2.1.0' },
        { number: 3, title: 'Release 2.2.1' },
        { number: 8, title: 'Release 2.2.0' },
        { number: 1, title: 'Release 1.9.9' },
      ] }),
      context: mockContext(),
      core: mockCore({ throwOnFail: true }),
    });

    assert.equal(result.title, 'Release 2.2.1');
  });

  it('should compare major version correctly', async () => {
    const result = await findLastClosedMilestone({
      github: mockGithub({ milestones: [
        { number: 1, title: 'Release 1.99.99' },
        { number: 2, title: 'Release 3.0.0' },
        { number: 3, title: 'Release 2.50.50' },
      ] }),
      context: mockContext(),
      core: mockCore({ throwOnFail: true }),
    });

    assert.equal(result.title, 'Release 3.0.0');
  });
});



