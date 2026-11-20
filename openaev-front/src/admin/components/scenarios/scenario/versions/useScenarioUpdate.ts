import { useEffect, useMemo, useState } from 'react';

import { type ScenarioChangeOutput, type ScenarioUpdateInput, type ScenarioUpdatePreviewOutput } from '../../../../../utils/api-types';
import { MESSAGING$ } from '../../../../../utils/Environment';
import { valueText, withStatus } from './scenarioChanges';
import { type ScenarioUpdateTarget } from './scenarioUpdateTargets';

export type ScenarioUpdateChoice = NonNullable<ScenarioUpdateInput['scenario_update_choices']>[string];

// The changes an update or a rollback brings, the side chosen or the value typed for each conflict, and applying it.
const useScenarioUpdate = (target: ScenarioUpdateTarget, onApplied: () => void) => {
  const [preview, setPreview] = useState<ScenarioUpdatePreviewOutput | null>(null);
  const [choices, setChoices] = useState<Record<string, ScenarioUpdateChoice>>({});
  const [edits, setEdits] = useState<Record<string, string>>({});
  const [applying, setApplying] = useState(false);

  useEffect(() => {
    let active = true;
    target.preview().then((previewed) => {
      if (active) setPreview(previewed);
    });
    return () => {
      active = false;
    };
  }, [target]);

  const changes = useMemo(() => {
    const all = preview?.scenario_update_changes ?? [];
    return {
      conflicts: withStatus(all, 'CONFLICT'),
      taken: withStatus(all, 'TAKE_APPLIED'),
      kept: withStatus(all, 'KEEP_MINE'),
      added: withStatus(all, 'ADDED'),
      removed: withStatus(all, 'REMOVED'),
    };
  }, [preview]);
  const ready = preview !== null && changes.conflicts.every(conflict => choices[conflict.scenario_change_id]);

  const choose = (changeId: string, choice: ScenarioUpdateChoice) => setChoices(previous => ({
    ...previous,
    [changeId]: choice,
  }));
  const chooseAll = (choice: ScenarioUpdateChoice) => setChoices(Object.fromEntries(changes.conflicts.map(conflict => [conflict.scenario_change_id, choice])));
  // Typing starts from the client's own value.
  const startEdit = (conflict: ScenarioChangeOutput) => {
    choose(conflict.scenario_change_id, 'EDIT');
    setEdits(previous => ({
      [conflict.scenario_change_id]: valueText(conflict, conflict.scenario_change_local),
      ...previous,
    }));
  };
  const edit = (changeId: string, value: string) => setEdits(previous => ({
    ...previous,
    [changeId]: value,
  }));

  const apply = () => {
    setApplying(true);
    target.apply({
      scenario_update_choices: choices,
      scenario_update_edits: Object.fromEntries(Object.entries(edits).filter(([changeId]) => choices[changeId] === 'EDIT')),
    })
      .then(() => {
        MESSAGING$.notifySuccess(target.appliedMessage);
        onApplied();
      })
      // The call already shows why it was refused; the dialog stays open to try again.
      .catch(() => undefined)
      .finally(() => setApplying(false));
  };

  return {
    preview,
    ...changes,
    choices,
    edits,
    choose,
    chooseAll,
    startEdit,
    edit,
    ready,
    applying,
    apply,
  };
};

export default useScenarioUpdate;
