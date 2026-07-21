import { DescriptionOutlined, GridViewOutlined, HelpOutlineOutlined, LockOutlined, ViewListOutlined } from '@mui/icons-material';
import { Box, Chip, List, ListItem, ListItemButton, ListItemIcon, ListItemSecondaryAction, ListItemText, Skeleton, ToggleButton, ToggleButtonGroup, Tooltip } from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { searchDocuments } from '../../../../actions/Document';
import { createFolder, deleteFolder, fetchFolders, moveDocumentToFolder, updateFolder } from '../../../../actions/folders/folder-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import PaginationComponent from '../../../../components/common/pagination/PaginationComponent';
import SortHeadersComponent from '../../../../components/common/pagination/SortHeadersComponent';
import { initSorting } from '../../../../components/common/queryable/Page';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import CreateDocument from './CreateDocument';
import DocumentPopover from './DocumentPopover';
import DocumentType from './DocumentType';
import FileCard from './FileCard';
import FileFolderPanel, { ALL_FILES, ROOT_FOLDER } from './FileFolderPanel';

const VIEW_MODE_STORAGE_KEY = 'files:view-mode';
const readViewMode = () => (typeof window !== 'undefined' && window.localStorage.getItem(VIEW_MODE_STORAGE_KEY) === 'list' ? 'list' : 'cards');

const useStyles = makeStyles()(() => ({
  itemHead: {
    paddingLeft: 10,
    textTransform: 'uppercase',
    cursor: 'pointer',
  },
  item: {
    paddingLeft: 10,
    height: 50,
  },
}));

const inlineStyles = {
  document_name: { width: '25%' },
  document_description: { width: '20%' },
  document_exercises: {
    width: '20%',
    cursor: 'default',
  },
  document_scenarios: {
    width: '20%',
    cursor: 'default',
  },
  document_type: { width: '15%' },
};

