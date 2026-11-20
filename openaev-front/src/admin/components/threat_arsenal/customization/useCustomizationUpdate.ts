import { useState } from 'react';

import { updateThreatArsenalCustomization } from '../../../../actions/threat_arsenals/threatArsenal-customization-actions';
import {
  type PayloadCustomizationUpdateInput,
  type PayloadCustomizationUpdateOutput,
  type ThreatArsenalAction,
} from '../../../../utils/api-types';
import { changedByFiligran, changedByYou, type CustomizableField } from './fieldUpdates';

type Choices = NonNullable<PayloadCustomizationUpdateInput['update_choices']>;
export type UpdateChoice = NonNullable<Choices[CustomizableField]>;

/** The fields of an update by what happens to them, and the choice made on each conflict. */
const useCustomizationUpdate = (actionId: string, update: PayloadCustomizationUpdateOutput) => {
  const [choices, setChoices] = useState<Choices>({});
  const [applying, setApplying] = useState(false);

  const conflicts = update.update_fields.filter(field => field.field_conflict);
  const news = update.update_fields.filter(field => !field.field_conflict && changedByFiligran(field));
  const kept = update.update_fields.filter(field => !field.field_conflict && !changedByFiligran(field) && changedByYou(field));

  const choose = (field: CustomizableField, choice: UpdateChoice) => setChoices(previous => ({
    ...previous,
    [field]: choice,
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
    news,
    kept,
    choices,
    choose,
    ready: conflicts.every(field => choices[field.field_name]),
    applying,
    apply,
  };
};

export default useCustomizationUpdate;
