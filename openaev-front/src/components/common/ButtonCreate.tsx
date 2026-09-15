import { Button as FdsButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Add } from '@mui/icons-material';
import { Button } from '@mui/material';
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

  // Opt-in for a header row only. Default keeps the MUI button this component
  // has always rendered — 31px measured in the app, a value MUI computes and a
  // product theme override adjusts. Moving all 51 call sites onto the library
  // button would make them GROW to 36px: a deliberate change that belongs to a
  // Button wave with its own boards, not to a container wave that is iso by
  // contract.
  //
  // `sm` renders the library button at 24px, the height of the library Paper
  // header row. A header action has to pass it: a taller control overflows the
  // row and eats into the 8px gap below.
  size?: 'sm';
}

// Top-right inline creation button (OpenCTI-aligned): a contained primary
// button rendered in the list header row instead of a floating bottom-right
// Fab. The accessible name is the visible label (WCAG 2.5.3 Label in Name);
// e2e selectors target the stable data-testid instead.
const ButtonCreate: FunctionComponent<Props> = ({ onClick, style, label, disabled, disabledMessage, size }) => {
  const { t } = useFormatter();
  const content = label ?? t('Create');

  const button = size === 'sm'
    ? (
        <FdsButton
          onClick={onClick}
          size="sm"
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
        </FdsButton>
      )
    : (
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
          {content}
        </Button>
      );

  // A disabled MUI button fires no pointer event, so the tooltip needs an
  // enabled wrapper to hang on to.
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
