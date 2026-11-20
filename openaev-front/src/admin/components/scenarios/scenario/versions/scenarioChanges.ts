import { type BeforeAfterRow } from '../../../../../components/common/update/BeforeAfterTable';
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

const elementLabel = (change: ScenarioChangeOutput, t: Translate) => {
  if (change.scenario_change_element.startsWith(INJECT_ELEMENT)) {
    return change.scenario_change_title ?? t('Inject');
  }
  return t(change.scenario_change_element === VARIABLES_ELEMENT ? 'Variables' : 'Scenario');
};

// A variable is named by its key, an inject content field by its own name.
const fieldLabel = (change: ScenarioChangeOutput, field: string, t: Translate) => {
  if (change.scenario_change_element === VARIABLES_ELEMENT) {
    return field;
  }
  if (field.startsWith(CONTENT_FIELD)) {
    return field.slice(CONTENT_FIELD.length);
  }
  return t(FIELD_LABELS[field] ?? field);
};

/** What a change is about, as people read it: "Scan · Title", "Variables · target". */
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

/** A changed field as the before/after table shows it. */
export const beforeAfterRow = (change: ScenarioChangeOutput, before: unknown, after: unknown, t: Translate): BeforeAfterRow => ({
  id: change.scenario_change_id,
  field: changeLabel(change, t),
  before: valueText(before),
  after: valueText(after),
});
