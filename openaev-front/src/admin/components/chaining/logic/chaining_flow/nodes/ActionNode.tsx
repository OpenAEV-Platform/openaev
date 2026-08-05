import { MoreVert } from '@mui/icons-material';
import { IconButton, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type Node, type NodeProps, Position } from '@xyflow/react';
import { memo, type MouseEvent, useState } from 'react';

import ActionTypeIcon from '../../ActionTypeIcon';
import { ACTION_WIDTH } from '../../logic-flow-helpers';
import NodePopover from './NodePopover';

export type ActionNodeData = Node<{
  label: string;
  injectorType?: string;
  payloadType?: string;
  isPayload?: boolean;
  injectorContract?: string;
  /** Highlighted in blue when it produces or consumes the currently selected event. */
  isHighlighted?: boolean;
  /** 1-based position of this step in the selected event's data-flow path (badge). */
  pathIndex?: number;
  onEdit?: (id: string, type: string) => void;
  onDelete?: (id: string) => void;
}>;

const ActionNode = ({ id, data }: NodeProps<ActionNodeData>) => {
  const theme = useTheme();
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
        boxShadow: data.isHighlighted ? `0 0 0 2px ${theme.palette.primary.main}` : 'none',
      }}
    >
      {data.pathIndex !== undefined && (
        <div
          style={{
            position: 'absolute',
            top: -10,
            left: -10,
            width: 20,
            height: 20,
            borderRadius: '50%',
            background: theme.palette.primary.main,
            color: theme.palette.primary.contrastText,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 12,
            fontWeight: 700,
            zIndex: 2,
          }}
        >
          {data.pathIndex}
        </div>
      )}
      <Handle
        type="target"
        position={Position.Left}
        style={{
          background: 'transparent',
          border: 'none',
        }}
      />
      {/* Hidden source handle used as the origin of informational (data-flow) arrows toward events. */}
      <Handle
        id="action-source-right"
        type="source"
        position={Position.Right}
        isConnectable={false}
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
          <ActionTypeIcon
            injectorType={data.injectorType}
            payloadType={data.payloadType}
            isPayload={data.isPayload}
          />
          <Typography variant="body2" color="text.secondary" sx={{ userSelect: 'none' }}>
            -
          </Typography>
        </div>
        <IconButton
          size="small"
          onClick={handleMenuOpen}
          sx={{ display: (data.onEdit || data.onDelete) ? undefined : 'none' }}
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
      <NodePopover
        anchorEl={anchorEl}
        onClose={handleMenuClose}
        onEdit={handleEdit}
        onDelete={handleDelete}
      />
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
