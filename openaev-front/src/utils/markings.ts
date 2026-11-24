import { type ChipSeverity } from '@filigran/design-system';

import { type MarkingDefinitionOutput } from './api-types';
import { type Option } from './Option';

// Seeded definitions already store the display value with its type baked in, e.g.
// `marking_definition_definition: "TLP:RED"` for a `marking_definition_type: "TLP"`. Prefixing
// unconditionally would render `TLP:TLP:RED`, so the type is only prepended when the definition
// does not already carry it. Always rendered uppercase (e.g. `TLP:GREEN`), regardless of how the
// type/definition were cased when the marking was created.
// Shared by every marking display (MarkingChip, MarkingField's picker, ItemMarkings' tooltip) so
// they all render the exact same label instead of re-deriving their own "type:definition" format.
export const markingLabel = (marking: MarkingDefinitionOutput) => {
  const type = marking.marking_definition_type.toUpperCase();
  const definition = marking.marking_definition_definition.toUpperCase();
  return definition.startsWith(`${type}:`) ? definition : `${type}:${definition}`;
};

// The tone a predefined marking (TLP, PAP) is drawn with, the same mapping every XTM Platform
// module uses (OpenCTI, OpenCRQ, XTM One): red is critical, amber high, green low, clear neutral.
// Drawn through the design system's severity chip. `undefined`: a custom marking, which keeps
// its administrator's colour.
export const markingTone = (marking: MarkingDefinitionOutput): ChipSeverity | undefined => {
  switch (markingLabel(marking)) {
    case 'TLP:RED':
    case 'PAP:RED':
      return 'critical';
    case 'TLP:AMBER':
    case 'TLP:AMBER+STRICT':
    case 'PAP:AMBER':
      return 'high';
    case 'TLP:GREEN':
    case 'PAP:GREEN':
      return 'low';
    case 'TLP:CLEAR':
    case 'TLP:WHITE':
    case 'PAP:CLEAR':
    case 'PAP:WHITE':
      return 'neutral';
    default:
      return undefined;
  }
};

// The `{ id, label }` option of a marking definition, for the value pickers of the marking filter.
export const markingOption = (marking: MarkingDefinitionOutput): Option => ({
  id: marking.marking_definition_id,
  label: markingLabel(marking),
});

// Keeps only the highest-order marking per type (e.g. TLP:CLEAR/GREEN/AMBER collapse to
// TLP:AMBER): holding a level already implies holding every less restrictive level of the same
// type (see MarkingScopeResolver), so there is never a product reason to show or keep more than
// one level per type at once. Shared by ItemMarkings (collapses for display only) and MarkingField
// (enforces the same rule on the value itself, not just how it's displayed).
// Result is ordered most-restrictive-first (descending order), regardless of input order - the
// most sensitive marking should always be the first thing a reader sees, in a list or in a picker.
export const collapseToHighestPerType = (
  markings: MarkingDefinitionOutput[],
): MarkingDefinitionOutput[] => {
  const highestByType = new Map<string, MarkingDefinitionOutput>();
  markings.forEach((marking) => {
    const current = highestByType.get(marking.marking_definition_type);
    if (!current || marking.marking_definition_order > current.marking_definition_order) {
      highestByType.set(marking.marking_definition_type, marking);
    }
  });
  return Array.from(highestByType.values())
    .sort((a, b) => b.marking_definition_order - a.marking_definition_order);
};

// Most restrictive first within a type (TLP:RED at the top), for the marking pickers.
export const sortMarkings = (markings: MarkingDefinitionOutput[]): MarkingDefinitionOutput[] =>
  [...markings].sort((a, b) => {
    if (a.marking_definition_type !== b.marking_definition_type) {
      return a.marking_definition_type.localeCompare(b.marking_definition_type);
    }
    return b.marking_definition_order - a.marking_definition_order;
  });
