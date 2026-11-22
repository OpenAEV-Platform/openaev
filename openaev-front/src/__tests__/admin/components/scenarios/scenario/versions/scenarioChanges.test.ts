import { describe, expect, it } from 'vitest';

import { changeLabel, changesTableRow, valueText, withStatus } from '../../../../../../admin/components/scenarios/scenario/versions/scenarioChanges';
import { type ScenarioChangeOutput } from '../../../../../../utils/api-types';

const t = ((key: string, values?: Record<string, string>) => `t:${key.replace(/\{(\w+)\}/g, (_, name: string) => values?.[name] ?? name)}`) as Parameters<typeof changeLabel>[1];

const change = (
  element: ScenarioChangeOutput['scenario_change_element_kind'],
  field?: ScenarioChangeOutput['scenario_change_field_kind'],
  values: Partial<ScenarioChangeOutput> = {},
): ScenarioChangeOutput => ({
  scenario_change_id: `${element}/${field}`,
  scenario_change_element_kind: element,
  scenario_change_field_kind: field,
  scenario_change_status: 'CONFLICT',
  scenario_change_editable: false,
  ...values,
});

describe('scenarioChanges', () => {
  describe('changeLabel', () => {
    it.each([
      [change('SCENARIO', 'NAME'), 't:Scenario · t:Name'],
      [change('VARIABLES', 'VARIABLE', { scenario_change_field_key: 'target' }), 't:Variables · t:Variable target'],
      [change('INJECT', 'TITLE', { scenario_change_title: 'Scan' }), 't:Inject “Scan” · t:Title'],
      [change('INJECT', 'ARGUMENT', {
        scenario_change_title: 'Scan',
        scenario_change_field_key: 'command',
      }), 't:Inject “Scan” · t:Argument command'],
      [change('INJECT', undefined, { scenario_change_title: 'Scan' }), 't:Inject “Scan”'],
      [change('INJECT', 'CONTRACT'), 't:Inject · t:Payload version'],
      [change('INJECT', 'DEPENDS_ON'), 't:Inject · t:Depends on'],
    ])('names %o as people read it', (scenarioChange, label) => {
      // Act & Assert
      expect(changeLabel(scenarioChange, t)).toBe(label);
    });
  });

  describe('valueText', () => {
    it.each([
      [change('SCENARIO', 'NAME'), null, ''],
      [change('SCENARIO', 'NAME'), 'nmap', 'nmap'],
      [change('INJECT', 'CONTRACT'), {
        payload_external_id: 'p',
        payload_version: '1.2.0',
      }, 'v1.2.0'],
      [change('INJECT', 'CONTRACT'), { injector_contract_id: 'email' }, '{\n  "injector_contract_id": "email"\n}'],
      [change('INJECT', 'DEPENDS_DURATION'), 3, '3'],
    ])('reads the value of %o as text', (scenarioChange, value, text) => {
      // Act & Assert
      expect(valueText(scenarioChange, value)).toBe(text);
    });
  });

  it('keeps the changes of one status', () => {
    // Arrange
    const taken: ScenarioChangeOutput = change('SCENARIO', 'NAME', { scenario_change_status: 'TAKE_APPLIED' });

    // Act & Assert
    expect(withStatus([change('SCENARIO', 'DESCRIPTION'), taken], 'TAKE_APPLIED')).toEqual([taken]);
  });

  it('reads a change as a diff row', () => {
    // Act & Assert
    expect(changesTableRow(change('INJECT', 'TITLE', { scenario_change_title: 'Scan' }), 'nmap', null, t)).toEqual({
      id: 'INJECT/TITLE',
      field: 't:Inject “Scan” · t:Title',
      before: 'nmap',
      after: '',
    });
  });
});
