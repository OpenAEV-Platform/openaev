import { CreateNewFolderOutlined, DeleteOutlined, EditOutlined, FolderOffOutlined, FolderOutlined, InboxOutlined, MoreVert } from '@mui/icons-material';
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, List, ListItemButton, ListItemIcon, ListItemText, Menu, MenuItem, TextField, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { useState } from 'react';

import { useFormatter } from '../../../../components/i18n';

// Special selection sentinels: "all" shows every file, "root" only unfiled files.
export const ALL_FILES = 'all';
export const ROOT_FOLDER = 'root';

const FileFolderPanel = ({
  folders,
  counts,
  selectedFolderId,
  onSelect,
  onCreate,
  onRename,
  onDelete,
}) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const [menuAnchor, setMenuAnchor] = useState(null);
  const [menuFolder, setMenuFolder] = useState(null);
  const [dialog, setDialog] = useState(null); // 'create' | 'rename' | null
  const [folderName, setFolderName] = useState('');

  const sortedFolders = R.sortWith([R.ascend(R.prop('folder_name'))], folders);

  const openMenu = (event, folder) => {
    event.stopPropagation();
    setMenuAnchor(event.currentTarget);
    setMenuFolder(folder);
  };
  const closeMenu = () => {
    setMenuAnchor(null);
    setMenuFolder(null);
  };

  const submitDialog = () => {
    if (!folderName.trim()) return;
    if (dialog === 'create') {
      onCreate(folderName.trim());
    } else if (dialog === 'rename' && menuFolder) {
      onRename(menuFolder.folder_id, folderName.trim());
    }
    setDialog(null);
    setFolderName('');
  };

  const staticRow = (id, icon, label, count) => (
    <ListItemButton
      selected={selectedFolderId === id}
      onClick={() => onSelect(id)}
      sx={{ borderRadius: 1 }}
    >
      <ListItemIcon sx={{ minWidth: 34 }}>{icon}</ListItemIcon>
      <ListItemText primary={label} />
      {count != null && (
        <Typography variant="body2" sx={{ color: 'text.secondary' }}>{count}</Typography>
      )}
    </ListItemButton>
  );

  return (
    <Box
      component="aside"
      sx={{
        width: 260,
        flexShrink: 0,
        alignSelf: 'flex-start',
        position: 'sticky',
        top: theme.spacing(2),
      }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginBottom: 1,
      }}
      >
        <Typography sx={{
          fontFamily: theme.typography.h1.fontFamily,
          fontSize: 12,
          fontWeight: 600,
          letterSpacing: '0.12em',
          textTransform: 'uppercase',
          color: 'text.secondary',
        }}
        >
          {t('Folders')}
        </Typography>
        <IconButton
          size="small"
          onClick={() => {
            setFolderName('');
            setDialog('create');
          }}
        >
          <CreateNewFolderOutlined fontSize="small" />
        </IconButton>
      </Box>
      <Box sx={{
        border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
        borderRadius: 1,
        padding: 0.5,
        backgroundColor: theme.palette.background.paper,
      }}
      >
        <List disablePadding>
          {staticRow(ALL_FILES, <InboxOutlined fontSize="small" />, t('All files'), counts?.[ALL_FILES])}
          {staticRow(ROOT_FOLDER, <FolderOffOutlined fontSize="small" />, t('Unfiled'), counts?.[ROOT_FOLDER])}
          {sortedFolders.map(folder => (
            <ListItemButton
              key={folder.folder_id}
              selected={selectedFolderId === folder.folder_id}
              onClick={() => onSelect(folder.folder_id)}
              sx={{ borderRadius: 1 }}
            >
              <ListItemIcon sx={{ minWidth: 34 }}>
                <FolderOutlined fontSize="small" color="primary" />
              </ListItemIcon>
              <ListItemText
                primary={folder.folder_name}
                primaryTypographyProps={{ noWrap: true }}
              />
              <Typography
                variant="body2"
                sx={{
                  color: 'text.secondary',
                  marginRight: 0.5,
                }}
              >
                {counts?.[folder.folder_id] ?? 0}
              </Typography>
              <IconButton size="small" onClick={event => openMenu(event, folder)}>
                <MoreVert fontSize="small" />
              </IconButton>
            </ListItemButton>
          ))}
        </List>
      </Box>

      <Menu anchorEl={menuAnchor} open={!!menuAnchor} onClose={closeMenu}>
        <MenuItem onClick={() => {
          setFolderName(menuFolder?.folder_name ?? '');
          setDialog('rename');
          closeMenu();
        }}
        >
          <ListItemIcon><EditOutlined fontSize="small" /></ListItemIcon>
          {t('Rename')}
        </MenuItem>
        <MenuItem onClick={() => {
          onDelete(menuFolder);
          closeMenu();
        }}
        >
          <ListItemIcon><DeleteOutlined fontSize="small" /></ListItemIcon>
          {t('Delete')}
        </MenuItem>
      </Menu>

      <Dialog open={dialog != null} onClose={() => setDialog(null)} fullWidth maxWidth="xs">
        <DialogTitle>{dialog === 'rename' ? t('Rename folder') : t('Create a new folder')}</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            variant="standard"
            label={t('Folder name')}
            value={folderName}
            onChange={event => setFolderName(event.target.value)}
            onKeyDown={(event) => { if (event.key === 'Enter') submitDialog(); }}
          />
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" onClick={() => setDialog(null)}>{t('Cancel')}</Button>
          <Button variant="contained" onClick={submitDialog} disabled={!folderName.trim()}>
            {dialog === 'rename' ? t('Update') : t('Create')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default FileFolderPanel;
