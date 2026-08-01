import { Add, BoltOutlined, MoreVert } from '@mui/icons-material';
import { Box, Button, IconButton, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type Node, type NodeProps, Position } from '@xyflow/react';
import { memo, type MouseEvent, useState } from 'react';

import { useFormatter } from '../../../../../../components/i18n';
import NodePopover from './NodePopover';

export type EventNodeData = Node<{
  label: string;
  conditions?: string[];
  /** Whether this event is currently selected to reveal its informational data-flow arrows. */
  isSelected?: boolean;
  /** 1-based position of this event in the selected data-flow path (badge). */
  pathIndex?: number;
  onEdit?: (id: string, type: string) => void;
  onDelete?: (id: string) => void;
  /** Open the action drawer to add an action linked to this event. */
  onAddAction?: (id: string) => void;
  /** Hide every mutation affordance (edit/delete/add) — used for autonomous read-only inspection. */
  readOnly?: boolean;
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

  const handleAddAction = (e: MouseEvent<HTMLElement>) => {
    e.stopPropagation();
    data.onAddAction?.(id);
  };

  const handleEdit = () => {
    handleMenuClose();
    data.onEdit?.(id, 'event');
  };

  const handleDelete = () => {
    handleMenuClose();
    data.onDelete?.(id);
  };

  return (
    <Box sx={{
      position: 'relative',
      minWidth: 200,
      paddingTop: 2,
    }}
    >
      {data.pathIndex !== undefined && (
        <Box sx={{
          position: 'absolute',
          top: 8,
          left: -10,
          width: 20,
          height: 20,
          borderRadius: '50%',
          background: theme.palette.warning.main,
          color: theme.palette.warning.contrastText,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 12,
          fontWeight: 700,
          zIndex: 2,
        }}
        >
          {data.pathIndex}
        </Box>
      )}
      <Box
        sx={{
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
      </Box>

      <Box
        sx={{
          background: `${theme.palette.primary.main}10`,
          borderRadius: 1,
          padding: 2,
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          position: 'relative',
          boxShadow: data.isSelected ? `0 0 0 2px ${theme.palette.warning.main}` : 'none',
        }}
      >
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
        {!data.readOnly && (
          <IconButton
            size="small"
            aria-haspopup="true"
            onClick={handleMenuOpen}
          >
            <MoreVert sx={{
              fontSize: 18,
              color: theme.palette.primary.main,
            }}
            />
          </IconButton>
        )}
        <Handle
          type="source"
          position={Position.Right}
          style={{
            background: theme.palette.primary.main,
            border: 'none',
          }}
        />
        {/* Hidden target handle used to receive informational (data-flow) arrows from provider actions. */}
        <Handle
          id="event-target"
          type="target"
          position={Position.Left}
          isConnectable={false}
          style={{
            background: 'transparent',
            border: 'none',
          }}
        />
      </Box>

      {!data.readOnly && (
        <Box sx={{
          display: 'flex',
          justifyContent: 'center',
          marginTop: 1,
        }}
        >
          <Tooltip title={t('Add an action')}>
            <Button
              color="primary"
              variant="contained"
              onClick={handleAddAction}
              sx={{
                minWidth: 0,
                width: 24,
                height: 24,
                padding: 0,
              }}
            >
              <Add sx={{ fontSize: 16 }} />
            </Button>

          </Tooltip>
        </Box>
      )}

      {!data.readOnly && (
        <NodePopover
          anchorEl={anchorEl}
          onClose={handleMenuClose}
          onEdit={handleEdit}
          onDelete={handleDelete}
        />
      )}
    </Box>
  );
};

export default memo(EventNode);
