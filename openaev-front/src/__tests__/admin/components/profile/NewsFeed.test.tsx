import '@testing-library/jest-dom/vitest';

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

let enabled = false;
vi.mock('../../../../utils/utils', () => ({ isFeatureEnabled: () => enabled }));
vi.mock('../../../../admin/components/profile/news_feed/NewsFeedList', () => ({ default: () => <div data-testid="news-feed-list" /> }));
vi.mock('../../../../components/NotFound', () => ({ default: () => <div data-testid="not-found" /> }));

const { default: NewsFeed } = await import('../../../../admin/components/profile/news_feed/NewsFeed');

describe('NewsFeed route guard', () => {
  afterEach(() => {
    cleanup();
    enabled = false;
  });

  it('shows the list when the feature is enabled', () => {
    enabled = true;
    render(<NewsFeed />);
    expect(screen.getByTestId('news-feed-list')).toBeInTheDocument();
  });

  it('is not reachable when the feature is disabled', () => {
    enabled = false;
    render(<NewsFeed />);
    expect(screen.getByTestId('not-found')).toBeInTheDocument();
    expect(screen.queryByTestId('news-feed-list')).not.toBeInTheDocument();
  });
});
