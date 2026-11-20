import { describe, expect, it } from 'vitest';

import sameValue from '../../../../../admin/components/threat_arsenal/customization/sameValue';

describe('sameValue', () => {
  it.each([
    ['empty values', '', null],
    ['an absent value and an empty list item', [{ key: 'a', separator: '' }], [{ key: 'a' }]],
    ['objects whose keys come in another order', { a: 1, b: 2 }, { b: 2, a: 1 }],
  ])('given_%s_should_beTheSame', (_, left, right) => {
    // Act & Assert
    expect(sameValue(left, right)).toBe(true);
  });

  it('given_anotherValue_should_differ', () => {
    // Act & Assert
    expect(sameValue(['Windows'], ['Linux'])).toBe(false);
  });
});
