import { ChatBubbleOutlineOutlined, FavoriteBorderOutlined, NewspaperOutlined, ShareOutlined, VisibilityOutlined } from '@mui/icons-material';
import {
  Avatar, Button, Card, CardContent, CardHeader, CardMedia, Chip,
  Grid, IconButton, Tooltip, Typography,
} from '@mui/material';
import { green, orange } from '@mui/material/colors';
import { Fragment, type FunctionComponent, useContext, useMemo, useState } from 'react';
import { Link } from 'react-router';

import { type FullArticleStore } from '../../../../actions/channels/Article';
import { type ChannelsHelper } from '../../../../actions/channels/channel-helper';
import { type DocumentHelper } from '../../../../actions/helper';
import Empty from '../../../../components/Empty';
import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import ChannelColor from '../../../../public/components/channels/ChannelColor';
import { useHelper } from '../../../../store';
import { type Article, type Channel, type Document } from '../../../../utils/api-types';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import useSearchAndFilter from '../../../../utils/SortingFiltering';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import ChannelIcon from '../../components/channels/ChannelIcon';
import { type ChannelOption } from '../../components/channels/ChannelOption';
import ChannelsFilter from '../../components/channels/ChannelsFilter';
import { ArticleContext, PermissionsContext } from '../Context';
import ArticlePopover from './ArticlePopover';
import CreateArticle from './CreateArticle';

interface Props { articles: Article[] }
type ArticleWithChannel = Article & { article_fullchannel?: Channel };

const getHeaderDocuments = (article: ArticleWithChannel, docs: Document[]) => {
  if (article.article_fullchannel?.channel_type === 'newspaper') {
    return docs.filter(d => d.document_type.includes('image/'));
  }
  if (article.article_fullchannel?.channel_type === 'tv') {
    return docs.filter(d => d.document_type.includes('video/'));
  }
  return docs.filter(d => d.document_type.includes('image/') || d.document_type.includes('video/'));
};

const getColumnsByDocumentsLength = (documentsLength: number) => {
  if (documentsLength === 2) {
    return 6;
  }
  if (documentsLength === 3) {
    return 4;
  }
  if (documentsLength >= 4) {
    return 3;
  }
  return 12;
};

