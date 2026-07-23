import { InboxOutlined } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ComponentType, type FunctionComponent, type ReactNode } from 'react';

interface EmptyProps {
  message: ReactNode;
  /** Optional secondary hint rendered under the message. */
  hint?: ReactNode;
  /** Optional icon (defaults to an inbox); pass the entity's icon for context. */
  icon?: ComponentType<{ sx?: object }>;
  id?: string;
}

/**
 * The ONE empty-state used by every list / section in the app: a softly
 * tinted icon medallion, the message in secondary text and an optional hint.
 * Kept intentionally neutral (no borders, no CTA) so it reads well inside
 * papers, lists and drawers alike.
 */
const Empty: FunctionComponent<EmptyProps> = ({ message, hint, icon: Icon = InboxOutlined, id = '' }) => {
  const theme = useTheme();
  return (
    <Box
      id={id}
      sx={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        textAlign: 'center',
        gap: 1,
        width: '100%',
        height: '100%',
        paddingBlock: 5,
        paddingInline: 2,
      }}
    >
      <Box
        aria-hidden
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 44,
          height: 44,
          borderRadius: '50%',
          backgroundColor: alpha(theme.palette.text.primary, 0.04),
          border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
        }}
      >
        <Icon sx={{
          fontSize: 22,
          color: 'text.disabled',
        }}
        />
      </Box>
      <Typography sx={{
        fontSize: 13,
        color: 'text.secondary',
      }}
      >
        {message}
      </Typography>
      {hint && (
        <Typography sx={{
          fontSize: 12,
          color: 'text.disabled',
          marginTop: -0.5,
        }}
        >
          {hint}
        </Typography>
      )}
    </Box>
  );
};

export default Empty;
