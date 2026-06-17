/**
 * FindingsDrawer — right-side slide-in panel showing all captured
 * endpoints / files / credentials / users / CVEs for the current simulation.
 *
 * Clicking any item focuses that finding on the attack map and
 * highlights the producing action in the execution feed.
 */
import {
  BugReportOutlined,
  Close,
  FileDownloadOutlined,
  FolderOpenOutlined,
  GroupOutlined,
  KeyOutlined,
  RouterOutlined,
} from '@mui/icons-material';
import {
  Box,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  Tooltip,
  Typography,
} from '@mui/material';
import { type FunctionComponent, useMemo } from 'react';

import { type AttackPathEdge, type AttackPathNode, STATUS_COLORS } from './attackPathUtils';

// ── Types ─────────────────────────────────────────────────────────────────────

export type DrawerFilter = 'endpoints' | 'files' | 'credentials' | 'users' | 'cves';

export interface DrawerFindingItem {
  id: string;
  label: string;
  /** Secondary info shown below the label (e.g. endpoint name for files/creds) */
  subLabel?: string;
  endpointId: string;
  actionId?: string;
  /** Extra detail shown on far-right (e.g. IP address for endpoints) */
  meta?: string;
  statusColor?: string;
  /**
   * Finding ID matching the format used by AttackPathGraphV6's extractFindings:
   * `${endpointId}::cred::${raw}`, `${endpointId}::file::${raw}`, etc.
   * Undefined for endpoint-type items.
   */
  findingId?: string;
}

interface FindingsDrawerProps {
  open: boolean;
  filterType: DrawerFilter | null;
  nodes: AttackPathNode[];
  edges: AttackPathEdge[];
  onClose: () => void;
  /** Called when user clicks a finding; parent focuses the map + feed + finding */
  onFindingClick: (endpointId: string, actionId?: string, findingId?: string) => void;
}

// ── Config ────────────────────────────────────────────────────────────────────

const FILTER_META: Record<
  DrawerFilter,
  { title: string; color: string; icon: React.ReactNode }
> = {
  endpoints: {
    title: 'Compromised Endpoints',
    color: '#e91e63',
    icon: <RouterOutlined fontSize="small" />,
  },
  files: {
    title: 'Captured Files',
    color: '#9c27b0',
    icon: <FolderOpenOutlined fontSize="small" />,
  },
  credentials: {
    title: 'Captured Credentials',
    color: '#f44336',
    icon: <KeyOutlined fontSize="small" />,
  },
  users: {
    title: 'Discovered Users',
    color: '#a855f7',
    icon: <GroupOutlined fontSize="small" />,
  },
  cves: {
    title: 'Detected CVEs',
    color: '#ef4444',
    icon: <BugReportOutlined fontSize="small" />,
  },
};

function endpointStatusColor(status: string | undefined): string {
  if (status === 'prevented') return STATUS_COLORS.prevented.fill;
  if (status === 'detected') return STATUS_COLORS.detected.fill;
  if (status === 'undetected') return STATUS_COLORS.undetected.fill;
  return 'rgba(255,255,255,0.3)';
}

// ── Credential masking ────────────────────────────────────────────────────────
// Format: "username:password" or "DOMAIN\user:password" or hash-only strings
// Output: "username:abc*****xyz"  (first 3 + ***** + last 3 chars of password)

function maskCredential(cred: string): string {
  const colonIdx = cred.lastIndexOf(':');
  if (colonIdx <= 0 || colonIdx === cred.length - 1) {
    // No colon or no password part — mask the whole string if long enough
    if (cred.length <= 6) return cred;
    return `${cred.slice(0, 3)}${'*'.repeat(5)}${cred.slice(-3)}`;
  }
  const user = cred.slice(0, colonIdx);
  const pass = cred.slice(colonIdx + 1);
  if (pass.length <= 6) {
    // Short password — show first char + stars
    const masked = pass.length > 1 ? `${pass[0]}${'*'.repeat(Math.max(3, pass.length - 1))}` : '*';
    return `${user}:${masked}`;
  }
  return `${user}:${pass.slice(0, 3)}${'*'.repeat(5)}${pass.slice(-3)}`;
}

// ── Export helper ─────────────────────────────────────────────────────────────

