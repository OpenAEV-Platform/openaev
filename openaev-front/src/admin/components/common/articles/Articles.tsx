import { VisibilityOutlined } from '@mui/icons-material';
import { IconButton, List, ListItem, ListItemIcon, ListItemSecondaryAction, ListItemText, Tooltip } from '@mui/material';
import { green, orange } from '@mui/material/colors';
import * as R from 'ramda';
import { type CSSProperties, type FunctionComponent, useContext, useState } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type FullArticleStore } from '../../../../actions/channels/Article';
import { type ChannelsHelper } from '../../../../actions/channels/channel-helper';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Article } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import useSearchAndFilter from '../../../../utils/SortingFiltering';
import ChannelIcon from '../../components/channels/ChannelIcon';
import { type ChannelOption } from '../../components/channels/ChannelOption';
import ChannelsFilter from '../../components/channels/ChannelsFilter';
import ConfigurationSection from '../ConfigurationSection';
import { ArticleContext, PermissionsContext } from '../Context';
import ArticlePopover from './ArticlePopover';
import CreateArticle from './CreateArticle';

const useStyles = makeStyles()(() => ({
  itemHead: {
    textTransform: 'uppercase',
    cursor: 'pointer',
  },
  item: { height: 50 },
  bodyItem: {
    height: '100%',
    fontSize: 13,
  },
}));

const headerStyles: {
  iconSort: CSSProperties;
  article_name: CSSProperties;
  article_author: CSSProperties;
  article_channel: CSSProperties;
  article_is_scheduled: CSSProperties;
} = {
  iconSort: {
    position: 'absolute',
    margin: '0 0 0 5px',
    padding: 0,
    top: '0px',
  },
  article_name: {
    float: 'left',
    width: '30%',
    fontSize: 12,
    fontWeight: '700',
  },
  article_author: {
    float: 'left',
    width: '20%',
    fontSize: 12,
    fontWeight: '700',
  },
  article_channel: {
    float: 'left',
    width: '25%',
    fontSize: 12,
    fontWeight: '700',
  },
  article_is_scheduled: {
    float: 'left',
    width: '25%',
    fontSize: 12,
    fontWeight: '700',
  },
};

const inlineStyles: {
  article_name: CSSProperties;
  article_author: CSSProperties;
  article_channel: CSSProperties;
  article_is_scheduled: CSSProperties;
} = {
  article_name: {
    float: 'left',
    width: '30%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    fontWeight: 600,
  },
  article_author: {
    float: 'left',
    width: '20%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  article_channel: {
    float: 'left',
    width: '25%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
  article_is_scheduled: {
    float: 'left',
    width: '25%',
    height: 20,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  },
};

interface Props { articles: Article[] }

// Media pressure articles rendered as a sortable table, sharing the exact
// same anatomy as the Teams and Variables configuration tabs (header row,
// 50px rows, right-aligned row actions). Creation lives in the section
// header action only, so an empty tab reads like the other tabs.
const Articles: FunctionComponent<Props> = ({ articles }) => {
  // Context
  const { previewArticleUrl, fetchChannels, fetchDocuments } = useContext(ArticleContext);
  const { permissions } = useContext(PermissionsContext);

  // Standard hooks
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const { t } = useFormatter();

  // Fetching data
  const { channelsMap } = useHelper((helper: ChannelsHelper) => ({ channelsMap: helper.getChannelsMap() }));
  useDataLoader(() => {
    dispatch(fetchChannels());
    dispatch(fetchDocuments());
  });

  // Creation
  const [openCreate, setOpenCreate] = useState(false);
  const handleOpenCreate = () => setOpenCreate(true);
  const handleCloseCreate = () => setOpenCreate(false);

  // Filter and sort hook
  const [channels, setChannels] = useState<ChannelOption[]>([]);
  const handleChannelsChange = (value: ChannelOption[]) => {
    setChannels(value);
  };
  const handleClearChannels = () => {
    setChannels([]);
  };
  const searchColumns = ['name', 'type', 'content'];
  const filtering = useSearchAndFilter('article', 'name', searchColumns);
  // Rendering
  const fullArticles = articles.map(item => ({
    ...item,
    article_fullchannel: item.article_channel ? channelsMap[item.article_channel] : {},
  }));
  const sortedArticles: FullArticleStore[] = R.filter(
    (n: FullArticleStore) => channels.length === 0
      || channels.map(o => o.id).includes(n.article_fullchannel?.channel_id ?? ''),
    filtering.filterAndSort(fullArticles),
  );

  return (
    <ConfigurationSection
      title={t('Media pressure')}
      count={fullArticles.length}
      action={permissions.canManage && (
        <CreateArticle
          openCreate={openCreate}
          handleOpenCreate={handleOpenCreate}
          handleCloseCreate={handleCloseCreate}
        />
      )}
    >
      {fullArticles.length > 0 && (
        <ChannelsFilter
          onChannelsChange={handleChannelsChange}
          onClearChannels={handleClearChannels}
        />
      )}
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          divider={false}
          style={{ paddingTop: 0 }}
        >
          <ListItemIcon>
            <span
              style={{
                padding: '0 8px 0 10px',
                fontWeight: 700,
                fontSize: 12,
              }}
            >
              #
            </span>
          </ListItemIcon>
          <ListItemText
            primary={(
              <>
                {filtering.buildHeader(
                  'article_name',
                  'Name',
                  true,
                  headerStyles,
                )}
                {filtering.buildHeader(
                  'article_author',
                  'Author',
                  true,
                  headerStyles,
                )}
                {filtering.buildHeader(
                  'article_channel',
                  'Channel',
                  false,
                  headerStyles,
                )}
                {filtering.buildHeader(
                  'article_is_scheduled',
                  'Status',
                  false,
                  headerStyles,
                )}
              </>
            )}
          />
          <ListItemSecondaryAction>&nbsp;</ListItemSecondaryAction>
        </ListItem>
        {sortedArticles.map(article => (
          <ListItem
            key={article.article_id}
            classes={{ root: classes.item }}
            divider
            secondaryAction={(
              <>
                <Tooltip title={t('Preview')}>
                  <IconButton
                    size="small"
                    color="primary"
                    component={Link}
                    to={previewArticleUrl(article)}
                  >
                    <VisibilityOutlined fontSize="small" />
                  </IconButton>
                </Tooltip>
                <ArticlePopover article={article} onRemoveArticle={undefined} />
              </>
            )}
          >
            <ListItemIcon>
              <ChannelIcon
                type={article.article_fullchannel?.channel_type}
                tooltip={article.article_fullchannel?.channel_name}
              />
            </ListItemIcon>
            <ListItemText
              primary={(
                <>
                  <div
                    className={classes.bodyItem}
                    style={inlineStyles.article_name}
                  >
                    {article.article_name}
                  </div>
                  <div
                    className={classes.bodyItem}
                    style={inlineStyles.article_author}
                  >
                    {article.article_author || t('Unknown')}
                  </div>
                  <div
                    className={classes.bodyItem}
                    style={inlineStyles.article_channel}
                  >
                    {article.article_fullchannel?.channel_name || '-'}
                  </div>
                  <div
                    className={classes.bodyItem}
                    style={inlineStyles.article_is_scheduled}
                  >
                    {article.article_is_scheduled ? (
                      <span style={{ color: green[500] }}>
                        {t('Scheduled')}
                      </span>
                    ) : (
                      <span style={{ color: orange[500] }}>
                        {t('Not used in the context')}
                      </span>
                    )}
                  </div>
                </>
              )}
            />
          </ListItem>
        ))}
      </List>
    </ConfigurationSection>
  );
};

export default Articles;
