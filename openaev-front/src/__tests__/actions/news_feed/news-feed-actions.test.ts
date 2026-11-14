import { beforeEach, describe, expect, it, vi } from 'vitest';

const { simplePostCall } = vi.hoisted(() => ({ simplePostCall: vi.fn((_uri?: string) => Promise.resolve({ data: { content: [] } })) }));
const { simpleCall, simplePutCall } = vi.hoisted(() => ({
  simpleCall: vi.fn((_uri?: string) => Promise.resolve({ data: 0 })),
  simplePutCall: vi.fn((_uri?: string) => Promise.resolve({ data: undefined })),
}));
vi.mock('../../../utils/Action', () => ({
  simplePostCall,
  simpleCall,
  simplePutCall,
}));

const { fetchMyUnreadNewsFeedCount, markAllMyNewsFeedItemsRead, searchMyNewsFeedItems } = await import('../../../actions/news_feed/news-feed-actions');

const input = {
  page: 0,
  size: 1,
};

describe('news feed actions', () => {
  beforeEach(() => {
    simplePostCall.mockClear();
    simpleCall.mockClear();
    simplePutCall.mockClear();
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

  it('fetchMyUnreadNewsFeedCount leaves the error to the caller by default', async () => {
    // Act
    await fetchMyUnreadNewsFeedCount();

    // Assert
    expect(simpleCall).toHaveBeenCalledWith('/api/news-feed/me/unread-count', undefined, false);
  });

  it('markAllMyNewsFeedItemsRead notifies neither the success nor the error', async () => {
    // Act
    await markAllMyNewsFeedItemsRead();

    // Assert
    expect(simplePutCall).toHaveBeenCalledWith('/api/news-feed/me/read-all', undefined, undefined, false, false);
  });
});
