import { type MarkingDefinitionOutput } from './api-types';

// Order-independent: a marking picker's Autocomplete can re-emit the same selection in a
// different order without the assigned set actually changing, and treating that as "changed"
// would fire the markings PUT (and its own success/failure notification) on every save even when
// markings never moved. Shared by every form that hosts a markings picker (AssetForm,
// AiTargetForm, ...) so they all draw the same "did markings actually change?" line.
const markingIdSetsEqual = (a: string[], b: string[]): boolean => {
  const setA = new Set(a);
  const setB = new Set(b);
  return setA.size === setB.size && [...setA].every(id => setB.has(id));
};

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

export default markingIdSetsEqual;
