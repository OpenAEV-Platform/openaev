import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';

interface Props {
  /** Uppercase overline title of the section. */
  title: ReactNode;
  /** Optional one-line guidance rendered under the title. */
  helper?: ReactNode;
  /** Optional control rendered at the top-right of the section header (e.g. a toggle, a button). */
  action?: ReactNode;
  /** Optional leading control rendered right after the title (e.g. a reset icon). */
  titleAdornment?: ReactNode;
  required?: boolean;
  error?: boolean;
  children: ReactNode;
}

/**
 * The single section primitive for the inject drawer (details, content and
 * logical chains). Renders a consistent uppercase overline label with optional
 * inline helper text and a right-aligned action slot, above the section body.
 * Replaces the mixed h2/h5 headings and negative-margin spacing that made the
 * old drawer feel cramped and inconsistent.
 */
const InjectFormSection: FunctionComponent<Props> = ({ title, helper, action, titleAdornment, required, error, children }) => {
  const theme = useTheme();
  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 1,
    }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
        minHeight: 32,
      }}
      >
        <Box sx={{ minWidth: 0 }}>
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.5,
          }}
          >
            <Typography sx={{
              fontFamily: '"Geologica", sans-serif',
              fontWeight: 600,
              fontSize: 11,
              letterSpacing: '0.12em',
              textTransform: 'uppercase',
              color: error ? 'error.main' : 'text.secondary',
            }}
            >
              {title}
              {required ? ' *' : ''}
            </Typography>
            {titleAdornment}
          </Box>
          {helper && (
            <Typography sx={{
              fontSize: 12,
              color: 'text.disabled',
              marginTop: 0.25,
            }}
            >
              {helper}
            </Typography>
          )}
        </Box>
        {action && (
          <Box sx={{
            marginLeft: 'auto',
            display: 'flex',
            alignItems: 'center',
            gap: 1,
          }}
          >
            {action}
          </Box>
        )}
      </Box>
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
        borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.05)}`,
        paddingTop: 1.5,
      }}
      >
        {children}
      </Box>
    </Box>
  );
};

export default InjectFormSection;
