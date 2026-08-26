import { useEffect, useState } from 'react';

import { searchMarkingDefinitions } from '../../actions/markings/marking-definition-actions';
import { type MarkingDefinitionOutput } from '../api-types';

/**
 * Loads every marking definition once and indexes it by id.
 *
 * Markings are not held in the Redux store (unlike tags, which resolve through `helper.getTag`), so
 * a list rendering `asset_markings` has to resolve the ids itself. Fetching here — once per page —
 * rather than inside the chip component keeps a 50-row list at one request instead of fifty.
 *
 * The definition set is small and effectively static (nine seeded TLP/PAP levels per tenant), so a
 * single generous page is enough; there is nothing to paginate through.
 */
const useMarkingDefinitions = (): Record<string, MarkingDefinitionOutput> => {
  const [definitions, setDefinitions] = useState<Record<string, MarkingDefinitionOutput>>({});

  useEffect(() => {
    let cancelled = false;
    searchMarkingDefinitions({
      page: 0,
      size: 200,
    })
      .then((result: { data?: { content?: MarkingDefinitionOutput[] } }) => {
        if (cancelled) {
          return;
        }
        const content = result?.data?.content ?? [];
        setDefinitions(Object.fromEntries(content.map(marking => [marking.marking_id, marking])));
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
  }, []);

  return definitions;
};

export default useMarkingDefinitions;
