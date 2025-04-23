import { CloseRounded } from '@mui/icons-material';
import { IconButton, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchAssetGroup, searchEndpointsFromAssetGroup } from '../../../../actions/asset_groups/assetgroup-action';
import { type AssetGroupsHelper } from '../../../../actions/asset_groups/assetgroup-helper';
import { type UserHelper } from '../../../../actions/helper';
import { type Page } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useHelper } from '../../../../store';
import { type AssetGroup, type Endpoint, type EndpointOutput, type SearchPaginationInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import EndpointPopover from '../endpoints/EndpointPopover';
import EndpointsList from '../endpoints/EndpointsList';
import AssetGroupAddEndpoints from './AssetGroupAddEndpoints';

const useStyles = makeStyles()(theme => ({
  // Drawer Header
  header: {
    backgroundColor: theme.palette.background.nav,
    padding: '20px 20px 20px 60px',
  },
  closeButton: {
    position: 'absolute',
    top: 12,
    left: 5,
    color: 'inherit',
  },
  title: {
    float: 'left',
    marginRight: 20,
  },
  parameters: {
    float: 'right',
    marginTop: -8,
  },
  tags: { float: 'right' },
  search: {
    float: 'right',
    width: 200,
    marginRight: 20,
  },
}));

interface Props {
  assetGroupId: string;
  handleClose: () => void;
  onUpdate?: (result: AssetGroup) => void;
  onRemoveEndpointFromAssetGroup?: (assetId: Endpoint['asset_id']) => void;
}

const AssetGroupManagement: FunctionComponent<Props> = ({
  assetGroupId,
  handleClose,
  onUpdate,
  onRemoveEndpointFromAssetGroup,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const theme = useTheme();
  const dispatch = useAppDispatch();

  // Fetching data
  const { assetGroup, userAdmin } = useHelper((helper: AssetGroupsHelper & UserHelper) => ({
    assetGroup: helper.getAssetGroup(assetGroupId),
    userAdmin: helper.getMe()?.user_admin ?? false,
  }));
  useDataLoader(() => {
    dispatch(fetchAssetGroup(assetGroupId));
  });

  // Pagination
  const [endpoints, setEndpoints] = useState<EndpointOutput[]>([]);
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

  const onRemoveEndpointFromList = (asset: EndpointOutput) => {
    setEndpoints(endpoints.toSpliced(endpoints.findIndex(endpoint => endpoint.asset_id === asset.asset_id), 1));
    if (onRemoveEndpointFromAssetGroup) {
      onRemoveEndpointFromAssetGroup(asset.asset_id);
    }
  };

  const onUpdateList = (result: AssetGroup) => {
    if (onUpdate) {
      onUpdate(result);
    }
    searchEndpointsFromAssetGroup(searchPaginationInput, assetGroupId).then((result: { data: Page<EndpointOutput> }) => {
      const { data } = result;
      setEndpoints(data.content);
      queryableHelpers.paginationHelpers.handleChangeTotalElements(data.totalElements);
    });
  };

  return (
    <>
      <div className={classes.header}>
        <IconButton
          aria-label="Close"
          className={classes.closeButton}
          onClick={handleClose}
          size="large"
          color="primary"
        >
          <CloseRounded fontSize="small" color="primary" />
        </IconButton>
        <Typography variant="h6" classes={{ root: classes.title }}>
          {assetGroup?.asset_group_name}
        </Typography>
        <div className="clearfix" />
      </div>
      <div style={{ padding: theme.spacing(1) }}>
        <PaginationComponentV2
          fetch={((searchPaginationInput: SearchPaginationInput) => load(searchPaginationInput))}
          searchPaginationInput={searchPaginationInput}
          setContent={setEndpoints}
          entityPrefix="endpoint"
          availableFilterNames={availableFilterNames}
          queryableHelpers={queryableHelpers}
        />
      </div>

      <EndpointsList
        endpoints={endpoints}
        loading={loading}
        actions={userAdmin
          ? (
            // @ts-expect-error: Endpoint property handle by EndpointsList
              <EndpointPopover
                inline
                assetGroupId={assetGroup?.asset_group_id}
                assetGroupEndpointIds={assetGroup?.asset_group_assets ?? []}
                onRemoveEndpointFromAssetGroup={onRemoveEndpointFromList}
              />
            )
          : <span> &nbsp; </span>}
      />
      {userAdmin
        && (
          <AssetGroupAddEndpoints
            assetGroupId={assetGroup?.asset_group_id}
            assetGroupEndpointIds={assetGroup?.asset_group_assets ?? []}
            onUpdate={onUpdateList}
          />
        )}
    </>
  );
};

export default AssetGroupManagement;
