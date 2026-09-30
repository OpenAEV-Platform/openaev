import { LensOutlined } from '@mui/icons-material';
import { Chip, Tooltip } from '@mui/material';
import { useMemo } from 'react';

import { type MarkingDefinitionOutput } from '../utils/api-types';
import { hexToRGB } from '../utils/Colors';
import { truncate } from '../utils/String';

interface Props {
  /** Marking definition ids carried by the entity, as returned in `asset_markings`. */
  markingIds?: string[];
  /**
   * Resolved definitions, keyed by id. Passed in rather than fetched here so a 50-row list issues
   * one request for the whole page instead of one per row - see `useMarkingDefinitions`.
   */
  definitions: Record<string, MarkingDefinitionOutput>;
  variant?: 'list';
  limit?: number;
}

// Seeded definitions already store the display value with its type baked in, e.g.
// `marking_definition_definition: "TLP:RED"` for a `marking_definition_type: "TLP"`. Prefixing
// unconditionally would render `TLP:TLP:RED`, so the type is only prepended when the definition
// does not already carry it. Always rendered uppercase (e.g. `TLP:GREEN`), regardless of how the
// type/definition were cased when the marking was created.
// Exported so other marking displays (e.g. MarkingField's picker) render the exact same label
// instead of re-deriving their own "type:definition" formatting.
// eslint-disable-next-line react-refresh/only-export-components
export const markingLabel = (marking: MarkingDefinitionOutput) => {
  const type = marking.marking_definition_type.toUpperCase();
  const definition = marking.marking_definition_definition.toUpperCase();
  return definition.startsWith(`${type}:`) ? definition : `${type}:${definition}`;
};

const ItemMarkings = ({ markingIds, definitions, variant, limit = 2 }: Props) => {
  // The design system's MuiChip/MuiTooltip theme overrides force `text-transform: lowercase` (+
  // capitalize first letter) globally - see ThemeDark.ts/ThemeLight.ts. Markings must stay fully
  // uppercase (e.g. `TLP:GREEN`, not `Tlp:green`), so that is explicitly undone here rather than
  // in the shared theme, which drives every other chip/tooltip in the app.
  const noCaseTransformSx = {
    'textTransform': 'none',
    '&::first-letter': { textTransform: 'none' },
    '& .MuiChip-label': { textTransform: 'none' },
  };
  const chipSx = {
    height: variant === 'list' ? 20 : 25,
    fontSize: 12,
    margin: 0,
    borderRadius: 1,
    ...noCaseTransformSx,
  };

  // Only the highest-order marking of each type is kept (e.g. TLP:CLEAR/GREEN/AMBER collapse to
  // TLP:AMBER)
  const resolved = useMemo(
    () => {
      const sorted = (markingIds ?? [])
        .map(id => definitions[id])
        .filter((marking): marking is MarkingDefinitionOutput => !!marking)
        .sort((a, b) => b.marking_definition_order - a.marking_definition_order);
      const highestByType = new Map<string, MarkingDefinitionOutput>();
      sorted.forEach((marking) => {
        if (!highestByType.has(marking.marking_definition_type)) {
          highestByType.set(marking.marking_definition_type, marking);
        }
      });
      return Array.from(highestByType.values());
    },
    [markingIds, definitions],
  );

  // Sliced directly rather than through the String helpers used by ItemTags: those accept
  // nullable inputs and so return nullable results, which `resolved` never is.
  const visible = resolved.slice(0, limit);
  const remaining = resolved.length - visible.length;
  const tooltipLabel = resolved.slice(limit).map(marking => markingLabel(marking)).join(', ');

  if (resolved.length === 0) {
    return <span>-</span>;
  }

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      flexWrap: 'wrap',
      gap: 6,
    }}
    >
      {visible.map((marking: MarkingDefinitionOutput) => (
        <Tooltip key={marking.marking_definition_id} title={<span style={{ textTransform: 'none' }}>{markingLabel(marking)}</span>}>
          <Chip
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
              ...chipSx,
              color: marking.marking_definition_color,
              borderColor: marking.marking_definition_color,
              backgroundColor: marking.marking_definition_color ? hexToRGB(marking.marking_definition_color) : undefined,
            }}
            label={truncate(markingLabel(marking), variant === 'list' ? 15 : 20)}
          />
        </Tooltip>
      ))}
      {remaining > 0 && (
        <Tooltip title={<span style={{ textTransform: 'none' }}>{tooltipLabel}</span>}>
          <Chip variant="outlined" sx={chipSx} label={`+${remaining}`} />
        </Tooltip>
      )}
    </div>
  );
};

export default ItemMarkings;
