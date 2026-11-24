import { type AttackPatternHelper } from '../../../../actions/attack_patterns/attackpattern-helper';
import { fetchAttackPatterns } from '../../../../actions/AttackPattern';
import type { DomainHelper } from '../../../../actions/domains/domain-helper';
import { fetchThreatArsenalAuthorCounts } from '../../../../actions/threat_arsenals/threatArsenal-actions';
import { useAuthorOptions } from '../../../../components/common/facets/ContractFacets';
import { type QueryableHelpers } from '../../../../components/common/queryable/QueryableHelpers';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useHelper } from '../../../../store';
import {
  type AttackPattern,
  type Domain,
  type InjectorContractAuthorCountOutput,
  type SearchPaginationInput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import useDomainIconFilter from '../../common/domains/useDomainIconFilter';
import useThreatArsenalFacetCounts from '../useThreatArsenalFacetCounts';

const loadAuthors = () => fetchThreatArsenalAuthorCounts(buildSearchPagination({}))
  .then(({ data }: { data: InjectorContractAuthorCountOutput[] }) => data.map(row => ({
    value: row.author ?? '',
    label: row.author_name ?? row.author ?? '',
    type: row.author_type,
  })));

// What the sidebar and the filter bar of the Active tab offer to filter on.
const useActiveFacets = (searchPaginationInput: SearchPaginationInput, queryableHelpers: QueryableHelpers) => {
  const dispatch = useAppDispatch();
  useDataLoader(() => {
    dispatch(fetchAttackPatterns());
  });
  const attackPatterns: AttackPattern[] = useHelper((helper: AttackPatternHelper) => helper.getAttackPatterns());
  const domainOptions: Domain[] = useHelper((helper: DomainHelper) => helper.getDomains());
  const { iconBarOrderedDomains } = useDomainIconFilter({
    domainOptions,
    searchPaginationInput,
    queryableHelpers,
    apiPrefix: 'threat_arsenals',
    domainFilterKey: 'action_domains',
  });
  const facetCounts = useThreatArsenalFacetCounts(searchPaginationInput);
  const authorOptions = useAuthorOptions(loadAuthors);

  return {
    attackPatterns,
    domainOptions,
    iconBarOrderedDomains,
    facetCounts,
    authorOptions,
  };
};

export default useActiveFacets;
