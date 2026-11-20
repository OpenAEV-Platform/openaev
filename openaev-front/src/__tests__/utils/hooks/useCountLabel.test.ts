import { cleanup, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import useCountLabel from '../../../utils/hooks/useCountLabel';

vi.mock('../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (key: string, values?: Record<string, string>) => (values
      ? key.replace('{count}', values.count)
      : `tr:${key}`),
  }),
}));

describe('useCountLabel', () => {
  afterEach(cleanup);

  it('given_one_should_useTheSingularKey', () => {
    // Arrange
    const { result } = renderHook(() => useCountLabel());

    // Act
    const label = result.current(1, '1 action selected', '{count} actions selected');

    // Assert
    expect(label).toBe('tr:1 action selected');
  });

  it.each([0, 2, 42])('given_%i_should_useThePluralKeyWithTheCount', (count) => {
    // Arrange
    const { result } = renderHook(() => useCountLabel());

    // Act
    const label = result.current(count, '1 action selected', '{count} actions selected');

    // Assert
    expect(label).toBe(`${count} actions selected`);
  });
});
