import { Card, CardContent, CardFooter, CardHeader, CardSubtitle, CardTitle, Text } from '@filigran/design-system';
import { styled } from '@mui/material/styles';
import { useState } from 'react';

import OverflowChips from '../../../../components/common/chips/OverflowChips';
import { useFormatter } from '../../../../components/i18n';
import type { NewsFeedItemOutput } from '../../../../utils/api-types';
import useAuth from '../../../../utils/hooks/useAuth';
import { toHttpUrl } from '../../../../utils/url-helper';
import {
  buildBlogPostHubUrl,
  getNewsFeedMetadataValue,
  NEWS_FEED_METADATA_KEYS,
  parseNewsFeedAuthorNames,
  parseNewsFeedReadMinutes,
} from './newsFeedUtils';

// Blog titles can be long: the library truncates the card title on one line, here it is displayed in full instead,
// in a smaller size so that it stays compact.
// FDS-WORKAROUND: restyles the internal `.truncate` span of `CardTitle` to wrap the title — remove when `CardTitle` can wrap — see no upstream issue yet
const FullTitle = styled('div')({
  'minWidth': 0,
  '& .truncate': {
    overflow: 'visible',
    whiteSpace: 'normal',
    textOverflow: 'clip',
    overflowWrap: 'anywhere',
    fontSize: 'var(--text-title-xs)',
    lineHeight: 'var(--leading-title-xs)',
    fontWeight: 'var(--font-weight-title-xs)',
  },
});

interface Props { item: NewsFeedItemOutput }

const NewsFeedBlogPostCard = ({ item }: Props) => {
  const { t, fsd } = useFormatter();
  const { settings } = useAuth();
  // The image that failed to load: a corrected URL sent later by XTM Hub gets a new chance.
  const [brokenImageUrl, setBrokenImageUrl] = useState<string | null>(null);

  // Every metadata is optional and comes from an external source: nothing is trusted.
  const metadata = item.news_feed_item_metadata;
  const href = buildBlogPostHubUrl(item.news_feed_item_url_path, settings.xtm_hub_url, settings.platform_id);
  const imageUrl = toHttpUrl(getNewsFeedMetadataValue(metadata, NEWS_FEED_METADATA_KEYS.IMAGE_URL));
  const isImageShown = imageUrl !== undefined && imageUrl !== brokenImageUrl;
  const imageAlt = getNewsFeedMetadataValue(metadata, NEWS_FEED_METADATA_KEYS.IMAGE_ALT) ?? '';
  const description = getNewsFeedMetadataValue(metadata, NEWS_FEED_METADATA_KEYS.DESCRIPTION);
  const authors = parseNewsFeedAuthorNames(getNewsFeedMetadataValue(metadata, NEWS_FEED_METADATA_KEYS.AUTHOR_NAMES));
  const readMinutes = parseNewsFeedReadMinutes(getNewsFeedMetadataValue(metadata, NEWS_FEED_METADATA_KEYS.READ_MINUTES));
  const tags = item.news_feed_item_tags.filter(tag => tag.length > 0);

  return (
    <Card
      padding={0}
      gap={0}
      // clips the image to the rounded corners of the card
      style={{ overflow: 'hidden' }}
      // The article is on the Filigran blog: it opens in a new tab, never in the platform.
      onClick={href ? () => window.open(href, '_blank', 'noopener,noreferrer') : undefined}
    >
      {isImageShown && (
        <img
          src={imageUrl}
          alt={imageAlt}
          loading="lazy"
          onError={() => setBrokenImageUrl(imageUrl)}
          style={{
            display: 'block',
            width: '100%',
            height: 150,
            objectFit: 'cover',
          }}
        />
      )}
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
        padding: 24,
      }}
      >
        <CardHeader>
          <div style={{
            display: 'flex',
            flexDirection: 'column',
            gap: 8,
            minWidth: 0,
            flex: 1,
          }}
          >
            <FullTitle>
              <CardTitle as="h3">{item.news_feed_item_title}</CardTitle>
            </FullTitle>
            {(authors.length > 0 || readMinutes !== undefined) && (
              // the read time sticks to the right of the card, the authors take the remaining space
              <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 8,
                minWidth: 0,
              }}
              >
                <div style={{
                  minWidth: 0,
                  flex: 1,
                }}
                >
                  {authors.length > 0 && <CardSubtitle>{authors.join(', ')}</CardSubtitle>}
                </div>
                {readMinutes !== undefined && (
                  <Text variant="content-compact" className="text-default-secondary" style={{ whiteSpace: 'nowrap' }}>
                    {t('{count} min read', { count: String(readMinutes) })}
                  </Text>
                )}
              </div>
            )}
          </div>
        </CardHeader>
        {description && <CardContent>{description}</CardContent>}
        <CardFooter>
          <div style={{
            flex: 1,
            minWidth: 0,
          }}
          >
            {tags.length > 0 && <OverflowChips labels={tags} />}
          </div>
          <time dateTime={item.news_feed_item_creation_date} style={{ whiteSpace: 'nowrap' }}>{fsd(item.news_feed_item_creation_date)}</time>
        </CardFooter>
      </div>
    </Card>
  );
};

export default NewsFeedBlogPostCard;
