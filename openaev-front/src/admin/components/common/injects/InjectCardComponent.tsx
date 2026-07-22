import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import type { FunctionComponent } from 'react';

interface Props {
  avatar: React.ReactNode;
  title: string | undefined;
  disabled?: boolean;
  action: React.ReactNode;
  content: string;
}

/**
 * Compact identity header for the inject drawer: framed contract icon, the
 * inject title as the headline, the TTP / injector context as an overline, and
 * the platform icons on the right. Replaces the old tall MUI Card that ate a
 * full section of vertical space at the top of every tab.
 */
const InjectCardComponent: FunctionComponent<Props> = ({
  avatar,
  title,
  action,
  content,
  disabled,
}) => {
  const theme = useTheme();
  const accent = theme.palette.primary.main;

  return (
    <Box sx={{
      display: 'flex',
      alignItems: 'center',
      gap: 1.5,
      padding: 1.5,
      borderRadius: 1,
      border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
      background: `linear-gradient(135deg, ${alpha(accent, 0.06)}, transparent 60%)`,
    }}
    >
      <Box sx={{
        width: 40,
        height: 40,
        borderRadius: 1,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexShrink: 0,
        backgroundColor: alpha(theme.palette.text.primary, 0.04),
        border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
      }}
      >
        {avatar}
      </Box>
      <Box sx={{
        minWidth: 0,
        flex: 1,
      }}
      >
        {title && (
          <Typography sx={{
            fontFamily: '"Geologica", sans-serif',
            fontWeight: 600,
            fontSize: 11,
            letterSpacing: '0.08em',
            textTransform: 'uppercase',
            color: 'primary.main',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
          >
            {title}
          </Typography>
        )}
        <Typography sx={{
          fontSize: 15,
          fontWeight: 600,
          lineHeight: 1.3,
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          ...(disabled && {
            color: 'text.disabled',
            fontStyle: 'italic',
          }),
        }}
        >
          {content}
        </Typography>
      </Box>
      {action && (
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          flexShrink: 0,
        }}
        >
          {action}
        </Box>
      )}
    </Box>
  );
};

export default InjectCardComponent;
