import type { MarkingDefinitionOutput } from '../../utils/api-types';

export interface MarkingDefinitionHelper { getMarkingDefinitionsMap: () => Record<string, MarkingDefinitionOutput> }
