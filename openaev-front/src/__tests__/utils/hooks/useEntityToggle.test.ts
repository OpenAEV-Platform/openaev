import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import useEntityToggle from '../../../utils/hooks/useEntityToggle';

interface Item {
  item_id: string;
  item_name: string;
}

const first: Item = {
  item_id: 'a',
  item_name: 'first',
};
const second: Item = {
  item_id: 'b',
  item_name: 'second',
};
const third: Item = {
  item_id: 'c',
  item_name: 'third',
};

describe('useEntityToggle', () => {
  describe('when toggling an array of entities', () => {
    it('given_entities_should_index_the_new_ones_by_id', () => {
      // -- ARRANGE --
      const { result } = renderHook(() => useEntityToggle<Item>('item', [first, second, third]));

      // -- ACT --
      act(() => result.current.onToggleEntity([first, second] as unknown as Item));

      // -- ASSERT --
      expect(result.current.selectedElements).toEqual({
        a: first,
        b: second,
      });
    });

    it('given_already_selected_entities_should_keep_them_untouched', () => {
      // -- ARRANGE --
      const { result } = renderHook(() => useEntityToggle<Item>('item', [first, second, third]));
      act(() => result.current.onToggleEntity(first));

      // -- ACT --
      act(() => result.current.onToggleEntity([first, third] as unknown as Item));

      // -- ASSERT --
      expect(result.current.selectedElements).toEqual({
        a: first,
        c: third,
      });
    });

    it('given_force_remove_should_drop_those_entities', () => {
      // -- ARRANGE --
      const { result } = renderHook(() => useEntityToggle<Item>('item', [first, second, third]));
      act(() => result.current.onToggleEntity(first));

      // -- ACT --
      act(() => result.current.onToggleEntity([second, third] as unknown as Item, undefined, [first]));

      // -- ASSERT --
      expect(result.current.selectedElements).toEqual({
        b: second,
        c: third,
      });
    });
  });
});
