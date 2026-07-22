import { useEffect, useState } from 'react';
import { useLocation } from 'react-router';

import { type Page } from '../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { type InjectTestStatusOutput, type SearchPaginationInput } from '../../../utils/api-types';

type SearchInjectTestsFn = (
  contextId: string,
  searchPaginationInput: SearchPaginationInput,
) => Promise<{ data: Page<InjectTestStatusOutput> }>;

// Whether the scenario / simulation Tests tab should be shown at all: tests
// only exist for testable injects (email / SMS) that have actually been
// tested, so the tab is irrelevant for everything else. Probes for at least
// one test record (cheapest possible search: single-element page). Being on
// the tests screen forces the tab visible so the tab bar stays consistent
// right after a first test is launched from the injects list.
const useHasInjectTests = (searchInjectTests: SearchInjectTestsFn, contextId: string): boolean => {
  const location = useLocation();
  const onTestsScreen = /\/tests(\/|$)/.test(location.pathname);
  const [hasTests, setHasTests] = useState(false);

  useEffect(() => {
    let cancelled = false;
    searchInjectTests(contextId, buildSearchPagination({ size: 1 }))
      .then((result) => {
        if (!cancelled) setHasTests(result.data.totalElements > 0);
      })
      // On failure (e.g. missing permission) just keep the tab hidden.
      .catch(() => {});
    return () => {
      cancelled = true;
    };
    // Re-probe when entering/leaving the tests screen so the tab appears
    // permanently once the first test result actually exists.
  }, [searchInjectTests, contextId, onTestsScreen]);

  return hasTests || onTestsScreen;
};

export default useHasInjectTests;
