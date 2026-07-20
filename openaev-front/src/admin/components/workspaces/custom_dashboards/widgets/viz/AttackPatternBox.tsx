import { Box, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type CSSProperties, type FunctionComponent, memo } from 'react';

import { getCoverageAccent } from './securityCoverageUtils';

interface AttackPatternBoxProps {
  attackPatternName: string;
  attackPatternExternalId: string;
  successRate: number | null;
  total?: number;
  style?: CSSProperties;
  onClick?: () => void;
}

/**
 * A single MITRE ATT&CK technique cell in the coverage matrix. Redesigned as a
 * compact card: a coverage-colored left accent, the technique id + name, and a
 * thin coverage meter with the success/total ratio. Untested techniques stay
 * muted so the tested ones pop.
 */
const AttackPatternBox: FunctionComponent<AttackPatternBoxProps> = ({
  attackPatternName,
  attackPatternExternalId,
  successRate = null,
  total,
  style = {},
  onClick,
}) => {
  const theme = useTheme();

  const tested = (total ?? 0) > 0 && successRate !== null;
  const accent = getCoverageAccent(tested ? successRate : null);
  const successCount = successRate ? Math.round(successRate * (total ?? 0)) : 0;
  const dark = theme.palette.mode === 'dark';

  const idleBg = dark ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.03)';
  const idleBorder = dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.06)';
  const cellBackground = tested
    ? `linear-gradient(90deg, ${alpha(accent, dark ? 0.22 : 0.16)}, ${alpha(accent, 0.04)})`
    : idleBg;
  const cellBorderColor = tested ? alpha(accent, 0.4) : idleBorder;

  return (
    <Tooltip title={`${attackPatternExternalId} - ${attackPatternName}`} placement="top">
      <Box
        onClick={onClick}
        className="noDrag"
        sx={{
          'position': 'relative',
          'cursor': onClick ? 'pointer' : 'default',
          'overflow': 'hidden',
          'borderRadius': 1,
          'padding': '5px 8px 5px 10px',
          'background': cellBackground,
          'border': `1px solid ${cellBorderColor}`,
          'transition': 'transform 0.15s, box-shadow 0.15s, border-color 0.15s',
          '&::before': {
            content: '""',
            position: 'absolute',
            left: 0,
            top: 0,
            bottom: 0,
            width: 3,
            background: tested ? accent : 'transparent',
          },
          '&:hover': onClick
            ? {
                transform: 'translateY(-1px)',
                borderColor: alpha(accent, 0.7),
                boxShadow: `0 2px 10px ${alpha(accent, 0.25)}`,
              }
            : undefined,
        }}
        style={style}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'baseline',
            justifyContent: 'space-between',
            gap: 6,
          }}
        >
          <Typography
            sx={{
              fontSize: 10,
              fontWeight: 700,
              fontFamily: '"Geologica", sans-serif',
              letterSpacing: '0.04em',
              color: tested ? accent : 'text.secondary',
            }}
          >
            {attackPatternExternalId}
          </Typography>
          {tested && (
            <Typography sx={{
              fontSize: 10.5,
              fontWeight: 600,
              color: 'text.primary',
            }}
            >
              {successCount}
              /
              {total}
            </Typography>
          )}
        </div>
        <Typography
          sx={{
            fontSize: 11,
            lineHeight: 1.25,
            color: tested ? 'text.primary' : 'text.secondary',
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
            wordBreak: 'break-word',
            minHeight: 28,
          }}
        >
          {attackPatternName}
        </Typography>
        <div
          style={{
            marginTop: 4,
            height: 3,
            borderRadius: 2,
            background: dark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)',
            overflow: 'hidden',
          }}
        >
          <div
            style={{
              width: `${tested ? Math.round((successRate ?? 0) * 100) : 0}%`,
              height: '100%',
              borderRadius: 2,
              background: accent,
              transition: 'width 0.6s cubic-bezier(0.22, 1, 0.36, 1)',
            }}
          />
        </div>
      </Box>
    </Tooltip>
  );
};

export default memo(AttackPatternBox);
