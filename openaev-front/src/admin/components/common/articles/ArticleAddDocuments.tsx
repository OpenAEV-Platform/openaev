import { ControlPointOutlined, DescriptionOutlined } from '@mui/icons-material';
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
import { useContext, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchDocuments } from '../../../../actions/Document';
import type { DocumentHelper } from '../../../../actions/helper';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import SearchFilter from '../../../../components/SearchFilter';
import { useHelper } from '../../../../store';
import { type Document, type Tag } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { truncate } from '../../../../utils/String';
import CreateDocument from '../../components/documents/CreateDocument';
import { PermissionsContext } from '../Context';
import TagsFilter from '../filters/TagsFilter';
import { isMimeTypeValid, matchesSearch, matchesTags } from './ArticleUtils';

const useStyles = makeStyles()(theme => ({
  box: {
    width: '100%',
    minHeight: '100%',
    padding: 20,
    border: `1px dashed ${theme.palette.divider}`,
  },
  chip: { margin: '0 10px 10px 0' },
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
  const [tags, setTags] = useState<Tag[]>();

  // Fetching data
  const { documents } = useHelper((helper: DocumentHelper) => ({ documents: helper.getDocumentsMap() }));
  useDataLoader(() => {
    dispatch(fetchDocuments());
  });

  const handleOpen = () => {
    setOpen(true);
  };

  const handleClose = () => {
    setOpen(false);
    setKeyword('');
    setDocumentsIds([]);
  };

  const handleSearchDocuments = (value: string | undefined) => {
    setKeyword(value ?? '');
  };

  const handleAddTag = (value: Tag | null) => {
    if (value) {
      setTags([value]);
    }
  };

  const handleClearTag = () => {
    setTags([]);
  };

  const addDocument = (documentId: string) => {
    setDocumentsIds(prev => [...prev, documentId]);
  };

  const removeDocument = (documentId: string) => {
    setDocumentsIds(prev => prev.filter(id => id !== documentId));
  };

  const submitAddDocuments = () => {
    if (documentsIds) {
      handleAddDocuments(documentsIds);
      handleClose();
    }
  };

  const onCreate = (result: Document) => {
    addDocument(result.document_id);
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
      .filter(doc =>
        matchesTags(doc.document_tags, tags ?? [])
        && matchesSearch(doc, keyword)
        && isMimeTypeValid(doc.document_type, allowedMimeTypes),
      )
      .slice(0, 10);
  }, [documents, tags, keyword, allowedMimeTypes]);

  // Context
  const { permissions } = useContext(PermissionsContext);

  return (
    (
      <div>
        <ListItemButton
          classes={{ root: classes.item }}
          divider
          onClick={handleOpen}
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
        <Dialog
          open={open}
          onClose={handleClose}
          fullWidth
          maxWidth="lg"
          slots={{ transition: Transition }}
          slotProps={{
            paper: {
              elevation: 1,
              sx: {
                minHeight: 580,
                maxHeight: 580,
              },
            },
          }}
        >
          <DialogTitle>{t('Add documents in this media pressure article')}</DialogTitle>
          <DialogContent>
            <Grid container spacing={3}>
              <Grid size={{ xs: 8 }}>
                <Grid container spacing={3}>
                  <Grid size={{ xs: 6 }}>
                    <SearchFilter
                      onChange={value => handleSearchDocuments(value)}
                      fullWidth
                    />
                  </Grid>
                  <Grid size={{ xs: 6 }}>
                    <TagsFilter
                      onAddTag={handleAddTag}
                      onClearTag={handleClearTag}
                      currentTags={tags}
                      fullWidth
                    />
                  </Grid>
                </Grid>
                <List>
                  {finalDocuments.map((document) => {
                    const disabled = documentsIds.includes(document.document_id)
                      || articleDocumentsIds.includes(document.document_id);
                    return (
                      (
                        <ListItemButton
                          key={document.document_id}
                          disabled={disabled}
                          divider
                          dense
                          onClick={() => addDocument(document.document_id)}
                        >
                          <ListItemIcon>
                            <DescriptionOutlined />
                          </ListItemIcon>
                          <ListItemText
                            primary={document.document_name}
                            secondary={document.document_description}
                          />
                          <ItemTags
                            variant="list"
                            tags={document.document_tags}
                          />
                        </ListItemButton>
                      )
                    );
                  })}
                  <CreateDocument
                    inline
                    onCreate={onCreate}
                    filters={filters}
                  />
                </List>
              </Grid>
              <Grid size={{ xs: 4 }}>
                <Box className={classes.box}>
                  {documentsIds.map((documentId) => {
                    const document = documents[documentId];
                    return (
                      <Chip
                        key={documentId}
                        onDelete={() => removeDocument(documentId)}
                        label={truncate(document?.document_name, 22)}
                        icon={<DescriptionOutlined />}
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
              onClick={submitAddDocuments}
            >
              {t('Add')}
            </Button>
          </DialogActions>
        </Dialog>
      </div>
    )
  );
};

export default ArticleAddDocuments;
