import * as R from 'ramda';
import { type FunctionComponent, useContext, useMemo, useState } from 'react';

import { type FullArticleStore } from '../../../../../../actions/channels/Article';
import { fetchChannels } from '../../../../../../actions/channels/channel-action';
import { type ChannelsHelper } from '../../../../../../actions/channels/channel-helper';
import SelectListPicker, { type SelectListPickerElements } from '../../../../../../components/common/SelectListPicker';
import { useFormatter } from '../../../../../../components/i18n';
import SearchFilter from '../../../../../../components/SearchFilter';
import { useHelper } from '../../../../../../store';
import { type Article } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import useDataLoader from '../../../../../../utils/hooks/useDataLoader';
import ChannelIcon from '../../../../components/channels/ChannelIcon';
import CreateArticle from '../../../articles/CreateArticle';
import { PermissionsContext } from '../../../Context';

interface Props {
  open: boolean;
  onHandleClose: () => void;
  articles: Article[];
  handleAddArticles: (articleIds: string[]) => void;
  handleRemoveArticle: (articleId: string) => void;
  injectArticlesIds: string[];
  disabled?: boolean;
}

// Always rendered as an inline dialog: the inject form is already a drawer
// (never drawer over drawer).
const InjectAddArticles: FunctionComponent<Props> = ({
  open,
  onHandleClose,
  articles,
  handleAddArticles,
  handleRemoveArticle,
  injectArticlesIds,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { permissions } = useContext(PermissionsContext);

  const { channelsMap } = useHelper((helper: ChannelsHelper) => ({ channelsMap: helper.getChannelsMap() }));

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

  const toggleArticle = (articleId: string) => {
    if (articleIds.includes(articleId)) {
      setArticleIds(articleIds.filter(u => u !== articleId));
    } else if (injectArticlesIds.includes(articleId)) {
      handleRemoveArticle(articleId);
    } else {
      setArticleIds(R.append(articleId, articleIds));
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
    setArticleIds(prev => [...prev, result]);
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
    R.take(20),
  )(fullArticles);

  const elements: SelectListPickerElements<FullArticleStore> = useMemo(() => ({
    icon: {
      value: (article: FullArticleStore) => (
        <ChannelIcon
          type={article.article_fullchannel?.channel_type}
          variant="inline"
        />
      ),
    },
    headers: [
      {
        field: 'article_name',
        label: 'Name',
        isSortable: true,
        value: (article: FullArticleStore) => article.article_name ?? '',
        width: 60,
      },
      {
        field: 'article_author',
        label: 'Author',
        isSortable: true,
        value: (article: FullArticleStore) => article.article_author ?? '',
        width: 40,
      },
    ],
  }), []);

  const headerComponent = (
    <SearchFilter
      onChange={(value?: string) => setKeyword(value || '')}
      fullWidth
    />
  );

  return (
    <SelectListPicker<FullArticleStore>
      open={open}
      onClose={handleClose}
      onSubmit={submitAddArticles}
      title={t('Add media pressure in this inject')}
      submitLabel={t('Add')}
      inline
      headerComponent={headerComponent}
      values={filteredArticles}
      elements={elements}
      selectedIds={[...injectArticlesIds, ...articleIds]}
      onToggle={toggleArticle}
      getId={element => element.article_id}
      buttonComponent={permissions.canManage
        ? (
            <CreateArticle
              inline
              openCreate={openCreate}
              onCreate={onCreate}
              handleOpenCreate={handleOpenCreate}
              handleCloseCreate={handleCloseCreate}
            />
          )
        : undefined}
    />
  );
};

export default InjectAddArticles;
