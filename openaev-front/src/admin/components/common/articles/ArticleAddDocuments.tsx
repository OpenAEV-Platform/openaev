import { ControlPointOutlined, DescriptionOutlined } from '@mui/icons-material';
import { Box, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { useContext, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchDocuments } from '../../../../actions/Document';
import type { DocumentHelper } from '../../../../actions/helper';
import SelectListPicker, { type SelectListPickerElements } from '../../../../components/common/SelectListPicker';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import SearchFilter from '../../../../components/SearchFilter';
import { useHelper } from '../../../../store';
import { type Document, type Tag } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import CreateDocument from '../../components/documents/CreateDocument';
import { PermissionsContext } from '../Context';
import TagsFilter from '../filters/TagsFilter';
import { isMimeTypeValid, matchesSearch, matchesTags } from './ArticleUtils';

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

export type ChannelType = 'newspaper' | 'tv' | 'microblogging';

interface ArticleAddDocumentsProps {
  handleAddDocuments: (docsIds: string[]) => void;
  articleDocumentsIds: string[];
  channelType: ChannelType;
}

const ArticleAddDocuments = ({ handleAddDocuments, articleDocumentsIds, channelType }: ArticleAddDocumentsProps) => {
  // Standard hooks
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const { t } = useFormatter();

  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [documentsIds, setDocumentsIds] = useState<string[]>([]);
  const [tags, setTags] = useState<Tag[]>([]);

  // Fetching data
  const { documents } = useHelper((helper: DocumentHelper) => ({ documents: helper.getDocumentsMap() }));
  useDataLoader(() => {
    dispatch(fetchDocuments());
  });

  const handleClose = () => {
    setOpen(false);
    setKeyword('');
    setDocumentsIds([]);
  };

  const toggleDocument = (documentId: string) => {
    setDocumentsIds(prev => (prev.includes(documentId)
      ? prev.filter(id => id !== documentId)
      : [...prev, documentId]));
  };

  const submitAddDocuments = () => {
    handleAddDocuments(documentsIds);
    handleClose();
  };

  const onCreate = (result: Document) => {
    setDocumentsIds(prev => [...prev, result.document_id]);
  };

  const allowedMimeTypes = useMemo(() => {
    switch (channelType) {
      case 'newspaper': return ['image/'];
      case 'microblogging': return ['image/', 'video/'];
      case 'tv': return ['video/'];
      default: return [];
    }
  }, [channelType]);

  const filters = allowedMimeTypes.length > 0 ? allowedMimeTypes : null;

  const finalDocuments = useMemo(() => {
    const allDocuments: Document[] = Object.values(documents);
    return allDocuments
      .filter(doc => matchesTags(doc.document_tags, tags)
        && matchesSearch(doc, keyword)
        && isMimeTypeValid(doc.document_type, allowedMimeTypes))
      .slice(0, 20);
  }, [documents, tags, keyword, allowedMimeTypes]);

  // Context
  const { permissions } = useContext(PermissionsContext);

  const elements: SelectListPickerElements<Document> = useMemo(() => ({
    icon: { value: () => <DescriptionOutlined /> },
    headers: [
      {
        field: 'document_name',
        label: 'Name',
        isSortable: true,
        value: document => document.document_name ?? '',
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
        onChange={(value: string | undefined) => setKeyword(value ?? '')}
        fullWidth
      />
      <TagsFilter
        onAddTag={(value: Tag | null) => {
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
