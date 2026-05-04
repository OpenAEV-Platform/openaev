import { DeleteOutlined, EditOutlined, MoreVert, TerminalOutlined } from '@mui/icons-material';
import { IconButton, ListItemIcon, ListItemText, Menu, MenuItem, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type Node, type NodeProps, Position } from '@xyflow/react';
import { memo, type MouseEvent, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';

export type ActionNodeData = Node<{
  label: string;
  injectorContract?: string;
  onEdit?: (id: string, type: string) => void;
  onDelete?: (id: string) => void;
}>;

const ActionNode = ({ id, data }: NodeProps<ActionNodeData>) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

  const handleMenuOpen = (e: MouseEvent<HTMLElement>) => {
    e.stopPropagation();
    setAnchorEl(e.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const handleEdit = () => {
    handleMenuClose();
    data.onEdit?.(id, 'action');
  };

  const handleDelete = () => {
    handleMenuClose();
    data.onDelete?.(id);
  };

  return (
    <div
      style={{
        border: `1px solid ${theme.palette.primary.main}50`,
        borderRadius: 8,
        padding: '12px 16px',
        background: `${theme.palette.primary.main}14`,
        minWidth: 180,
        textAlign: 'center',
        position: 'relative',
      }}
    >
      <Handle type="target" position={Position.Top} />
      <IconButton
        size="small"
        onClick={handleMenuOpen}
        sx={{
          position: 'absolute',
          top: 4,
          right: 4,
          padding: '2px',
        }}
      >
        <MoreVert sx={{ fontSize: 16 }} />
      </IconButton>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handleMenuClose}
      >
        <MenuItem onClick={handleEdit}>
          <ListItemIcon>
            <EditOutlined fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t('Edit')}</ListItemText>
        </MenuItem>
        <MenuItem onClick={handleDelete}>
          <ListItemIcon>
            <DeleteOutlined fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t('Delete')}</ListItemText>
        </MenuItem>
      </Menu>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        justifyContent: 'center',
      }}
      >
        <TerminalOutlined sx={{
          color: theme.palette.primary.main,
          fontSize: 20,
        }}
        />
        <Typography variant="body2" fontWeight={600}>
          {data.label}
        </Typography>
      </div>
      {data.injectorContract && (
        <Typography
          variant="caption"
          sx={{
            color: 'text.secondary',
            mt: 0.5,
            display: 'block',
          }}
        >
          {data.injectorContract}
        </Typography>
      )}
    </div>
  );
};

export default memo(ActionNode);
