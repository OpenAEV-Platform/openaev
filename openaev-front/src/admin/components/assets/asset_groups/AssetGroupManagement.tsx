import { type FunctionComponent, useState } from 'react';

import { fetchAssetGroup, searchEndpointsFromAssetGroup } from '../../../../actions/asset_groups/assetgroup-action';
import { type AssetGroupsHelper } from '../../../../actions/asset_groups/assetgroup-helper';
import { type UserHelper } from '../../../../actions/helper';
import { type Page } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useHelper } from '../../../../store';
import { type AssetGroup, type AssetOutput, type Endpoint, type SearchPaginationInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import AssetPopover from '../endpoints/AssetPopover';
import AssetsList from '../endpoints/AssetsList';
import AssetGroupAddEndpoints from './AssetGroupAddEndpoints';

interface Props {
  assetGroupId: string;
  onUpdate?: (result: AssetGroup) => void;
  onRemoveEndpointFromAssetGroup?: (assetId: Endpoint['asset_id']) => void;
}

// Body of the asset group "Manage assets" panel; the design-system Drawer
// (title + close on the right) is provided by the caller (AssetGroupPopover).
const AssetGroupManagement: FunctionComponent<Props> = ({
  assetGroupId,
  onUpdate,
  onRemoveEndpointFromAssetGroup,
}) => {
  // Standard hooks
  const dispatch = useAppDispatch();

  // Fetching data
  const { assetGroup } = useHelper((helper: AssetGroupsHelper & UserHelper) => ({ assetGroup: helper.getAssetGroup(assetGroupId) }));
  useDataLoader(() => {
    dispatch(fetchAssetGroup(assetGroupId));
  });

  // Pagination
  const [endpoints, setEndpoints] = useState<AssetOutput[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const load = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchEndpointsFromAssetGroup(input, assetGroupId).finally(() => setLoading(false));
  };

  const availableFilterNames = [
    'endpoint_platform',
    'endpoint_arch',
    'asset_tags',
  ];
  const { queryableHelpers, searchPaginationInput } = useQueryable(buildSearchPagination({}));

  const onRemoveEndpointFromList = (asset: AssetOutput) => {
    setEndpoints(endpoints.toSpliced(endpoints.findIndex(endpoint => endpoint.asset_id === asset.asset_id), 1));
    if (onRemoveEndpointFromAssetGroup) {
      onRemoveEndpointFromAssetGroup(asset.asset_id);
    }
  };

  const onUpdateList = (result: AssetGroup) => {
    if (onUpdate) {
      onUpdate(result);
    }
    searchEndpointsFromAssetGroup(searchPaginationInput, assetGroupId).then((result: { data: Page<AssetOutput> }) => {
      const { data } = result;
      setEndpoints(data.content);
      queryableHelpers.paginationHelpers.handleChangeTotalElements(data.totalElements);
    });
  };

  return (
    <>
      <PaginationComponentV2
        fetch={((searchPaginationInput: SearchPaginationInput) => load(searchPaginationInput))}
        searchPaginationInput={searchPaginationInput}
        setContent={setEndpoints}
        entityPrefix="endpoint"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        topBarButtons={(
          <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSETS}>
            <AssetGroupAddEndpoints
              assetGroupId={assetGroup?.asset_group_id}
              assetGroupEndpointIds={assetGroup?.asset_group_assets ?? []}
              onUpdate={onUpdateList}
            />
          </Can>
        )}
      />

      <AssetsList
        endpoints={endpoints}
        loading={loading}
        withHeaders
        sortHelpers={queryableHelpers.sortHelpers}
        renderActions={(asset: AssetOutput) => (
          <AssetPopover
            inline
            endpoint={asset}
            assetGroupId={assetGroup?.asset_group_id}
            assetGroupEndpointIds={assetGroup?.asset_group_assets ?? []}
            onRemoveEndpointFromAssetGroup={onRemoveEndpointFromList}
          />
        )}
      />
    </>
  );
};

export default AssetGroupManagement;
