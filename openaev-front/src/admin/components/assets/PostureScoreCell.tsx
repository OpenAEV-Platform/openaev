import { Box, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';
import { postureBandColor } from './usePostureScores';

interface Props {
  /** Expectations met (SUCCESS) for this entity. */
  success: number;
  /** Expectations missed (FAILED) for this entity. */
  failed: number;
  loading?: boolean;
}

/**
 * Compact, list-sized rendition of the posture score: a mini ring gauge and
 * the banded score, matching the hero PostureScore orb semantics (HIGHER is
 * BETTER). Rows without validated expectations show a plain dash.
 */
const PostureScoreCell: FunctionComponent<Props> = ({ success, failed, loading = false }) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const total = success + failed;
  const score = total > 0 ? Math.round((success / total) * 100) : null;

  if (loading) {
    return <span>-</span>;
  }
  if (score === null) {
    return (
      <Tooltip title={t('No validations yet')}>
        <span>-</span>
      </Tooltip>
    );
  }

  const color = postureBandColor(theme, score);

  // Mini ring gauge geometry, scaled down to list row height.
  const ringSize = 18;
  const ringRadius = 7;
  const circumference = 2 * Math.PI * ringRadius;

  return (
    <Tooltip title={t('{met} of {total} validated expectations met', {
      met: success,
      total,
    })}
    >
      <Box sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 0.75,
      }}
      >
        <svg width={ringSize} height={ringSize} viewBox={`0 0 ${ringSize} ${ringSize}`} style={{ transform: 'rotate(-90deg)' }}>
          <circle
            cx={ringSize / 2}
            cy={ringSize / 2}
            r={ringRadius}
            fill={alpha(color, 0.1)}
            stroke={alpha(theme.palette.text.primary, 0.12)}
            strokeWidth={2}
          />
          <circle
            cx={ringSize / 2}
            cy={ringSize / 2}
            r={ringRadius}
            fill="none"
            stroke={color}
            strokeWidth={2}
            strokeLinecap="round"
            strokeDasharray={circumference}
            strokeDashoffset={circumference * (1 - score / 100)}
          />
        </svg>
        <Typography
          component="span"
          sx={{
            fontFamily: '"Geologica", sans-serif',
            fontSize: 13,
            fontWeight: 600,
            color,
          }}
        >
          {score}
        </Typography>
      </Box>
    </Tooltip>
  );
};

export default PostureScoreCell;
