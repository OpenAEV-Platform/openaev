import { useState } from 'react';

import { searchPayloads } from '../../../../../../actions/payloads/payload-actions';
import { initSorting } from '../../../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../../../components/common/queryable/useQueryableWithLocalStorage';
import type { Payload, SearchPaginationInput } from '../../../../../../utils/api-types';
import TableData from '../ui/TableData';
import usePayloadGrant from './usePayloadGrant';

interface GroupManagePayloadGrantsProps {
  groupId: string;
  onGrantChange: () => void;
}

const GroupManagePayloadGrants = ({ groupId, onGrantChange }: GroupManagePayloadGrantsProps) => {
  const { configs } = usePayloadGrant({
    groupId,
    onGrantChange,
  });
  const [payloads, setPayloads] = useState<Payload[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(`group-${groupId}-payloads`, buildSearchPagination({ sorts: initSorting('payload_updated_at', 'DESC') }));
  const search = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchPayloads(input).finally(() => setLoading(false));
  };

  return (
    <>
      <PaginationComponentV2
        fetch={search}
        searchPaginationInput={searchPaginationInput}
        setContent={setPayloads}
        entityPrefix="payload"
        queryableHelpers={queryableHelpers}
        disableFilters
      />
      <TableData
        datas={payloads}
        configs={configs}
        loading={loading}
      />
    </>
  );
};

export default GroupManagePayloadGrants;
