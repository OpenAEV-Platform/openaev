import type { NewsFeedItemMetadataOutput } from '../../../../utils/api-types';
import { XTM_HUB_DEFAULT_URL } from '../../../../utils/Environment';

export const NEWS_FEED_BLOG_POST_TYPE = 'BLOG_POST';

// The path comes from XTM Hub data: only a link that stays on the XTM Hub origin is rendered.
export const buildHubUrl = (urlPath: string, hubUrl: string): string | null => {
  try {
    const base = new URL(hubUrl);
    const url = new URL(urlPath, base);
    const isWeb = url.protocol === 'https:' || url.protocol === 'http:';
    return isWeb && url.origin === base.origin ? url.toString() : null;
  } catch {
    return null;
  }
};

/** The XTM Hub page of a path, on the XTM Hub configured on the platform or the default one. */
export const buildConfiguredHubUrl = (urlPath: string | undefined, configuredHubUrl: string | undefined): string | null => (
  urlPath ? buildHubUrl(urlPath, configuredHubUrl ?? XTM_HUB_DEFAULT_URL) : null
);

/**
 * The XTM Hub redirect to a blogpost, which tells the Hub the platform the reader comes from. The tracking of the
 * campaign is in the path XTM Hub sends.
 */
export const buildBlogPostHubUrl = (urlPath: string | undefined, configuredHubUrl: string | undefined, platformId: string | undefined): string | null => {
  const hubUrl = buildConfiguredHubUrl(urlPath, configuredHubUrl);
  if (!hubUrl) {
    return null;
  }
  const url = new URL(hubUrl);
  if (platformId) {
    url.searchParams.append('platform_id', platformId);
  }
  return url.toString();
};

/** The value of a metadata, `undefined` when it is missing or empty: every metadata is optional. */
export const getNewsFeedMetadataValue = (metadata: NewsFeedItemMetadataOutput[] | undefined, key: string): string | undefined => {
  const value = metadata?.find(entry => entry.news_feed_item_metadata_key === key)?.news_feed_item_metadata_value;
  return value || undefined;
};

/**
 * `author_names` is a JSON array of names serialized as a string by XTM Hub.
 * Anything that is not a JSON array (invalid JSON, an object, a string...) gives no author.
 */
export const parseNewsFeedAuthorNames = (rawAuthorNames: string | undefined): string[] => {
  if (!rawAuthorNames) {
    return [];
  }
  try {
    const parsed: unknown = JSON.parse(rawAuthorNames);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed
      .filter((name): name is string => typeof name === 'string')
      .map(name => name.trim())
      .filter(name => name.length > 0);
  } catch {
    return [];
  }
};

/** `read_minutes` is serialized as a string by XTM Hub: only a positive integer is displayed. */
export const parseNewsFeedReadMinutes = (rawReadMinutes: string | undefined): number | undefined => {
  if (!rawReadMinutes || !/^\d+$/.test(rawReadMinutes.trim())) {
    return undefined;
  }
  const minutes = Number(rawReadMinutes);
  return minutes > 0 ? minutes : undefined;
};

const FILIGRAN_BLOG_URL = 'https://filigran.io/blog';
const FILIGRAN_STAGING_BLOG_URL = 'https://staging.filigran.io/blog';

/**
 * Link of the "See all" button of the blogposts: the Filigran blog (staging blog when the platform is linked to a
 * staging XTM Hub), tagged with the UTM of the news feed. No `platform_id`: this link does not go through the XTM Hub
 * redirect.
 */
export const buildNewsFeedBlogSeeAllUrl = (platformId: string | undefined, configuredHubUrl: string | undefined): string => {
  let isStaging = false;
  try {
    isStaging = /(^|\.)staging\./.test(new URL(configuredHubUrl ?? XTM_HUB_DEFAULT_URL).hostname);
  } catch {
    // malformed XTM Hub URL: the production blog
  }
  const url = new URL(isStaging ? FILIGRAN_STAGING_BLOG_URL : FILIGRAN_BLOG_URL);
  if (platformId) {
    url.searchParams.append('utm_source', platformId);
  }
  url.searchParams.append('utm_medium', 'newsfeed');
  url.searchParams.append('utm_campaign', 'blog_posts');
  url.searchParams.append('utm_content', 'see_all');
  return url.toString();
};
