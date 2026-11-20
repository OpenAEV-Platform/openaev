import { describe, expect, it } from 'vitest';

import { diffRuns } from '../../../../components/common/update/textDiffRuns';

describe('diffRuns', () => {
  it.each([
    ['', 'nmap', []],
    ['scan a b', '(scan', [
      {
        token: 'scan ',
        changed: false,
      },
      {
        token: 'a b',
        changed: true,
      },
    ]],
  ])('splits %o compared with %o into runs', (text, other, runs) => {
    // Act & Assert
    expect(diffRuns(text, other)).toEqual(runs);
  });

  it('given_aTooLongText_should_markNothingChanged', () => {
    // Arrange
    const text = 'a '.repeat(201);

    // Act
    const runs = diffRuns(text, 'b');

    // Assert
    expect(runs.some(run => run.changed)).toBe(false);
    expect(runs.map(run => run.token).join('')).toBe(text);
  });
});
