import { LensOutlined } from '@mui/icons-material';
import { Chip, type ChipProps } from '@mui/material';
import { type FunctionComponent } from 'react';

import { type MarkingDefinitionOutput } from '../../utils/api-types';
import { hexToRGB } from '../../utils/Colors';
import { markingLabel } from '../../utils/markings';
import { truncate } from '../../utils/String';

interface Props extends Omit<ChipProps, 'variant' | 'label' | 'icon' | 'sx'> {
  marking: MarkingDefinitionOutput;
  variant?: 'list';
  /** Truncates the label beyond this many characters; the full label is shown when omitted. */
  maxLabelLength?: number;
}

// Single rendering of a marking as a chip, shared by read-only displays (ItemMarkings) and pickers
// (MarkingField), so a marking looks the same whether it is shown on an entity or being assigned.
// Any other Chip prop (e.g. the `onDelete`/`className`/`tabIndex` an Autocomplete's getTagProps
// hands out) is passed through untouched.
const MarkingChip: FunctionComponent<Props> = ({ marking, variant, maxLabelLength, ...chipProps }) => {
  const label = markingLabel(marking);
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
      label={maxLabelLength ? truncate(label, maxLabelLength) : label}
    />
  );
};

export default MarkingChip;
