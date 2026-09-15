import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ComponentType, type ReactNode } from 'react';

// Modern empty state for the lessons learned screens: centered tinted icon
// above a muted message (same pattern as the simulation overview placeholder).
const LessonsPlaceholder = ({ icon: Icon, message, action, disablePadding }: {
  icon: ComponentType<{ sx?: object }>;
  message: string;
  action?: ReactNode;
  // Opt-in: drops this component's own 32px so the surface above it can own
  // the padding instead (PAPER-GAP-INVENTORY §5.6). Default keeps today's
  // rendering, so the call sites that don't pass it are untouched.
  disablePadding?: boolean;
}) => {
  const theme = useTheme();
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 1.5,
        padding: disablePadding ? 0 : 4,
        textAlign: 'center',
        height: '100%',
      }}
    >
      <Box
        sx={{
          width: 40,
          height: 40,
          borderRadius: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'primary.main',
          backgroundColor: alpha(theme.palette.primary.main, 0.1),
        }}
      >
        <Icon sx={{ fontSize: 22 }} />
      </Box>
      <Typography
        variant="body2"
        sx={{
          color: 'text.secondary',
          maxWidth: 420,
        }}
      >
        {message}
      </Typography>
      {action}
    </Box>
  );
};

export default LessonsPlaceholder;
