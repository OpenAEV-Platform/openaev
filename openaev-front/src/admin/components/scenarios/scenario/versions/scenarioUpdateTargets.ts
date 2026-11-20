import {
  previewScenarioRollback,
  previewScenarioUpdate,
  rollbackScenario,
  updateScenario,
} from '../../../../../actions/scenarios/scenario-update-actions';
import { type Translate } from '../../../../../components/i18n';
import { type ScenarioRevisionOutput, type ScenarioUpdateInput, type ScenarioUpdatePreviewOutput } from '../../../../../utils/api-types';

/** What the update dialog applies, a catalogue version or a revision to roll back to, and how it names it. */
export interface ScenarioUpdateTarget {
  title: string;
  summaryTitle: string;
  takenLabel: (count: string) => string;
  // The label of the applied side of a conflict.
  theirsLabel: string;
  conflictsIntro: string;
  removedConflictLabel: string;
  newsTitle: string;
  addedReason: string;
  removedReason: string;
  afterLabel: string;
  keptTitle: string;
  applyLabel: string;
  appliedMessage: string;
  preview: () => Promise<ScenarioUpdatePreviewOutput>;
  apply: (input: ScenarioUpdateInput) => Promise<unknown>;
}

export const updateTarget = (scenarioId: string, version: string, t: Translate): ScenarioUpdateTarget => ({
  title: t('Update to Filigran v{version}', { version }),
  summaryTitle: t('In this update'),
  takenLabel: count => t('Changes from Filigran: {count}', { count }),
  theirsLabel: t('Filigran version v{version}', { version }),
  conflictsIntro: t('Filigran changed things you edited. Pick which version to keep for each.'),
  removedConflictLabel: t('Removed in v{version}, changed by you', { version }),
  newsTitle: t('What\'s new in Filigran v{version}', { version }),
  addedReason: t('New in v{version}', { version }),
  removedReason: t('Removed in v{version}', { version }),
  afterLabel: t('After · v{version}', { version }),
  keptTitle: t('Your edits: the update keeps them'),
  applyLabel: t('Update'),
  appliedMessage: t('Scenario updated to v{version}', { version }),
  preview: () => previewScenarioUpdate(scenarioId, version),
  apply: input => updateScenario(scenarioId, version, input),
});

export const rollbackTarget = (scenarioId: string, revision: ScenarioRevisionOutput, t: Translate): ScenarioUpdateTarget => {
  const number = String(revision.scenario_revision_number);
  return {
    title: t('Roll back to revision #{number}', { number }),
    summaryTitle: t('In this rollback'),
    takenLabel: count => t('Changes restored: {count}', { count }),
    theirsLabel: t('Revision #{number}', { number }),
    conflictsIntro: t('You edited things this rollback restores. Pick which version to keep for each.'),
    removedConflictLabel: t('Not in revision #{number}, changed by you', { number }),
    newsTitle: t('Restored from the revision'),
    addedReason: t('In revision #{number}', { number }),
    removedReason: t('Not in revision #{number}', { number }),
    afterLabel: t('After · revision #{number}', { number }),
    keptTitle: t('Your edits: the rollback keeps them'),
    applyLabel: t('Roll back'),
    appliedMessage: t('Scenario rolled back'),
    preview: () => previewScenarioRollback(scenarioId, revision.scenario_revision_id),
    apply: input => rollbackScenario(scenarioId, revision.scenario_revision_id, input),
  };
};
