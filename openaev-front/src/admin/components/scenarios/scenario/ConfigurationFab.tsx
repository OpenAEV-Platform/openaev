import { Box } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';

// Promotes an inline "+" trigger (the IconButton rendered by UpdateTeams /
// CreateVariable / ...) into the platform's standard top-right creation button
// placement (OpenCTI-aligned), without touching those shared components. The
// wrapped trigger keeps its own dialog; only its presentation is upgraded to
// a contained primary action pinned to the top right of the tab content.
const ConfigurationFab: FunctionComponent<{ children: ReactNode }> = ({ children }) => (
  <Box
    data-testid="configuration-fab"
    sx={theme => ({
      'display': 'flex',
      'justifyContent': 'flex-end',
      'marginBottom': theme.spacing(1),
      '& .MuiIconButton-root': {
        'color': theme.palette.primary.contrastText,
        'backgroundColor': theme.palette.primary.main,
        'borderRadius': theme.shape.borderRadius,
        'padding': theme.spacing(0.5, 1.5),
        '&:hover': { backgroundColor: theme.palette.primary.dark },
      },
      '& .MuiSvgIcon-root': { fontSize: 20 },
    })}
  >
    {children}
  </Box>
);

export default ConfigurationFab;
