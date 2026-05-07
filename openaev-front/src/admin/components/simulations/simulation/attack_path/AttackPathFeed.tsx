import { Chip } from '@mui/material';
import { type FunctionComponent, useCallback, useEffect, useRef, useState } from 'react';
import { useFormatter } from '../../../../../components/i18n';
import {
  type AttackPathNode,
  type AttackStepStatus,
  getNodeStatus,
  STATUS_COLORS,
} from './attackPathUtils';

interface AttackPathFeedProps {
  nodes: AttackPathNode[];
  selectedNodeId: string | null;
  onSelectNode: (nodeId: string | null) => void;
}

const AttackPathFeed: FunctionComponent<AttackPathFeedProps> = ({
  nodes,
  selectedNodeId,
  onSelectNode,
}) => {
  const { t, fldt } = useFormatter();
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const feedRef = useRef<HTMLDivElement>(null);
  const entryRefs = useRef<Map<string, HTMLDivElement>>(new Map());

  // Only show action nodes, sorted most recent first
  const actionNodes = nodes
    .filter((n) => n.node_type === 'ACTION')
    .sort((a, b) => {
      const dateA = a.node_executed_at ?? '';
      const dateB = b.node_executed_at ?? '';
      return dateB.localeCompare(dateA);
    });

  // Sync selection from graph → expand in feed
  useEffect(() => {
    if (selectedNodeId && nodes.find((n) => n.node_id === selectedNodeId && n.node_type === 'ACTION')) {
      setExpandedId(selectedNodeId);
      const el = entryRefs.current.get(selectedNodeId);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      }
    }
  }, [selectedNodeId, nodes]);

  const handleEntryClick = useCallback(
    (nodeId: string) => {
      const newExpanded = expandedId === nodeId ? null : nodeId;
      setExpandedId(newExpanded);
      onSelectNode(newExpanded);
    },
    [expandedId, onSelectNode],
  );

  const setEntryRef = useCallback((nodeId: string, el: HTMLDivElement | null) => {
    if (el) {
      entryRefs.current.set(nodeId, el);
    } else {
      entryRefs.current.delete(nodeId);
    }
  }, []);

  return (
    <div
      ref={feedRef}
      style={{
        width: 320,
        minWidth: 320,
        height: '100%',
        overflow: 'auto',
        borderRight: '1px solid rgba(255,255,255,0.12)',
        padding: 12,
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
      }}
    >
      <span style={{ fontSize: 12, fontWeight: 600, opacity: 0.7, padding: '0 4px 4px' }}>
        {t('Execution feed')}
      </span>

      {actionNodes.length === 0 && (
        <span style={{ fontSize: 13, opacity: 0.5, textAlign: 'center', padding: '32px 0' }}>
          {t('No injects executed yet')}
        </span>
      )}

      {actionNodes.map((node) => {
        const status = getNodeStatus(node);
        const colors = STATUS_COLORS[status];
        const isSelected = selectedNodeId === node.node_id;
        const isExpanded = expandedId === node.node_id;

        return (
          <div
            key={node.node_id}
            ref={(el) => setEntryRef(node.node_id, el)}
            onClick={() => handleEntryClick(node.node_id)}
            style={{
              padding: 10,
              borderRadius: 6,
              cursor: 'pointer',
              border: isSelected ? `2px solid ${colors.fill}` : '2px solid transparent',
              backgroundColor: isSelected ? colors.bg : 'rgba(255,255,255,0.03)',
              transition: 'border 0.2s, background-color 0.2s',
            }}
          >
            {/* Header row */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <div style={{
                width: 10,
                height: 10,
                borderRadius: '50%',
                backgroundColor: colors.fill,
                flexShrink: 0,
              }} />
              <span style={{ fontSize: 13, fontWeight: 600, flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {node.node_label}
              </span>
            </div>

            {/* Target asset */}
            {node.node_hostname && (
              <span style={{ fontSize: 11, opacity: 0.6, paddingLeft: 18, display: 'block', marginTop: 2 }}>
                → {node.node_hostname}{node.node_ip ? ` (${node.node_ip})` : ''}
              </span>
            )}

            {/* Timestamp */}
            {node.node_executed_at && (
              <span style={{ fontSize: 10, opacity: 0.4, paddingLeft: 18, display: 'block', marginTop: 2 }}>
                {fldt(node.node_executed_at)}
              </span>
            )}

            {/* Inline expanded details */}
            {isExpanded && (
              <div style={{
                marginTop: 10,
                padding: 10,
                borderRadius: 4,
                backgroundColor: 'rgba(0,0,0,0.3)',
                borderTop: `2px solid ${colors.fill}`,
              }}>
                {/* Status badge */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 }}>
                  <Chip
                    label={t(status.charAt(0).toUpperCase() + status.slice(1))}
                    size="small"
                    sx={{
                      backgroundColor: colors.fill,
                      color: '#fff',
                      fontWeight: 600,
                      fontSize: 11,
                      height: 22,
                    }}
                  />
                </div>

                {/* Details grid */}
                {node.node_hostname && (
                  <DetailRow label={t('Target')} value={node.node_hostname} />
                )}
                {node.node_ip && (
                  <DetailRow label={t('IP')} value={node.node_ip} />
                )}
                {node.node_executed_at && (
                  <DetailRow label={t('Executed')} value={fldt(node.node_executed_at)} />
                )}
                {node.node_platform && (
                  <DetailRow label={t('Platform')} value={node.node_platform} />
                )}

                {/* Expectations list */}
                {node.node_expectations && node.node_expectations.length > 0 && (
                  <div style={{ marginTop: 8 }}>
                    <span style={{ fontSize: 10, fontWeight: 600, opacity: 0.6, display: 'block', marginBottom: 4 }}>
                      {t('Expectations')}
                    </span>
                    {node.node_expectations.map((exp) => (
                      <div
                        key={exp.expectation_id}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 6,
                          padding: '3px 0',
                          fontSize: 11,
                        }}
                      >
                        <span style={{
                          width: 6,
                          height: 6,
                          borderRadius: '50%',
                          backgroundColor: exp.expectation_status === 'SUCCESS' ? '#4caf50'
                            : exp.expectation_status === 'FAILED' ? '#f44336' : '#9e9e9e',
                          flexShrink: 0,
                        }} />
                        <span style={{ opacity: 0.8 }}>{exp.expectation_type}</span>
                        <span style={{ opacity: 0.5, marginLeft: 'auto' }}>{exp.expectation_status}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
};

const DetailRow: FunctionComponent<{ label: string; value: string }> = ({ label, value }) => (
  <div style={{ display: 'flex', fontSize: 11, padding: '2px 0' }}>
    <span style={{ opacity: 0.5, width: 70, flexShrink: 0 }}>{label}</span>
    <span style={{ opacity: 0.9 }}>{value}</span>
  </div>
);

export default AttackPathFeed;
