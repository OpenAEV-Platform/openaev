import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import NewsFeedBlogPostCard from '../../../../../admin/components/profile/news_feed/NewsFeedBlogPostCard';
import { type NewsFeedItemOutput, type PlatformSettings, type User } from '../../../../../utils/api-types';
import { UserContext } from '../../../../../utils/hooks/useAuth';

const HUB_URL = 'https://hub.example.com';
const PLATFORM_ID = 'platform-1';
const TITLE = 'A very long blogpost title about the latest threats that does not fit on one line';

const metadataOf = (values: Record<string, string>): NewsFeedItemOutput['news_feed_item_metadata'] => Object.entries(values)
  .map(([key, value]) => ({
    news_feed_item_metadata_key: key,
    news_feed_item_metadata_value: value,
  }));

const FULL_METADATA = {
  description: 'What changed this month',
  image_url: 'https://img.example.com/cover.png',
  image_alt: 'The cover',
  author_names: '["Jane Doe","John Doe"]',
  read_minutes: '7',
};

const buildBlogPost = (overrides: Partial<NewsFeedItemOutput> = {}): NewsFeedItemOutput => ({
  news_feed_item_id: 'item-1',
  news_feed_item_hub_id: 'hub-1',
  news_feed_item_is_read: false,
  news_feed_item_type: 'BLOG_POST',
  news_feed_item_title: TITLE,
  news_feed_item_creation_date: '2026-03-15T12:00:00Z',
  news_feed_item_tags: ['threat intel', 'ransomware'],
  news_feed_item_url_path: '/redirect/blog/hub-1',
  news_feed_item_metadata: metadataOf(FULL_METADATA),
  ...overrides,
});

const renderCard = (item: NewsFeedItemOutput) => render(
  <IntlProvider locale="en" onError={() => {}}>
    <TooltipProvider>
      <UserContext.Provider
        value={{
          me: {} as User,
          settings: {
            xtm_hub_url: HUB_URL,
            platform_id: PLATFORM_ID,
          } as PlatformSettings,
          isXTMHubAccessible: true,
          userTenants: [],
          currentUserTenant: null,
          switchUserTenant: async () => {},
          reloadUserTenants: async () => {},
        }}
      >
        <NewsFeedBlogPostCard item={item} />
      </UserContext.Provider>
    </TooltipProvider>
  </IntlProvider>,
);

describe('NewsFeedBlogPostCard', () => {
  beforeAll(() => {
    if (!('ResizeObserver' in globalThis)) {
      (globalThis as unknown as { ResizeObserver: unknown }).ResizeObserver = class {
        observe() {}

        disconnect() {}
      };
    }
  });

  beforeEach(() => {
    vi.spyOn(window, 'open').mockImplementation(() => null);
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  describe('content', () => {
    it('shows the image, the title, the authors, the read time, the tags and the date', () => {
      // Act
      renderCard(buildBlogPost());

      // Assert
      expect(screen.getByRole('img', { name: 'The cover' })).toHaveAttribute('src', FULL_METADATA.image_url);
      expect(screen.getByRole('heading', {
        level: 3,
        name: TITLE,
      })).toBeInTheDocument();
      expect(screen.getByText('Jane Doe, John Doe')).toBeInTheDocument();
      expect(screen.getByText('7 min read')).toBeInTheDocument();
      expect(screen.getByRole('group', { name: 'Jane Doe, John Doe' })).toBeInTheDocument();
      expect(screen.getByText('threat intel')).toBeInTheDocument();
      expect(document.querySelector('time')).toHaveAttribute('dateTime', '2026-03-15T12:00:00Z');
    });

    it('shows only the title and the date when there is no metadata', () => {
      // Act
      renderCard(buildBlogPost({
        news_feed_item_metadata: [],
        news_feed_item_tags: [],
      }));

      // Assert
      expect(screen.getByRole('heading', { name: TITLE })).toBeInTheDocument();
      expect(screen.queryByRole('img', { name: 'The cover' })).not.toBeInTheDocument();
      expect(screen.queryByText(/min read/)).not.toBeInTheDocument();
      expect(document.querySelector('time')).toBeInTheDocument();
    });

    it.each([
      ['not a JSON', 'Jane Doe'],
      ['a JSON object', '{"name":"Jane Doe"}'],
    ])('shows no author when the authors are %s', (_, authorNames) => {
      // Act
      renderCard(buildBlogPost({ news_feed_item_metadata: metadataOf({ author_names: authorNames }) }));

      // Assert
      expect(screen.queryByText('Jane Doe')).not.toBeInTheDocument();
    });

    it('folds the authors beyond the third into a counter', () => {
      // Act
      renderCard(buildBlogPost({ news_feed_item_metadata: metadataOf({ author_names: '["Ann A","Bob B","Cyd C","Dan D","Eve E"]' }) }));

      // Assert
      expect(screen.getByText('+2')).toBeInTheDocument();
    });

    it('shows no read time when it is not a positive number of minutes', () => {
      // Act
      renderCard(buildBlogPost({ news_feed_item_metadata: metadataOf({ read_minutes: 'seven' }) }));

      // Assert
      expect(screen.queryByText(/min read/)).not.toBeInTheDocument();
    });
  });

  describe('image', () => {
    it.each([
      ['a javascript scheme', 'javascript:alert(1)'],
      ['a data URL', 'data:image/png;base64,AAAA'],
      ['not an URL', 'cover.png'],
    ])('shows no image for %s', (_, imageUrl) => {
      // Act
      renderCard(buildBlogPost({ news_feed_item_metadata: metadataOf({ image_url: imageUrl }) }));

      // Assert
      expect(screen.queryByRole('img', { name: 'The cover' })).not.toBeInTheDocument();
    });

    it('removes an image that fails to load, leaving no gap', () => {
      // Arrange
      renderCard(buildBlogPost());

      // Act
      fireEvent.error(screen.getByRole('img', { name: 'The cover' }));

      // Assert
      expect(screen.queryByRole('img', { name: 'The cover' })).not.toBeInTheDocument();
      expect(screen.getByRole('heading', { name: TITLE })).toBeInTheDocument();
    });
  });

  describe('click', () => {
    it('opens the blogpost through the XTM Hub redirect, in a new tab, with the platform', () => {
      // Arrange
      renderCard(buildBlogPost());

      // Act
      fireEvent.click(screen.getByRole('button', { name: TITLE }));

      // Assert
      expect(window.open).toHaveBeenCalledWith(`${HUB_URL}/redirect/blog/hub-1?platform_id=${PLATFORM_ID}`, '_blank', 'noopener,noreferrer');
    });

    it('shows the external icon as a plain icon, not as a second control', () => {
      // Act
      renderCard(buildBlogPost());

      // Assert
      expect(screen.queryByRole('button', { name: 'Open on Filigran blog' })).not.toBeInTheDocument();
      expect(screen.queryByRole('link')).not.toBeInTheDocument();
      expect(screen.getByTestId('OpenInNewOutlinedIcon')).toHaveAttribute('aria-hidden', 'true');
    });

    it('cannot be clicked when the XTM Hub path leaves XTM Hub', () => {
      // Act
      renderCard(buildBlogPost({ news_feed_item_url_path: 'https://evil.example/steal' }));

      // Assert
      expect(screen.queryByRole('button', { name: TITLE })).not.toBeInTheDocument();
      expect(screen.queryByTestId('OpenInNewOutlinedIcon')).not.toBeInTheDocument();
    });
  });
});
