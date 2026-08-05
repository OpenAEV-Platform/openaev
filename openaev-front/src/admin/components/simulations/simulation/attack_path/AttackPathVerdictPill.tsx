import { Box } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import attackPathStatusColor from './attack-path-colors';

interface Props {
  /** Translated, display-ready label (status is never conveyed by colour alone). */
  label: string;
  /** Backend prevention/detection verdict: GREEN | ORANGE | RED (anything else reads neutral). */
  status?: string;
}

// Alpha-tinted verdict pill in the shared StatusPill visual language, but coloured from the
// attack-path GREEN/ORANGE/RED verdict scale (statusUtils only knows execution statuses like
// SUCCESS/FAILED, so the shared pill cannot be reused directly).
const AttackPathVerdictPill: FunctionComponent<Props> = ({ label, status }) => {
  const theme = useTheme();
  const color = attackPathStatusColor(theme, status);
  return (
    <Box
      component="span"
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        paddingInline: 1,
        paddingBlock: 0.25,
        borderRadius: 1,
        backgroundColor: alpha(color, 0.08),
        color,
        fontSize: 11,
        fontWeight: 700,
        letterSpacing: '0.04em',
        textTransform: 'uppercase',
        whiteSpace: 'nowrap',
        flexShrink: 0,
      }}
    >
      {label}
    </Box>
  );
};

export default AttackPathVerdictPill;
