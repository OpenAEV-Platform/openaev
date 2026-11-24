import { renderHook } from '@testing-library/react';
import { type ReactNode } from 'react';
import { IntlProvider } from 'react-intl';
import { describe, expect, it } from 'vitest';

import useCountLabel from '../../../utils/hooks/useCountLabel';

const wrapper = ({ children }: { children: ReactNode }) => (
  <IntlProvider locale="en" defaultLocale="en" onError={() => {}}>{children}</IntlProvider>
);

describe('useCountLabel', () => {
  it.each([
    [1, '1 inject'],
    [0, '0 injects'],
    [3, '3 injects'],
  ])('given %i should read "%s"', (count, expected) => {
    // Arrange
    const { result } = renderHook(() => useCountLabel(), { wrapper });

    // Act
    const label = result.current(count, '1 inject', '{count} injects');

    // Assert
    expect(label).toBe(expected);
  });
});
