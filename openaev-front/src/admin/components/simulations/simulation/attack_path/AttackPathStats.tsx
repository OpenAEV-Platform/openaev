import { type FunctionComponent } from 'react';
import { useFormatter } from '../../../../../components/i18n';
import { type AttackPathStats, STATUS_COLORS } from './attackPathUtils';

interface AttackPathStatsProps {
  stats: AttackPathStats;
}

const StatBadge: FunctionComponent<{ label: string; count: number; color: string }> = ({
  label,
  count,
  color,
}) => (
  <div style={{
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    padding: '4px 12px',
    borderRadius: 4,
    backgroundColor: `${color}18`,
    border: `1px solid ${color}40`,
  }}>
    <div style={{
      width: 8,
      height: 8,
      borderRadius: '50%',
      backgroundColor: color,
    }} />
    <span style={{ fontSize: 13, fontWeight: 600, color }}>{count}</span>
    <span style={{ fontSize: 11, opacity: 0.8 }}>{label}</span>
  </div>
);

const AttackPathStatsComponent: FunctionComponent<AttackPathStatsProps> = ({ stats }) => {
  const { t } = useFormatter();

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: 12,
      padding: '8px 16px',
      borderBottom: '1px solid var(--divider, rgba(255,255,255,0.12))',
      flexShrink: 0,
    }}>
      <StatBadge label={t('Prevented')} count={stats.stats_prevented} color={STATUS_COLORS.prevented.fill} />
      <StatBadge label={t('Detected')} count={stats.stats_detected} color={STATUS_COLORS.detected.fill} />
      <StatBadge label={t('Undetected')} count={stats.stats_undetected} color={STATUS_COLORS.undetected.fill} />
      <StatBadge label={t('Pending')} count={stats.stats_pending} color={STATUS_COLORS.pending.fill} />
      <div style={{ flex: 1 }} />
      <span style={{ fontSize: 12, opacity: 0.7 }}>
        {stats.stats_executed_actions} / {stats.stats_total_actions} {t('actions executed')}
      </span>
    </div>
  );
};

export default AttackPathStatsComponent;
