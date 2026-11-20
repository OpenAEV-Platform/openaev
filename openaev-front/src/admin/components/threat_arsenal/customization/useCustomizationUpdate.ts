import { useState } from 'react';

import { updateThreatArsenalCustomization } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { type ConflictSide } from '../../../../components/common/update/ConflictChoice';
import {
  type PayloadCustomizationUpdateInput,
  type PayloadCustomizationUpdateOutput,
  type ThreatArsenalAction,
} from '../../../../utils/api-types';
import { changedByFiligran, changedByYou, type CustomizableField } from './fieldUpdates';

type Choices = NonNullable<PayloadCustomizationUpdateInput['update_choices']>;

const CHOICE_OF_SIDE: Record<ConflictSide, Choices[string]> = {
  theirs: 'FILIGRAN',
  mine: 'YOURS',
};

/** The fields of an update by what happens to them, and the choice made on each conflict. */
const useCustomizationUpdate = (actionId: string, update: PayloadCustomizationUpdateOutput) => {
  const [sides, setSides] = useState<Record<string, ConflictSide>>({});
  const [applying, setApplying] = useState(false);

  const conflicts = update.update_fields.filter(field => field.field_conflict);
  const news = update.update_fields.filter(field => !field.field_conflict && changedByFiligran(field));
  const kept = update.update_fields.filter(field => !field.field_conflict && !changedByFiligran(field) && changedByYou(field));

  const choose = (field: CustomizableField, side: ConflictSide) => setSides(previous => ({
    ...previous,
    [field]: side,
  }));

  const apply = (): Promise<ThreatArsenalAction> => {
    setApplying(true);
    return updateThreatArsenalCustomization(actionId, {
      update_payload_id: update.update_payload_id,
      update_choices: Object.fromEntries(Object.entries(sides).map(([field, side]) => [field, CHOICE_OF_SIDE[side]])),
    })
      .then(({ data }: { data: ThreatArsenalAction }) => data)
      .finally(() => setApplying(false));
  };

  return {
    conflicts,
    news,
    kept,
    sides,
    choose,
    ready: conflicts.every(field => sides[field.field_name]),
    applying,
    apply,
  };
};

export default useCustomizationUpdate;
