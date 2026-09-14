import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { afterEach, describe, expect, it, vi } from 'vitest';

import SearchInput from '../../components/SearchFilter';

afterEach(cleanup);

const renderSearch = (props: Partial<React.ComponentProps<typeof SearchInput>> = {}) => render(
  <IntlProvider locale="en" defaultLocale="en" onError={() => {}}>
    <SearchInput {...props} />
  </IntlProvider>,
);

describe('SearchFilter', () => {
  it('renders the library search field with a compact size for the small variant', () => {
    renderSearch({ variant: 'small' });
    const box = screen.getByRole('searchbox', { name: 'Search these results' });
    expect(box).toHaveAttribute('placeholder', 'Search these results...');
    expect(screen.getByRole('search', { name: 'Search these results' }).className).toContain('h-7');
    expect(document.querySelector('.MuiTextField-root')).toBeNull();
  });

  it('debounces typing and applies a clear at once', () => {
    vi.useFakeTimers();
    const onChange = vi.fn();
    renderSearch({
      onChange,
      keyword: '',
    });
    const box = screen.getByRole('searchbox');
    fireEvent.change(box, { target: { value: 'adv' } });
    expect(onChange).not.toHaveBeenCalled();
    vi.advanceTimersByTime(500);
    expect(onChange).toHaveBeenCalledWith('adv');
    fireEvent.click(screen.getByRole('button', { name: 'Clear search' }));
    expect(onChange).toHaveBeenLastCalledWith('');
    expect(box).toHaveValue('');
    vi.useRealTimers();
  });
});
