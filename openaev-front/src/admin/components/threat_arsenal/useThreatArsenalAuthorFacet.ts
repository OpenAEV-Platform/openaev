import { useEffect, useMemo, useState } from 'react';

import { fetchThreatArsenalAuthorCounts } from '../../../actions/threat_arsenals/threatArsenal-actions';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { type SearchPaginationInput } from '../../../utils/api-types';
import { type AuthorOption } from './ThreatArsenalSidebar';

interface AuthorCountRow {
  author: string;
  author_name?: string;
  author_type?: string;
  count?: number;
}

/**
 * Author facet source for the Threat Arsenal sidebar. Mirrors the domain facet:
 * a stable universe of every author (fetched unfiltered, so rows never vanish)
 * plus per-author counts under the current filters (so zero-count authors can be
 * greyed out instead of disappearing).
 */
const useThreatArsenalAuthorFacet = (
  searchPaginationInput: SearchPaginationInput,
): AuthorOption[] => {
  const [universe, setUniverse] = useState<AuthorCountRow[]>([]);
  const [counts, setCounts] = useState<Record<string, number>>({});

  // Stable universe of all authors (unfiltered), fetched once.
  useEffect(() => {
    let cancelled = false;
    fetchThreatArsenalAuthorCounts(buildSearchPagination({}))
      .then((response) => {
        if (!cancelled) setUniverse((response.data ?? []) as AuthorCountRow[]);
      })
      .catch(() => {
        if (!cancelled) setUniverse([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // Per-author counts under the active filters.
  useEffect(() => {
    let cancelled = false;
    fetchThreatArsenalAuthorCounts(searchPaginationInput)
      .then((response) => {
        if (cancelled) return;
        const map: Record<string, number> = {};
        ((response.data ?? []) as AuthorCountRow[]).forEach((row) => {
          map[row.author] = row.count ?? 0;
        });
        setCounts(map);
      })
      .catch(() => {
        if (!cancelled) setCounts({});
      });
    return () => {
      cancelled = true;
    };
  }, [searchPaginationInput]);

  return useMemo(
    () =>
      universe
        .map(row => ({
          value: row.author,
          label: row.author_name ?? row.author,
          type: row.author_type,
          count: counts[row.author] ?? 0,
        }))
        .sort((a, b) => a.label.localeCompare(b.label)),
    [universe, counts],
  );
};

export default useThreatArsenalAuthorFacet;