const Articles: FunctionComponent<Props> = ({ articles }) => {
  // Context
  const { previewArticleUrl, fetchChannels, fetchDocuments } = useContext(ArticleContext);
  const { permissions } = useContext(PermissionsContext);

  // Standard hooks
  const { t } = useFormatter();

  // Fetching data
  const { channelsMap, documentsMap } = useHelper((helper: ChannelsHelper & DocumentHelper) => ({
    channelsMap: helper.getChannelsMap(),
    documentsMap: helper.getDocumentsMap(),
  }));
  useDataLoader(() => {
    fetchChannels();
    fetchDocuments();
  });

  // Creation
  const [openCreate, setOpenCreate] = useState(false);

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
  const fullArticles = useMemo<ArticleWithChannel[]>(() => (
    articles.map(item => ({
      ...item,
      article_fullchannel: item.article_channel ? channelsMap[item.article_channel] : undefined,
    }))
  ), [articles, channelsMap]);

  const selectedChannelIds = useMemo(() => channels.map(c => c.id), [channels]);

  const sortedArticles = useMemo<ArticleWithChannel[]>(() => (
    filtering
      .filterAndSort(fullArticles)
      .filter((article: ArticleWithChannel) => {
        if (selectedChannelIds.length === 0) {
          return true;
        }

        const articleChannelId = article.article_fullchannel?.channel_id;
        return articleChannelId ? selectedChannelIds.includes(articleChannelId) : false;
      })
  ), [filtering, fullArticles, selectedChannelIds]);

  return (
    <div>
      <Typography variant="h4" gutterBottom sx={{ float: 'left' }}>
        {t('Media pressure')}
      </Typography>
      {permissions.canManage && (
        <CreateArticle
          openCreate={openCreate}
          isOpen={setOpenCreate}
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
                  sx={{ mt: 2.5 }}
                  startIcon={<NewspaperOutlined />}
                  variant="outlined"
                  color="primary"
                  size="small"
                  onClick={() => setOpenCreate(true)}
                >
                  {t('Create an article')}
                </Button>
              )}
          </div>
        )}
        />
      )}
      <Grid container spacing={3}>
        {sortedArticles.map((article, index) => {
          const docs = (article.article_documents ?? [])
            .map(docId => documentsMap[docId])
            .filter((doc): doc is Document => doc !== undefined);
          const headerDocs = getHeaderDocuments(article, docs);
          const columns = getColumnsByDocumentsLength(headerDocs.length);
          const channelColor = ChannelColor(article.article_fullchannel?.channel_type);
          const previewUrl = article.article_fullchannel
            ? previewArticleUrl(article as FullArticleStore)
            : '#';

          return (
            <Grid key={article.article_id} size={{ xs: 4 }} sx={index < 3 ? { pt: 0 } : undefined}>
              <Card
                variant="outlined"
                sx={{
                  position: 'relative',
                  width: '100%',
                  height: '100%',
                }}
              >
                <CardHeader
                  avatar={(
                    <Avatar
                      sx={{ bgcolor: channelColor }}
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
                        to={previewUrl}
                        disabled={!article.article_fullchannel}
                      >
                        <VisibilityOutlined />
                      </IconButton>
                      <ArticlePopover article={article} />
                    </Fragment>
                  )}
                />
                <Grid container={true} spacing={3}>
                  {headerDocs.map(doc => (
                    <Grid key={doc.document_id} size={{ xs: columns }}>
                      {doc.document_type.includes('image/') && (
                        <CardMedia
                          component="img"
                          height="150"
                          src={buildTenantApiPath(`/api/documents/${doc.document_id}/file`)}
                        />
                      )}
                      {doc.document_type.includes('video/') && (
                        <CardMedia
                          component="video"
                          height="150"
                          src={buildTenantApiPath(`/api/documents/${doc.document_id}/file`)}
                          controls
                        />
                      )}
                    </Grid>
                  ))}
                </Grid>
                <CardContent sx={{ mb: 3.75 }}>
                  <Typography
                    gutterBottom
                    variant="h1"
                    component="div"
                    sx={{
                      margin: '0 auto',
                      textAlign: 'center',
                    }}
                  >
                    {article.article_name}
                  </Typography>
                  <ExpandableMarkdown source={article.article_content ?? ''} limit={500} />
                  <div
                    style={{
                      width: '100%',
                      position: 'absolute',
                      padding: '0 15px',
                      left: 0,
                      bottom: 10,
                    }}
                  >
                    <div style={{ float: 'left' }}>
                      <Tooltip title={article.article_fullchannel?.channel_name}>
                        <Chip
                          icon={(
                            <ChannelIcon
                              type={article.article_fullchannel?.channel_type}
                              variant="chip"
                            />
                          )}
                          sx={{
                            fontSize: 12,
                            float: 'left',
                            mr: 1,
                            maxWidth: 300,
                            borderRadius: 1,
                            color: channelColor,
                            borderColor: channelColor,
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
                        sx={{ cursor: 'default' }}
                      >
                        {article.article_comments || 0}
                      </Button>
                      <Button
                        size="small"
                        startIcon={<ShareOutlined />}
                        sx={{ cursor: 'default' }}
                      >
                        {article.article_shares || 0}
                      </Button>
                      <Button
                        size="small"
                        startIcon={<FavoriteBorderOutlined />}
                        sx={{ cursor: 'default' }}
                      >
                        {article.article_likes || 0}
                      </Button>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </Grid>
          );
        })}
      </Grid>
    </div>
  );
};

export default Articles;
