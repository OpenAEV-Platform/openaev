import { useState } from 'react';

import { updateThreatArsenalCustomization } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type ConflictSide } from '../../../../components/common/update/ConflictChoice';
import {
  type PayloadCustomizationUpdateOutput,
  type PayloadFieldUpdateOutput,
  type ThreatArsenalAction,
} from '../../../../utils/api-types';
import { type CustomizableBlock } from './fieldUpdates';

interface ConflictBlock {
  block: CustomizableBlock;
  fields: PayloadFieldUpdateOutput[];
}

/** The fields of an update by what happens to them, and the choice made on each conflicting block. */
const useCustomizationUpdate = (actionId: string, update: PayloadCustomizationUpdateOutput) => {
  const [choices, setChoices] = useState<Record<string, ConflictSide>>({});
  const [applying, setApplying] = useState(false);

  const withStatus = (status: PayloadFieldUpdateOutput['field_status']) => update.update_fields.filter(field => field.field_status === status);
  const conflicting = withStatus('CONFLICT');
  const conflicts: ConflictBlock[] = [...new Set(conflicting.map(field => field.field_block))].map(block => ({
    block,
    fields: conflicting.filter(field => field.field_block === block),
  }));

  const choose = (block: CustomizableBlock, side: ConflictSide) => setChoices(previous => ({
    ...previous,
    [block]: side,
  }));

  const apply = (): Promise<ThreatArsenalAction> => {
    setApplying(true);
    return updateThreatArsenalCustomization(actionId, {
      update_payload_id: update.update_payload_id,
      update_choices: choices,
    })
      .then(({ data }: { data: ThreatArsenalAction }) => data)
      .finally(() => setApplying(false));
  };

  return {
    conflicts,
    news: withStatus('TAKE_APPLIED'),
    kept: withStatus('KEEP_MINE'),
    choices,
    choose,
    ready: conflicts.every(({ block }) => choices[block]),
    applying,
    apply,
  };
};

export default useCustomizationUpdate;
