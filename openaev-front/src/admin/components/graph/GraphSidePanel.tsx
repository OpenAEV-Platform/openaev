import { Button, Chip, IconButton, Text } from '@filigran/design-system';
import {
  CenterFocusStrongOutlined,
  CloseOutlined,
  OpenInNewOutlined,
  PushPinOutlined,
  RouteOutlined,
  VisibilityOffOutlined,
} from '@mui/icons-material';
import { Drawer, List, ListItemButton, ListItemText } from '@mui/material';
import { Link } from 'react-router';

import { useFormatter } from '../../../components/i18n';
import OpenCrqPivot from '../../../components/xtm/XtmOpenCrq';
import OpenCtiPivot from '../../../components/xtm/XtmPivot';
import NODE_ICON from './graph-icons';
import {
  EDGE_TYPE_LABEL,
  EDGE_TYPES_BY_NODE_TYPE,
  type GraphEdgeType,
  type GraphNodeOutput,
  type GraphResult,
  NODE_TYPE_LABEL,
  nodeCounterpart,
  nodeHref,
  RESULT_LABEL,
} from './graph-model';
import { type GraphNodeAction } from './GraphExplorer';

interface GraphNeighbour {
  node: GraphNodeOutput;
  type: GraphEdgeType;
  result?: GraphResult | null;
}

interface GraphSidePanelProps {
  /** The selected node the panel describes. */
  node: GraphNodeOutput;
  width: number;
  /** One row per neighbour and relation of the node. */
  neighbours: GraphNeighbour[];
  /** The actions a host adds on a node; only those that apply to this node are shown. */
  nodeActions: GraphNodeAction[];
  pinned: boolean;
  /** The path search starts from this node and waits for its destination. */
  pathFromHere: boolean;
  /** The last expansion of this node was cut at its limit: "Load more" is offered. */
  truncated: boolean;
  /** "Targets · 12 (8 not shown)": a relation with its count in the data and what is not drawn. */
  relationLabel: (node: GraphNodeOutput, type: GraphEdgeType) => string;
  onClose: () => void;
  onExpand: (edges?: GraphEdgeType[], limit?: number) => void;
  onFindPath: () => void;
  onTogglePin: () => void;
  onHide: () => void;
  onSelect: (nodeId: string) => void;
  onHover: (nodeId: string | null) => void;
}

/**
 * The side panel of the selected node, as OpenCTI's graph: its type and counts, its counterparts,
 * the node actions (open, expand, path, pin, hide, the host's own), the expansion by relation and
 * the list of its neighbours. A persistent drawer on the right of the canvas frame.
 */
const GraphSidePanel = ({
  node,
  width,
  neighbours,
  nodeActions,
  pinned,
  pathFromHere,
  truncated,
  relationLabel,
  onClose,
  onExpand,
  onFindPath,
  onTogglePin,
  onHide,
  onSelect,
  onHover,
}: GraphSidePanelProps) => {
  const { t } = useFormatter();
  const counterpart = nodeCounterpart(node);
  const href = nodeHref(node);
  return (
    <Drawer
      data-testid="graph-side-panel"
      anchor="right"
      variant="persistent"
      open
      slotProps={{
        paper: {
          sx: {
            position: 'absolute',
            width,
            p: 2,
          },
        },
      }}
      sx={{
        '& .MuiDrawer-paper': {
          height: '100%',
          boxSizing: 'border-box',
        },
      }}
    >
      <div className="flex items-center gap-2">
        {NODE_ICON[node.node_type]()}
        <Text variant="title-sm" as="div" className="min-w-0 truncate" style={{ flexGrow: 1 }}>
          {node.node_label}
        </Text>
        <IconButton priority="tertiary" icon={<CloseOutlined fontSize="small" />} onClick={onClose} aria-label={t('Close')} />
      </div>
      <Text variant="content-compact" as="div" className="text-default-secondary">
        {t(NODE_TYPE_LABEL[node.node_type])}
        {node.node_subtype ? ` · ${node.node_subtype}` : ''}
      </Text>
      <div className="mt-3 flex flex-wrap gap-2">
        <Chip severity={node.node_failed_count > 0 ? 'critical' : 'neutral'} label={`${t('Failed expectations')}: ${node.node_failed_count}`} />
        <Chip label={`${t('Findings')}: ${node.node_finding_count}`} />
      </div>
      <div className="mt-4 flex flex-col gap-2">
        {/* The node's counterparts, as on the entity's own screen: "Open in", "Not in" or the
            connect call to action, per product. */}
        {counterpart && <OpenCtiPivot kind={counterpart.kind} objectKey={counterpart.key} size="md" />}
        {counterpart && <OpenCrqPivot kind={counterpart.kind} objectKey={counterpart.key} size="md" />}
        {href && (
          <Button asChild priority="secondary">
            <Link to={href}>
              <OpenInNewOutlined fontSize="small" />
              {t('Open')}
            </Link>
          </Button>
        )}
        <Button type="button" priority="secondary" startIcon={<CenterFocusStrongOutlined fontSize="small" />} onClick={() => onExpand()}>
          {t('Expand')}
        </Button>
        <Button
          type="button"
          priority="secondary"
          startIcon={<RouteOutlined fontSize="small" />}
          onClick={onFindPath}
          disabled={pathFromHere}
        >
          {pathFromHere ? t('Select the destination node') : t('Find a path from here')}
        </Button>
        <div className="flex gap-2">
          <Button type="button" priority="tertiary" startIcon={<PushPinOutlined fontSize="small" />} onClick={onTogglePin}>
            {pinned ? t('Unpin') : t('Pin')}
          </Button>
          <Button type="button" priority="tertiary" startIcon={<VisibilityOffOutlined fontSize="small" />} onClick={onHide}>
            {t('Hide')}
          </Button>
        </div>
        {nodeActions.filter(a => a.appliesTo(node)).map(action => (
          <Button key={action.key} type="button" priority="secondary" startIcon={action.icon} onClick={() => action.onClick(node)}>
            {action.label}
          </Button>
        ))}
      </div>
      <Text variant="content-compact-bold" as="div" className="mt-4">{t('Expand by relation')}</Text>
      <div className="mt-1 flex flex-wrap gap-1">
        {EDGE_TYPES_BY_NODE_TYPE[node.node_type].map(type => (
          <Chip key={type} label={relationLabel(node, type)} onClick={() => onExpand([type])} />
        ))}
      </div>
      {truncated && (
        <div className="mt-2">
          <Button type="button" priority="tertiary" onClick={() => onExpand([], 200)}>{t('Load more')}</Button>
        </div>
      )}
      <Text variant="content-compact-bold" as="div" className="mt-4">{t('Neighbours')}</Text>
      <List dense sx={{ overflowY: 'auto' }}>
        {neighbours.map(({ node: neighbour, type, result }) => (
          <ListItemButton
            key={`${type}-${neighbour.node_id}`}
            onClick={() => onSelect(neighbour.node_id)}
            onMouseEnter={() => onHover(neighbour.node_id)}
            onMouseLeave={() => onHover(null)}
          >
            <ListItemText
              primary={neighbour.node_label}
              secondary={`${t(EDGE_TYPE_LABEL[type])} · ${t(NODE_TYPE_LABEL[neighbour.node_type])}${result ? ` · ${t(RESULT_LABEL[result])}` : ''}`}
            />
          </ListItemButton>
        ))}
      </List>
    </Drawer>
  );
};

export default GraphSidePanel;
