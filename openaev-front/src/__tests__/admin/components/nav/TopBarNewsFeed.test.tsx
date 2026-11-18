import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { cleanup, render, screen } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { expectNoMuiControls } from '../../../utils/designSystemAssertions';

let enabled = false;
vi.mock('../../../../utils/utils', () => ({ isFeatureEnabled: () => enabled }));

const { default: TopBarNewsFeed } = await import('../../../../admin/components/nav/TopBarNewsFeed');

const renderMegaphone = (path = '/admin') => render(
  <MemoryRouter initialEntries={[path]}>
    <IntlProvider locale="en" messages={{}}>
      <TooltipProvider>
        <TopBarNewsFeed />
      </TooltipProvider>
    </IntlProvider>
  </MemoryRouter>,
);

describe('TopBarNewsFeed', () => {
  afterEach(() => {
    cleanup();
    enabled = false;
  });

  it('links to the news feed page when the feature is enabled', () => {
    enabled = true;
    const { container } = renderMegaphone();
    const link = screen.getByRole('link', { name: 'News feed' });
    expect(link).toHaveAttribute('href', '/admin/profile/news-feed');
    expectNoMuiControls(container, 'the news feed megaphone');
  });

  it('is current on the news feed page', () => {
    enabled = true;
    renderMegaphone('/admin/profile/news-feed');
    expect(screen.getByRole('link', { name: 'News feed' })).toBeInTheDocument();
  });

  it('renders nothing when the feature is disabled', () => {
    enabled = false;
    const { container } = renderMegaphone();
    expect(container).toBeEmptyDOMElement();
  });
});
