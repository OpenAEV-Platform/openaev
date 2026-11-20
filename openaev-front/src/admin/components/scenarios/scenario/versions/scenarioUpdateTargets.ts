import {
  previewScenarioRollback,
  previewScenarioUpdate,
  rollbackScenario,
  updateScenario,
} from '../../../../../actions/scenarios/scenario-update-actions';
import { type Translate } from '../../../../../components/i18n';
import { type ScenarioChangeOutput, type ScenarioUpdateInput } from '../../../../../utils/api-types';

/** What the update dialog applies: a catalogue version, or a revision to roll back to. */
export interface ScenarioUpdateTarget {
  title: string;
  // The label of the applied side of a conflict.
  theirsLabel: string;
  applyLabel: string;
  appliedMessage: string;
  preview: () => Promise<ScenarioChangeOutput[]>;
  apply: (input: ScenarioUpdateInput) => Promise<unknown>;
}

export const updateTarget = (scenarioId: string, version: string, t: Translate): ScenarioUpdateTarget => ({
  title: t('Update to v{version}', { version }),
  theirsLabel: t('Version v{version}', { version }),
  applyLabel: t('Update'),
  appliedMessage: t('Scenario updated to v{version}', { version }),
  preview: () => previewScenarioUpdate(scenarioId, version),
  apply: input => updateScenario(scenarioId, version, input),
});

export const rollbackTarget = (scenarioId: string, revisionId: string, date: string, t: Translate): ScenarioUpdateTarget => ({
  title: t('Roll back to the content of {date}', { date }),
  theirsLabel: t('Content of {date}', { date }),
  applyLabel: t('Roll back'),
  appliedMessage: t('Scenario rolled back'),
  preview: () => previewScenarioRollback(scenarioId, revisionId),
  apply: input => rollbackScenario(scenarioId, revisionId, input),
});
