import { describe, expect, it } from 'vitest';

import sameValue from '../../../../../admin/components/threat_arsenal/customization/sameValue';

const argument = { key: 'a' };
const argumentWithEmptySeparator = {
  key: 'a',
  separator: '',
};
const ordered = {
  a: 1,
  b: 2,
};
const reordered = {
  b: 2,
  a: 1,
};

describe('sameValue', () => {
  it.each([
    ['empty values', '', null],
    ['an empty field and an absent one', [argumentWithEmptySeparator], [argument]],
    ['objects whose keys come in another order', ordered, reordered],
  ])('given_%s_should_beTheSame', (_, left, right) => {
    // Act & Assert
    expect(sameValue(left, right)).toBe(true);
  });

  it('given_anotherValue_should_differ', () => {
    // Act & Assert
    expect(sameValue(['Windows'], ['Linux'])).toBe(false);
  });
});
