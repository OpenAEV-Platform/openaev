import { useEffect, useMemo, useState } from 'react';

import { type ScenarioChangeOutput, type ScenarioUpdateInput } from '../../../../../utils/api-types';
import { MESSAGING$ } from '../../../../../utils/Environment';
import { isConflict } from './scenarioChanges';
import { type ScenarioUpdateTarget } from './scenarioUpdateTargets';

export type ScenarioUpdateChoice = NonNullable<ScenarioUpdateInput['scenario_update_choices']>[string];

// The changes an update or a rollback brings, the side chosen for each conflict, and applying it.
const useScenarioUpdate = (target: ScenarioUpdateTarget, onApplied: () => void) => {
  const [changes, setChanges] = useState<ScenarioChangeOutput[] | null>(null);
  const [choices, setChoices] = useState<Record<string, ScenarioUpdateChoice>>({});
  const [applying, setApplying] = useState(false);

  useEffect(() => {
    let active = true;
    target.preview().then((previewed) => {
      if (active) setChanges(previewed);
    });
    return () => {
      active = false;
    };
  }, [target]);

  const conflicts = useMemo(() => (changes ?? []).filter(isConflict), [changes]);
  const others = useMemo(() => (changes ?? []).filter(change => !isConflict(change)), [changes]);
  const ready = changes !== null && conflicts.every(conflict => choices[conflict.scenario_change_id]);

  const choose = (changeId: string, choice: ScenarioUpdateChoice) => setChoices(previous => ({
    ...previous,
    [changeId]: choice,
  }));

  const apply = () => {
    setApplying(true);
    target.apply({ scenario_update_choices: choices })
      .then(() => {
        MESSAGING$.notifySuccess(target.appliedMessage);
        onApplied();
      })
      .finally(() => setApplying(false));
  };

  return {
    loading: changes === null,
    conflicts,
    others,
    choices,
    choose,
    ready,
    applying,
    apply,
  };
};

export default useScenarioUpdate;
