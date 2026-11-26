import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { useMemo } from 'react';

import { type MarkingDefinitionHelper } from '../actions/marking_definitions/marking-definition-helper';
import { useHelper } from '../store';
import { type MarkingDefinitionOutput } from '../utils/api-types';
import { collapseToHighestPerType, markingLabel } from '../utils/markings';

interface Props {
  markingIds?: string[];
  limit?: number;
}

const ItemMarkings = ({ markingIds, limit = 2 }: Props) => {
  const { definitions } = useHelper((helper: MarkingDefinitionHelper) => ({ definitions: helper.getMarkingDefinitionsMap() }));

  const resolved = useMemo(() => collapseToHighestPerType(
    (markingIds ?? [])
      .map(id => definitions[id])
      .filter((marking): marking is MarkingDefinitionOutput => !!marking),
  ), [markingIds, definitions]);

  if (resolved.length === 0) {
    return <span>-</span>;
  }

  const visible = resolved.slice(0, limit);
  const hidden = resolved.slice(limit);

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      flexWrap: 'nowrap',
      overflow: 'hidden',
      gap: 6,
    }}
    >
      {visible.map(marking => (
        <Tooltip key={marking.marking_definition_id}>
          <TooltipTrigger asChild>
            <Chip label={markingLabel(marking)} color={marking.marking_definition_color} style={{ maxWidth: '100%' }} />
          </TooltipTrigger>
          <TooltipContent>{markingLabel(marking)}</TooltipContent>
        </Tooltip>
      ))}
      {hidden.length > 0 && (
        <Tooltip>
          <TooltipTrigger asChild>
            <Chip label={`+${hidden.length}`} />
          </TooltipTrigger>
          <TooltipContent>{hidden.map(markingLabel).join(', ')}</TooltipContent>
        </Tooltip>
      )}
    </div>
  );
};

export default ItemMarkings;
