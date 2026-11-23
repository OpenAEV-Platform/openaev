import { Chip as FdsChip } from '@filigran/design-system';
import { LensOutlined } from '@mui/icons-material';
import { Chip, type ChipProps } from '@mui/material';
import { type FunctionComponent } from 'react';

import { type MarkingDefinitionOutput } from '../../utils/api-types';
import { hexToRGB } from '../../utils/Colors';
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

  // A predefined marking (TLP, PAP) is drawn in the tone every XTM Platform module uses for it,
  // so a TLP:RED reads the same red in OpenAEV, OpenCTI, OpenCRQ and XTM One; the design system
  // draws it pastel while the user's "Pastel colours" preference is on (the OpenAEV default).
  if (tone) {
    const { onDelete, tabIndex, className, disabled, onClick: _onClick, size: _size, color: _color, ...spanProps } = chipProps;
    return (
      <FdsChip
        {...(spanProps as Record<string, unknown>)}
        className={className}
        disabled={disabled}
        label={shownLabel}
        severity={tone}
        // Markings stay fully uppercase (`TLP:GREEN`), never the chip's capitalised label.
        style={{ textTransform: 'none' }}
        // The library's handler takes no event; the Autocomplete's reads none it needs.
        onDelete={onDelete ? () => onDelete({} as never) : undefined}
        deleteTabIndex={tabIndex}
      />
    );
  }

  return (
    <Chip
      {...chipProps}
      variant="outlined"
      // The dot mirrors the "Color" column of the marking definitions admin list, so a
      // marking reads the same way wherever it is shown - definition list, group list, or
      // here on the entity it is attached to.
      icon={(
        <LensOutlined sx={{
          color: `${marking.marking_definition_color} !important`,
          fontSize: variant === 'list' ? 12 : 14,
        }}
        />
      )}
      sx={{
        'height': variant === 'list' ? 20 : 25,
        'fontSize': 12,
        'margin': 0,
        'borderRadius': 1,
        // The design system's MuiChip theme overrides force `text-transform: lowercase` (+
        // capitalize first letter) globally - see ThemeDark.ts/ThemeLight.ts. Markings must stay
        // fully uppercase (e.g. `TLP:GREEN`, not `Tlp:green`), so that is explicitly undone here
        // rather than in the shared theme, which drives every other chip in the app.
        'textTransform': 'none',
        '&::first-letter': { textTransform: 'none' },
        '& .MuiChip-label': { textTransform: 'none' },
        'color': marking.marking_definition_color,
        'borderColor': marking.marking_definition_color,
        'backgroundColor': marking.marking_definition_color ? hexToRGB(marking.marking_definition_color) : undefined,
      }}
      label={shownLabel}
    />
  );
};

export default MarkingChip;
