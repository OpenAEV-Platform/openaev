import { useEffect, useState } from 'react';

import { fetchAssignableMarkingDefinitions, searchMarkingDefinitions } from '../../actions/marking_definitions/marking-definition-actions';
import { type MarkingDefinitionOutput } from '../api-types';

/**
 * Loads marking definitions once and indexes them by id.
 *
 * Markings are not held in the Redux store (unlike tags, which resolve through `helper.getTag`), so
 * a list rendering `asset_markings` has to resolve the ids itself. Fetching here — once per page —
 * rather than inside the chip component keeps a 50-row list at one request instead of fifty.
 *
 * The definition set is small and effectively static (nine seeded TLP/PAP levels per tenant), so a
 * single generous page is enough; there is nothing to paginate through.
 *
 * @param options.skip when true (e.g. the `MARKING` feature flag is off), issues no request at all —
 * a caller behind a disabled flag must not leak a `marking_definitions` search even if its own
 * rendering is otherwise gated.
 * @param options.assignableOnly when true, loads only the definitions the current user is cleared
 * to assign (server-filtered, cumulative per type - see `fetchAssignableMarkingDefinitions`)
 * instead of every definition in the tenant. Needs no capability, so use it for pickers and for
 * displaying markings on rows (a row's markings are always within the viewer's clearance).
 */
const useMarkingDefinitions = (options?: {
  skip?: boolean;
  assignableOnly?: boolean;
}): Record<string, MarkingDefinitionOutput> => {
  const skip = options?.skip ?? false;
  const assignableOnly = options?.assignableOnly ?? false;
  const [definitions, setDefinitions] = useState<Record<string, MarkingDefinitionOutput>>({});

  useEffect(() => {
    if (skip) {
      setDefinitions({});
      return undefined;
    }
    let cancelled = false;
    const request = assignableOnly
      ? fetchAssignableMarkingDefinitions().then((result: { data?: MarkingDefinitionOutput[] }) => result?.data ?? [])
      : searchMarkingDefinitions({
          page: 0,
          size: 200,
        }).then((result: { data?: { content?: MarkingDefinitionOutput[] } }) => result?.data?.content ?? []);
    request
      .then((content: MarkingDefinitionOutput[]) => {
        if (cancelled) {
          return;
        }
        setDefinitions(Object.fromEntries(content.map(marking => [marking.marking_definition_id, marking])));
      })
      // A failed lookup must not break the list: ItemMarkings renders "-" for ids it cannot
      // resolve, so the column degrades rather than throwing.
      .catch(() => {
        if (!cancelled) {
          setDefinitions({});
        }
      });
    return () => {
      cancelled = true;
    };
  }, [skip, assignableOnly]);

  return definitions;
};

export default useMarkingDefinitions;
