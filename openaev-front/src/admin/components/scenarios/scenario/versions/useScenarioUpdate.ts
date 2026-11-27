import { useEffect, useMemo, useState } from 'react';

import { type MergeChoice } from '../../../../../components/common/update/ConflictChoice';
import { type ScenarioChangeOutput, type ScenarioUpdatePreviewOutput } from '../../../../../utils/api-types';
import { valueText, withStatus } from './scenarioChanges';
import { type ScenarioUpdateTarget } from './scenarioUpdateTargets';

interface Refusal {
  message: string;
  status?: number;
}

const refusalOf = (error: {
  message?: string;
  status?: number;
} | false): Refusal => ({
  message: (error && error.message) || '',
  status: error ? error.status : undefined,
});

// The changes an update or a rollback brings, the side chosen or the value typed for each conflict, and applying it.
const useScenarioUpdate = (target: ScenarioUpdateTarget, onApplied: () => void) => {
  const [preview, setPreview] = useState<ScenarioUpdatePreviewOutput | null>(null);
  const [refusal, setRefusal] = useState<Refusal | null>(null);
  const [choices, setChoices] = useState<Record<string, MergeChoice>>({});
  const [edits, setEdits] = useState<Record<string, string>>({});
  const [applying, setApplying] = useState(false);
  const [applyError, setApplyError] = useState<Refusal | null>(null);
  const [previewKey, setPreviewKey] = useState(0);

  useEffect(() => {
    let active = true;
    setPreview(null);
    setRefusal(null);
    target.preview()
      .then((previewed) => {
        if (active) setPreview(previewed);
      })
      .catch((error: Parameters<typeof refusalOf>[0]) => {
        if (active) setRefusal(refusalOf(error));
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

  const choose = (changeId: string, choice: MergeChoice) => setChoices(previous => ({
    ...previous,
    [changeId]: choice,
  }));
  const chooseAll = (choice: MergeChoice) => setChoices(Object.fromEntries(changes.conflicts.map(conflict => [conflict.scenario_change_id, choice])));
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
    if (preview === null) return;
    setApplying(true);
    setApplyError(null);
    target.apply({
      scenario_update_from_version: preview.scenario_update_from_version,
      scenario_update_choices: choices,
      scenario_update_edits: Object.fromEntries(Object.entries(edits).filter(([changeId]) => choices[changeId] === 'EDIT')),
    })
      .then(() => onApplied())
      .catch((error: Parameters<typeof refusalOf>[0]) => setApplyError(refusalOf(error)))
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
