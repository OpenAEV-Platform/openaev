import { Box, Typography } from '@mui/material';
import { alpha, type Theme, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import { type ModuleDataState, type PostureData } from '../useReportingRenderData';
import { ModuleEmpty, ModuleError } from './ModuleSection';

/**
 * Executive summary: posture score, prevention / detection rates, injects
 * executed, and a plain-language verdict paragraph.
 */

interface Props {
  posture: ModuleDataState<PostureData>;
  injectCount: ModuleDataState<number>;
}

const rate = (success: number, failed: number): number | null =>
  (success + failed > 0 ? Math.round((success / (success + failed)) * 100) : null);

const scoreColor = (theme: Theme, score: number | null): string => {
  if (score === null) return theme.palette.text.secondary;
  if (score >= 75) return theme.palette.success.main;
  if (score >= 50) return theme.palette.warning.main;
  if (score >= 25) return '#ff7043';
  return theme.palette.error.main;
};

const Kpi: FunctionComponent<{
  label: string;
  value: ReactNode;
  accent?: string;
}> = ({ label, value, accent }) => {
  const theme = useTheme();
  return (
    <Box sx={{
      flex: 1,
      minWidth: 130,
      padding: 2.5,
      borderRadius: 1,
      border: `1px solid ${alpha(theme.palette.text.primary, 0.12)}`,
      // The accent tops the card so the score palette reads at a glance.
      borderTop: `3px solid ${accent ?? alpha(theme.palette.text.primary, 0.25)}`,
      backgroundColor: alpha(theme.palette.text.primary, 0.02),
    }}
    >
      <Typography sx={{
        fontSize: 10,
        letterSpacing: '0.12em',
        textTransform: 'uppercase',
        color: 'text.secondary',
        marginBottom: 1,
      }}
      >
        {label}
      </Typography>
      <Typography sx={{
        fontFamily: '"Geologica", sans-serif',
        fontSize: 28,
        fontWeight: 600,
        lineHeight: 1,
        color: accent ?? 'text.primary',
      }}
      >
        {value}
      </Typography>
    </Box>
  );
};

const ExecutiveSummaryModule: FunctionComponent<Props> = ({ posture, injectCount }) => {
  const theme = useTheme();
  const { t, n } = useFormatter();

  if (posture.status === 'error') return <ModuleError />;
  if (posture.status !== 'success' || !posture.data) return <ModuleEmpty />;

  const { success, failed, tested, breakdown } = posture.data;
  if (tested === 0) {
    return <ModuleEmpty message={t('No validated expectation over the selected time range.')} />;
  }

  const globalScore = rate(success, failed);
  const prevention = breakdown.find(entry => entry.type === 'PREVENTION');
  const detection = breakdown.find(entry => entry.type === 'DETECTION');
  const preventionRate = prevention ? rate(prevention.success, prevention.failed) : null;
  const detectionRate = detection ? rate(detection.success, detection.failed) : null;

  let verdictKey = 'No conclusion can be drawn from the available results.';
  if (globalScore !== null) {
    if (globalScore >= 75) {
      verdictKey = 'The security posture over the period is strong: most adversarial behaviors were successfully handled by the defenses in place.';
    } else if (globalScore >= 50) {
      verdictKey = 'The security posture over the period is moderate: a meaningful share of adversarial behaviors bypassed the defenses and deserves attention.';
    } else {
      verdictKey = 'The security posture over the period is weak: a majority of adversarial behaviors were not stopped, remediation should be prioritized.';
    }
  }

  return (
    <Box>
      <Box sx={{
        display: 'flex',
        gap: 2,
        flexWrap: 'wrap',
        marginBottom: 3,
      }}
      >
        <Kpi
          label={t('Global score')}
          value={globalScore !== null ? `${globalScore}%` : '-'}
          accent={scoreColor(theme, globalScore)}
        />
        <Kpi
          label={t('Prevention rate')}
          value={preventionRate !== null ? `${preventionRate}%` : '-'}
          accent={scoreColor(theme, preventionRate)}
        />
        <Kpi
          label={t('Detection rate')}
          value={detectionRate !== null ? `${detectionRate}%` : '-'}
          accent={scoreColor(theme, detectionRate)}
        />
        <Kpi
          label={t('Expectations tested')}
          value={n(tested)}
        />
        {injectCount.status === 'success' && (
          <Kpi
            label={t('Injects executed')}
            value={n(injectCount.data ?? 0)}
          />
        )}
      </Box>
      {/* Verdict callout: quoted-analysis styling instead of a floating line. */}
      <Box sx={{
        padding: '12px 16px',
        borderLeft: `3px solid ${theme.palette.primary.main}`,
        borderRadius: '0 4px 4px 0',
        backgroundColor: alpha(theme.palette.primary.main, 0.05),
      }}
      >
        <Typography sx={{
          fontSize: 13,
          lineHeight: 1.7,
          color: 'text.secondary',
        }}
        >
          {t(verdictKey)}
        </Typography>
      </Box>
    </Box>
  );
};

export default ExecutiveSummaryModule;