function exportItemsToTxt(items: DrawerFindingItem[], filterType: DrawerFilter): void {
  const meta = FILTER_META[filterType];
  const timestamp = new Date().toISOString().replace('T', ' ').slice(0, 19);
  const lines: string[] = [
    `OpenAEV — ${meta.title}`,
    `Exported: ${timestamp}`,
    `Total: ${items.length} item${items.length !== 1 ? 's' : ''}`,
    '─'.repeat(60),
    '',
  ];

  for (const item of items) {
    lines.push(item.label);
    if (item.subLabel) lines.push(`  Endpoint : ${item.subLabel}`);
    if (item.meta)     lines.push(`  IP       : ${item.meta}`);
    lines.push('');
  }

  const blob = new Blob([lines.join('\n')], { type: 'text/plain;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `openaev_${filterType}_${Date.now()}.txt`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

// ── Main component ────────────────────────────────────────────────────────────

const FindingsDrawer: FunctionComponent<FindingsDrawerProps> = ({
  open,
  filterType,
  nodes,
  edges,
  onClose,
  onFindingClick,
}) => {
  // Asset nodes ordered by status severity
  const assetNodes = useMemo(
    () => nodes.filter((n) => n.node_type === 'ASSET'),
    [nodes],
  );

  // Pre-build asset → actions map
  const assetToActions = useMemo(() => {
    const m = new Map<string, AttackPathNode[]>();
    for (const edge of edges.filter((e) => e.edge_type === 'asset_link')) {
      const action = nodes.find((n) => n.node_id === edge.edge_source);
      if (!action) continue;
      const existing = m.get(edge.edge_target) ?? [];
      existing.push(action);
      m.set(edge.edge_target, existing);
    }
    return m;
  }, [nodes, edges]);

  const items = useMemo<DrawerFindingItem[]>(() => {
    if (!filterType) return [];

    if (filterType === 'endpoints') {
      return assetNodes.map((n) => ({
        id: n.node_id,
        label: n.node_hostname ?? n.node_label,
        subLabel: n.node_platform ?? undefined,
        meta: n.node_ip ?? undefined,
        endpointId: n.node_id,
        statusColor: endpointStatusColor(n.node_status),
      }));
    }

    const result: DrawerFindingItem[] = [];
    // track per-endpoint dedup: "endpointId::rawValue"
    const seenPerEndpoint = new Set<string>();

    for (const asset of assetNodes) {
      const actions = assetToActions.get(asset.node_id) ?? [];
      const endpointLabel = asset.node_hostname ?? asset.node_label;

      if (filterType === 'files') {
        // Process ACTIONS first so actionId is captured; then add asset-node-only leftovers
        for (const action of actions) {
          for (const f of action.node_accessed_files ?? []) {
            const key = `${asset.node_id}::${f}`;
            if (seenPerEndpoint.has(key)) continue;
            seenPerEndpoint.add(key);
            const name = f.split(/[\\/]/).pop() ?? f;
            result.push({
              id: key,
              label: name,
              subLabel: endpointLabel,
              endpointId: asset.node_id,
              actionId: action.node_id,
              findingId: `${asset.node_id}::file::${f}`,
            });
          }
        }
        for (const f of asset.node_accessed_files ?? []) {
          const key = `${asset.node_id}::${f}`;
          if (seenPerEndpoint.has(key)) continue;
          seenPerEndpoint.add(key);
          const name = f.split(/[\\/]/).pop() ?? f;
          result.push({
            id: key,
            label: name,
            subLabel: endpointLabel,
            endpointId: asset.node_id,
            findingId: `${asset.node_id}::file::${f}`,
          });
        }
      } else if (filterType === 'credentials') {
        // Process ACTIONS first so actionId is captured; then add asset-node-only leftovers
        for (const action of actions) {
          for (const c of action.node_credentials_found ?? []) {
            const key = `${asset.node_id}::${c}`;
            if (seenPerEndpoint.has(key)) continue;
            seenPerEndpoint.add(key);
            result.push({
              id: key,
              label: maskCredential(c),
              subLabel: endpointLabel,
              endpointId: asset.node_id,
              actionId: action.node_id,
              findingId: `${asset.node_id}::cred::${c}`,
            });
          }
        }
        for (const c of asset.node_credentials_found ?? []) {
          const key = `${asset.node_id}::${c}`;
          if (seenPerEndpoint.has(key)) continue;
          seenPerEndpoint.add(key);
          result.push({
            id: key,
            label: maskCredential(c),
            subLabel: endpointLabel,
            endpointId: asset.node_id,
            findingId: `${asset.node_id}::cred::${c}`,
          });
        }
      } else if (filterType === 'users') {
        // node_users_found on actions (+ deduplicate globally by value+endpoint)
        for (const action of actions) {
          for (const u of action.node_users_found ?? []) {
            const key = `${asset.node_id}::${u}`;
            if (seenPerEndpoint.has(key)) continue;
            seenPerEndpoint.add(key);
            result.push({
              id: key,
              label: u,
              subLabel: endpointLabel,
              endpointId: asset.node_id,
              actionId: action.node_id,
              findingId: `${asset.node_id}::session::${u}`,
            });
          }
        }
      } else if (filterType === 'cves') {
        // node_cves_found on actions; dedup by CVE ID
        const cveIdSeen = new Set<string>();
        for (const action of actions) {
          for (const c of action.node_cves_found ?? []) {
            const cveId = c.match(/CVE-\d{4}-\d+/i)?.[0]?.toUpperCase() ?? c;
            const key = `${asset.node_id}::${cveId}`;
            if (cveIdSeen.has(key)) continue;
            cveIdSeen.add(key);
            if (seenPerEndpoint.has(key)) continue;
            seenPerEndpoint.add(key);
            result.push({
              id: key,
              label: c.length > 50 ? `${c.slice(0, 48)}…` : c,
              subLabel: endpointLabel,
              endpointId: asset.node_id,
              actionId: action.node_id,
              findingId: `${asset.node_id}::cve::${c.slice(0, 30)}`,
            });
          }
        }
      }
    }

    return result;
  }, [filterType, assetNodes, assetToActions]);

  const meta = filterType ? FILTER_META[filterType] : null;
  if (!meta) return null;

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      variant="temporary"
      PaperProps={{
        sx: {
          width: 380,
          backgroundColor: 'rgba(12, 14, 26, 0.97)',
          borderLeft: '1px solid rgba(255,255,255,0.10)',
          backdropFilter: 'blur(20px)',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
          // Must be above AppBar which uses zIndex.drawer + 1 = 1201
          zIndex: (theme: { zIndex: { drawer: number } }) => theme.zIndex.drawer + 2,
        },
      }}
      sx={{
        zIndex: (theme: { zIndex: { drawer: number } }) => theme.zIndex.drawer + 2,
      }}
      SlotProps={{ backdrop: { sx: { backgroundColor: 'rgba(0,0,0,0.3)', zIndex: (theme: { zIndex: { drawer: number } }) => theme.zIndex.drawer + 1 } } }}
    >
      {/* Header */}
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          px: 2,
          py: 1.5,
          borderBottom: '1px solid rgba(255,255,255,0.08)',
          flexShrink: 0,
        }}
      >
        <Box sx={{ color: meta.color, display: 'flex', mr: 0.5 }}>{meta.icon}</Box>
        <Typography
          sx={{ flex: 1, fontWeight: 700, fontSize: 13, color: 'rgba(255,255,255,0.9)' }}
        >
          {meta.title}
        </Typography>
        <Box
          sx={{
            px: 1,
            py: 0.25,
            borderRadius: 2,
            backgroundColor: `${meta.color}22`,
            border: `1px solid ${meta.color}44`,
            fontSize: 11,
            fontWeight: 700,
            color: meta.color,
            mr: 1,
          }}
        >
          {items.length}
        </Box>
        <IconButton
          size="small"
          onClick={() => exportItemsToTxt(items, filterType!)}
          disabled={items.length === 0}
          title="Export list as TXT"
          sx={{ color: 'rgba(255,255,255,0.4)', p: 0.5, '&:hover': { color: meta.color } }}
        >
          <FileDownloadOutlined fontSize="small" />
        </IconButton>
        <IconButton size="small" onClick={onClose} sx={{ color: 'rgba(255,255,255,0.4)', p: 0.5 }}>
          <Close fontSize="small" />
        </IconButton>
      </Box>

      {/* Hint */}
      <Box sx={{ px: 2, py: 1, borderBottom: '1px solid rgba(255,255,255,0.05)', flexShrink: 0 }}>
        <Typography sx={{ fontSize: 10, color: 'rgba(255,255,255,0.35)', lineHeight: 1.4 }}>
          Click any item to highlight it on the attack map and focus the producing action in the feed.
        </Typography>
      </Box>

      {/* List */}
      {items.length === 0 ? (
        <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <Typography sx={{ fontSize: 12, color: 'rgba(255,255,255,0.3)', fontStyle: 'italic' }}>
            No items found
          </Typography>
        </Box>
      ) : (
        <List disablePadding sx={{ flex: 1, overflowY: 'auto', overflowX: 'hidden' }}>
          {items.map((item) => (
            <FindingRow
              key={item.id}
              item={item}
              filterType={filterType!}
              metaColor={meta.color}
              onClick={() => {
                onFindingClick(item.endpointId, item.actionId, item.findingId);
                onClose();
              }}
            />
          ))}
        </List>
      )}
    </Drawer>
  );
};

