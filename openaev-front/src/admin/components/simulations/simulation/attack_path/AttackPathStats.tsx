import { FolderOpenOutlined, KeyOutlined, AccountTreeOutlined, ArrowDropDown, GroupOutlined, BugReportOutlined, RouterOutlined } from '@mui/icons-material';
import { Chip, Menu, MenuItem } from '@mui/material';
import { type FunctionComponent, useState } from 'react';
import { useFormatter } from '../../../../../components/i18n';
import { type AttackPathStats, type AttackPathDefinition, STATUS_COLORS } from './attackPathUtils';

interface AttackPathStatsProps {
  stats?: AttackPathStats;
  onEndpointsClick?: () => void;
  onFilesClick?: () => void;
  onCredentialsClick?: () => void;
  onUsersClick?: () => void;
  onCvesClick?: () => void;
  paths?: AttackPathDefinition[];
  selectedPathId?: string | null;
  onPathSelect?: (id: string | null) => void;
}

const AttackPathsSelector: FunctionComponent<{
  paths: AttackPathDefinition[];
  selectedPathId?: string | null;
  onPathSelect: (id: string | null) => void;
}> = ({ paths, selectedPathId, onPathSelect }) => {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const open = Boolean(anchorEl);
  const selected = paths.find((p) => p.path_id === selectedPathId);
  const color = '#42a5f5';

  return (
    <>
      <div
        onClick={(e) => setAnchorEl(e.currentTarget)}
        style={{
          display: 'flex', alignItems: 'center', gap: 10,
          padding: '7px 12px', borderRadius: 6, cursor: 'pointer',
          backgroundColor: `${color}${selectedPathId ? '28' : '18'}`,
          border: `1px solid ${selectedPathId ? color : `${color}40`}`,
          minWidth: 160, transition: 'background-color 0.15s, border-color 0.15s',
        }}
        onMouseEnter={(e) => {
          (e.currentTarget as HTMLDivElement).style.backgroundColor = `${color}30`;
          (e.currentTarget as HTMLDivElement).style.borderColor = `${color}80`;
        }}
        onMouseLeave={(e) => {
          (e.currentTarget as HTMLDivElement).style.backgroundColor = `${color}${selectedPathId ? '28' : '18'}`;
          (e.currentTarget as HTMLDivElement).style.borderColor = selectedPathId ? color : `${color}40`;
        }}
      >
        <AccountTreeOutlined style={{ color, fontSize: 18, flexShrink: 0 }} />
        <div style={{ display: 'flex', flexDirection: 'column', gap: 0, flex: 1 }}>
          <span style={{ fontSize: 17, fontWeight: 700, color, lineHeight: 1.1 }}>{paths.length}</span>
          <span style={{ fontSize: 10, opacity: 0.75, lineHeight: 1.2 }}>Attack Paths</span>
        </div>
        {selected && (
          <Chip
            label={selected.path_name.length > 14 ? `${selected.path_name.slice(0, 12)}…` : selected.path_name}
            size="small"
            onDelete={(e) => { e.stopPropagation(); onPathSelect(null); }}
            sx={{ backgroundColor: `${color}25`, color, fontSize: 10, height: 20, maxWidth: 110, '& .MuiChip-deleteIcon': { fontSize: 12, color: `${color}99` } }}
          />
        )}
        <ArrowDropDown style={{ color, fontSize: 18, opacity: 0.65, flexShrink: 0 }} />
      </div>
      <Menu
        anchorEl={anchorEl} open={open} onClose={() => setAnchorEl(null)}
        PaperProps={{ sx: { backgroundColor: 'rgba(18,20,36,0.98)', border: '1px solid rgba(255,255,255,0.12)', minWidth: 240, boxShadow: '0 8px 32px rgba(0,0,0,0.7)' } }}
      >
        <MenuItem
          onClick={() => { onPathSelect(null); setAnchorEl(null); }}
          sx={{ fontSize: 12, color: 'rgba(255,255,255,0.45)', fontStyle: 'italic', '&:hover': { backgroundColor: 'rgba(255,255,255,0.05)' } }}
        >
          — All Paths
        </MenuItem>
        {paths.map((path) => {
          const isFailed = path.path_outcome === 'failed' || path.path_outcome === 'partial';
          const dotColor = isFailed
            ? (path.path_outcome === 'partial' ? STATUS_COLORS.detected.fill : STATUS_COLORS.prevented.fill)
            : STATUS_COLORS.undetected.fill;
          return (
            <MenuItem
              key={path.path_id} selected={path.path_id === selectedPathId}
              onClick={() => { onPathSelect(path.path_id); setAnchorEl(null); }}
              sx={{ fontSize: 12, color: 'rgba(255,255,255,0.85)', gap: 1, '&.Mui-selected': { backgroundColor: `${color}18` }, '&:hover': { backgroundColor: 'rgba(255,255,255,0.06)' } }}
            >
              <span style={{ width: 8, height: 8, borderRadius: '50%', backgroundColor: dotColor, display: 'inline-block', flexShrink: 0 }} />
              {path.path_name}
            </MenuItem>
          );
        })}
      </Menu>
    </>
  );
};

