import { type ChangeDiffRow } from '../../../../../components/common/update/ChangeDiff';
import { type Translate } from '../../../../../components/i18n';
import { type ScenarioChangeOutput } from '../../../../../utils/api-types';

const INJECT_ELEMENT = 'inject:';
const VARIABLES_ELEMENT = 'variables';
const CONTENT_FIELD = 'content.';

const FIELD_LABELS: Record<string, string> = {
  name: 'Name',
  description: 'Description',
  severity: 'Severity',
  category: 'Category',
  title: 'Title',
  contract: 'Payload version',
  depends_duration: 'Delay',
  depends_on: 'Dependencies',
};

/** What a change is on: the scenario, its variables, or an inject by its title. */
export const elementLabel = (change: ScenarioChangeOutput, t: Translate) => {
  if (change.scenario_change_element.startsWith(INJECT_ELEMENT)) {
    return change.scenario_change_title ? t('Inject “{title}”', { title: change.scenario_change_title }) : t('Inject');
  }
  return t(change.scenario_change_element === VARIABLES_ELEMENT ? 'Variables' : 'Scenario');
};

// A variable is named by its key, an inject content field as an argument.
const fieldLabel = (change: ScenarioChangeOutput, field: string, t: Translate) => {
  if (change.scenario_change_element === VARIABLES_ELEMENT) {
    return t('Variable {key}', { key: field });
  }
  if (field.startsWith(CONTENT_FIELD)) {
    return t('Argument {key}', { key: field.slice(CONTENT_FIELD.length) });
  }
  return t(FIELD_LABELS[field] ?? field);
};

/** What a change is about, as people read it: "Inject “Scan” · Title", "Variables · Variable target". */
export const changeLabel = (change: ScenarioChangeOutput, t: Translate) => {
  const element = elementLabel(change, t);
  const field = change.scenario_change_field;
  return field ? `${element} · ${fieldLabel(change, field, t)}` : element;
};

/** A changed value as text: a payload version reads "v1.2.0", a structure its indented JSON. */
export const valueText = (value: unknown): string => {
  if (value === null || value === undefined) {
    return '';
  }
  if (typeof value === 'string') {
    return value;
  }
  if (typeof value === 'object' && 'payload_version' in value) {
    return `v${String(value.payload_version)}`;
  }
  return JSON.stringify(value, null, 2);
};

export const withStatus = (changes: ScenarioChangeOutput[], status: ScenarioChangeOutput['scenario_change_status']) => changes
  .filter(change => change.scenario_change_status === status);

/** A changed field as the diff shows it. */
export const changeDiffRow = (change: ScenarioChangeOutput, before: unknown, after: unknown, t: Translate): ChangeDiffRow => ({
  id: change.scenario_change_id,
  field: changeLabel(change, t),
  before: valueText(before),
  after: valueText(after),
});
