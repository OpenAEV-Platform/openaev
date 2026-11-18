import { Badge, Text, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type Node, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import { useFormatter } from '../../../components/i18n';
import { ProductMark } from '../../../components/xtm/XtmPivot';
import { LABEL_MARGIN, NODE_CIRCLE_SIZE, NODE_WIDTH } from './graph-edge-geometry';
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
  /** The node has a counterpart in the connected OpenCTI: a small product mark on its circle. */
  inOpenCti?: boolean;
}

export type GraphEntityNodeType = Node<GraphEntityNodeData, 'entity'>;

const SIZE = NODE_CIRCLE_SIZE;

const GraphEntityNode = ({ data }: NodeProps<GraphEntityNodeType>) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { node, focused, pinned, matched, dimmed, highlighted, truncated, inOpenCti } = data;
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
    <Tooltip>
      <TooltipTrigger asChild>
        <Box
          data-testid={`graph-node-${node.node_id}`}
          sx={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            width: NODE_WIDTH,
            opacity: dimmed ? 0.25 : 1,
            transition: 'opacity 0.2s',
          }}
        >
          <Handle type="target" position={Position.Top} style={{ visibility: 'hidden' }} />
          <Badge
            content={node.node_failed_count}
            invisible={!(node.node_failed_count > 0)}
            circularAnchor
          >
            <Box
              sx={{
                width: SIZE,
                height: SIZE,
                borderRadius: '50%',
                position: 'relative',
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
              {inOpenCti && (
                <Box
                  data-testid={`graph-node-opencti-${node.node_id}`}
                  aria-label={t('In OpenCTI')}
                  sx={{
                    position: 'absolute',
                    top: -6,
                    left: -6,
                    display: 'flex',
                    borderRadius: '50%',
                    backgroundColor: theme.palette.background.paper,
                    border: `1px solid ${theme.palette.divider}`,
                  }}
                >
                  <ProductMark size={16} />
                </Box>
              )}
            </Box>
          </Badge>
          <Text
            variant="content-caption"
            style={{
              maxWidth: NODE_WIDTH,
              marginTop: LABEL_MARGIN,
              // Opaque, so an edge passing under the label (edges are drawn under nodes) stays hidden.
              padding: '0 4px',
              borderRadius: 4,
              backgroundColor: theme.palette.background.paper,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              fontWeight: focused ? 700 : 400,
            }}
          >
            {pinned ? '\u25CF ' : ''}
            {node.node_label}
            {truncated ? ' +' : ''}
          </Text>
          <Handle type="source" position={Position.Bottom} style={{ visibility: 'hidden' }} />
        </Box>
      </TooltipTrigger>
      <TooltipContent>
        {`${t(NODE_TYPE_LABEL[node.node_type])}: ${node.node_label}`}
        {inOpenCti ? ` (${t('In OpenCTI')})` : ''}
      </TooltipContent>
    </Tooltip>
  );
};

export default memo(GraphEntityNode);
