import { ChatBubbleOutlineOutlined, FavoriteBorderOutlined, NewspaperOutlined, ShareOutlined, VisibilityOutlined } from '@mui/icons-material';
import { Avatar, Button, Card, CardContent, CardHeader, CardMedia, Chip, GridLegacy, IconButton, Tooltip, Typography } from '@mui/material';
import { green, orange } from '@mui/material/colors';
import * as R from 'ramda';
import { Fragment, type FunctionComponent, useContext, useState } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type FullArticleStore } from '../../../../actions/channels/Article';
import { type ChannelsHelper } from '../../../../actions/channels/channel-helper';
import { type DocumentHelper } from '../../../../actions/helper';
import Empty from '../../../../components/Empty';
import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import ChannelColor from '../../../../public/components/channels/ChannelColor';
import { useHelper } from '../../../../store';
import { type Article } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import useSearchAndFilter from '../../../../utils/SortingFiltering';
import ChannelIcon from '../../components/channels/ChannelIcon';
import { type ChannelOption } from '../../components/channels/ChannelOption';
import ChannelsFilter from '../../components/channels/ChannelsFilter';
import { ArticleContext, PermissionsContext } from '../Context';
import ArticlePopover from './ArticlePopover';
import CreateArticle from './CreateArticle';

const useStyles = makeStyles()(() => ({
  channel: {
    fontSize: 12,
    float: 'left',
    marginRight: 7,
    maxWidth: 300,
    borderRadius: 4,
  },
  card: { position: 'relative' },
  footer: {
    width: '100%',
    position: 'absolute',
    padding: '0 15px 0 15px',
    left: 0,
    bottom: 10,
  },
  button: { cursor: 'default' },
}));

interface Props { articles: Article[] }

