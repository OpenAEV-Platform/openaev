import { Add } from '@mui/icons-material';
import { Button } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../i18n';

interface Props {
  onClick: () => void;
  style?: React.CSSProperties;
  label?: string;
  disabled?: boolean;
}

// Top-right inline creation button (OpenCTI-aligned): a contained primary
// button rendered in the list header row instead of a floating bottom-right
// Fab. The accessible name is the visible label (WCAG 2.5.3 Label in Name);
// e2e selectors target the stable data-testid instead.
const ButtonCreate: FunctionComponent<Props> = ({ onClick, style, label, disabled }) => {
  const { t } = useFormatter();

  return (
    <Button
      onClick={onClick}
      color="primary"
      variant="contained"
      size="small"
      data-testid="button-create"
      startIcon={<Add />}
      style={style}
      disabled={disabled}
      sx={{
        whiteSpace: 'nowrap',
        flexShrink: 0,
      }}
    >
      {label ?? t('Create')}
    </Button>
  );
};

export default ButtonCreate;
