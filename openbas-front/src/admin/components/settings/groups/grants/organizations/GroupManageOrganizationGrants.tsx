import { useState } from 'react';

import { searchOrganizations } from '../../../../../../actions/organizations/organization-actions';
import { initSorting } from '../../../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../../../components/common/queryable/useQueryableWithLocalStorage';
import type { Organization, SearchPaginationInput } from '../../../../../../utils/api-types';
import TableData from '../ui/TableData';
import useOrganizationGrant from './useOrganizationGrant';

const GroupManageOrganizationGrants = ({ groupId }: { groupId: string }) => {
  const { configs } = useOrganizationGrant(groupId);
  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(`group-${groupId}-organizations`, buildSearchPagination({ sorts: initSorting('organization_updated_at', 'DESC') }));
  const search = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchOrganizations(input).finally(() => setLoading(false));
  };

  return (
    <>
      <PaginationComponentV2
        fetch={search}
        searchPaginationInput={searchPaginationInput}
        setContent={setOrganizations}
        entityPrefix="organization"
        queryableHelpers={queryableHelpers}
        disableFilters
      />
      <TableData
        datas={organizations}
        configs={configs}
        loading={loading}
      />
    </>
  );
};

export default GroupManageOrganizationGrants;
