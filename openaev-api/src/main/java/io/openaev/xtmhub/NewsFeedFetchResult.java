package io.openaev.xtmhub;

/** Outcome of consuming the provisioned news feed items with one registration token. */
public sealed interface NewsFeedFetchResult {

  /** The Hub answered with its provisioned items; they are consumed and must be persisted. */
  record Fetched(ProvisionedNewsFeedItems payload) implements NewsFeedFetchResult {}

  /** The Hub does not accept the token: another registration may still work. */
  record TokenRejected() implements NewsFeedFetchResult {}

  /** The Hub is unreachable or answered something unusable: trying another token will not help. */
  record Failed() implements NewsFeedFetchResult {}
}
