import { Chip, Tooltip } from '@mui/material';
import {
  ComputerOutlined,
  ExpandLessOutlined,
  ExpandMoreOutlined,
  FolderOpenOutlined,
  KeyOutlined,
  KeyboardTabOutlined,
  PersonOutlined,
  ScheduleOutlined,
} from '@mui/icons-material';
import { type FunctionComponent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useFormatter } from '../../../../../components/i18n';
import {
  type AttackPathNode,
  getNodeStatus,
  STATUS_COLORS,
} from './attackPathUtils';

interface AttackPathFeedProps {
  nodes: AttackPathNode[];
  selectedNodeId: string | null;
  onSelectNode: (nodeId: string | null) => void;
  onOpenResult: (node: AttackPathNode) => void;
  highlightedActionIds?: Set<string>;
  pathActionOrder?: Map<string, number>;
}

// ── Feed item types ───────────────────────────────────────────────────────────
// A "solo" item is a single action that has no siblings with the same
// payload_name + endpoint. A "group" item bundles 2+ such actions.
interface SoloItem { kind: 'solo'; node: AttackPathNode }
interface GroupItem { kind: 'group'; key: string; label: string; endpointLabel: string; nodes: AttackPathNode[] }
type FeedItem = SoloItem | GroupItem;

function buildFeedItems(actionNodes: AttackPathNode[]): FeedItem[] {
  // Group CONSECUTIVE nodes that share the same node_payload_name (endpoint-agnostic).
  // Non-consecutive same-payload actions are NOT grouped.
  const items: FeedItem[] = [];
  let i = 0;

  while (i < actionNodes.length) {
    const node = actionNodes[i];
    const payloadKey = node.node_payload_name ?? node.node_label;

    // Collect all consecutive nodes with the same payload key
    const run: AttackPathNode[] = [node];
    let j = i + 1;
    while (j < actionNodes.length) {
      const nextKey = actionNodes[j].node_payload_name ?? actionNodes[j].node_label;
      if (nextKey === payloadKey) {
        run.push(actionNodes[j]);
        j++;
      } else {
        break;
      }
    }

    if (run.length === 1) {
      items.push({ kind: 'solo', node: run[0] });
    } else {
      const first = run[0];
      const payloadLabel = first.node_payload_name ?? first.node_label;
      // Collect distinct endpoints across the run for the subtitle
      const eps = [...new Set(
        run.map((n) => [n.node_hostname, n.node_ip].filter(Boolean).join(' · ')).filter(Boolean),
      )];
      const endpointLabel = eps.length === 0 ? '' : eps.length <= 2 ? eps.join(', ') : `${eps[0]} +${eps.length - 1} more`;
      items.push({ kind: 'group', key: `${payloadLabel}::${i}`, label: payloadLabel, endpointLabel, nodes: run });
    }

    i = j;
  }

  return items;
}

// ── Shared detail row ─────────────────────────────────────────────────────────
const DetailRow: FunctionComponent<{
  icon: React.ReactNode; label: string; value: string; mono?: boolean; color?: string;
}> = ({ icon, label, value, mono, color }) => (
  <div style={{ display: 'flex', alignItems: 'flex-start', gap: 6, marginTop: 5 }}>
    <span style={{ display: 'flex', alignItems: 'center', opacity: 0.4, marginTop: 1, flexShrink: 0 }}>{icon}</span>
    <div style={{ flex: 1, minWidth: 0 }}>
      <span style={{ fontSize: 9, opacity: 0.45, display: 'block', textTransform: 'uppercase', letterSpacing: 0.5 }}>{label}</span>
      <span style={{ fontSize: 10, fontFamily: mono ? 'monospace' : undefined, color: color ?? 'rgba(255,255,255,0.82)', wordBreak: 'break-all' }}>
        {value}
      </span>
    </div>
  </div>
);

