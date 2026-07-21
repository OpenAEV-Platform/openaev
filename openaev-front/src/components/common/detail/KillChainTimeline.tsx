import { Box, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { type KillChainPhase } from '../../../utils/api-types';

interface Props { phases: KillChainPhase[] }

/**
 * Renders the ordered kill chain as an actual attack-progression timeline:
 * connected disks (one per phase) flowing left to right, the final phase filled
 * to signal the adversary's objective. Falls back to "-" when empty.
 */
const KillChainTimeline: FunctionComponent<Props> = ({ phases }) => {
  const theme = useTheme();
  const accent = theme.palette.primary.main;

  // The source list can repeat a phase (several attack patterns share the same
  // kill chain phase); a kill chain reads as one disk per phase, so dedupe by
  // id (falling back to name) while preserving the incoming order.
  const seen = new Set<string>();
  const uniquePhases = phases.filter((phase) => {
    const key = phase.phase_id ?? phase.phase_name ?? '';
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });

  if (uniquePhases.length === 0) {
    return <span>-</span>;
  }

  const connector = `linear-gradient(90deg, ${alpha(accent, 0.45)}, ${alpha(accent, 0.45)})`;

  return (
    <Box sx={{
      display: 'flex',
      alignItems: 'flex-start',
      overflowX: 'auto',
      paddingBlock: 1,
    }}
    >
      {uniquePhases.map((phase, index) => {
        const isLast = index === uniquePhases.length - 1;
        return (
          <Box
            key={`${phase.phase_id}-${index}`}
            sx={{
              flex: '1 0 92px',
              minWidth: 92,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
            }}
          >
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              width: '100%',
            }}
            >
              <Box sx={{
                flex: 1,
                height: 2,
                borderRadius: 1,
                background: index === 0 ? 'transparent' : connector,
              }}
              />
              <Box sx={{
                width: 34,
                height: 34,
                flexShrink: 0,
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontFamily: '"Geologica", sans-serif',
                fontWeight: 600,
                fontSize: 13,
                color: isLast ? theme.palette.background.paper : accent,
                background: isLast
                  ? accent
                  : `radial-gradient(circle at 30% 30%, ${alpha(accent, 0.32)}, ${alpha(accent, 0.1)})`,
                border: `1.5px solid ${isLast ? accent : alpha(accent, 0.6)}`,
                boxShadow: `0 0 10px ${alpha(accent, isLast ? 0.5 : 0.25)}`,
              }}
              >
                {index + 1}
              </Box>
              <Box sx={{
                flex: 1,
                height: 2,
                borderRadius: 1,
                background: isLast ? 'transparent' : connector,
              }}
              />
            </Box>
            <Tooltip title={phase.phase_name}>
              <Typography sx={{
                marginTop: 1,
                maxWidth: 104,
                fontSize: 10,
                fontWeight: 600,
                lineHeight: 1.3,
                letterSpacing: '0.06em',
                textTransform: 'uppercase',
                textAlign: 'center',
                color: 'text.secondary',
                display: '-webkit-box',
                WebkitLineClamp: 2,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
              }}
              >
                {phase.phase_name}
              </Typography>
            </Tooltip>
          </Box>
        );
      })}
    </Box>
  );
};

export default KillChainTimeline;
