import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Chip } from '@mui/material';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { getSeverityAndColor, hexToRGB } from '../utils/Colors';
import { useFormatter } from './i18n';

const useStyles = makeStyles()(() => ({
  chip: {
    fontSize: 12,
    height: 25,
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 120,
  },
  chipInList: {
    fontSize: 12,
    height: 20,
    float: 'left',
    textTransform: 'uppercase',
    borderRadius: 4,
    width: 120,
  },
}));

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

// CVSS score rendered as a design-system severity chip (same geometry and
// tinted palette as ItemSeverity / ItemCriticality): "9.8 CRITICAL".
const CVSSBadge: FunctionComponent<CvssBadgeProps> = ({ score, variant }) => {
  const { t } = useFormatter();
  const { classes } = useStyles();

  if (score == null) {
    return <>-</>;
  }

  const { severity, color } = getSeverityAndColor(score);
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Chip
          classes={{ root: variant === 'inList' ? classes.chipInList : classes.chip }}
          sx={{
            backgroundColor: hexToRGB(color, 0.08),
            color,
          }}
          label={`${score.toFixed(1)} ${t(SEVERITY_LABELS[severity])}`}
        />
      </TooltipTrigger>
      <TooltipContent>{`${t('CVSS score')}: ${score.toFixed(1)}`}</TooltipContent>
    </Tooltip>
  );
};

export default CVSSBadge;
