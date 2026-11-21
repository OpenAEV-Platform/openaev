import { Badge, Text, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { Handle, type Node, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import { useFormatter } from '../../../components/i18n';
import { OpenCrqMark } from '../../../components/xtm/XtmOpenCrq';
import { ProductMark } from '../../../components/xtm/XtmPivot';
import { type NodeConnections } from './graph-connections';
import { LABEL_MARGIN, NODE_CIRCLE_SIZE, NODE_WIDTH } from './graph-edge-geometry';
import NODE_ICON from './graph-icons';
import { type GraphNodeOutput, NODE_TYPE_LABEL, NODE_TYPE_PLURAL } from './graph-model';

export interface GraphEntityNodeData extends Record<string, unknown> {
  node: GraphNodeOutput;
  /** Selected (the node of the side panel or of the multi-selection): the one selection ring. */
  focused: boolean;
  /** The entity the view is centred on: a quieter dashed mark, never the selection ring. */
  anchor?: boolean;
  pinned: boolean;
  matched: boolean;
  dimmed: boolean;
  highlighted: boolean;
  truncated: boolean;
  /** A group node (same-type neighbours folded, as OpenCTI's graph collapse): how many it stands for. */
  groupCount?: number;
  /** Its relations in the data against the ones drawn (#9572): the connection badge; null when not counted. */
  connections?: NodeConnections | null;
  /** The node has a counterpart in the connected OpenCTI / OpenCRQ: the product mark of the list rows on its circle. */
  inOpenCti?: boolean;
  inOpenCrq?: boolean;
}

export type GraphEntityNodeType = Node<GraphEntityNodeData, 'entity'>;

const SIZE = NODE_CIRCLE_SIZE;

const GraphEntityNode = ({ data }: NodeProps<GraphEntityNodeType>) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { node, focused, anchor, pinned, matched, dimmed, highlighted, truncated, groupCount, connections, inOpenCti, inOpenCrq } = data;
  // OpenCTI's number on a node: its relations in the data, "+N" when N of them are not drawn yet.
  const expandable = connections ? connections.hidden : 0;
  let connectionsHint = '';
  if (connections && connections.total > 0) {
    connectionsHint = expandable > 0
      ? ` · ${connections.total} ${t('connections')}, ${expandable} ${t('not shown')}, ${t('double-click to expand')}`
      : ` · ${connections.total} ${t('connections')}`;
  }
  let outline = 'none';
  if (focused || matched || highlighted) {
    outline = `3px solid ${matched ? theme.palette.secondary.main : theme.palette.primary.main}`;
  } else if (anchor) {
    outline = `2px dashed ${theme.palette.text.secondary}`;
  }
  const marks = [
    ...(inOpenCti
      ? [{
          key: 'opencti',
          label: t('In OpenCTI'),
          mark: <ProductMark size={14} />,
        }]
      : []),
    ...(inOpenCrq
      ? [{
          key: 'opencrq',
          label: t('In OpenCRQ'),
          mark: <OpenCrqMark size={14} />,
        }]
      : []),
  ];
  const label = groupCount ? `+${groupCount} ${t(NODE_TYPE_PLURAL[node.node_type])}` : node.node_label;
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
        <div
          data-testid={`graph-node-${node.node_id}`}
          data-selected={focused ? 'true' : 'false'}
          data-anchor={anchor ? 'true' : 'false'}
          style={{
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
            <div
              style={{
                width: SIZE,
                height: SIZE,
                borderRadius: '50%',
                position: 'relative',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                backgroundColor: theme.palette.background.paper,
                color: theme.palette.text.primary,
                border: `2px ${groupCount ? 'dashed' : 'solid'} ${ring}`,
                outline,
                outlineOffset: 2,
                boxShadow: highlighted ? `0 0 12px ${theme.palette.error.main}` : 'none',
              }}
            >
              <Icon />
              {connections && connections.total > 0 && (
                // Bottom right of the circle, away from the failed badge (top right) and the product
                // marks (top left); the label sits under the circle, so it is never covered.
                <div
                  data-testid={`graph-node-connections-${node.node_id}`}
                  data-total={connections.total}
                  data-hidden={expandable}
                  aria-label={`${connections.total} ${t('connections')}`}
                  style={{
                    position: 'absolute',
                    bottom: -6,
                    right: -10,
                    minWidth: 18,
                    height: 16,
                    paddingLeft: 3,
                    paddingRight: 3,
                    borderRadius: '8px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 10,
                    fontWeight: 600,
                    lineHeight: 1,
                    color: expandable > 0 ? theme.palette.primary.contrastText : theme.palette.text.secondary,
                    backgroundColor: expandable > 0 ? theme.palette.primary.main : theme.palette.background.paper,
                    border: `1px solid ${expandable > 0 ? theme.palette.primary.main : theme.palette.divider}`,
                  }}
                >
                  {expandable > 0 ? `+${expandable}` : connections.total}
                </div>
              )}
              {marks.length > 0 && (
                <div
                  style={{
                    position: 'absolute',
                    top: -6,
                    left: -6,
                    display: 'flex',
                    gap: '2px',
                  }}
                >
                  {marks.map(m => (
                    <div
                      key={m.key}
                      data-testid={`graph-node-${m.key}-${node.node_id}`}
                      aria-label={m.label}
                      style={{
                        display: 'flex',
                        borderRadius: '50%',
                        backgroundColor: theme.palette.background.paper,
                        border: `1px solid ${theme.palette.divider}`,
                      }}
                    >
                      {m.mark}
                    </div>
                  ))}
                </div>
              )}
            </div>
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
            {label}
            {truncated ? ' +' : ''}
          </Text>
          {!groupCount && (
            // The entity type under the name, as OpenCTI paints its nodes.
            <Text
              variant="content-caption"
              style={{
                maxWidth: NODE_WIDTH,
                fontSize: 10,
                opacity: 0.7,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {t(NODE_TYPE_LABEL[node.node_type])}
            </Text>
          )}
          <Handle type="source" position={Position.Bottom} style={{ visibility: 'hidden' }} />
        </div>
      </TooltipTrigger>
      <TooltipContent>
        {groupCount ? label : `${t(NODE_TYPE_LABEL[node.node_type])}: ${node.node_label}`}
        {node.node_failed_count > 0 ? ` · ${t('Failed expectations')}: ${node.node_failed_count}` : ''}
        {node.node_finding_count > 0 ? ` · ${t('Findings')}: ${node.node_finding_count}` : ''}
        {connectionsHint}
        {marks.map(m => ` · ${m.label}`).join('')}
      </TooltipContent>
    </Tooltip>
  );
};

export default memo(GraphEntityNode);
