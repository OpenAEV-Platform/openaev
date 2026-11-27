import { AvatarGroup, Card, CardFooter, CardHeader, CardInfo, CardMedia, CardTitle, IconButton, Text, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { OpenInNewOutlined } from '@mui/icons-material';
import { useEffect, useId, useRef, useState } from 'react';

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

// Avatars shown before the "+N" of the group.
const MAX_AVATARS = 3;

interface Props { item: NewsFeedItemOutput }

const NewsFeedBlogPostCard = ({ item }: Props) => {
  const { t, fsd } = useFormatter();
  const authorsId = useId();
  const { settings } = useAuth();
  // The image that failed to load: a corrected URL sent later by XTM Hub gets a new chance.
  const [brokenImageUrl, setBrokenImageUrl] = useState<string | null>(null);

  // Every metadata is optional and comes from an external source: nothing is trusted.
  const metadata = item.news_feed_item_metadata;
  const href = buildBlogPostHubUrl(item.news_feed_item_url_path, settings.xtm_hub_url, settings.platform_id);
  const imageUrl = toHttpUrl(getNewsFeedMetadataValue(metadata, NEWS_FEED_METADATA_KEYS.IMAGE_URL));
  const isImageShown = imageUrl !== undefined && imageUrl !== brokenImageUrl;
  const imageAlt = getNewsFeedMetadataValue(metadata, NEWS_FEED_METADATA_KEYS.IMAGE_ALT) ?? '';
  const authors = parseNewsFeedAuthorNames(getNewsFeedMetadataValue(metadata, NEWS_FEED_METADATA_KEYS.AUTHOR_NAMES));
  const readMinutes = parseNewsFeedReadMinutes(getNewsFeedMetadataValue(metadata, NEWS_FEED_METADATA_KEYS.READ_MINUTES));
  const tags = item.news_feed_item_tags.filter(tag => tag.length > 0);

  // FDS-WORKAROUND: `CardMedia` has no image load failure handling, so the `error` event of its `<img>` is caught here — remove when `CardMedia` handles a failed image — see https://github.com/XTM-Foundation/filigran-design-system/issues/462
  const mediaRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const media = mediaRef.current;
    if (!media || imageUrl === undefined) {
      return undefined;
    }
    // An `error` event does not bubble: it is caught on its way down to the image.
    const onImageError = () => setBrokenImageUrl(imageUrl);
    media.addEventListener('error', onImageError, true);
    return () => media.removeEventListener('error', onImageError, true);
  }, [imageUrl, isImageShown]);

  return (
    <Card
      // The article is on the Filigran blog: it opens in a new tab, never in the platform.
      // FDS-WORKAROUND: the `href` of `Card` opens in the same tab and cannot set `target`/`rel`, so the card is a button — remove when `Card` can open an external link in a new tab — see https://github.com/XTM-Foundation/filigran-design-system/issues/462
      onClick={href ? () => window.open(href, '_blank', 'noopener,noreferrer') : undefined}
    >
      {isImageShown && <CardMedia ref={mediaRef} src={imageUrl} alt={imageAlt} />}
      <CardHeader>
        <CardTitle as="h3">{item.news_feed_item_title}</CardTitle>
      </CardHeader>
      {tags.length > 0 && <OverflowChips labels={tags} />}
      <CardFooter
        start={(
          <>
            {authors.length > 0 && (
              <>
                <AvatarGroup
                  aria-labelledby={authorsId}
                  max={MAX_AVATARS}
                  items={authors.map((name, index) => ({
                    // two authors may share a name
                    id: `${index}-${name}`,
                    name,
                  }))}
                  overflowLabel={hiddenNames => t('Other authors: {names}', { names: hiddenNames.join(', ') })}
                />
                <Text as="span" id={authorsId} variant="content-compact-bold" className="truncate">{authors.join(', ')}</Text>
              </>
            )}
            <CardInfo
              orientation="horizontal"
              label={readMinutes !== undefined ? t('{count} min read', { count: String(readMinutes) }) : undefined}
              value={<time dateTime={item.news_feed_item_creation_date}>{fsd(item.news_feed_item_creation_date)}</time>}
            />
          </>
        )}
        end={href && (
          <Tooltip>
            <TooltipTrigger asChild>
              <IconButton
                asChild
                priority="tertiary"
                size="sm"
                aria-label={t('Open on Filigran blog')}
                icon={<OpenInNewOutlined fontSize="small" />}
              >
                <a href={href} target="_blank" rel="noopener noreferrer" />
              </IconButton>
            </TooltipTrigger>
            <TooltipContent>{t('Open on Filigran blog')}</TooltipContent>
          </Tooltip>
        )}
      />
    </Card>
  );
};

export default NewsFeedBlogPostCard;
