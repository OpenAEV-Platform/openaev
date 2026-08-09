import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ReactNode } from 'react';

interface Props {
  icon: ReactNode;
  title: string;
  body: string;
}

/** Shared how-to step row used on phishing email / landing overview panels. */
const PhishingUsageStep = ({ icon, title, body }: Props) => {
  const theme = useTheme();
  return (
    <Box sx={{
      display: 'flex',
      gap: 1.5,
      alignItems: 'flex-start',
    }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: 36,
        height: 36,
        borderRadius: 1,
        flexShrink: 0,
        color: 'primary.main',
        backgroundColor: alpha(theme.palette.primary.main, 0.12),
        border: `1px solid ${alpha(theme.palette.primary.main, 0.28)}`,
      }}
      >
        {icon}
      </Box>
      <Box sx={{
        minWidth: 0,
        pt: 0.25,
      }}
      >
        <Typography
          variant="subtitle2"
          sx={{
            fontWeight: 600,
            lineHeight: 1.3,
            mb: 0.35,
          }}
        >
          {title}
        </Typography>
        <Typography
          variant="body2"
          sx={{
            color: 'text.secondary',
            lineHeight: 1.55,
          }}
        >
          {body}
        </Typography>
      </Box>
    </Box>
  );
};

export default PhishingUsageStep;
