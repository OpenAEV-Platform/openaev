import { type MarkingDefinitionOutput } from '../../../../utils/api-types';

export type MarkingDefinitionStoreResult = {
  result?: string;
  entities?: { marking_definitions?: Record<string, MarkingDefinitionOutput> };
};

export const extractMarkingDefinitionFromStoreResult = (
  value: MarkingDefinitionStoreResult,
): MarkingDefinitionOutput | null => {
  if (!value?.result) {
    return null;
  }
  return value.entities?.marking_definitions?.[value.result] ?? null;
};
