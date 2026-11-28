package io.openaev.database.model;

/**
 * The news feed items read, and counted unread, together: the blogposts, the GitHub releases, and
 * the resources, which are every other type, including the ones OpenAEV no longer knows.
 */
public enum NewsFeedCategory {
  BLOG_POST,
  GITHUB_RELEASE,
  RESOURCE
}
