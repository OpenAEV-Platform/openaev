import { describe, expect, it } from 'vitest';

import quietControlSpacing from '../../components/quietControlSpacing';

const entries = Object.entries(quietControlSpacing);
const pullBacks = entries.filter(([, value]) => value.marginLeft === -4);
const exceptions = entries.filter(([, value]) => value.marginLeft === 0);

describe('quiet control spacing', () => {
  it('pulls neighbouring quiet controls together', () => {
    expect(pullBacks.length).toBeGreaterThan(0);
    for (const [selector] of pullBacks) {
      expect(selector).toContain('+');
      expect(selector).toContain('.border-0');
    }
  });

  it('cancels the pull inside a stacked column', () => {
    // A number field draws its two stepper arrows as quiet buttons one above the
    // other; an inline pull-back moves the lower one out of its own column.
    const stacked = exceptions.filter(([selector]) => selector.startsWith('.flex-col > '));
    expect(stacked).toHaveLength(pullBacks.length);
  });

  it('cancels the pull inside runs that are flush by construction', () => {
    const flush = exceptions.filter(([selector]) => selector.includes('[role="tablist"]'));
    expect(flush).toHaveLength(pullBacks.length);
  });

  it('keeps every exception more specific than the rule it overrides', () => {
    // Same selector text plus a prefix: the cascade can only resolve in the
    // exception's favour if it is strictly longer on the same classes.
    for (const [selector] of pullBacks) {
      expect(exceptions.some(([other]) => other.endsWith(selector) && other.length > selector.length)).toBe(true);
    }
  });
});
