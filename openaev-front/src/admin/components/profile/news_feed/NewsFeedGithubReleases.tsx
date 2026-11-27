import { Button, IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { CellTowerOutlined, OpenInNewOutlined } from '@mui/icons-material';
// fds:keep-mui the library has no Table yet; dressed to its spec by FDS_TABLE_SX
import { Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material';

import { FDS_TABLE_SX } from '../../../../components/common/fdsTableSpec';
import Empty from '../../../../components/Empty';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import SimpleError from '../../../../components/SimpleError';
import useAuth from '../../../../utils/hooks/useAuth';
import {
  buildHubRedirectUrl,
  getNewsFeedMetadataValue,
  NEWS_FEED_CATEGORIES,
  NEWS_FEED_GITHUB_RELEASES_URL,
  NEWS_FEED_METADATA_KEYS,
} from './newsFeedUtils';
import useNewsFeedItems from './useNewsFeedItems';

/** The OpenAEV GitHub releases XTM Hub provisioned for the user, newest first, with the link to all of them. */
const NewsFeedGithubReleases = () => {
  const { t, fsd } = useFormatter();
  const { settings } = useAuth();
  const { items: releases, failed } = useNewsFeedItems(NEWS_FEED_CATEGORIES.GITHUB_RELEASE);

  let content;
  if (releases === null) {
    content = failed ? <SimpleError /> : <Loader variant="inElement" />;
  } else if (releases.length === 0) {
    content = <Empty message={t('No GitHub releases yet')} icon={CellTowerOutlined} />;
  } else {
    content = (
      <div style={{ overflowX: 'auto' }}>
        <Table size="small" aria-label={t('GitHub releases')} sx={FDS_TABLE_SX}>
          <TableHead>
            <TableRow>
              <TableCell>{t('GitHub release')}</TableCell>
              <TableCell>{t('Version')}</TableCell>
              <TableCell>{t('Release date')}</TableCell>
              <TableCell align="right">{t('Link')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {releases.map((release) => {
              // Every metadata is optional and comes from an external source: nothing is trusted.
              const version = getNewsFeedMetadataValue(release.news_feed_item_metadata, NEWS_FEED_METADATA_KEYS.PRODUCT_VERSION);
              const href = buildHubRedirectUrl(release.news_feed_item_url_path, settings.xtm_hub_url, settings.platform_id);
              return (
                <TableRow key={release.news_feed_item_id}>
                  <TableCell style={{ overflowWrap: 'anywhere' }}>{release.news_feed_item_title}</TableCell>
                  <TableCell>{version ?? '-'}</TableCell>
                  <TableCell>{fsd(release.news_feed_item_creation_date)}</TableCell>
                  <TableCell align="right">
                    {href && (
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <IconButton
                            asChild
                            priority="tertiary"
                            size="sm"
                            aria-label={`${t('Open on GitHub')}: ${release.news_feed_item_title}`}
                            icon={<OpenInNewOutlined fontSize="small" />}
                          >
                            <a href={href} target="_blank" rel="noopener noreferrer" />
                          </IconButton>
                        </TooltipTrigger>
                        <TooltipContent>{t('Open on GitHub')}</TooltipContent>
                      </Tooltip>
                    )}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>
    );
  }

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 24,
      paddingBottom: 24,
    }}
    >
      {content}
      <div style={{
        display: 'flex',
        justifyContent: 'flex-end',
      }}
      >
        <Button asChild priority="secondary">
          <a href={NEWS_FEED_GITHUB_RELEASES_URL} target="_blank" rel="noopener noreferrer">
            {t('See all releases')}
            <OpenInNewOutlined fontSize="small" />
          </a>
        </Button>
      </div>
    </div>
  );
};

export default NewsFeedGithubReleases;
