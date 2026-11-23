import { describe, expect, it } from 'vitest';

import { type MarkingDefinitionOutput } from '../../utils/api-types';
import { markingTone } from '../../utils/markings';

const marking = (type: string, definition: string): MarkingDefinitionOutput => ({
  marking_definition_id: `${type}-${definition}`,
  marking_definition_type: type,
  marking_definition_definition: definition,
  marking_definition_color: '#123456',
  marking_definition_order: 0,
} as MarkingDefinitionOutput);

describe('markingTone', () => {
  // The mapping every XTM Platform module shares, so a TLP level reads the same everywhere.
  it.each([
    ['TLP', 'TLP:RED', 'critical'],
    ['PAP', 'PAP:RED', 'critical'],
    ['TLP', 'TLP:AMBER', 'high'],
    ['TLP', 'TLP:AMBER+STRICT', 'high'],
    ['PAP', 'PAP:AMBER', 'high'],
    ['TLP', 'TLP:GREEN', 'low'],
    ['PAP', 'PAP:GREEN', 'low'],
    ['TLP', 'TLP:CLEAR', 'neutral'],
    ['PAP', 'PAP:CLEAR', 'neutral'],
  ])('draws %s %s as %s', (type, definition, tone) => {
    expect(markingTone(marking(type, definition))).toBe(tone);
  });

  it('reads a definition stored without its type prefix or in lower case', () => {
    expect(markingTone(marking('tlp', 'red'))).toBe('critical');
  });

  it('leaves a custom marking to its administrator colour', () => {
    expect(markingTone(marking('Statement', 'Internal only'))).toBeUndefined();
  });
});
