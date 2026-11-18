import type { MarkingDefinitionOutput } from '../../utils/api-types';

export interface MarkingDefinitionHelper {
  getMarkingDefinition: (markingDefinitionId: MarkingDefinitionOutput['marking_definition_id']) => MarkingDefinitionOutput | undefined;
  getMarkingDefinitionsMap: () => Record<string, MarkingDefinitionOutput>;
}
