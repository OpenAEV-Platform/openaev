import { beforeEach, describe, expect, it, vi } from 'vitest';

const { simplePostCall } = vi.hoisted(() => ({ simplePostCall: vi.fn((_uri?: string) => Promise.resolve({ data: { content: [] } })) }));
vi.mock('../../../utils/Action', () => ({ simplePostCall }));

const { searchMyNewsFeedItems } = await import('../../../actions/news_feed/news-feed-actions');

const input = {
  page: 0,
  size: 1,
};

describe('news feed actions', () => {
  beforeEach(() => {
    simplePostCall.mockClear();
  });

  it('searchMyNewsFeedItems notifies errors by default', async () => {
    // Act
    await searchMyNewsFeedItems(input);

    // Assert
    expect(simplePostCall).toHaveBeenCalledWith('/api/news-feed/me/search', input, undefined, true);
  });

  it('searchMyNewsFeedItems can leave the error to the caller', async () => {
    // Act
    await searchMyNewsFeedItems(input, false);

    // Assert
    expect(simplePostCall).toHaveBeenCalledWith('/api/news-feed/me/search', input, undefined, false);
  });
});
