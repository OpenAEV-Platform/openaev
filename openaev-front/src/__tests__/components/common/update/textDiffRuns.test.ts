import { describe, expect, it } from 'vitest';

import { diffRuns, lineDiff } from '../../../../components/common/update/textDiffRuns';

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

describe('lineDiff', () => {
  it('numbers the unchanged, removed and added lines, a changed line paired with the one it replaces', () => {
    // Act
    const lines = lineDiff('echo start\nnmap -A\nexit', 'echo start\nnmap -sV\nexit');

    // Assert
    expect(lines).toEqual([
      {
        kind: 'same',
        text: 'echo start',
        beforeNo: 1,
        afterNo: 1,
      },
      {
        kind: 'removed',
        text: 'nmap -A',
        beforeNo: 2,
        counterpart: 'nmap -sV',
      },
      {
        kind: 'added',
        text: 'nmap -sV',
        afterNo: 2,
        counterpart: 'nmap -A',
      },
      {
        kind: 'same',
        text: 'exit',
        beforeNo: 3,
        afterNo: 3,
      },
    ]);
  });

  it('given_anEmptySide_should_onlyAddOrRemove', () => {
    // Act & Assert
    expect(lineDiff('', 'a\nb').map(line => line.kind)).toEqual(['added', 'added']);
    expect(lineDiff('a', '').map(line => line.kind)).toEqual(['removed']);
  });
});
