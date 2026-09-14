import { Add } from '@mui/icons-material';
import { Button, Tooltip } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../i18n';
import { INLINE_CONTROL_HEIGHT } from '../Theme';

interface Props {
  onClick: () => void;
  style?: React.CSSProperties;
  label?: string;
  disabled?: boolean;
  /** Reason shown on hover while disabled. Raw i18n key, translated here. */
  disabledMessage?: string;
}

// Top-right inline creation button (OpenCTI-aligned): a contained primary
// button rendered in the list header row instead of a floating bottom-right
// Fab. The accessible name is the visible label (WCAG 2.5.3 Label in Name);
// e2e selectors target the stable data-testid instead.
const ButtonCreate: FunctionComponent<Props> = ({ onClick, style, label, disabled, disabledMessage }) => {
  const { t } = useFormatter();

  const button = (
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
        minHeight: INLINE_CONTROL_HEIGHT,
      }}
    >
      {label ?? t('Create')}
    </Button>
  );

  // A disabled MUI button fires no pointer event, so the tooltip needs an
  // enabled wrapper to hang on to.
  if (disabled && disabledMessage) {
    return (
      <Tooltip title={t(disabledMessage)}>
        <span style={{ display: 'inline-flex' }}>{button}</span>
      </Tooltip>
    );
  }

  return button;
};

export default ButtonCreate;
