/** The attribute schema of the news feed item events on the SSE stream. */
export const NEWS_FEED_ITEM_SCHEMA = 'newsfeeditems';

/** The stream events of a burst of items are folded into one reaction. */
export const NEWS_FEED_STREAM_BATCH_MS = 1000;

export interface NewsFeedItemStreamEvent<T> {
  event_type: string;
  attribute_schema: string;
  instance: T;
}

/** The news feed item event carried by a stream message, `null` for any other message. */
export const parseNewsFeedItemStreamEvent = <T = unknown>(message: MessageEvent): NewsFeedItemStreamEvent<T> | null => {
  // Cheap check first: the stream carries every kind of event, and most are not news feed items.
  if (!message.data.includes(`"${NEWS_FEED_ITEM_SCHEMA}"`)) {
    return null;
  }
  const event: NewsFeedItemStreamEvent<T> = JSON.parse(message.data);
  return event.attribute_schema === NEWS_FEED_ITEM_SCHEMA ? event : null;
};
