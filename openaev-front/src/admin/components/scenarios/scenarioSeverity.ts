import { type Theme } from '@mui/material/styles';

export const SCENARIO_SEVERITIES = ['critical', 'high', 'medium', 'low'];

// Severity values are lowercase; their translation keys are capitalized.
export const severityLabelKey = (severity: string) => severity.charAt(0).toUpperCase() + severity.slice(1);

export const severityAccent = (theme: Theme, severity: string | undefined) => {
  switch (severity) {
    case 'low': return theme.palette.success.main;
    case 'medium': return theme.palette.warning.main;
    case 'high': return theme.palette.error.light;
    case 'critical': return theme.palette.error.main;
    default: return theme.palette.primary.main;
  }
};
