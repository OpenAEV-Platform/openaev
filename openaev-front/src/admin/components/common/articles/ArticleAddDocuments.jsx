import { ControlPointOutlined, DescriptionOutlined } from '@mui/icons-material';
import { Box, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import * as R from 'ramda';
import { useContext, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchDocuments } from '../../../../actions/Document';
import SelectListPicker from '../../../../components/common/SelectListPicker';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import SearchFilter from '../../../../components/SearchFilter';
import { useHelper } from '../../../../store';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import CreateDocument from '../../components/documents/CreateDocument';
import { PermissionsContext } from '../Context';
import TagsFilter from '../filters/TagsFilter';

const useStyles = makeStyles()(theme => ({
  item: {
    paddingLeft: 10,
    height: 50,
  },
  text: {
    fontSize: 15,
    color: theme.palette.primary.main,
    fontWeight: 500,
  },
}));

const ArticleAddDocuments = (props) => {
  const { handleAddDocuments, articleDocumentsIds, channelType } = props;
  // Standard hooks
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const { t } = useFormatter();

  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [documentsIds, setDocumentsIds] = useState([]);
  const [tags, setTags] = useState([]);

  // Fetching data
  const { documents } = useHelper(helper => ({ documents: helper.getDocumentsMap() }));
  useDataLoader(() => {
    dispatch(fetchDocuments());
  });

  const handleClose = () => {
    setOpen(false);
    setKeyword('');
    setDocumentsIds([]);
  };

  const toggleDocument = (documentId) => {
    if (documentsIds.includes(documentId)) {
      setDocumentsIds(documentsIds.filter(id => id !== documentId));
    } else {
      setDocumentsIds([...documentsIds, documentId]);
    }
  };

  const submitAddDocuments = () => {
    handleAddDocuments(documentsIds);
    handleClose();
  };

  const onCreate = (result) => {
    setDocumentsIds(prev => [...prev, result.document_id]);
  };

  const filterByKeyword = n => keyword === ''
    || (n.document_name || '').toLowerCase().indexOf(keyword.toLowerCase())
    !== -1
    || (n.document_description || '')
      .toLowerCase()
      .indexOf(keyword.toLowerCase()) !== -1
      || (n.document_type || '').toLowerCase().indexOf(keyword.toLowerCase())
      !== -1;
  const filteredDocuments = R.pipe(
    R.filter(
      n => tags.length === 0
        || R.any(
          filter => R.includes(filter, n.document_tags),
          R.pluck('id', tags),
        ),
    ),
    R.filter(filterByKeyword),
  )(Object.values(documents));
  let finalDocuments = filteredDocuments;
  let filters = null;
  if (channelType === 'newspaper') {
    finalDocuments = filteredDocuments.filter(d => d.document_type.includes('image/'));
    filters = ['image/'];
  } else if (channelType === 'microblogging') {
    finalDocuments = filteredDocuments.filter(
      d => d.document_type.includes('image/')
        || d.document_type.includes('video/'),
    );
    filters = ['image/', 'video/'];
  } else if (channelType === 'tv') {
    finalDocuments = filteredDocuments.filter(d => d.document_type.includes('video/'));
    filters = ['video/'];
  }
  finalDocuments = R.take(20, finalDocuments);

  // Context
  const { permissions } = useContext(PermissionsContext);

  const elements = useMemo(() => ({
    icon: { value: () => <DescriptionOutlined /> },
    headers: [
      {
        field: 'document_name',
        label: 'Name',
        isSortable: true,
        value: document => document.document_name,
        width: 45,
      },
      {
        field: 'document_description',
        label: 'Description',
        isSortable: true,
        value: document => document.document_description ?? '',
        width: 30,
      },
      {
        field: 'document_tags',
        label: 'Tags',
        value: document => <ItemTags variant="list" limit={1} tags={document.document_tags} />,
        width: 25,
      },
    ],
  }), []);

  const headerComponent = (
    <Box sx={{
      display: 'flex',
      gap: 1,
    }}
    >
      <SearchFilter
        onChange={value => setKeyword(value || '')}
        fullWidth
      />
      <TagsFilter
        onAddTag={(value) => {
          if (value) {
            setTags([value]);
          }
        }}
        onClearTag={() => setTags([])}
        currentTags={tags}
        fullWidth
      />
    </Box>
  );

  return (
    <div>
      <ListItemButton
        classes={{ root: classes.item }}
        divider
        onClick={() => setOpen(true)}
        color="primary"
        disabled={!permissions.canManage}
      >
        <ListItemIcon color="primary">
          <ControlPointOutlined color="primary" />
        </ListItemIcon>
        <ListItemText
          primary={t('Add documents')}
          classes={{ primary: classes.text }}
        />
      </ListItemButton>
      {/* Inline dialog: the article form always lives in a drawer or dialog
          (never drawer over drawer). */}
      <SelectListPicker
        open={open}
        onClose={handleClose}
        onSubmit={submitAddDocuments}
        title={t('Add documents in this media pressure article')}
        submitLabel={t('Add')}
        inline
        headerComponent={headerComponent}
        values={finalDocuments}
        elements={elements}
        selectedIds={documentsIds}
        lockedIds={articleDocumentsIds}
        onToggle={toggleDocument}
        getId={element => element.document_id}
        buttonComponent={(
          <CreateDocument
            inline
            onCreate={onCreate}
            filters={filters}
          />
        )}
      />
    </div>
  );
};

export default ArticleAddDocuments;
