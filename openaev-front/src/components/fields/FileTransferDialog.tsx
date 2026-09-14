import { DescriptionOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import { type DocumentHelper, type UserHelper } from '../../actions/helper';
import TagsFilter from '../../admin/components/common/filters/TagsFilter';
import CreateDocument from '../../admin/components/components/documents/CreateDocument';
import { useHelper } from '../../store';
import { type RawDocument } from '../../utils/api-types';
import { type Option } from '../../utils/Option';
import { Can } from '../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import SelectListPicker, { type SelectListPickerElements } from '../common/SelectListPicker';
import { useFormatter } from '../i18n';
import ItemTags from '../ItemTags';
import SearchFilter from '../SearchFilter';

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

/**
 * Document picker rendered as a design-system dialog (SelectListPicker inline:
 * it usually opens above a drawer form, never drawer over drawer). Single mode
 * selects exactly one document, multiple mode toggles a selection.
 */
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
  const { t } = useFormatter();

  const [keyword, setKeyword] = useState<string>('');
  const [tags, setTags] = useState<Option[]>([]);
  const [selectedDocuments, setSelectedDocuments] = useState<RawDocument[]>([]);

  // Fetching data
  const { documents }: { documents: [RawDocument] } = useHelper((helper: DocumentHelper & UserHelper) => ({ documents: helper.getDocuments() }));

  useEffect(() => {
    // If initial data hasn't arrived yet, do nothing
    if (initialDocumentIds.length === 0) return;

    // If we already have selected documents, don't override user changes
    if (selectedDocuments.length > 0) return;

    // Initialize selectedDocuments from initialDocumentIds (only once)
    setSelectedDocuments(
      documents.filter(
        doc => doc.document_id && initialDocumentIds.includes(doc.document_id),
      ),
    );
  }, [initialDocumentIds]);

  const handleAddTag = (value: Option) => {
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

  const toggleDocument = (documentId: string, document: RawDocument) => {
    const alreadySelected = selectedDocuments.some(doc => doc.document_id === documentId);
    if (multiple) {
      setSelectedDocuments(alreadySelected
        ? selectedDocuments.filter(doc => doc.document_id !== documentId)
        : [...selectedDocuments, document]);
    } else {
      // Single mode: selecting a row replaces the previous selection.
      setSelectedDocuments(alreadySelected ? [] : [document]);
    }
  };

  const handleSubmit = () => {
    if (multiple) {
      onSubmitAddDocuments?.(selectedDocuments);
    } else if (selectedDocuments[0]) {
      onAddDocument?.(selectedDocuments[0]);
    }
    handleClose();
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

  const selectedIds = selectedDocuments
    .map(doc => doc.document_id)
    .filter((id): id is string => !!id);

  const filteredDocuments = documents
    .filter(document => filterByExtensions(document)
      && filterByKeyword(document)
      && filterByTag(document))
    .slice(0, 20);

  const elements: SelectListPickerElements<RawDocument> = useMemo(() => ({
    icon: { value: () => <DescriptionOutlined /> },
    headers: [
      {
        field: 'document_name',
        label: 'Name',
        isSortable: true,
        value: (document: RawDocument) => document.document_name ?? '-',
        width: 35,
      },
      {
        field: 'document_description',
        label: 'Description',
        isSortable: true,
        value: (document: RawDocument) => document.document_description ?? '-',
        width: 40,
      },
      {
        field: 'document_tags',
        label: 'Tags',
        value: (document: RawDocument) => <ItemTags variant="list" limit={2} tags={document.document_tags} />,
        width: 25,
      },
    ],
  }), []);

  return (
    <SelectListPicker<RawDocument>
      open={open}
      onClose={handleClose}
      onSubmit={handleSubmit}
      title={label}
      submitLabel={t('Add')}
      inline
      headerComponent={(
        <Box
          sx={{
            display: 'flex',
            gap: 2,
          }}
        >
          <SearchFilter
            fullWidth
            onChange={(value?: string) => setKeyword(value ?? '')}
            keyword={keyword}
          />
          <TagsFilter
            fullWidth
            onAddTag={handleAddTag}
            onClearTag={handleClearTag}
            currentTags={tags}
          />
        </Box>
      )}
      values={filteredDocuments}
      elements={elements}
      selectedIds={selectedIds}
      onToggle={toggleDocument}
      getId={document => document.document_id ?? ''}
      submitDisabled={!multiple && selectedIds.length === 0}
      buttonComponent={(
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.DOCUMENTS}>
          <CreateDocument
            inline
            onCreate={(document: RawDocument) => {
              if (multiple) {
                setSelectedDocuments(prev => [...prev, document]);
              } else {
                setSelectedDocuments([document]);
              }
            }}
          />
        </Can>
      )}
    />
  );
};

export default FileTransferDialog;
