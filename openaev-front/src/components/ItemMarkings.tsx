import { Chip, Tooltip } from '@mui/material';
import { useMemo } from 'react';

import { type MarkingDefinitionOutput } from '../utils/api-types';
import { collapseToHighestPerType, markingLabel } from '../utils/markings';
import MarkingChip from './common/MarkingChip';

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
  // Sized like MarkingChip so the overflow counter lines up with the markings next to it.
  const remainingChipSx = {
    height: variant === 'list' ? 20 : 25,
    fontSize: 12,
    margin: 0,
    borderRadius: 1,
  };

  // Only the highest-order marking of each type is kept (e.g. TLP:CLEAR/GREEN/AMBER collapse to
  // TLP:AMBER) - see collapseToHighestPerType.
  const resolved = useMemo(
    () => collapseToHighestPerType(
      (markingIds ?? [])
        .map(id => definitions[id])
        .filter((marking): marking is MarkingDefinitionOutput => !!marking),
    ),
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
          <MarkingChip
            marking={marking}
            variant={variant}
            maxLabelLength={variant === 'list' ? 15 : 20}
          />
        </Tooltip>
      ))}
      {remaining > 0 && (
        <Tooltip title={<span style={{ textTransform: 'none' }}>{tooltipLabel}</span>}>
          <Chip variant="outlined" sx={remainingChipSx} label={`+${remaining}`} />
        </Tooltip>
      )}
    </div>
  );
};

export default ItemMarkings;
