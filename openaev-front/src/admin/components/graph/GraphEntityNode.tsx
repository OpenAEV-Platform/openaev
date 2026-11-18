import { Badge, Box, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type Node, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import { useFormatter } from '../../../components/i18n';
import NODE_ICON from './graph-icons';
import { type GraphNodeOutput, NODE_TYPE_LABEL } from './graph-model';

export interface GraphEntityNodeData extends Record<string, unknown> {
  node: GraphNodeOutput;
  focused: boolean;
  pinned: boolean;
  matched: boolean;
  dimmed: boolean;
  highlighted: boolean;
  truncated: boolean;
}

export type GraphEntityNodeType = Node<GraphEntityNodeData, 'entity'>;

const SIZE = 44;

const GraphEntityNode = ({ data }: NodeProps<GraphEntityNodeType>) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { node, focused, pinned, matched, dimmed, highlighted, truncated } = data;
  let ring = theme.palette.divider;
  if (node.node_result === 'FAILED') {
    ring = theme.palette.error.main;
  } else if (node.node_result === 'SUCCESS') {
    ring = theme.palette.success.main;
  } else if (node.node_result === 'PENDING') {
    ring = theme.palette.warning.main;
  }
  const Icon = NODE_ICON[node.node_type];
  return (
    <Tooltip title={`${t(NODE_TYPE_LABEL[node.node_type])}: ${node.node_label}`} placement="top" disableInteractive>
      <Box
        data-testid={`graph-node-${node.node_id}`}
        sx={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          width: 140,
          opacity: dimmed ? 0.25 : 1,
          transition: 'opacity 0.2s',
        }}
      >
        <Handle type="target" position={Position.Top} style={{ visibility: 'hidden' }} />
        <Badge
          color="error"
          badgeContent={node.node_failed_count > 0 ? node.node_failed_count : undefined}
          overlap="circular"
        >
          <Box
            sx={{
              width: SIZE,
              height: SIZE,
              borderRadius: '50%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              backgroundColor: theme.palette.background.paper,
              color: theme.palette.text.primary,
              border: `2px solid ${ring}`,
              outline: focused || matched || highlighted ? `3px solid ${matched ? theme.palette.secondary.main : theme.palette.primary.main}` : 'none',
              outlineOffset: 2,
              boxShadow: highlighted ? `0 0 12px ${theme.palette.error.main}` : 'none',
            }}
          >
            <Icon />
          </Box>
        </Badge>
        <Typography
          variant="caption"
          noWrap
          sx={{
            maxWidth: 140,
            mt: 0.5,
            fontWeight: focused ? 700 : 400,
          }}
        >
          {pinned ? '\u25CF ' : ''}
          {node.node_label}
          {truncated ? ' +' : ''}
        </Typography>
        <Handle type="source" position={Position.Bottom} style={{ visibility: 'hidden' }} />
      </Box>
    </Tooltip>
  );
};

export default memo(GraphEntityNode);
