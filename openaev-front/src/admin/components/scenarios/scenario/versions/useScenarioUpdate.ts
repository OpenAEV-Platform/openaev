import { useEffect, useMemo, useState } from 'react';

import { type ScenarioChangeOutput, type ScenarioUpdateInput, type ScenarioUpdatePreviewOutput } from '../../../../../utils/api-types';
import { valueText, withStatus } from './scenarioChanges';
import { type ScenarioUpdateTarget } from './scenarioUpdateTargets';

export type ScenarioUpdateChoice = NonNullable<ScenarioUpdateInput['scenario_update_choices']>[string];

// The changes an update or a rollback brings, the side chosen or the value typed for each conflict, and applying it.
const useScenarioUpdate = (target: ScenarioUpdateTarget, onApplied: () => void) => {
  const [preview, setPreview] = useState<ScenarioUpdatePreviewOutput | null>(null);
  const [refusal, setRefusal] = useState<{
    message: string;
    status?: number;
  } | null>(null);
  const [choices, setChoices] = useState<Record<string, ScenarioUpdateChoice>>({});
  const [edits, setEdits] = useState<Record<string, string>>({});
  const [applying, setApplying] = useState(false);
  // 409: the scenario changed since the preview.
  const [applyError, setApplyError] = useState<'changed' | 'failed' | null>(null);
  const [previewKey, setPreviewKey] = useState(0);

  useEffect(() => {
    let active = true;
    setPreview(null);
    setRefusal(null);
    target.preview()
      .then((previewed) => {
        if (active) setPreview(previewed);
      })
      .catch((error: {
        message?: string;
        status?: number;
      } | false) => {
        if (active) {
          setRefusal({
            message: (error && error.message) || '',
            status: error ? error.status : undefined,
          });
        }
      });
    return () => {
      active = false;
    };
  }, [target, previewKey]);

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
    setApplyError(null);
    target.apply({
      scenario_update_choices: choices,
      scenario_update_edits: Object.fromEntries(Object.entries(edits).filter(([changeId]) => choices[changeId] === 'EDIT')),
    })
      .then(() => onApplied())
      .catch((error: { status?: number }) => setApplyError(error.status === 409 ? 'changed' : 'failed'))
      .finally(() => setApplying(false));
  };

  return {
    preview,
    refusal,
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
    applyError,
    retryPreview: () => setPreviewKey(key => key + 1),
    showLatestPreview: () => {
      setApplyError(null);
      setChoices({});
      setEdits({});
      setPreviewKey(key => key + 1);
    },
  };
};

export default useScenarioUpdate;
