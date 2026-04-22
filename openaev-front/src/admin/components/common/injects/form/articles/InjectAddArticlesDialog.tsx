import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
} from '@mui/material';
import * as R from 'ramda';
import { type FunctionComponent, useContext, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type FullArticleStore } from '../../../../../../actions/channels/Article';
import { type ArticlesHelper } from '../../../../../../actions/channels/article-helper';
import { fetchChannels } from '../../../../../../actions/channels/channel-action';
import { type ChannelsHelper } from '../../../../../../actions/channels/channel-helper';
import Transition from '../../../../../../components/common/Transition';
import { useFormatter } from '../../../../../../components/i18n';
import SearchFilter from '../../../../../../components/SearchFilter';
import { useHelper } from '../../../../../../store';
import { type Article } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import useDataLoader from '../../../../../../utils/hooks/useDataLoader';
import { truncate } from '../../../../../../utils/String';
import ChannelIcon from '../../../../components/channels/ChannelIcon';
import CreateArticle from '../../../articles/CreateArticle';
import { PermissionsContext } from '../../../Context';

const useStyles = makeStyles()(theme => ({
  box: {
    width: '100%',
    minHeight: '100%',
    padding: 20,
    border: `1px dashed ${theme.palette.divider}`,
  },
  chip: { margin: `0 ${theme.spacing(2)}px ${theme.spacing(2)}px 0` },
}));

interface Props {
  open: boolean;
  onHandleClose: () => void;
  articles: Article[];
  handleAddArticles: (articleIds: string[]) => void;
  handleRemoveArticle: (articleId: string) => void;
  injectArticlesIds: string[];
  disabled?: boolean;
}

const InjectAddArticlesDialog: FunctionComponent<Props> = ({
  open,
  onHandleClose,
  articles,
  handleAddArticles,
  handleRemoveArticle,
  injectArticlesIds,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { permissions } = useContext(PermissionsContext);

  const { articlesMap, channelsMap } = useHelper((helper: ArticlesHelper & ChannelsHelper) => ({
    articlesMap: helper.getArticlesMap(),
    channelsMap: helper.getChannelsMap(),
  }));

  useDataLoader(() => {
    dispatch(fetchChannels());
  });

  const [keyword, setKeyword] = useState('');
  const [articleIds, setArticleIds] = useState<string[]>([]);

  const handleClose = () => {
    onHandleClose();
    setKeyword('');
    setArticleIds([]);
  };

  const handleSearchArticles = (value?: string) => {
    setKeyword(value || '');
  };

  const addArticle = (articleId: string) => setArticleIds(R.append(articleId, articleIds));

  const removeArticle = (articleId: string) => {
    if (articleIds.includes(articleId)) {
      setArticleIds(articleIds.filter(u => u !== articleId));
    } else if (injectArticlesIds.includes(articleId)) {
      handleRemoveArticle(articleId);
    }
  };

  const submitAddArticles = () => {
    handleAddArticles(articleIds);
    handleClose();
  };

  // Creation
  const [openCreate, setOpenCreate] = useState(false);
  const handleOpenCreate = () => setOpenCreate(true);
  const handleCloseCreate = () => setOpenCreate(false);
  const onCreate = (result: string) => {
    addArticle(result);
  };

  const filterByKeyword = (n: FullArticleStore) => keyword === ''
    || (n.article_name || '').toLowerCase().indexOf(keyword.toLowerCase())
    !== -1
    || (n.article_fullchannel?.channel_name || '')
      .toLowerCase()
      .indexOf(keyword.toLowerCase()) !== -1;
  const fullArticles = articles.map(item => ({
    ...item,
    article_fullchannel: item.article_channel ? channelsMap[item.article_channel] : {},
  }));
  const filteredArticles = R.pipe(
    R.filter(filterByKeyword),
    R.take(10),
  )(fullArticles);
  return (
    <Dialog
      open={open}
      onClose={handleClose}
      fullWidth
      maxWidth="lg"
      slots={{ transition: Transition }}
      slotProps={{
        paper: {
          elevation: 1,
          style: {
            minHeight: 580,
            maxHeight: 580,
          },
        },
      }}
    >
      <DialogTitle>{t('Add media pressure in this inject')}</DialogTitle>
      <DialogContent>
        <Grid container spacing={3}>
          <Grid size={{ xs: 8 }}>
            <Grid container spacing={3}>
              <Grid size={{ xs: 6 }}>
                <SearchFilter
                  onChange={handleSearchArticles}
                  fullWidth
                />
              </Grid>
            </Grid>
            <List>
              {filteredArticles.map((article: FullArticleStore) => {
                const disabled = articleIds.includes(article.article_id)
                  || injectArticlesIds.includes(article.article_id);
                return (
                  <ListItemButton
                    key={article.article_id}
                    disabled={disabled}
                    divider
                    dense
                    onClick={() => addArticle(article.article_id)}
                  >
                    <ListItemIcon>
                      <ChannelIcon
                        type={article.article_fullchannel?.channel_type}
                        variant="inline"
                      />
                    </ListItemIcon>
                    <ListItemText
                      primary={article.article_name}
                      secondary={article.article_author}
                    />
                  </ListItemButton>
                );
              })}
              {permissions.canManage
                && (
                  <CreateArticle
                    inline
                    openCreate={openCreate}
                    onCreate={onCreate}
                    handleOpenCreate={handleOpenCreate}
                    handleCloseCreate={handleCloseCreate}
                  />
                )}
            </List>
          </Grid>
          <Grid size={{ xs: 4 }}>
            <Box className={classes.box}>
              {[...injectArticlesIds, ...articleIds].map((articleId) => {
                const article = articlesMap[articleId];
                const channel = article
                  ? channelsMap[article.article_channel] || {}
                  : {};
                return (
                  <Chip
                    key={articleId}
                    onDelete={() => removeArticle(articleId)}
                    label={truncate(article?.article_name, 22)}
                    icon={
                      <ChannelIcon type={channel.channel_type} variant="chip" />
                    }
                    classes={{ root: classes.chip }}
                  />
                );
              })}
            </Box>
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>{t('Cancel')}</Button>
        <Button
          color="secondary"
          onClick={submitAddArticles}
        >
          {t('Add')}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default InjectAddArticlesDialog;
