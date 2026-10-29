import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';

import TopBarIconLink from '../../../../admin/components/nav/TopBarIconLink';

// The selected background this control must show, as the design system's own
// token. Asserted as a token reference, never as a literal colour.
const SELECTED_BACKGROUND = 'var(--color-filigran-brand-primary-transparency-10)';

const renderLink = (props: Partial<Parameters<typeof TopBarIconLink>[0]> = {}) => render(
  <MemoryRouter>
    <TopBarIconLink
      aria-label="notifications"
      to="/admin/profile/notifications"
      icon={<svg />}
      {...props}
    />
  </MemoryRouter>,
);

const getLink = () => screen.getByRole('link', { name: 'notifications' });

describe('TopBarIconLink current-page state', () => {
  afterEach(cleanup);

  it('paints the selected background when it is the current page', () => {
    // Regression, review #7305 (Romuald): the bell was not highlighted on the
    // notifications page. The class route cannot carry this state. The library's
    // `iconButtonVariants` already emits `bg-transparent` for the tertiary
    // priority, and in the compiled stylesheet `.bg-transparent` comes AFTER
    // `.bg-filigran-brand-primary-transparency-10` — same cascade layer, same
    // specificity, so source order decides and the transparent one wins.
    // Appending the selected utility to the class list therefore renders
    // nothing. The library's own IconButton escapes this because it merges its
    // classes with tailwind-merge, which DROPS the conflicting `bg-transparent`;
    // this product composes the class list itself, so it must not rely on that.
    renderLink({ active: true });
    expect(getLink().style.backgroundColor).toBe(SELECTED_BACKGROUND);
  });

  it('marks the current page for assistive technology', () => {
    renderLink({ active: true });
    expect(getLink().getAttribute('aria-current')).toBe('page');
  });

  it('paints no selected background when it is not the current page', () => {
    renderLink({ active: false });
    expect(getLink().style.backgroundColor).toBe('');
    expect(getLink().getAttribute('aria-current')).toBeNull();
  });

  it('leaves the state out entirely when the control has no current-page notion', () => {
    // An external link (XTM One) never represents a page of this application.
    renderLink({
      active: undefined,
      href: 'https://one.filigran.io',
      to: undefined,
    });
    const link = screen.getByRole('link', { name: 'notifications' });
    expect(link.style.backgroundColor).toBe('');
    expect(link.getAttribute('aria-current')).toBeNull();
  });

  it('keeps the glyph colour it is given', () => {
    // The colour compensation (feedback #24) and the new background must not
    // overwrite one another - both are inline on the same element.
    renderLink({
      active: true,
      color: 'var(--color-filigran-ia-primary)',
    });
    expect(getLink().style.color).toBe('var(--color-filigran-ia-primary)');
    expect(getLink().style.backgroundColor).toBe(SELECTED_BACKGROUND);
  });
});
