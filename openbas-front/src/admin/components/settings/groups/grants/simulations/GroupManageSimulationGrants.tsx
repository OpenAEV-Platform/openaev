import { useState } from 'react';

import { searchExercises } from '../../../../../../actions/Exercise';
import { initSorting } from '../../../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../../../components/common/queryable/useQueryableWithLocalStorage';
import type { Exercise, SearchPaginationInput } from '../../../../../../utils/api-types';
import TableData from '../ui/TableData';
import useSimulationGrant from './useSimulationGrant';

interface GGroupManageSimulationGrantsProps {
  groupId: string;
  onGrantChange: () => void;
}

const GroupManageSimulationGrants = ({ groupId, onGrantChange }: GGroupManageSimulationGrantsProps) => {
  const { configs } = useSimulationGrant({
    groupId,
    onGrantChange,
  });
  const [simulations, setSimulations] = useState<Exercise[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(`group-${groupId}-simulations`, buildSearchPagination({ sorts: initSorting('exercise_updated_at', 'DESC') }));
  const search = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchExercises(input).finally(() => setLoading(false));
  };

  return (
    <>
      <PaginationComponentV2
        fetch={search}
        searchPaginationInput={searchPaginationInput}
        setContent={setSimulations}
        entityPrefix="exercise"
        queryableHelpers={queryableHelpers}
        disableFilters
      />
      <TableData
        datas={simulations}
        configs={configs}
        loading={loading}
      />
    </>
  );
};

export default GroupManageSimulationGrants;
