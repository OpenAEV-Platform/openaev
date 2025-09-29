import { useState } from 'react';

import { searchAtomicTestings } from '../../../../../../actions/atomic_testings/atomic-testing-actions';
import { initSorting } from '../../../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../../../components/common/queryable/useQueryableWithLocalStorage';
import type { InjectResultOutput, SearchPaginationInput } from '../../../../../../utils/api-types';
import TableData from '../ui/TableData';
import useAtomicTestingGrant from './useAtomicTestingGrant';

interface GroupManageAtomicTestingGrantsProps {
  groupId: string;
  onGrantChange: () => void;
}

const GroupManageAtomicTestingGrants = ({ groupId, onGrantChange }: GroupManageAtomicTestingGrantsProps) => {
  const { configs } = useAtomicTestingGrant({
    groupId,
    onGrantChange,
  });
  const [injects, setInjects] = useState<InjectResultOutput[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(`group-${groupId}-injects`, buildSearchPagination({ sorts: initSorting('inject_updated_at', 'DESC') }));
  const search = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchAtomicTestings(input).finally(() => setLoading(false));
  };

  return (
    <>
      <PaginationComponentV2
        fetch={search}
        searchPaginationInput={searchPaginationInput}
        setContent={setInjects}
        entityPrefix="inject"
        queryableHelpers={queryableHelpers}
        disableFilters
      />
      <TableData
        datas={injects}
        configs={configs}
        loading={loading}
      />
    </>
  );
};

export default GroupManageAtomicTestingGrants;
