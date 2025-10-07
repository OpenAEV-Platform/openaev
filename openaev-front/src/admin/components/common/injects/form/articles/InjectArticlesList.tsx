import { ControlPointOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { useContext, useEffect, useState } from 'react';
import { useFormContext, useWatch } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../../components/i18n';
import type { Article, Channel } from '../../../../../../utils/api-types';
import { Can } from '../../../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../../../utils/permissions/types';
import ChannelIcon from '../../../../components/channels/ChannelIcon';
import ArticlePopover from '../../../articles/ArticlePopover';
import { ArticleContext } from '../../../Context';
import InjectAddArticlesDialog from './InjectAddArticlesDialog';

const useStyles = makeStyles()(theme => ({
  columns: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr 1fr 1fr',
  },
  bodyItem: {
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    fontSize: theme.typography.h3.fontSize,
  },
  text: {
    fontSize: 15,
    color: theme.palette.primary.main,
    fontWeight: 500,
  },
}));

interface Props {
  readOnly?: boolean;
  allArticles?: Article[];
}

const InjectArticlesList = ({ allArticles = [], readOnly = false }: Props) => {
  const { t } = useFormatter();
  const { control, setValue } = useFormContext();
  const { fetchChannels, fetchArticles } = useContext(ArticleContext);
  const { classes } = useStyles();
  const [openAddArticles, setOpenAddArticles] = useState(false);

  const injectArticlesIds: string[] = (useWatch({
    control,
    name: 'inject_content.articles',
    defaultValue: [],
  }));

  const [sortedArticles, setSortedArticles] = useState<(Article & {
    article_channel_type: string;
    article_channel_name: string;
  })[]>([]);

  useEffect(() => {
    const processArticles = async () => {
      const sortArticles = (articles: Article[], channels: Record<string, Channel>): (Article & {
        article_channel_type: string;
        article_channel_name: string;
      })[] => {
        return articles
          .map(a => ({
            ...a,
            article_channel_type: channels[a.article_channel]?.channel_type ?? '',
            article_channel_name: channels[a.article_channel]?.channel_name ?? '',
          }))
          .toSorted((a, b) => (a.article_name ?? '').localeCompare(b.article_name ?? ''));
      };

      const [channelResult, result] = await Promise.all([
        fetchChannels(),
        fetchArticles(),
      ]);

      const articles = injectArticlesIds.map(id => result.entities.articles[id]).filter(a => a !== undefined);
      setSortedArticles(sortArticles(articles, channelResult.entities.channels));
    };

    processArticles();
  }, [injectArticlesIds]);

  const addArticles = (ids: string[]) => setValue('inject_content.articles', [...ids, ...injectArticlesIds]);
  const removeArticle = (articleId: string) => setValue('inject_content.articles', injectArticlesIds.filter(id => id !== articleId));

  return (
    <>
      <List>
        {
          sortedArticles.map(article => (
            <ListItem
              key={article.article_id}
              divider
              secondaryAction={(
                <ArticlePopover
                  article={article}
                  onRemoveArticle={removeArticle}
                  disabled={readOnly}
                />
              )}
            >
              <ListItemIcon>
                <ChannelIcon
                  type={article.article_channel_type}
                  variant="inline"
                />
              </ListItemIcon>
              <ListItemText
                primary={(
                  <div className={classes.columns}>
                    <div className={classes.bodyItem}>
                      {t(article.article_channel_type || 'Unknown')}
                    </div>
                    <div className={classes.bodyItem}>
                      {article.article_channel_name}
                    </div>
                    <div className={classes.bodyItem}>
                      {article.article_name}
                    </div>
                    <div className={classes.bodyItem}>
                      {article.article_author}
                    </div>
                  </div>
                )}
              />
            </ListItem>
          ))
        }
      </List>
      <Can I={ACTIONS.ACCESS} a={SUBJECTS.DOCUMENTS}>
        <ListItemButton
          divider
          onClick={() => setOpenAddArticles(true)}
          color="primary"
          disabled={readOnly}
        >
          <ListItemIcon color="primary">
            <ControlPointOutlined color="primary" />
          </ListItemIcon>
          <ListItemText
            primary={t('Add media pressure')}
            classes={{ primary: classes.text }}
          />
        </ListItemButton>
        {openAddArticles && (
          <InjectAddArticlesDialog
            open={openAddArticles}
            onHandleClose={() => setOpenAddArticles(false)}
            articles={allArticles || []}
            injectArticlesIds={injectArticlesIds ?? []}
            handleAddArticles={addArticles}
            handleRemoveArticle={removeArticle}
          />
        )}
      </Can>
    </>
  );
};

export default InjectArticlesList;
