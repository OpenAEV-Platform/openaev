import { Box, Divider, Typography } from '@mui/material';
import { type ReactNode } from 'react';

export interface TooltipRow {
  label: string;
  value: ReactNode;
}

interface LogicNodeTooltipProps {
  /** Small uppercase kicker above the title (e.g. the injector/payload type or "Trigger"). */
  eyebrow?: string;
  title: string;
  description?: string;
  /** Structured label/value rows rendered below the description. */
  rows?: TooltipRow[];
  /** Short pills rendered at the bottom (e.g. produced output types). */
  chips?: string[];
  /** Accent color for the eyebrow and chips (defaults to the primary color). */
  accentColor?: string;
}

/**
 * Rich, structured tooltip body shared by the logic-flow action and event nodes. Rendered inside
 * the MUI Tooltip popper, it gives read-only viewers (autonomous runs) a legible breakdown of what
 * each box does even when the orchestrator leaves titles empty.
 */
const LogicNodeTooltip = ({
  eyebrow,
  title,
  description,
  rows = [],
  chips = [],
  accentColor,
}: LogicNodeTooltipProps): ReactNode => (
  <Box sx={{
    maxWidth: 320,
    padding: 0.5,
  }}
  >
    {eyebrow && (
      <Typography
        component="div"
        sx={{
          color: accentColor ?? 'primary.main',
          fontSize: '0.625rem',
          fontWeight: 700,
          letterSpacing: '0.06em',
          textTransform: 'uppercase',
          marginBottom: '2px',
        }}
      >
        {eyebrow}
      </Typography>
    )}
    <Typography
      component="div"
      sx={{
        fontSize: '0.8125rem',
        fontWeight: 700,
        lineHeight: 1.3,
        wordBreak: 'break-word',
      }}
    >
      {title}
    </Typography>
    {description && (
      <Typography
        component="div"
        sx={{
          marginTop: 0.5,
          fontSize: '0.75rem',
          color: 'text.secondary',
          lineHeight: 1.4,
          display: '-webkit-box',
          WebkitLineClamp: 5,
          WebkitBoxOrient: 'vertical',
          overflow: 'hidden',
        }}
      >
        {description}
      </Typography>
    )}
    {rows.length > 0 && (
      <>
        <Divider sx={{ marginBlock: 0.75 }} />
        <Box sx={{
          display: 'grid',
          gridTemplateColumns: 'auto 1fr',
          columnGap: 1,
          rowGap: 0.5,
        }}
        >
          {rows.map(row => (
            <Box key={row.label} sx={{ display: 'contents' }}>
              <Typography
                component="div"
                sx={{
                  fontSize: '0.625rem',
                  fontWeight: 700,
                  letterSpacing: '0.04em',
                  textTransform: 'uppercase',
                  color: 'text.secondary',
                  whiteSpace: 'nowrap',
                  paddingTop: '1px',
                }}
              >
                {row.label}
              </Typography>
              <Typography
                component="div"
                sx={{
                  fontSize: '0.75rem',
                  lineHeight: 1.4,
                  wordBreak: 'break-word',
                }}
              >
                {row.value}
              </Typography>
            </Box>
          ))}
        </Box>
      </>
    )}
    {chips.length > 0 && (
      <Box sx={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: 0.5,
        marginTop: 0.75,
      }}
      >
        {chips.map(chip => (
          <Box
            key={chip}
            component="span"
            sx={{
              fontSize: '0.625rem',
              fontWeight: 600,
              lineHeight: 1.6,
              paddingInline: 0.75,
              borderRadius: 0.75,
              color: accentColor ?? 'primary.main',
              border: '1px solid',
              borderColor: 'divider',
              backgroundColor: 'action.hover',
            }}
          >
            {chip}
          </Box>
        ))}
      </Box>
    )}
  </Box>
);

export default LogicNodeTooltip;
