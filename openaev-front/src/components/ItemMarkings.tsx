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
  const resolved = useMemo(
    () => (markingIds ?? [])
      .map(id => definitions[id])
      .filter((marking): marking is MarkingDefinitionOutput => !!marking)
      .sort((a, b) => a.marking_order - b.marking_order),
    [markingIds, definitions],
  );

  // Sliced directly rather than through the String helpers used by ItemTags: those accept
  // nullable inputs and so return nullable results, which `resolved` never is.
  const visible = resolved.slice(0, limit);
  const remaining = resolved.length - visible.length;
  const tooltipLabel = resolved.slice(limit).map(marking => marking.marking_name).join(', ');

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
        <Tooltip key={marking.marking_id} title={`${marking.marking_type}:${marking.marking_name}`}>
          <Chip
            variant="outlined"
            sx={{
              ...chipSx,
              color: marking.marking_color,
              borderColor: marking.marking_color,
              backgroundColor: marking.marking_color ? hexToRGB(marking.marking_color) : undefined,
            }}
            label={truncate(marking.marking_name, variant === 'list' ? 15 : 20)}
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