const Articles: FunctionComponent<Props> = ({ articles }) => {
  // Context
  const { previewArticleUrl, fetchChannels, fetchDocuments } = useContext(ArticleContext);
  const { permissions } = useContext(PermissionsContext);

  // Standard hooks
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const { t } = useFormatter();

  // Fetching data
  const { channelsMap, documentsMap } = useHelper((helper: ChannelsHelper & DocumentHelper) => ({
    channelsMap: helper.getChannelsMap(),
    documentsMap: helper.getDocumentsMap(),
  }));
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
    <div>
      <Typography variant="h4" gutterBottom style={{ float: 'left' }}>
        {t('Media pressure')}
      </Typography>
      {permissions.canManage && (
        <CreateArticle
          openCreate={openCreate}
          handleOpenCreate={handleOpenCreate}
          handleCloseCreate={handleCloseCreate}
        />
      )}
      {fullArticles.length > 0 && (
        <ChannelsFilter
          onChannelsChange={handleChannelsChange}
          onClearChannels={handleClearChannels}
        />
      )}
      <div className="clearfix" />
      {sortedArticles.length === 0 && (
        <Empty message={(
          <div style={{ textAlign: 'center' }}>
            <div style={{ fontSize: 18 }}>
              {t('No media pressure article available in this simulation yet.')}
            </div>
            {permissions.canManage
              && (
                <Button
                  style={{ marginTop: 20 }}
                  startIcon={<NewspaperOutlined />}
                  variant="outlined"
                  color="primary"
                  size="small"
                  onClick={handleOpenCreate}
                >
                  {t('Create an article')}
                </Button>
              )}
          </div>
        )}
        />
      )}
      <GridLegacy container spacing={3}>
        {sortedArticles.map((article, index) => {
          const docs = (article.article_documents ?? [])
            .map(docId => (documentsMap[docId] ? documentsMap[docId] : undefined))
            .filter(d => d !== undefined);
          const images = docs.filter(d => d.document_type.includes('image/'));
          const videos = docs.filter(d => d.document_type.includes('video/'));
          let headersDocs = [];
          if (article.article_fullchannel?.channel_type === 'newspaper') {
            headersDocs = images;
          } else if (article.article_fullchannel?.channel_type === 'tv') {
            headersDocs = videos;
          } else {
            headersDocs = [...images, ...videos];
          }
          let columns = 12;
          if (headersDocs.length === 2) {
            columns = 6;
          } else if (headersDocs.length === 3) {
            columns = 4;
          } else if (headersDocs.length >= 4) {
            columns = 3;
          }
          // const shouldBeTruncated = (article.article_content || '').length > 500;
          return (
            <GridLegacy key={article.article_id} item xs={4} style={index < 3 ? { paddingTop: 0 } : undefined}>
              <Card
                variant="outlined"
                classes={{ root: classes.card }}
                sx={{
                  width: '100%',
                  height: '100%',
                }}
              >
                <CardHeader
                  avatar={(
                    <Avatar
                      sx={{
                        bgcolor: ChannelColor(
                          article.article_fullchannel?.channel_type,
                        ),
                      }}
                    >
                      {(article.article_author || t('Unknown')).charAt(0)}
                    </Avatar>
                  )}
                  title={article.article_author || t('Unknown')}
                  subheader={
                    article.article_is_scheduled ? (
                      <span style={{ color: green[500] }}>
                        {t('Scheduled')}
                      </span>
                    ) : (
                      <span style={{ color: orange[500] }}>
                        {t('Not used in the context')}
                      </span>
                    )
                  }
                  action={(
                    <Fragment>
                      <IconButton
                        aria-haspopup="true"
                        size="large"
                        component={Link}
                        to={previewArticleUrl(article)}
                      >
                        <VisibilityOutlined />
                      </IconButton>
                      <ArticlePopover article={article} onRemoveArticle={undefined} />
                    </Fragment>
                  )}
                />
                <GridLegacy container={true} spacing={3}>
                  {headersDocs.map(doc => (
                    <GridLegacy key={doc.document_id} item xs={columns}>
                      {doc.document_type.includes('image/') && (
                        <CardMedia
                          component="img"
                          height="150"
                          src={`/api/documents/${doc.document_id}/file`}
                        />
                      )}
                      {doc.document_type.includes('video/') && (
                        <CardMedia
                          component="video"
                          height="150"
                          src={`/api/documents/${doc.document_id}/file`}
                          controls
                        />
                      )}
                    </GridLegacy>
                  ))}
                </GridLegacy>
                <CardContent style={{ marginBottom: 30 }}>
                  <Typography
                    gutterBottom
                    variant="h1"
                    component="div"
                    style={{
                      margin: '0 auto',
                      textAlign: 'center',
                    }}
                  >
                    {article.article_name}
                  </Typography>
                  <ExpandableMarkdown source={article.article_content ?? ''} limit={500} />
                  <div className={classes.footer}>
                    <div style={{ float: 'left' }}>
                      <Tooltip title={article.article_fullchannel?.channel_name}>
                        <Chip
                          icon={(
                            <ChannelIcon
                              type={article.article_fullchannel?.channel_type}
                              variant="chip"
                            />
                          )}
                          classes={{ root: classes.channel }}
                          style={{
                            color: ChannelColor(
                              article.article_fullchannel?.channel_type,
                            ),
                            borderColor: ChannelColor(
                              article.article_fullchannel?.channel_type,
                            ),
                          }}
                          variant="outlined"
                          label={article.article_fullchannel?.channel_name}
                        />
                      </Tooltip>
                    </div>
                    <div style={{ float: 'right' }}>
                      <Button
                        size="small"
                        startIcon={<ChatBubbleOutlineOutlined />}
                        className={classes.button}
                      >
                        {article.article_comments || 0}
                      </Button>
                      <Button
                        size="small"
                        startIcon={<ShareOutlined />}
                        className={classes.button}
                      >
                        {article.article_shares || 0}
                      </Button>
                      <Button
                        size="small"
                        startIcon={<FavoriteBorderOutlined />}
                        className={classes.button}
                      >
                        {article.article_likes || 0}
                      </Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </GridLegacy>
          );
        })}
      </GridLegacy>
    </div>
  );
};

export default Articles;
