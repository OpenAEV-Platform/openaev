import { Chip, type ChipSeverity, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

// A tag stays short: past its width it truncates, and the hover always says it in full.
const HintChip: FunctionComponent<{
  label: string;
  hint: string;
  severity?: ChipSeverity;
  maxWidth: number;
}> = ({ label, hint, severity, maxWidth }) => (
  <Tooltip>
    <TooltipTrigger asChild>
      <span
        className="inline-flex"
        style={{
          maxWidth,
          minWidth: 0,
        }}
      >
        <Chip label={label} severity={severity} style={{ maxWidth: '100%' }} />
      </span>
    </TooltipTrigger>
    <TooltipContent>{hint}</TooltipContent>
  </Tooltip>
);

export default HintChip;
