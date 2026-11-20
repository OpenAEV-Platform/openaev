import { describe, expect, it } from 'vitest';

import {
  buildBlogPostHubUrl,
  buildNewsFeedBlogSeeAllUrl,
  getNewsFeedMetadataValue,
  parseNewsFeedAuthorNames,
  parseNewsFeedReadMinutes,
} from '../../../../../admin/components/profile/news_feed/newsFeedUtils';

const HUB_URL = 'https://hub.example.com';

describe('newsFeedUtils', () => {
  describe('getNewsFeedMetadataValue', () => {
    const metadata = [
      {
        news_feed_item_metadata_key: 'description',
        news_feed_item_metadata_value: 'A description',
      },
      {
        news_feed_item_metadata_key: 'image_alt',
        news_feed_item_metadata_value: '',
      },
    ];

    it('returns the value of the key', () => {
      expect(getNewsFeedMetadataValue(metadata, 'description')).toBe('A description');
    });

    it.each(['image_alt', 'read_minutes'] as const)('returns undefined for the empty or missing key %s', (key) => {
      expect(getNewsFeedMetadataValue(metadata, key)).toBeUndefined();
    });

    it('returns undefined when there is no metadata', () => {
      expect(getNewsFeedMetadataValue(undefined, 'description')).toBeUndefined();
    });
  });

  describe('parseNewsFeedAuthorNames', () => {
    it('returns every author of a JSON array', () => {
      expect(parseNewsFeedAuthorNames('["Jane Doe","John Doe"]')).toEqual(['Jane Doe', 'John Doe']);
    });

    it('ignores the blank names and the values that are not a name', () => {
      expect(parseNewsFeedAuthorNames('["  Jane Doe ","",42,null,{"name":"x"}]')).toEqual(['Jane Doe']);
    });

    it.each([
      ['not a JSON', 'Jane Doe'],
      ['a truncated JSON', '["Jane'],
      ['a JSON object', '{"name":"Jane Doe"}'],
      ['a JSON string', '"Jane Doe"'],
      ['a JSON number', '42'],
      ['an empty string', ''],
      ['no value', undefined],
    ])('returns no author for %s', (_, raw) => {
      expect(parseNewsFeedAuthorNames(raw)).toEqual([]);
    });
  });

  describe('parseNewsFeedReadMinutes', () => {
    it('returns the number of minutes', () => {
      expect(parseNewsFeedReadMinutes('7')).toBe(7);
    });

    it.each(['0', '-3', '7.5', 'seven', '', ' ', '7 min', undefined])('returns undefined for "%s"', (raw) => {
      expect(parseNewsFeedReadMinutes(raw)).toBeUndefined();
    });
  });

  describe('buildBlogPostHubUrl', () => {
    it('adds the platform to the XTM Hub redirect, keeping the tracking of its path', () => {
      // Act
      const url = new URL(buildBlogPostHubUrl('/redirect/blog/1?utm_medium=newsfeed', HUB_URL, 'platform-1') as string);

      // Assert
      expect(url.origin + url.pathname).toBe(`${HUB_URL}/redirect/blog/1`);
      expect(url.searchParams.get('utm_medium')).toBe('newsfeed');
      expect(url.searchParams.get('platform_id')).toBe('platform-1');
    });

    it.each([
      ['another host', 'https://evil.example/steal'],
      ['a protocol-relative path', '//evil.example/steal'],
      ['a javascript scheme', 'javascript:alert(1)'],
    ])('links nowhere for %s', (_, urlPath) => {
      expect(buildBlogPostHubUrl(urlPath, HUB_URL, 'platform-1')).toBeNull();
    });

    it('links nowhere without a path', () => {
      expect(buildBlogPostHubUrl(undefined, HUB_URL, 'platform-1')).toBeNull();
    });
  });

  describe('buildNewsFeedBlogSeeAllUrl', () => {
    it('targets the Filigran blog with the UTM of the news feed and no platform id', () => {
      // Act
      const url = new URL(buildNewsFeedBlogSeeAllUrl('platform-1', HUB_URL));

      // Assert
      expect(url.origin + url.pathname).toBe('https://filigran.io/blog');
      expect(Object.fromEntries(url.searchParams)).toEqual({
        utm_source: 'platform-1',
        utm_medium: 'newsfeed',
        utm_campaign: 'blog_posts',
        utm_content: 'see_all',
      });
    });

    it('targets the staging blog when the platform is linked to a staging XTM Hub', () => {
      expect(new URL(buildNewsFeedBlogSeeAllUrl('platform-1', 'https://hub.staging.filigran.io')).origin).toBe('https://staging.filigran.io');
    });

    it.each([undefined, 'not an url'])('targets the production blog when the XTM Hub URL is %s', (hubUrl) => {
      expect(new URL(buildNewsFeedBlogSeeAllUrl('platform-1', hubUrl)).origin).toBe('https://filigran.io');
    });

    it('does not forge a utm_source when the platform identifier is missing', () => {
      expect(new URL(buildNewsFeedBlogSeeAllUrl(undefined, HUB_URL)).searchParams.has('utm_source')).toBe(false);
    });
  });
});
