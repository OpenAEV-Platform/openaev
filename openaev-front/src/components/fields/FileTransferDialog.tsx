import { DescriptionOutlined } from '@mui/icons-material';
import { Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, GridLegacy, List, ListItem, ListItemIcon, ListItemText } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type DocumentHelper, type UserHelper } from '../../actions/helper';
import TagsFilter from '../../admin/components/common/filters/TagsFilter';
import CreateDocument from '../../admin/components/components/documents/CreateDocument';
import { useHelper } from '../../store';
import { type RawDocument } from '../../utils/api-types';
import { Can } from '../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import { truncate } from '../../utils/String';
import Transition from '../common/Transition';
import { useFormatter } from '../i18n';
import ItemTags from '../ItemTags';
import SearchFilter from '../SearchFilter';

const useStyles = makeStyles()(theme => ({
  box: {
    width: '100%',
    minHeight: '100%',
    padding: 20,
    border: '1px dashed rgba(255, 255, 255, 0.3)',
  },
  chip: { margin: '0 10px 10px 0' },
  item: {
    'paddingLeft': 10,
    'height': 50,
    'cursor': 'pointer',
    '&:hover': { backgroundColor: theme.palette.action?.hover },
  },
}));

interface Props {
  label: string;
  open: boolean;
  setOpen: (open: boolean) => void;
  onAddDocument?: (document: RawDocument) => void;
  extensions?: string[];
  /* If we want to load multiples files */
  multiple?: boolean;
  initialDocumentIds?: string[];
  onSubmitAddDocuments?: (documents: RawDocument[]) => void;
}

const FileTransferDialog: FunctionComponent<Props> = ({
  label,
  open,
  setOpen,
  onAddDocument,
  extensions = [],
  multiple = false,
  initialDocumentIds = [],
  onSubmitAddDocuments,
}) => {
  const { classes } = useStyles();
  const { t } = useFormatter();

  const [keyword, setKeyword] = useState<string>('');
  const [tags, setTags] = useState<{
    id: string;
    label: string;
    color: string;
  }[]>([]);
  const [selectedDocuments, setSelectedDocuments] = useState<RawDocument[]>([]);

  // Fetching data
  const { documents }: { documents: [RawDocument] } = useHelper((helper: DocumentHelper & UserHelper) => ({ documents: helper.getDocuments() }));

  useEffect(() => {
    if (initialDocumentIds.length > 0) {
      setSelectedDocuments(documents.filter((document) => {
        const docId = document.document_id;
        return docId && initialDocumentIds.includes(docId);
      }));
    }
  }, [initialDocumentIds]);

  const handleSearchDocuments = (value?: string) => {
    setKeyword(value || '');
  };

  const handleAddTag = (value: {
    id: string;
    label: string;
    color: string;
  }) => {
    if (!tags.includes(value)) {
      setTags([...tags, value]);
    }
  };

  const handleClearTag = () => setTags([]);

  const handleClose = () => {
    setOpen(false);
    setTags([]);
    setKeyword('');
    setSelectedDocuments([]);
  };

  const handleAddDocument = (document: RawDocument) => {
    if (!selectedDocuments.some(doc => doc.document_id === document.document_id)) {
      setSelectedDocuments([...selectedDocuments, document]);
    }
    if (!multiple && onAddDocument) {
      onAddDocument(document);
      handleClose();
    }
  };

  const handleSubmitAddDocuments = () => {
    if (onSubmitAddDocuments) {
      onSubmitAddDocuments(selectedDocuments);
    }
    handleClose();
  };

  const handleRemoveDocument = (document: RawDocument) => {
    setSelectedDocuments(selectedDocuments.filter(doc => doc.document_id !== document.document_id));
  };

  const filterByExtensions = (document: RawDocument) => {
    return extensions?.length === 0
      || extensions?.map(ext => ext.toLowerCase()).includes(document.document_name?.split('.').pop()?.toLowerCase() || '');
  };

  const filterByKeyword = (document: RawDocument) => {
    return keyword === ''
      || document.document_name?.toLowerCase().includes(keyword.toLowerCase())
      || document.document_description?.toLowerCase().includes(keyword.toLowerCase())
      || document.document_type?.toLowerCase().includes(keyword.toLowerCase());
  };

  const filterByTag = (document: RawDocument) => {
    return tags.length === 0 || tags.every(tag => document.document_tags?.includes(tag.id));
  };

  const filteredDocuments = documents.filter((document) => {
    const isInitialValue = document.document_id && initialDocumentIds?.includes(document.document_id);
    return !isInitialValue
      && filterByExtensions(document)
      && filterByKeyword(document)
      && filterByTag(document);
  }).slice(0, 10);

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      fullWidth
      maxWidth="lg"
      PaperProps={{
        elevation: 1,
        sx: {
          minHeight: 580,
          maxHeight: 580,
        },
      }}
      TransitionComponent={Transition}
    >
      <DialogTitle>{t(label)}</DialogTitle>
      <DialogContent>
        <GridLegacy container spacing={3}>
          <GridLegacy item xs={multiple ? 8 : 12}>
            <GridLegacy container spacing={3}>
              <GridLegacy item xs={6}>
                <SearchFilter
                  onChange={handleSearchDocuments}
                  fullWidth
                />
              </GridLegacy>
              <GridLegacy item xs={6}>
                <TagsFilter
                  onAddTag={handleAddTag}
                  onClearTag={handleClearTag}
                  currentTags={tags}
                  fullWidth
                />
              </GridLegacy>
            </GridLegacy>
            <List>
              {filteredDocuments.map((document: RawDocument) => {
                return (
                  <ListItem
                    classes={{ root: classes.item }}
                    key={document.document_id}
                    divider
                    onClick={() => handleAddDocument(document)}
                  >
                    <ListItemIcon>
                      <DescriptionOutlined />
                    </ListItemIcon>
                    <ListItemText
                      primary={document.document_name}
                      secondary={document.document_description}
                    />
                    <ItemTags
                      variant="reduced-view"
                      tags={document.document_tags}
                    />
                  </ListItem>
                );
              })}
              <Can I={ACTIONS.MANAGE} a={SUBJECTS.DOCUMENTS}>
                <CreateDocument
                  inline
                  onCreate={handleAddDocument}
                />
              </Can>
            </List>
          </GridLegacy>
          {multiple && (
            <GridLegacy item xs={4}>
              <Box className={classes.box}>
                {selectedDocuments.map(document => (
                  <Chip
                    key={document.document_id}
                    variant="outlined"
                    onDelete={() => handleRemoveDocument(document)}
                    label={truncate(document?.document_name, 15)}
                    icon={<DescriptionOutlined />}
                    classes={{ root: classes.chip }}
                  />
                ))}
              </Box>
            </GridLegacy>
          )}
        </GridLegacy>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>{t('Cancel')}</Button>
        {multiple && (
          <Button
            color="secondary"
            onClick={handleSubmitAddDocuments}
          >
            {t('Add')}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default FileTransferDialog;
