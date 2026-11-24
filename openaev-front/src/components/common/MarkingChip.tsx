import { Chip as FdsChip } from '@filigran/design-system';
import { type ChipProps } from '@mui/material';
import { type FunctionComponent } from 'react';

import { type MarkingDefinitionOutput } from '../../utils/api-types';
import { markingLabel, markingTone } from '../../utils/markings';
import { truncate } from '../../utils/String';

interface Props extends Omit<ChipProps, 'variant' | 'label' | 'icon' | 'sx'> {
  marking: MarkingDefinitionOutput;
  variant?: 'list';
  /** Truncates the label beyond this many characters; the full label is shown when omitted. */
  maxLabelLength?: number;
}

// Rendering of a marking inside the MUI Autocomplete of MarkingField. Any other Chip prop (e.g. the `onDelete`/`className`/`tabIndex` an Autocomplete's getTagProps
// hands out) is passed through untouched.
const MarkingChip: FunctionComponent<Props> = ({ marking, variant, maxLabelLength, ...chipProps }) => {
  const label = markingLabel(marking);
  const shownLabel = (maxLabelLength ? truncate(label, maxLabelLength) : label) ?? label;
  const tone = markingTone(marking);

  // Every marking is the design-system Chip, in the single 1.13 style (fill and 30% hairline).
  // A predefined marking (TLP, PAP) is drawn in the tone every XTM Platform module uses for it,
  // so a TLP:RED reads the same red in OpenAEV, OpenCTI, OpenCRQ and XTM One; a custom marking
  // is drawn in its own definition colour through the Chip's data colour.
  const { onDelete, tabIndex, className, disabled, onClick: _onClick, size: _size, color: _color, ...spanProps } = chipProps;
  const colourProps = tone
    ? { severity: tone }
    : { color: marking.marking_definition_color || undefined };
  return (
    <FdsChip
      {...(spanProps as Record<string, unknown>)}
      {...colourProps}
      className={className}
      disabled={disabled}
      label={shownLabel}
      data-variant={variant}
      // Markings stay fully uppercase (`TLP:GREEN`), never the chip's capitalised label.
      style={{ textTransform: 'none' }}
      // The library's handler takes no event; the Autocomplete's reads none it needs.
      onDelete={onDelete ? () => onDelete({} as never) : undefined}
      deleteTabIndex={tabIndex}
    />
  );
};

export default MarkingChip;
