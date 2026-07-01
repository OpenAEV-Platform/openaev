import { BoltOutlined, DeleteOutlined, EditOutlined, MoreVert } from '@mui/icons-material';
import { IconButton, ListItemIcon, ListItemText, Menu, MenuItem, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type Node, type NodeProps, Position } from '@xyflow/react';
import { memo, type MouseEvent, useState } from 'react';

import { useFormatter } from '../../../../../../components/i18n';

export type EventNodeData = Node<{
  label: string;
  conditions?: string[];
  onEdit?: (id: string, type: string) => void;
  onDelete?: (id: string) => void;
}>;

const EventNode = ({ id, data }: NodeProps<EventNodeData>) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

  const handleMenuOpen = (e: MouseEvent<HTMLElement>) => {
    e.stopPropagation();
    setAnchorEl(e.currentTarget);
  };

  const handleMenuClose = () => setAnchorEl(null);

  const handleEdit = () => {
    handleMenuClose();
    data.onEdit?.(id, 'event');
  };

  const handleDelete = () => {
    handleMenuClose();
    data.onDelete?.(id);
  };

  return (
    <div
      style={{
        position: 'relative',
        minWidth: 200,
        paddingTop: 20,
      }}
    >

      {/* Bolt circle — same color as canvas background, overlapping card top to create a notch illusion */}
      <div
        style={{
          position: 'absolute',
          top: 0,
          left: '50%',
          transform: 'translateX(-50%)',
          width: 35,
          height: 35,
          borderRadius: '50%',
          background: theme.palette.background.default,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1,
        }}
      >
        <BoltOutlined sx={{
          fontSize: 22,
          fill: 'none',
          stroke: theme.palette.warning.main,
        }}
        />
      </div>

      {/* Card body */}
      <div
        style={{
          background: `${theme.palette.primary.main}10`,
          borderRadius: theme.spacing(1),
          padding: '24px 12px 16px',
          display: 'grid',
          gridTemplateColumns: '26px 1fr 26px',
          alignItems: 'center',
          position: 'relative',
        }}
      >
        <span />
        <Typography
          variant="body2"
          fontWeight={600}
          sx={{
            textAlign: 'center',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {data.label}
        </Typography>
        <IconButton size="small" onClick={handleMenuOpen} sx={{ padding: '2px' }}>
          <MoreVert sx={{
            fontSize: 18,
            color: theme.palette.primary.main,
          }}
          />
        </IconButton>
        <Handle
          type="source"
          position={Position.Right}
          style={{
            background: 'transparent',
            border: 'none',
          }}
        />
      </div>

      <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={handleMenuClose}>
        <MenuItem onClick={handleEdit}>
          <ListItemIcon><EditOutlined fontSize="small" /></ListItemIcon>
          <ListItemText>{t('Edit')}</ListItemText>
        </MenuItem>
        <MenuItem onClick={handleDelete}>
          <ListItemIcon><DeleteOutlined fontSize="small" /></ListItemIcon>
          <ListItemText>{t('Delete')}</ListItemText>
        </MenuItem>
      </Menu>
    </div>
  );
};

export default memo(EventNode);
