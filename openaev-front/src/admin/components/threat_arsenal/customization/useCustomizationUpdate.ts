import { useState } from 'react';

import { updateThreatArsenalCustomization } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type ConflictSide } from '../../../../components/common/update/ConflictChoice';
import {
  type PayloadCustomizationUpdateInput,
  type PayloadCustomizationUpdateOutput,
  type PayloadFieldUpdateOutput,
  type ThreatArsenalAction,
} from '../../../../utils/api-types';
import { type CustomizableBlock } from './fieldUpdates';

type Choices = NonNullable<PayloadCustomizationUpdateInput['update_choices']>;

const CHOICE_OF_SIDE: Record<ConflictSide, Choices[string]> = {
  theirs: 'TAKE_APPLIED',
  mine: 'KEEP_MINE',
};

interface ConflictBlock {
  block: CustomizableBlock;
  fields: PayloadFieldUpdateOutput[];
}

/** The fields of an update by what happens to them, and the choice made on each conflicting block. */
const useCustomizationUpdate = (actionId: string, update: PayloadCustomizationUpdateOutput) => {
  const [sides, setSides] = useState<Record<string, ConflictSide>>({});
  const [applying, setApplying] = useState(false);

  const withStatus = (status: PayloadFieldUpdateOutput['field_status']) => update.update_fields.filter(field => field.field_status === status);
  const conflicting = withStatus('CONFLICT');
  const conflicts: ConflictBlock[] = [...new Set(conflicting.map(field => field.field_block))].map(block => ({
    block,
    fields: conflicting.filter(field => field.field_block === block),
  }));

  const choose = (block: CustomizableBlock, side: ConflictSide) => setSides(previous => ({
    ...previous,
    [block]: side,
  }));

  const apply = (): Promise<ThreatArsenalAction> => {
    setApplying(true);
    return updateThreatArsenalCustomization(actionId, {
      update_payload_id: update.update_payload_id,
      update_choices: Object.fromEntries(Object.entries(sides).map(([block, side]) => [block, CHOICE_OF_SIDE[side]])),
    })
      .then(({ data }: { data: ThreatArsenalAction }) => data)
      .finally(() => setApplying(false));
  };

  return {
    conflicts,
    news: withStatus('TAKE_APPLIED'),
    kept: withStatus('KEEP_MINE'),
    sides,
    choose,
    ready: conflicts.every(({ block }) => sides[block]),
    applying,
    apply,
  };
};

export default useCustomizationUpdate;
