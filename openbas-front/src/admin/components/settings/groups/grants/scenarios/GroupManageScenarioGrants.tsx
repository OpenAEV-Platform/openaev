import { useState } from 'react';

import { searchScenarios } from '../../../../../../actions/scenarios/scenario-actions';
import { initSorting } from '../../../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../../../components/common/queryable/useQueryableWithLocalStorage';
import type { Scenario, SearchPaginationInput } from '../../../../../../utils/api-types';
import TableData from '../ui/TableData';
import useScenarioGrant from './useScenarioGrant';

const GroupManageScenarioGrants = ({ groupId }: { groupId: string }) => {
  const { configs } = useScenarioGrant(groupId);
  const [scenarios, setScenarios] = useState<Scenario[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(`group-${groupId}-scenarios`, buildSearchPagination({ sorts: initSorting('scenario_updated_at', 'DESC') }));
  const search = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchScenarios(input).finally(() => setLoading(false));
  };

  return (
    <>
      <PaginationComponentV2
        fetch={search}
        searchPaginationInput={searchPaginationInput}
        setContent={setScenarios}
        entityPrefix="scenario"
        queryableHelpers={queryableHelpers}
        disableFilters
      />
      <TableData
        datas={scenarios}
        configs={configs}
        loading={loading}
      />
    </>
  );
};

export default GroupManageScenarioGrants;
