import { describe, expect, it } from 'vitest';

import { changeDiffRow, changeLabel, valueText, withStatus } from '../../../../../../admin/components/scenarios/scenario/versions/scenarioChanges';
import { type ScenarioChangeOutput } from '../../../../../../utils/api-types';

const t = ((key: string, values?: Record<string, string>) => `t:${key.replace(/\{(\w+)\}/g, (_, name: string) => values?.[name] ?? name)}`) as Parameters<typeof changeLabel>[1];

const change = (element: string, field?: string, title?: string): ScenarioChangeOutput => ({
  scenario_change_id: field ? `${element}/${field}` : element,
  scenario_change_element: element,
  scenario_change_field: field,
  scenario_change_title: title,
  scenario_change_status: 'CONFLICT',
  scenario_change_editable: false,
});

describe('scenarioChanges', () => {
  describe('changeLabel', () => {
    it.each([
      [change('scenario', 'name'), 't:Scenario · t:Name'],
      [change('variables', 'target'), 't:Variables · t:Variable target'],
      [change('inject:a', 'title', 'Scan'), 't:Inject “Scan” · t:Title'],
      [change('inject:a', 'content.command', 'Scan'), 't:Inject “Scan” · t:Argument command'],
      [change('inject:a', undefined, 'Scan'), 't:Inject “Scan”'],
      [change('inject:a', 'contract'), 't:Inject · t:Payload version'],
    ])('names %o as people read it', (scenarioChange, label) => {
      // Act & Assert
      expect(changeLabel(scenarioChange, t)).toBe(label);
    });
  });

  describe('valueText', () => {
    it.each([
      [null, ''],
      ['nmap', 'nmap'],
      [{
        payload_external_id: 'p',
        payload_version: '1.2.0',
      }, 'v1.2.0'],
      [3, '3'],
    ])('reads %o as text', (value, text) => {
      // Act & Assert
      expect(valueText(value)).toBe(text);
    });
  });

  it('keeps the changes of one status', () => {
    // Arrange
    const taken: ScenarioChangeOutput = {
      ...change('scenario', 'name'),
      scenario_change_status: 'TAKE_APPLIED',
    };

    // Act & Assert
    expect(withStatus([change('scenario', 'description'), taken], 'TAKE_APPLIED')).toEqual([taken]);
  });

  it('reads a change as a diff row', () => {
    // Act & Assert
    expect(changeDiffRow(change('inject:a', 'title', 'Scan'), 'nmap', null, t)).toEqual({
      id: 'inject:a/title',
      field: 't:Inject “Scan” · t:Title',
      before: 'nmap',
      after: '',
    });
  });
});
