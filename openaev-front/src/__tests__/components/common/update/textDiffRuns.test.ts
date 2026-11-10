import { describe, expect, it } from 'vitest';

import { lineDiff } from '../../../../components/common/update/textDiffRuns';

describe('lineDiff', () => {
  it('marks a changed line as removed then added, numbered on each side, and pairs them', () => {
    // Arrange / Act
    const lines = lineDiff('a\nb\nc', 'a\nB\nc');

    // Assert
    expect(lines.map(line => line.kind)).toEqual(['same', 'removed', 'added', 'same']);
    expect(lines[1]).toMatchObject({
      text: 'b',
      beforeNo: 2,
      afterNo: undefined,
      counterpart: 'B',
    });
    expect(lines[2]).toMatchObject({
      text: 'B',
      beforeNo: undefined,
      afterNo: 2,
      counterpart: 'b',
    });
    expect(lines[3]).toMatchObject({
      beforeNo: 3,
      afterNo: 3,
    });
  });

  it('lists lines only added or only removed without a counterpart', () => {
    // Arrange / Act
    const added = lineDiff('a', 'a\nnew');
    const removed = lineDiff('a\nold', 'a');

    // Assert
    expect(added[1]).toMatchObject({
      kind: 'added',
      text: 'new',
    });
    expect(added[1].counterpart).toBeUndefined();
    expect(removed[1]).toMatchObject({
      kind: 'removed',
      text: 'old',
    });
    expect(removed[1].counterpart).toBeUndefined();
  });

  it('treats an empty side as no lines', () => {
    // Arrange / Act / Assert
    expect(lineDiff('', 'x').map(line => line.kind)).toEqual(['added']);
    expect(lineDiff('x', '')).toEqual([expect.objectContaining({ kind: 'removed' })]);
  });
});
