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

const ItemMarkings = ({ markingIds, definitions, variant, limit = 2 }: Props) => {
  const chipSx = {
    height: variant === 'list' ? 20 : 25,
    fontSize: 12,
    margin: 0,
    borderRadius: 1,
  };

  // An id with no matching definition is dropped rather than rendered raw: marking ids are stored
  // inline as text[] with no foreign key, so a deleted definition can leave a dangling id behind.
  // Highest order (most restrictive, e.g. TLP:RED) first - same convention as the assign-marking
  // picker, so the most sensitive grant is always the first thing a reader sees.
  const resolved = useMemo(
    () => (markingIds ?? [])
      .map(id => definitions[id])
      .filter((marking): marking is MarkingDefinitionOutput => !!marking)
      .sort((a, b) => b.marking_definition_order - a.marking_definition_order),
    [markingIds, definitions],
  );

  // Sliced directly rather than through the String helpers used by ItemTags: those accept
  // nullable inputs and so return nullable results, which `resolved` never is.
  const visible = resolved.slice(0, limit);
  const remaining = resolved.length - visible.length;
  const tooltipLabel = resolved.slice(limit).map(marking => marking.marking_definition_definition).join(', ');

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
        <Tooltip key={marking.marking_definition_id} title={`${marking.marking_definition_type}:${marking.marking_definition_definition}`}>
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
            label={truncate(marking.marking_definition_definition, variant === 'list' ? 15 : 20)}
          />
        </Tooltip>
      ))}
      {remaining > 0 && (
        <Tooltip title={tooltipLabel}>
          <Chip variant="outlined" sx={chipSx} label={`+${remaining}`} />
        </Tooltip>
      )}
    </div>
  );
};

export default ItemMarkings;
