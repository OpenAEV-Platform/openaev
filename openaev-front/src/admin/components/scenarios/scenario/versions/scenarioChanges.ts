import { type ChangeDiffRow } from '../../../../../components/common/update/ChangeDiff';
import { type Translate } from '../../../../../components/i18n';
import { type ScenarioChangeOutput } from '../../../../../utils/api-types';

type ElementKind = ScenarioChangeOutput['scenario_change_element_kind'];
type FieldKind = NonNullable<ScenarioChangeOutput['scenario_change_field_kind']>;

const ELEMENT_LABELS: Record<ElementKind, (t: Translate, title?: string) => string> = {
  SCENARIO: t => t('Scenario'),
  VARIABLES: t => t('Variables'),
  INJECT: (t, title) => (title ? t('Inject “{title}”', { title }) : t('Inject')),
};

// A variable is named by its key, an inject content field as an argument.
const FIELD_LABELS: Record<FieldKind, (t: Translate, key: string) => string> = {
  NAME: t => t('Name'),
  DESCRIPTION: t => t('Description'),
  SEVERITY: t => t('Severity'),
  CATEGORY: t => t('Category'),
  TITLE: t => t('Title'),
  CONTRACT: t => t('Payload version'),
  DEPENDS_DURATION: t => t('Delay'),
  DEPENDS_ON: t => t('Depends on'),
  ARGUMENT: (t, key) => t('Argument {key}', { key }),
  VARIABLE: (t, key) => t('Variable {key}', { key }),
};

/** What a change is on: the scenario, its variables, or an inject by its title. */
export const elementLabel = (change: ScenarioChangeOutput, t: Translate) => ELEMENT_LABELS[change.scenario_change_element_kind](t, change.scenario_change_title);

/** What a change is about, as people read it: "Inject “Scan” · Title", "Variables · Variable target". */
export const changeLabel = (change: ScenarioChangeOutput, t: Translate) => {
  const element = elementLabel(change, t);
  const field = change.scenario_change_field_kind;
  return field ? `${element} · ${FIELD_LABELS[field](t, change.scenario_change_field_key ?? '')}` : element;
};

/** A changed value as text: the payload version a contract runs reads "v1.2.0", a structure its indented JSON. */
export const valueText = (change: ScenarioChangeOutput, value: unknown): string => {
  if (value === null || value === undefined) {
    return '';
  }
  if (typeof value === 'string') {
    return value;
  }
  const payloadVersion = change.scenario_change_field_kind === 'CONTRACT' ? (value as { payload_version?: string }).payload_version : undefined;
  return payloadVersion ? `v${payloadVersion}` : JSON.stringify(value, null, 2);
};

export const withStatus = (changes: ScenarioChangeOutput[], status: ScenarioChangeOutput['scenario_change_status']) => changes
  .filter(change => change.scenario_change_status === status);

/** A changed field as the diff shows it. */
export const changeDiffRow = (change: ScenarioChangeOutput, before: unknown, after: unknown, t: Translate): ChangeDiffRow => ({
  id: change.scenario_change_id,
  field: changeLabel(change, t),
  before: valueText(change, before),
  after: valueText(change, after),
});
