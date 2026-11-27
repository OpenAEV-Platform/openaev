import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';

import { markingDefinitionQueries } from '../../actions/marking_definitions/marking-definition-queries';
import { type MarkingDefinitionOutput } from '../api-types';

const NO_DEFINITIONS: Record<string, MarkingDefinitionOutput> = {};

/**
 * Loads the marking definitions and indexes them by id.
 *
 * Markings are not held in the Redux store (unlike tags, which resolve through `helper.getTag`), so
 * a list rendering `asset_markings` has to resolve the ids itself. One query for the page, shared by
 * every component that calls this hook, rather than one request per chip.
 *
 * The definitions come from a live query (`markingDefinitionQueries`): a clearance change resets it,
 * so the markings the reader was just cleared for resolve on the rows that come back with them.
 *
 * @param options.skip when true (e.g. the `MARKING` feature flag is off), issues no request at all —
 * a caller behind a disabled flag must not leak a `marking_definitions` search even if its own
 * rendering is otherwise gated.
 * @param options.assignableOnly when true, loads only the definitions the current user is cleared
 * to assign (server-filtered, cumulative per type - see `getAssignableMarkingDefinitions`)
 * instead of every definition in the tenant. Needs no capability, so use it for pickers and for
 * displaying markings on rows (a row's markings are always within the viewer's clearance).
 */
export const useMarkingDefinitionsWithLoading = (options?: {
  skip?: boolean;
  assignableOnly?: boolean;
}): {
  definitions: Record<string, MarkingDefinitionOutput>;
  loading: boolean;
} => {
  const skip = options?.skip ?? false;
  const assignableOnly = options?.assignableOnly ?? false;
  const { data, isPending } = useQuery({
    ...markingDefinitionQueries.list(assignableOnly),
    enabled: !skip,
  });
  // A failed lookup must not break the list: ItemMarkings renders "-" for ids it cannot resolve,
  // so the column degrades rather than throwing.
  const definitions = useMemo(
    () => (skip || !data ? NO_DEFINITIONS : Object.fromEntries(data.map(marking => [marking.marking_definition_id, marking]))),
    [skip, data],
  );

  return {
    definitions,
    loading: !skip && isPending,
  };
};

const useMarkingDefinitions = (options?: {
  skip?: boolean;
  assignableOnly?: boolean;
}): Record<string, MarkingDefinitionOutput> => useMarkingDefinitionsWithLoading(options).definitions;

export default useMarkingDefinitions;