// ── Row sub-component ──────────────────────────────────────────────────────────

const FILTER_ICONS: Record<DrawerFilter, React.ReactNode> = {
  endpoints: <RouterOutlined sx={{ fontSize: 14 }} />,
  files: <FolderOpenOutlined sx={{ fontSize: 14 }} />,
  credentials: <KeyOutlined sx={{ fontSize: 14 }} />,
  users: <GroupOutlined sx={{ fontSize: 14 }} />,
  cves: <BugReportOutlined sx={{ fontSize: 14 }} />,
};

const FindingRow: FunctionComponent<{
  item: DrawerFindingItem;
  filterType: DrawerFilter;
  metaColor: string;
  onClick: () => void;
}> = ({ item, filterType, metaColor, onClick }) => (
  <Tooltip
    title="Click to focus on attack map"
    placement="left"
    arrow
    componentsProps={{ tooltip: { sx: { fontSize: 10 } } }}
  >
    <ListItemButton
      onClick={onClick}
      sx={{
        px: 2,
        py: 1,
        borderBottom: '1px solid rgba(255,255,255,0.04)',
        gap: 1.5,
        alignItems: 'flex-start',
        transition: 'background-color 0.15s',
        '&:hover': { backgroundColor: `${metaColor}12` },
      }}
    >
      {/* Status dot / icon */}
      <Box sx={{ pt: 0.25, flexShrink: 0 }}>
        {filterType === 'endpoints' && item.statusColor ? (
          <Box
            sx={{
              width: 8,
              height: 8,
              borderRadius: '50%',
              backgroundColor: item.statusColor,
              mt: 0.5,
              boxShadow: `0 0 6px ${item.statusColor}88`,
            }}
          />
        ) : (
          <Box sx={{ color: `${metaColor}99`, display: 'flex' }}>
            {FILTER_ICONS[filterType]}
          </Box>
        )}
      </Box>

      {/* Text */}
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography
          sx={{
            fontSize: 12,
            fontWeight: 600,
            color: 'rgba(255,255,255,0.88)',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {item.label}
        </Typography>
        {item.subLabel && (
          <Typography
            sx={{
              fontSize: 10,
              color: 'rgba(255,255,255,0.38)',
              mt: 0.2,
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {item.subLabel}
          </Typography>
        )}
      </Box>

      {/* Right meta (IP, etc.) */}
      {item.meta && (
        <Typography
          sx={{
            fontSize: 10,
            color: `${metaColor}88`,
            fontFamily: 'monospace',
            flexShrink: 0,
            alignSelf: 'center',
          }}
        >
          {item.meta}
        </Typography>
      )}

      {/* "Focus" arrow hint */}
      <Box
        sx={{
          color: 'rgba(255,255,255,0.15)',
          fontSize: 12,
          flexShrink: 0,
          alignSelf: 'center',
          '&:hover': { color: metaColor },
        }}
      >
        ›
      </Box>
    </ListItemButton>
  </Tooltip>
);

export default FindingsDrawer;
