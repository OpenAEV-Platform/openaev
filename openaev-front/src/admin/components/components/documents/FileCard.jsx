import { DescriptionOutlined, DriveFileMoveOutlined, LockOutlined } from '@mui/icons-material';
import { Box, Chip, IconButton, ListItemIcon, Menu, MenuItem, Paper, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import DocumentPopover from './DocumentPopover';
import DocumentType from './DocumentType';

// Marketplace-style card for a file, mirroring the security-platform card anatomy:
// framed icon, name, description, type + kind, tags, and a move-to-folder action.
const FileCard = ({ document, folders, onUpdate, onDelete, onMove }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const [moveAnchor, setMoveAnchor] = useState(null);
  const isSample = document.document_kind === 'MALWARE_SAMPLE';

  return (
    <Paper
      variant="outlined"
      data-testid="file-card"
      onClick={() => window.open(buildTenantApiPath(`/api/documents/${document.document_id}/file`), '_self')}
      sx={{
        'position': 'relative',
        'display': 'flex',
        'flexDirection': 'column',
        'gap': 1.5,
        'padding': 2,
        'borderRadius': 1,
        'height': '100%',
        'cursor': 'pointer',
        'transition': theme.transitions.create(['border-color', 'box-shadow', 'transform']),
        '&:hover': {
          borderColor: alpha(theme.palette.primary.main, 0.5),
          boxShadow: `0 4px 16px ${alpha(theme.palette.common.black, 0.25)}`,
          transform: 'translateY(-2px)',
        },
      }}
    >
      <Box
        sx={{
          position: 'absolute',
          top: 6,
          right: 6,
          display: 'flex',
        }}
        onClick={event => event.stopPropagation()}
      >
        <Tooltip title={t('Move to folder')}>
          <IconButton size="small" onClick={event => setMoveAnchor(event.currentTarget)}>
            <DriveFileMoveOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
        <DocumentPopover
          document={document}
          onUpdate={onUpdate}
          onDelete={onDelete}
          scenariosAndExercisesFetched
          inList
        />
      </Box>

      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        minWidth: 0,
        paddingRight: 6,
      }}
      >
        <Box sx={{
          width: 44,
          height: 44,
          flexShrink: 0,
          borderRadius: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: isSample ? theme.palette.warning.main : theme.palette.primary.main,
          border: `1px solid ${alpha(isSample ? theme.palette.warning.main : theme.palette.primary.main, 0.2)}`,
          backgroundColor: alpha(isSample ? theme.palette.warning.main : theme.palette.primary.main, 0.08),
        }}
        >
          {isSample ? <LockOutlined /> : <DescriptionOutlined />}
        </Box>
        <Tooltip title={document.document_name}>
          <Typography sx={{
            fontSize: 14,
            fontWeight: 600,
            lineHeight: 1.35,
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
            wordBreak: 'break-word',
          }}
          >
            {document.document_name}
          </Typography>
        </Tooltip>
      </Box>

      <Typography
        variant="body2"
        sx={{
          color: 'text.secondary',
          minHeight: 40,
          display: '-webkit-box',
          WebkitLineClamp: 2,
          WebkitBoxOrient: 'vertical',
          overflow: 'hidden',
        }}
      >
        {document.document_description || '-'}
      </Typography>

      <Box sx={{
        marginTop: 'auto',
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        flexWrap: 'wrap',
      }}
      >
        <DocumentType type={document.document_type} variant="list" />
        {isSample && (
          <Chip
            size="small"
            color="warning"
            variant="outlined"
            icon={<LockOutlined style={{ fontSize: 12 }} />}
            label={t('Encrypted sample')}
            sx={{ borderRadius: 1 }}
          />
        )}
        <ItemTags variant="reduced-view" tags={document.document_tags} />
      </Box>

      <Menu anchorEl={moveAnchor} open={!!moveAnchor} onClose={() => setMoveAnchor(null)} onClick={event => event.stopPropagation()}>
        <MenuItem
          disabled={!document.document_folder}
          onClick={() => {
            onMove(document, null);
            setMoveAnchor(null);
          }}
        >
          <ListItemIcon><DriveFileMoveOutlined fontSize="small" /></ListItemIcon>
          {t('Root')}
        </MenuItem>
        {folders.map(folder => (
          <MenuItem
            key={folder.folder_id}
            disabled={document.document_folder === folder.folder_id}
            onClick={() => {
              onMove(document, folder.folder_id);
              setMoveAnchor(null);
            }}
          >
            {folder.folder_name}
          </MenuItem>
        ))}
      </Menu>
    </Paper>
  );
};

export default FileCard;