const Documents = () => {
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t } = useFormatter();

  const headers = [
    {
      field: 'document_name',
      label: 'Name',
      isSortable: true,
    },
    {
      field: 'document_description',
      label: 'Description',
      isSortable: true,
    },
    {
      field: 'document_type',
      label: 'Type',
      isSortable: true,
    },
  ];

  const [documents, setDocuments] = useState([]);
  const [folders, setFolders] = useState([]);
  const [selectedFolderId, setSelectedFolderId] = useState(ALL_FILES);
  const [viewMode, setViewMode] = useState(readViewMode);
  const [searchPaginationInput, setSearchPaginationInput] = useState({ sorts: initSorting('document_name') });
  const [loadingDocuments, setLoadingDocuments] = useState(true);

  const loadFolders = () => fetchFolders().then(result => setFolders(result.data ?? []));
  // Fetch the folder tree once on mount.
  useEffect(() => {
    loadFolders();
  }, []);

  // Client-side folder filtering over the loaded page. "All" shows everything,
  // "Unfiled" shows files with no folder, otherwise the exact folder.
  const visibleDocuments = useMemo(() => documents.filter((document) => {
    if (selectedFolderId === ALL_FILES) return true;
    if (selectedFolderId === ROOT_FOLDER) return !document.document_folder;
    return document.document_folder === selectedFolderId;
  }), [documents, selectedFolderId]);

  const counts = useMemo(() => {
    const result = {
      [ALL_FILES]: documents.length,
      [ROOT_FOLDER]: 0,
    };
    documents.forEach((document) => {
      if (!document.document_folder) {
        result[ROOT_FOLDER] += 1;
      } else {
        result[document.document_folder] = (result[document.document_folder] ?? 0) + 1;
      }
    });
    return result;
  }, [documents]);

  const handleCreateDocuments = (result) => {
    if (documents.find(element => element.document_id === result.document_id) === undefined) {
      setDocuments([result, ...documents]);
    }
    loadFolders();
  };

  const handleViewModeChange = (_, value) => {
    if (!value) return;
    setViewMode(value);
    if (typeof window !== 'undefined') window.localStorage.setItem(VIEW_MODE_STORAGE_KEY, value);
  };

  const onFolderCreate = name => createFolder({ folder_name: name }).then(loadFolders);
  const onFolderRename = (folderId, name) => updateFolder(folderId, { folder_name: name }).then(loadFolders);
  const onFolderDelete = (folder) => {
    if (!folder) return undefined;
    return deleteFolder(folder.folder_id).then(() => {
      if (selectedFolderId === folder.folder_id) setSelectedFolderId(ALL_FILES);
      loadFolders();
    });
  };
  const onMove = (document, folderId) => moveDocumentToFolder(document.document_id, folderId).then(() => {
    setDocuments(prev => prev.map(d => (d.document_id === document.document_id
      ? {
          ...d,
          document_folder: folderId ?? undefined,
        }
      : d)));
  });

  const exportProps = {
    exportType: 'tags',
    exportKeys: ['document_name', 'document_description', 'document_type'],
    exportData: documents,
    exportFileName: `${t('Files')}.csv`,
  };

  const searchDocumentsToLoad = (input) => {
    setLoadingDocuments(true);
    return searchDocuments(input).finally(() => setLoadingDocuments(false));
  };

  const currentFolderId = [ALL_FILES, ROOT_FOLDER].includes(selectedFolderId) ? null : selectedFolderId;
  const loading = loadingDocuments;

  const viewSwitcher = (
    <ToggleButtonGroup
      value={viewMode}
      exclusive
      size="small"
      onChange={handleViewModeChange}
      aria-label={t('View mode')}
      sx={{ '& .MuiToggleButton-root.Mui-selected .MuiSvgIcon-root': { color: 'primary.main' } }}
    >
      <ToggleButton value="cards" aria-label={t('Cards view')}>
        <Tooltip title={t('Cards view')}><GridViewOutlined fontSize="small" /></Tooltip>
      </ToggleButton>
      <ToggleButton value="list" aria-label={t('List view')}>
        <Tooltip title={t('List view')}><ViewListOutlined fontSize="small" /></Tooltip>
      </ToggleButton>
    </ToggleButtonGroup>
  );

  const renderCards = () => {
    if (loading) {
      return (
        <Box sx={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
          gap: 2,
          mt: 2,
        }}
        >
          {Array.from({ length: 8 }).map((_, idx) => (
            <Skeleton key={idx} variant="rectangular" height={150} animation="wave" sx={{ borderRadius: 1 }} />
          ))}
        </Box>
      );
    }
    return (
      <Box sx={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
        gap: 2,
        mt: 2,
      }}
      >
        {visibleDocuments.map(document => (
          <FileCard
            key={document.document_id}
            document={document}
            folders={folders}
            onUpdate={result => setDocuments(documents.map(d => (d.document_id !== result.document_id ? d : result)))}
            onDelete={result => setDocuments(documents.filter(d => (d.document_id !== result)))}
            onMove={onMove}
          />
        ))}
      </Box>
    );
  };

  const renderList = () => (
    <List>
      <ListItem classes={{ root: classes.itemHead }} divider={false} style={{ paddingTop: 0 }}>
        <ListItemIcon>
          <span style={{
            padding: '0 8px',
            fontWeight: 700,
            fontSize: 12,
          }}
          >
&nbsp;
          </span>
        </ListItemIcon>
        <ListItemText
          primary={(
            <SortHeadersComponent
              headers={headers}
              inlineStylesHeaders={inlineStyles}
              searchPaginationInput={searchPaginationInput}
              setSearchPaginationInput={setSearchPaginationInput}
            />
          )}
        />
        <ListItemSecondaryAction> &nbsp; </ListItemSecondaryAction>
      </ListItem>
      {loading
        ? <PaginatedListLoader Icon={HelpOutlineOutlined} headers={headers} headerStyles={inlineStyles} />
        : visibleDocuments.map((document) => {
            const isSample = document.document_kind === 'MALWARE_SAMPLE';
            return (
              <ListItem
                key={document.document_id}
                divider
                secondaryAction={(
                  <DocumentPopover
                    document={document}
                    onUpdate={result => setDocuments(documents.map(d => (d.document_id !== result.document_id ? d : result)))}
                    onDelete={result => setDocuments(documents.filter(d => (d.document_id !== result)))}
                    scenariosAndExercisesFetched
                    inList
                  />
                )}
                disablePadding
              >
                <ListItemButton
                  classes={{ root: classes.item }}
                  component="a"
                  href={buildTenantApiPath(`/api/documents/${document.document_id}/file`)}
                >
                  <ListItemIcon>
                    {isSample
                      ? <LockOutlined color="warning" />
                      : <DescriptionOutlined color="primary" />}
                  </ListItemIcon>
                  <ListItemText
                    primary={(
                      <div style={bodyItemsStyles.bodyItems}>
                        <div style={{
                          ...bodyItemsStyles.bodyItem,
                          ...inlineStyles.document_name,
                        }}
                        >
                          {document.document_name}
                        </div>
                        <div style={{
                          ...bodyItemsStyles.bodyItem,
                          ...inlineStyles.document_description,
                        }}
                        >
                          {document.document_description || '-'}
                        </div>
                        <div style={{
                          ...bodyItemsStyles.bodyItem,
                          ...inlineStyles.document_type,
                        }}
                        >
                          <DocumentType type={document.document_type} variant="list" />
                        </div>
                        <div style={{
                          ...bodyItemsStyles.bodyItem,
                          ...inlineStyles.document_scenarios,
                        }}
                        >
                          {isSample
                            ? <Chip size="small" color="warning" variant="outlined" label={t('Encrypted sample')} sx={{ borderRadius: 1 }} />
                            : <ItemTags variant="list" tags={document.document_tags} />}
                        </div>
                      </div>
                    )}
                  />
                </ListItemButton>
              </ListItem>
            );
          })}
    </List>
  );

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{ label: t('Components') }, {
          label: t('Files'),
          current: true,
        }]}
      />
      <Box sx={{
        display: 'flex',
        gap: 3,
        alignItems: 'flex-start',
      }}
      >
        <FileFolderPanel
          folders={folders}
          counts={counts}
          selectedFolderId={selectedFolderId}
          onSelect={setSelectedFolderId}
          onCreate={onFolderCreate}
          onRename={onFolderRename}
          onDelete={onFolderDelete}
        />
        <Box sx={{
          flex: 1,
          minWidth: 0,
        }}
        >
          <PaginationComponent
            fetch={searchDocumentsToLoad}
            searchPaginationInput={searchPaginationInput}
            setContent={setDocuments}
            exportProps={exportProps}
            topRightSlot={viewSwitcher}
            createButton={(
              <Can I={ACTIONS.MANAGE} a={SUBJECTS.DOCUMENTS}>
                <CreateDocument
                  onCreate={handleCreateDocuments}
                  folders={folders}
                  currentFolderId={currentFolderId}
                />
              </Can>
            )}
          />
          {viewMode === 'cards' ? renderCards() : renderList()}
        </Box>
      </Box>
    </>
  );
};

export default Documents;
