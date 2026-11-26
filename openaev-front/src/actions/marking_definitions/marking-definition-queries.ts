import { queryOptions } from '@tanstack/react-query';

import { type MarkingDefinitionOutput } from '../../utils/api-types';
import { ENTITY_TYPES } from '../../utils/query/entityChange';
import { liveMeta } from '../../utils/query/live';
import { getAssignableMarkingDefinitions, listMarkingDefinitions } from './marking-definition-actions';

// Every parameter of a query is part of its key.
export const markingDefinitionKeys = {
  all: ['markingdefinitions'] as const,
  lists: () => [...markingDefinitionKeys.all, 'list'] as const,
  list: (assignableOnly: boolean) => [...markingDefinitionKeys.lists(), { assignableOnly }] as const,
};

export const markingDefinitionQueries = {
  // Every definition of the tenant, or only those within the reader's clearance (`assignableOnly`).
  // The latter depends on the clearance: a clearance change resets it with every other query, so a
  // marking the reader was just cleared for resolves on the rows the reset brings back.
  // Refetched when a definition it shows changes, or one is created or deleted. Only the readers
  // holding the marking definition capability receive these changes from the stream; the others see
  // a renamed or recoloured definition at the next refetch.
  list: (assignableOnly: boolean) => queryOptions({
    queryKey: markingDefinitionKeys.list(assignableOnly),
    queryFn: ({ signal }) => (assignableOnly ? getAssignableMarkingDefinitions({ signal }) : listMarkingDefinitions({ signal })),
    meta: liveMeta<MarkingDefinitionOutput[]>({
      collections: [ENTITY_TYPES.MARKING_DEFINITIONS],
      refs: definitions => definitions.map(definition => ({
        type: ENTITY_TYPES.MARKING_DEFINITIONS,
        id: definition.marking_definition_id,
      })),
    }),
  }),
};
