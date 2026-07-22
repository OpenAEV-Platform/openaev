import { Box, Typography } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';

import { SECTION_LABEL_SX } from '../../../components/common/detail/detailStyles';

interface Props {
  title: string;
  // Optional item count rendered as a subtle badge next to the title.
  count?: number;
  // Right-aligned action slot (add / preview button). Kept consistent across
  // every configuration tab so the drawer reads as one coherent surface.
  action?: ReactNode;
  children: ReactNode;
}

/**
 * Shared header shell for the simulation / scenario configuration tabs
 * (Teams, Variables, Media pressure, Challenges). Every tab gets the exact same
 * uppercase section label, optional count badge, and a single right-aligned
 * action button - replacing the previous mix of floated icon buttons, oval
 * "fab" pills and inline titles.
 */
const ConfigurationSection: FunctionComponent<Props> = ({ title, count, action, children }) => (
  <div>
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 1,
        minHeight: 32,
        marginBottom: 1.5,
      }}
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
      }}
      >
        <Typography sx={{
          ...SECTION_LABEL_SX,
          marginBottom: 0,
        }}
        >
          {title}
        </Typography>
        {count != null && (
          <Box
            component="span"
            sx={theme => ({
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              minWidth: 20,
              height: 18,
              paddingInline: 0.75,
              borderRadius: 0.5,
              fontSize: 11,
              fontWeight: 600,
              color: theme.palette.text.secondary,
              backgroundColor: theme.palette.action.hover,
            })}
          >
            {count}
          </Box>
        )}
      </Box>
      {action}
    </Box>
    {children}
  </div>
);

export default ConfigurationSection;
