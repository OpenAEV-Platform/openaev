import { Chip, type ChipSeverity, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { getSeverityAndColor, type SeverityAndColor } from '../utils/Colors';
import { useFormatter } from './i18n';

// Severity enum -> translatable label (the chip style uppercases it visually).
const SEVERITY_LABELS: Record<string, string> = {
  CRITICAL: 'Critical',
  HIGH: 'High',
  MEDIUM: 'Medium',
  LOW: 'Low',
  NONE: 'None',
};

interface CvssBadgeProps {
  score: number | null | undefined;
  variant?: 'inList';
}

const CVSS_SEVERITY: Record<SeverityAndColor['severity'], ChipSeverity> = {
  CRITICAL: 'critical',
  HIGH: 'high',
  MEDIUM: 'info',
  LOW: 'low',
  NONE: 'neutral',
};

// CVSS score rendered as a design-system severity chip (same geometry and
// tinted palette as ItemSeverity / ItemCriticality): "9.8 CRITICAL".
const CVSSBadge: FunctionComponent<CvssBadgeProps> = ({ score }) => {
  const { t } = useFormatter();

  if (score == null) {
    return <>-</>;
  }

  const { severity } = getSeverityAndColor(score);
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span className="inline-flex">
          <span className="inline-flex">
            <Chip
              label={`${score.toFixed(1)} ${t(SEVERITY_LABELS[severity])}`}
              severity={CVSS_SEVERITY[severity]}
            />
          </span>
        </span>
      </TooltipTrigger>
      <TooltipContent>{`${t('CVSS score')}: ${score.toFixed(1)}`}</TooltipContent>
    </Tooltip>
  );
};

export default CVSSBadge;
