import { Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';

import Section from '../../../../../../components/common/overview/Section';
import { useFormatter } from '../../../../../../components/i18n';
import { type InjectStatusOutput } from '../../../../../../utils/api-types';
import EndpointTraces from './EndpointTraces';

type Props = { injectStatus: InjectStatusOutput };

interface StatPillProps {
  label: string;
  value: string | null;
}

// Marketplace-style stat pill (same visual language as the hero stat chips).
const StatPill = ({ label, value }: StatPillProps) => {
  const theme = useTheme();
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: theme.spacing(0.75),
        padding: theme.spacing(0.5, 1.25),
        borderRadius: theme.shape.borderRadius,
        border: `1px solid ${alpha(theme.palette.text.primary, 0.1)}`,
        backgroundColor: alpha(theme.palette.text.primary, 0.04),
      }}
    >
      <Typography sx={{
        fontSize: 13,
        color: 'text.secondary',
      }}
      >
        {label}
      </Typography>
      <Typography sx={{
        fontSize: 13,
        fontWeight: 600,
        fontVariantNumeric: 'tabular-nums',
      }}
      >
        {value || '-'}
      </Typography>
    </div>
  );
};

const GlobalExecutionTraces = ({ injectStatus }: Props) => {
  const { t, fldt, du } = useFormatter();
  const theme = useTheme();

  const mainTraces = injectStatus.status_main_traces ?? [];
  const startDate = injectStatus.tracking_sent_date;
  const endDate = injectStatus.tracking_end_date;
  const duration = startDate && endDate
    ? du(new Date(endDate).getTime() - new Date(startDate).getTime())
    : null;

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(2),
    }}
    >
      <div style={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: theme.spacing(1),
      }}
      >
        <StatPill label={t('Start date')} value={fldt(startDate)} />
        <StatPill label={t('End date')} value={fldt(endDate)} />
        <StatPill label={t('Duration')} value={duration} />
      </div>
      <Section title={t('Traces')}>
        {mainTraces.length > 0
          ? <EndpointTraces tracesByAgent={mainTraces} />
          : <Typography variant="body2" sx={{ color: 'text.secondary' }}>{t('No data available')}</Typography>}
      </Section>
    </div>
  );
};

export default GlobalExecutionTraces;