const ClickableStatBadge: FunctionComponent<{
  label: string;
  count: number;
  color: string;
  icon?: React.ReactNode;
  onClick?: () => void;
}> = ({ label, count, color, icon, onClick }) => (
  <div
    onClick={onClick}
    style={{
      display: 'flex',
      alignItems: 'center',
      gap: 12,
      padding: '7px 12px',
      borderRadius: 6,
      backgroundColor: `${color}18`,
      border: `1px solid ${color}40`,
      minWidth: 160,
      cursor: onClick ? 'pointer' : 'default',
      transition: onClick ? 'background-color 0.15s, border-color 0.15s' : undefined,
    }}
    onMouseEnter={(e) => {
      if (onClick) {
        (e.currentTarget as HTMLDivElement).style.backgroundColor = `${color}28`;
        (e.currentTarget as HTMLDivElement).style.borderColor = `${color}70`;
      }
    }}
    onMouseLeave={(e) => {
      if (onClick) {
        (e.currentTarget as HTMLDivElement).style.backgroundColor = `${color}18`;
        (e.currentTarget as HTMLDivElement).style.borderColor = `${color}40`;
      }
    }}
  >
    {icon && (
      <span style={{ color, display: 'flex', fontSize: 18 }}>
        {icon}
      </span>
    )}
    <div style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
      <span style={{ fontSize: 17, fontWeight: 700, color, lineHeight: 1.1 }}>{count}</span>
      <span style={{ fontSize: 10, opacity: 0.75, lineHeight: 1.2 }}>{label}</span>
    </div>
  </div>
);

const AttackPathStatsComponent: FunctionComponent<AttackPathStatsProps> = ({
  stats,
  onEndpointsClick,
  onFilesClick,
  onCredentialsClick,
  onUsersClick,
  onCvesClick,
  paths,
  selectedPathId,
  onPathSelect,
}) => {
  const { t } = useFormatter();

  if (!stats) return null;

  return (
    <div style={{
      position: 'relative',
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      gap: 16,
      padding: '8px 16px',
      borderBottom: '1px solid var(--divider, rgba(255,255,255,0.12))',
      flexShrink: 0,
      flexWrap: 'wrap',
    }}>
      <ClickableStatBadge
        label={t('Endpoints')}
        count={stats.stats_captured_endpoints ?? 0}
        color="#e91e63"
        icon={<RouterOutlined fontSize="inherit" />}
        onClick={onEndpointsClick}
      />
      <ClickableStatBadge
        label={t('Files')}
        count={stats.stats_captured_files ?? 0}
        color="#9c27b0"
        icon={<FolderOpenOutlined fontSize="inherit" />}
        onClick={onFilesClick}
      />
      <ClickableStatBadge
        label={t('Credentials')}
        count={stats.stats_captured_credentials ?? 0}
        color="#f44336"
        icon={<KeyOutlined fontSize="inherit" />}
        onClick={onCredentialsClick}
      />
      <ClickableStatBadge
        label={t('Users')}
        count={stats.stats_captured_users ?? 0}
        color="#a855f7"
        icon={<GroupOutlined fontSize="inherit" />}
        onClick={onUsersClick}
      />
      <ClickableStatBadge
        label={t('CVEs')}
        count={stats.stats_captured_cves ?? 0}
        color="#ef4444"
        icon={<BugReportOutlined fontSize="inherit" />}
        onClick={onCvesClick}
      />
      {paths && paths.length > 0 && onPathSelect && (
        <AttackPathsSelector
          paths={paths}
          selectedPathId={selectedPathId}
          onPathSelect={onPathSelect}
        />
      )}
      <span style={{
        position: 'absolute',
        right: 16,
        fontSize: 11,
        opacity: 0.45,
        whiteSpace: 'nowrap',
      }}>
        {stats.stats_executed_actions}/{stats.stats_total_actions} {t('actions')}
      </span>
    </div>
  );
};

export default AttackPathStatsComponent;
