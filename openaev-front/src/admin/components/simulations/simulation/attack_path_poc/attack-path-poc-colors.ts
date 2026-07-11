import { type Theme } from '@mui/material/styles';

// The backend colours an endpoint GREEN (all prevented), RED (none prevented) or ORANGE (mixed).
// Map those onto the theme's semantic palette so the graph respects light/dark mode.
const attackPathStatusColor = (theme: Theme, status?: string): string => {
  switch (status) {
    case 'GREEN':
      return theme.palette.success.main;
    case 'ORANGE':
      return theme.palette.warning.main;
    case 'RED':
      return theme.palette.error.main;
    default:
      return theme.palette.text.disabled;
  }
};

export default attackPathStatusColor;
