import { useState } from 'react';

import { searchThreatArsenalActions } from '../../../../../../../actions/threat_arsenals/threatArsenal-actions';
import { initSorting } from '../../../../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../../../../components/common/queryable/useQueryableWithLocalStorage';
import type { SearchPaginationInput, ThreatArsenalAction } from '../../../../../../../utils/api-types';
import TableData from '../ui/TableData';
import useThreatArsenalGrant from './useThreatArsenalGrant';

interface GroupManagePayloadGrantsProps {
  groupId: string;
  onGrantChange: () => void;
}

const GroupManageThreatArsenalGrants = ({ groupId, onGrantChange }: GroupManagePayloadGrantsProps) => {
  const { configs } = useThreatArsenalGrant({
    groupId,
    onGrantChange,
  });

  const [threatArsenalActions, setThreatArsenalActions] = useState<ThreatArsenalAction[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(`group-${groupId}-threat-arsenal`, buildSearchPagination({ sorts: initSorting('injector_contract_updated_at', 'DESC') }));
  const search = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchThreatArsenalActions(input).finally(() => setLoading(false));
  };

  return (
    <>
      <PaginationComponentV2
        fetch={search}
        searchPaginationInput={searchPaginationInput}
        setContent={setThreatArsenalActions}
        entityPrefix="payload"
        queryableHelpers={queryableHelpers}
        disableFilters
      />
      <TableData
        datas={threatArsenalActions}
        configs={configs}
        loading={loading}
      />
    </>
  );
};

export default GroupManageThreatArsenalGrants;
