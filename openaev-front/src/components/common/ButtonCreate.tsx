import { Button, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Add } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../i18n';

interface Props {
  onClick: () => void;
  style?: React.CSSProperties;
  label?: string;
  disabled?: boolean;
  /** Reason shown on hover while disabled. Raw i18n key, translated here. */
  disabledMessage?: string;

  // A list header keeps the default 36px button. A Paper header row passes `sm`:
  // 24px, the height of that row; a taller control overflows it and eats into
  // the 8px gap below.
  size?: 'sm';
}

// Top-right inline creation button (OpenCTI-aligned): a contained primary
// button rendered in the list header row instead of a floating bottom-right
// Fab. The accessible name is the visible label (WCAG 2.5.3 Label in Name);
// e2e selectors target the stable data-testid instead.
const ButtonCreate: FunctionComponent<Props> = ({ onClick, style, label, disabled, disabledMessage, size }) => {
  const { t } = useFormatter();
  const content = label ?? t('Create');

  const button = (
    <Button
      type="button"
      onClick={onClick}
      size={size}
      data-testid="button-create"
      startIcon={<Add fontSize="small" />}
      style={{
        whiteSpace: 'nowrap',
        flexShrink: 0,
        ...style,
      }}
      disabled={disabled}
    >
      {content}
    </Button>
  );

  // A disabled button fires no pointer event, so the tooltip needs an enabled
  // wrapper to hang on to.
  if (disabled && disabledMessage) {
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <span style={{ display: 'inline-flex' }}>{button}</span>
        </TooltipTrigger>
        <TooltipContent>{t(disabledMessage)}</TooltipContent>
      </Tooltip>
    );
  }

  return button;
};

export default ButtonCreate;