// ── Single action card (used inside groups and as standalone) ─────────────────
const ActionCard: FunctionComponent<{
  node: AttackPathNode;
  isSelected: boolean;
  isHighlighted: boolean;
  stepNum?: number;
  hasPathHighlight: boolean;
  indented?: boolean;
  onSelect: (node: AttackPathNode) => void;
  onOpenResult: (node: AttackPathNode) => void;
  refCallback?: (el: HTMLDivElement | null) => void;
  t: (s: string) => string;
  fldt: (s: string) => string;
}> = ({ node, isSelected, isHighlighted, stepNum, hasPathHighlight, indented, onSelect, onOpenResult, refCallback, t, fldt }) => {
  const status = getNodeStatus(node);
  const colors = STATUS_COLORS[status];
  const filesCount = node.node_accessed_files?.length ?? 0;
  const credsCount = node.node_credentials_found?.length ?? 0;

  return (
    <div
      ref={refCallback}
      onClick={() => onSelect(node)}
      style={{
        padding: '8px 10px',
        paddingLeft: indented ? 10 : 12,
        borderRadius: 6,
        cursor: 'pointer',
        border: `1px solid ${colors.fill}${isSelected ? '70' : '30'}`,
        borderLeft: `4px solid ${colors.fill}`,
        backgroundColor: isSelected ? `${colors.fill}22` : `${colors.fill}0d`,
        transition: 'border 0.15s, background-color 0.15s, opacity 0.2s',
        opacity: isHighlighted ? 1 : 0.18,
        outline: hasPathHighlight && isHighlighted ? `1px solid ${colors.fill}55` : undefined,
        marginLeft: indented ? 8 : 0,
        marginTop: indented ? 3 : 0,
      }}
      onMouseEnter={(e) => { if (!isSelected) e.currentTarget.style.backgroundColor = `${colors.fill}18`; }}
      onMouseLeave={(e) => { if (!isSelected) e.currentTarget.style.backgroundColor = `${colors.fill}0d`; }}
    >
      {/* Header row */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        {stepNum !== undefined && (
          <div style={{
            width: 18, height: 18, borderRadius: '50%', flexShrink: 0,
            backgroundColor: colors.fill, color: 'rgba(0,0,0,0.88)',
            fontSize: 9, fontWeight: 800,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            {stepNum}
          </div>
        )}
        <span style={{ fontSize: 12, fontWeight: 600, flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: colors.fill }}>
          {node.node_label}
        </span>
        <Tooltip title={t('Open result panel')}>
          <span
            onClick={(e) => { e.stopPropagation(); onOpenResult(node); }}
            style={{ display: 'flex', alignItems: 'center', padding: '2px', borderRadius: 3, cursor: 'pointer', opacity: 0.4, transition: 'opacity 0.15s' }}
            onMouseEnter={(e) => (e.currentTarget.style.opacity = '0.9')}
            onMouseLeave={(e) => (e.currentTarget.style.opacity = '0.4')}
          >
            <KeyboardTabOutlined style={{ fontSize: 13 }} />
          </span>
        </Tooltip>
      </div>

      {/* Collapsed: compact host + time */}
      {!isSelected && (
        <>
          {node.node_hostname && (
            <span style={{ fontSize: 10, opacity: 0.45, paddingLeft: 16, display: 'block', marginTop: 2 }}>
              ↳ {node.node_hostname}{node.node_ip ? ` · ${node.node_ip}` : ''}
            </span>
          )}
          {node.node_executed_at && (
            <span style={{ fontSize: 9, opacity: 0.3, paddingLeft: 16, display: 'block', marginTop: 1 }}>
              {fldt(node.node_executed_at)}
            </span>
          )}
        </>
      )}

      {/* Expanded: rich detail */}
      {isSelected && (
        <div style={{ marginTop: 8, borderTop: `1px solid ${colors.fill}30`, paddingTop: 8 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
            <Chip
              label={status.charAt(0).toUpperCase() + status.slice(1)}
              size="small"
              sx={{ height: 18, fontSize: 10, fontWeight: 700, backgroundColor: `${colors.fill}22`, color: colors.fill }}
            />
            {node.node_payload_name && (
              <span style={{ fontSize: 10, opacity: 0.55, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1 }}>
                {node.node_payload_name}
              </span>
            )}
          </div>
          {(node.node_hostname || node.node_ip) && (
            <DetailRow icon={<ComputerOutlined style={{ fontSize: 12 }} />} label={t('Endpoint')} value={[node.node_hostname, node.node_ip].filter(Boolean).join(' · ')} mono color="#64b5f6" />
          )}
          {node.node_user_privileges && (
            <DetailRow icon={<PersonOutlined style={{ fontSize: 12 }} />} label={t('User / Privileges')} value={node.node_user_privileges} color="#ff9800" />
          )}
          {node.node_executed_at && (
            <DetailRow icon={<ScheduleOutlined style={{ fontSize: 12 }} />} label={t('Executed at')} value={fldt(node.node_executed_at)} />
          )}
          {filesCount > 0 && (
            <DetailRow icon={<FolderOpenOutlined style={{ fontSize: 12 }} />} label={t('Files accessed')} value={node.node_accessed_files!.slice(0, 3).join(', ') + (filesCount > 3 ? ` +${filesCount - 3} more` : '')} mono color="#ce93d8" />
          )}
          {credsCount > 0 && (
            <DetailRow icon={<KeyOutlined style={{ fontSize: 12 }} />} label={t('Credentials found')} value={`${credsCount} credential${credsCount > 1 ? 's' : ''} captured`} color="#f44336" />
          )}
        </div>
      )}
    </div>
  );
};

// ── Main feed ─────────────────────────────────────────────────────────────────
const AttackPathFeed: FunctionComponent<AttackPathFeedProps> = ({
  nodes,
  selectedNodeId,
  onSelectNode,
  onOpenResult,
  highlightedActionIds,
  pathActionOrder,
}) => {
  const { t, fldt } = useFormatter();
  const feedRef = useRef<HTMLDivElement>(null);
  const entryRefs = useRef<Map<string, HTMLDivElement>>(new Map());
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());

  const actionNodes = useMemo(() => {
    const all = nodes
      .filter((n) => n.node_type === 'ACTION')
      .sort((a, b) => (a.node_executed_at ?? '').localeCompare(b.node_executed_at ?? ''));

    const hasOrder = pathActionOrder && pathActionOrder.size > 0;
    if (!hasOrder) return all;

    const inPath = all.filter((n) => pathActionOrder!.has(n.node_id))
      .sort((a, b) => (pathActionOrder!.get(a.node_id) ?? 0) - (pathActionOrder!.get(b.node_id) ?? 0));
    const notInPath = all.filter((n) => !pathActionOrder!.has(n.node_id));
    return [...inPath, ...notInPath];
  }, [nodes, pathActionOrder]);

  const feedItems = useMemo(() => buildFeedItems(actionNodes), [actionNodes]);

  const hasPathHighlight = highlightedActionIds && highlightedActionIds.size > 0;

  // If selected node is inside a collapsed group → auto-expand that group
  useEffect(() => {
    if (!selectedNodeId) return;
    for (const item of feedItems) {
      if (item.kind === 'group' && item.nodes.some((n) => n.node_id === selectedNodeId)) {
        setExpandedGroups((prev) => {
          if (prev.has(item.key)) return prev;
          const next = new Set(prev);
          next.add(item.key);
          return next;
        });
        break;
      }
    }
  }, [selectedNodeId, feedItems]);

  // Scroll to selected node in feed
  useEffect(() => {
    if (selectedNodeId && nodes.find((n) => n.node_id === selectedNodeId && n.node_type === 'ACTION')) {
      const el = entryRefs.current.get(selectedNodeId);
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
  }, [selectedNodeId, nodes]);

  // Path highlight → scroll to first highlighted action
  useEffect(() => {
    if (!hasPathHighlight) return;
    for (const node of actionNodes) {
      if (highlightedActionIds!.has(node.node_id)) {
        const el = entryRefs.current.get(node.node_id);
        if (el) { el.scrollIntoView({ behavior: 'smooth', block: 'nearest' }); break; }
      }
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasPathHighlight, highlightedActionIds]);

  const handleEntryClick = useCallback(
    (node: AttackPathNode) => { onSelectNode(selectedNodeId === node.node_id ? null : node.node_id); },
    [selectedNodeId, onSelectNode],
  );

  const setEntryRef = useCallback((nodeId: string, el: HTMLDivElement | null) => {
    if (el) entryRefs.current.set(nodeId, el);
    else entryRefs.current.delete(nodeId);
  }, []);

  const toggleGroup = useCallback((key: string) => {
    setExpandedGroups((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }, []);

  return (
    <div
      ref={feedRef}
      style={{
        width: 300, minWidth: 300, height: '100%', overflow: 'auto',
        borderRight: '1px solid rgba(255,255,255,0.08)',
        padding: '8px 8px', display: 'flex', flexDirection: 'column', gap: 4,
      }}
    >
      <span style={{ fontSize: 11, fontWeight: 700, opacity: 0.5, padding: '4px 6px 6px', letterSpacing: 1, textTransform: 'uppercase' }}>
        {t('Execution Feed')}
        {hasPathHighlight && (
          <span style={{ marginLeft: 8, fontSize: 9, opacity: 0.6, fontWeight: 400, textTransform: 'none', letterSpacing: 0 }}>
            — path highlighted
          </span>
        )}
      </span>

      {feedItems.length === 0 && (
        <span style={{ fontSize: 13, opacity: 0.5, textAlign: 'center', padding: '32px 0' }}>
          {t('No injects executed yet')}
        </span>
      )}

      {feedItems.map((item) => {
        if (item.kind === 'solo') {
          const { node } = item;
          const isSelected = selectedNodeId === node.node_id;
          const isHighlighted = !hasPathHighlight || highlightedActionIds!.has(node.node_id);
          return (
            <ActionCard
              key={node.node_id}
              node={node}
              isSelected={isSelected}
              isHighlighted={isHighlighted}
              stepNum={pathActionOrder?.get(node.node_id)}
              hasPathHighlight={!!hasPathHighlight}
              onSelect={handleEntryClick}
              onOpenResult={onOpenResult}
              refCallback={(el) => setEntryRef(node.node_id, el)}
              t={t}
              fldt={fldt}
            />
          );
        }

        // ── Group card ────────────────────────────────────────────────────────
        const { key, label, endpointLabel, nodes: groupNodes } = item;
        const isExpanded = expandedGroups.has(key);
        const count = groupNodes.length;

        // Group highlight: dim if none of its nodes are highlighted
        const anyHighlighted = !hasPathHighlight || groupNodes.some((n) => highlightedActionIds!.has(n.node_id));
        // Use a neutral teal color for group headers
        const groupColor = '#4db6ac';

        return (
          <div key={key} style={{ opacity: anyHighlighted ? 1 : 0.18 }}>
            {/* Group header row */}
            <div
              onClick={() => toggleGroup(key)}
              style={{
                padding: '8px 10px 8px 12px',
                borderRadius: isExpanded ? '6px 6px 0 0' : 6,
                cursor: 'pointer',
                border: `1px solid ${groupColor}55`,
                borderLeft: `4px solid ${groupColor}`,
                backgroundColor: `${groupColor}14`,
                display: 'flex', alignItems: 'center', gap: 8,
                transition: 'background-color 0.15s',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = `${groupColor}22`)}
              onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = `${groupColor}14`)}
            >
              {/* Count badge */}
              <div style={{
                flexShrink: 0, padding: '1px 6px', borderRadius: 10,
                backgroundColor: `${groupColor}30`, border: `1px solid ${groupColor}60`,
                fontSize: 10, fontWeight: 800, color: groupColor,
                whiteSpace: 'nowrap',
              }}>
                ×{count}
              </div>

              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 12, fontWeight: 600, color: groupColor, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {label}
                </div>
                {endpointLabel && (
                  <div style={{ fontSize: 10, opacity: 0.5, marginTop: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    ↳ {endpointLabel}
                  </div>
                )}
              </div>

              {/* Expand/collapse chevron */}
              <span style={{ color: groupColor, opacity: 0.7, display: 'flex', alignItems: 'center' }}>
                {isExpanded ? <ExpandLessOutlined style={{ fontSize: 16 }} /> : <ExpandMoreOutlined style={{ fontSize: 16 }} />}
              </span>
            </div>

            {/* Expanded: individual action cards */}
            {isExpanded && (
              <div style={{
                border: `1px solid ${groupColor}40`,
                borderTop: 'none',
                borderRadius: '0 0 6px 6px',
                padding: '4px 6px 6px',
                backgroundColor: `${groupColor}08`,
              }}>
                {groupNodes.map((node) => {
                  const isSelected = selectedNodeId === node.node_id;
                  const isHighlighted = !hasPathHighlight || highlightedActionIds!.has(node.node_id);
                  return (
                    <ActionCard
                      key={node.node_id}
                      node={node}
                      isSelected={isSelected}
                      isHighlighted={isHighlighted}
                      stepNum={pathActionOrder?.get(node.node_id)}
                      hasPathHighlight={!!hasPathHighlight}
                      indented
                      onSelect={handleEntryClick}
                      onOpenResult={onOpenResult}
                      refCallback={(el) => setEntryRef(node.node_id, el)}
                      t={t}
                      fldt={fldt}
                    />
                  );
                })}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
};

export default AttackPathFeed;

