import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';

interface EmptyPlaceholderProps {
  /** Lucide/MUI icon element — its size is normalized by the tinted chip, so pass it unsized. */
  icon: ReactNode;
  title: string;
  message?: ReactNode;
  /** Optional call-to-action (a Button) rendered below the message. */
  action?: ReactNode;
  /**
   * Draw the dashed frame + faint paper background so the placeholder reads as a standalone card.
   * Turn OFF when the placeholder already sits inside a framed container (e.g. an outlined Paper),
   * to avoid a double border. Default true.
   */
  bordered?: boolean;
}

/**
 * Shared design-system zero-state: a centered, tinted rounded icon chip over a short title and an
 * optional explanatory line and CTA, framed by a dashed border on a faint paper wash. Fills its
 * parent (height/width 100%), so drop it straight into a tab body or a relative container. This is
 * the single source of truth for empty states — match it rather than hand-rolling another one (see
 * TimelineEmptyState / ThreatArsenalEmptyState, which established the language).
 */
const EmptyPlaceholder: FunctionComponent<EmptyPlaceholderProps> = ({
  icon,
  title,
  message,
  action,
  bordered = true,
}) => {
  const theme = useTheme();
  const accent = theme.palette.primary.main;

  return (
    <Box
      sx={{
        height: '100%',
        width: '100%',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        textAlign: 'center',
        gap: 2,
        padding: theme.spacing(4),
        boxSizing: 'border-box',
        ...(bordered
          ? {
              borderRadius: 1,
              border: `1px dashed ${theme.palette.divider}`,
              backgroundColor: alpha(theme.palette.background.paper, 0.4),
            }
          : {}),
      }}
    >
      <Box
        sx={{
          'width': 64,
          'height': 64,
          'display': 'grid',
          'placeItems': 'center',
          'borderRadius': 2,
          'flexShrink': 0,
          'color': accent,
          'backgroundColor': alpha(accent, 0.08),
          'border': `1px solid ${alpha(accent, 0.25)}`,
          '& svg': { fontSize: 30 },
        }}
      >
        {icon}
      </Box>
      <Typography variant="h6" sx={{ fontWeight: 600 }}>
        {title}
      </Typography>
      {message && (
        <Typography
          variant="body2"
          sx={{
            color: 'text.secondary',
            maxWidth: 460,
          }}
        >
          {message}
        </Typography>
      )}
      {action}
    </Box>
  );
};

export default EmptyPlaceholder;
