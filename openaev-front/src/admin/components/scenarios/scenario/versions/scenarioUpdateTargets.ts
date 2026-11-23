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
  scenarioId: string;
  title: string;
  // One sentence on what applying does.
  intro: string;
  // The version the content has after applying.
  version: string;
  // The label of the applied side of a conflict.
  theirsLabel: string;
  conflictsIntro: string;
  removedConflictLabel: string;
  addedReason: string;
  removedReason: string;
  // The two sides of the diff, both named with their version.
  afterLabel: string;
  // A kept edit is the same now and after: said in words, not as a diff.
  keptCaption: string;
  // The version the client's own edits are measured from.
  installedVersion: string;
  revokedSeverity: 'error' | 'warning';
  applyLabel: string;
  appliedMessage: string;
  failedTitle: string;
  preview: () => Promise<ScenarioUpdatePreviewOutput>;
  apply: (input: ScenarioUpdateInput) => Promise<unknown>;
}

export const updateTarget = (scenarioId: string, version: string, installedVersion: string, t: Translate): ScenarioUpdateTarget => ({
  scenarioId,
  title: t('Update to Filigran v{version}', { version }),
  intro: t('The content moves to Filigran v{version}.', { version }),
  version,
  theirsLabel: t('Filigran version v{version}', { version }),
  conflictsIntro: t('Filigran changed things you edited. Pick which version to keep for each.'),
  removedConflictLabel: t('Removed in v{version}, changed by you', { version }),
  addedReason: t('New in v{version}', { version }),
  removedReason: t('Removed in v{version}', { version }),
  afterLabel: t('After the update · v{version}', { version }),
  keptCaption: t('Now and after the update: unchanged'),
  installedVersion,
  revokedSeverity: 'error',
  applyLabel: t('Update'),
  appliedMessage: t('Scenario updated to v{version}.', { version }),
  failedTitle: t('The update didn\'t apply. Nothing was changed.'),
  preview: () => previewScenarioUpdate(scenarioId, version),
  apply: input => updateScenario(scenarioId, version, input),
});

// The revision is named by its version and date, so nobody has to remember what "#2" was.
export const rollbackTarget = (scenarioId: string, revision: ScenarioRevisionOutput, installedVersion: string, t: Translate, date: string): ScenarioUpdateTarget => {
  const number = String(revision.scenario_revision_number);
  const version = revision.scenario_revision_catalog_version;
  return {
    scenarioId,
    title: t('Roll back to revision #{number}', { number }),
    intro: t('The content goes back to revision #{number} (v{version}, {date}).', {
      number,
      version,
      date,
    }),
    version,
    theirsLabel: t('Revision #{number}', { number }),
    conflictsIntro: t('You edited things this rollback restores. Pick which version to keep for each.'),
    removedConflictLabel: t('Not in revision #{number}, changed by you', { number }),
    addedReason: t('In revision #{number}', { number }),
    removedReason: t('Not in revision #{number}', { number }),
    afterLabel: t('After the rollback · revision #{number} (v{version})', {
      number,
      version,
    }),
    keptCaption: t('Now and after the rollback: unchanged'),
    installedVersion,
    revokedSeverity: 'warning',
    applyLabel: t('Roll back'),
    appliedMessage: t('Scenario rolled back.'),
    failedTitle: t('The rollback didn\'t apply. Nothing was changed.'),
    preview: () => previewScenarioRollback(scenarioId, revision.scenario_revision_id),
    apply: input => rollbackScenario(scenarioId, revision.scenario_revision_id, input),
  };
};
