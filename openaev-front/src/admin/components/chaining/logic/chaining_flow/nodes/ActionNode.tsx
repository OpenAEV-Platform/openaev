import { DeleteOutlined, EditOutlined, MoreVert, TerminalOutlined } from '@mui/icons-material';
import { IconButton, ListItemIcon, ListItemText, Menu, MenuItem, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type Node, type NodeProps, Position } from '@xyflow/react';
import { memo, type MouseEvent, useState } from 'react';

import { useFormatter } from '../../../../../../components/i18n';
import InjectIcon from '../../../../common/injects/InjectIcon';
import { ACTION_WIDTH } from '../../logic-flow-helpers';

export type ActionNodeData = Node<{
  label: string;
  injectorType?: string;
  payloadType?: string;
  isPayload?: boolean;
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
        borderRadius: theme.spacing(1),
        padding: theme.spacing(1),
        background: theme.palette.background.default,
        width: ACTION_WIDTH,
        position: 'relative',
      }}
    >
      <Handle
        type="target"
        position={Position.Left}
        style={{
          background: 'transparent',
          border: 'none',
        }}
      />
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: theme.spacing(1),
      }}
      >
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(1),
        }}
        >
          {(data.payloadType ?? data.injectorType)
            ? (
                <InjectIcon
                  type={data.payloadType ?? data.injectorType}
                  isPayload={data.isPayload}
                  size="small"
                />
              )
            : <TerminalOutlined sx={{ color: theme.palette.primary.main }} />}
          <Typography variant="body2" color="text.secondary" sx={{ userSelect: 'none' }}>
            -
          </Typography>
        </div>
        <IconButton
          size="small"
          onClick={handleMenuOpen}
        >
          <MoreVert sx={{
            fontSize: 18,
            color: theme.palette.primary.main,
          }}
          />
        </IconButton>
      </div>
      <Typography
        variant="body2"
        fontWeight={500}
        sx={{
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {data.label}
      </Typography>
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
      <Handle
        type="source"
        position={Position.Right}
        style={{
          background: 'transparent',
          border: 'none',
        }}
      />
    </div>
  );
};

export default memo(ActionNode);
