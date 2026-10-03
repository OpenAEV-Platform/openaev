import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';

import { type AttackPattern, type AttackPatternSimple } from '../utils/api-types';

type Props = { attackPattern: AttackPattern | AttackPatternSimple };

const AttackPatternChip = (props: Props) => {
  const attackPattern = props.attackPattern;
  return (
    <Tooltip key={attackPattern.attack_pattern_id}>
      <TooltipTrigger asChild>
        <Chip
          label={`[${attackPattern.attack_pattern_external_id}] ${attackPattern.attack_pattern_name}`}
          severity="info"
        />
      </TooltipTrigger>
      <TooltipContent>{`[${attackPattern.attack_pattern_external_id}] ${attackPattern.attack_pattern_name}`}</TooltipContent>
    </Tooltip>
  );
};

export default AttackPatternChip;
