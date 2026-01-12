import { DevicesOtherOutlined } from '@mui/icons-material';
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { normalize } from 'normalizr';
import { type FunctionComponent, useContext, useEffect, useMemo, useState } from 'react';

import { arrayOfEndpoints } from '../../../../actions/assets/asset-schema';
import { findEndpoints, searchEndpoints } from '../../../../actions/assets/endpoint-actions';
import { fetchExecutors } from '../../../../actions/executors/executor-action';
import type { ExecutorHelper } from '../../../../actions/executors/executor-helper';
import { buildFilter } from '../../../../components/common/queryable/filter/FilterUtils';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import SelectList, { type SelectListElements } from '../../../../components/common/SelectList';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import PlatformIcon from '../../../../components/PlatformIcon';
import * as Constants from '../../../../constants/ActionTypes';
import { useHelper } from '../../../../store';
import { type Endpoint, type EndpointOutput, type FilterGroup } from '../../../../utils/api-types';
import { getActiveMsgTooltip, getExecutorsCount } from '../../../../utils/endpoints/utils';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import AssetStatus from '../AssetStatus';

interface Props {
  initialState: string[];
  open: boolean;
  onClose: () => void;
  onSubmit: (endpointIds: string[]) => void;
  title: string;
  platforms?: string[];
  payloadType?: string;
  payloadArch?: string;
}

const EndpointsDialogAdding: FunctionComponent<Props> = ({
  initialState = [],
  open,
  onClose,
  onSubmit,
  title,
  platforms,
  payloadArch,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [endpointValues, setEndpointValues] = useState<(Endpoint | EndpointOutput)[]>([]);
  const { executorsMap } = useHelper((helper: ExecutorHelper) => ({ executorsMap: helper.getExecutorsMap() }));

  useDataLoader(() => {
    if (ability.can(ACTIONS.ACCESS, SUBJECTS.ASSETS)) {
      dispatch(fetchExecutors());
    }
  });

  useEffect(() => {
    if (open) {
      findEndpoints(initialState).then(result => setEndpointValues(result.data));
    }
  }, [open, initialState]);

  const addEndpoint = (_endpointId: string, endpoint: EndpointOutput) => {
    setEndpointValues([...endpointValues, endpoint]);
  };
  const removeEndpoint = (endpointId: string) => {
    setEndpointValues(endpointValues.filter(v => v.asset_id !== endpointId));
  };

  // Dialog
  const handleClose = () => {
    setEndpointValues([]);
    onClose();
  };

  const handleSubmit = () => {
    dispatch({
      type: Constants.DATA_FETCH_SUCCESS,
      payload: normalize(endpointValues, arrayOfEndpoints),
    });
    onSubmit(endpointValues.map(v => v.asset_id));
    handleClose();
  };

  // Headers
  const elements: SelectListElements<EndpointOutput> = useMemo(() => ({
    icon: { value: () => <DevicesOtherOutlined color="primary" /> },
    headers: [
      {
        field: 'asset_name',
        value: (endpoint: EndpointOutput) => endpoint.asset_name,
        width: 35,
      },
      {
        field: 'endpoint_active',
        value: (endpoint: EndpointOutput) => {
          const status = getActiveMsgTooltip(endpoint.asset_agents.map(a => a.agent_active ?? false), t('Active'), t('Inactive'), t('Agentless'));
          return (
            <Tooltip title={status.activeMsgTooltip}>
              <span>
                <AssetStatus variant="list" status={status.status} />
              </span>
            </Tooltip>
          );
        },
        width: 25,
      },
      {
        field: 'endpoint_platform',
        value: (endpoint: EndpointOutput) => (
          <div style={{
            display: 'flex',
            alignItems: 'center',
          }}
          >
            <PlatformIcon platform={endpoint.endpoint_platform} width={20} marginRight={theme.spacing(2)} />
          </div>
        ),
        width: 10,
      },
      {
        field: 'endpoint_arch',
        value: (endpoint: EndpointOutput) => endpoint.endpoint_arch,
        width: 20,
      },
      {
        field: 'endpoint_agents_executor',
        value: (endpoint: EndpointOutput) => {
          if (endpoint.asset_agents.length > 0) {
            const groupedExecutors = getExecutorsCount(endpoint, executorsMap);
            return (
              <>
                {
                  Object.keys(groupedExecutors).map((executorType) => {
                    const executorsOfType = groupedExecutors[executorType];
                    const count = executorsOfType.length;
                    const base = executorsOfType[0];

                    if (count > 0) {
                      return (
                        <Tooltip
                          key={executorType}
                          title={`${base.executor_name} : ${count}`}
                          arrow
                        >
                          <div style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                          }}
                          >
                            <img
                              src={`/api/images/executors/icons/${executorType}`}
                              alt={executorType}
                              style={{
                                width: 20,
                                height: 20,
                                borderRadius: 4,
                                marginRight: 10,
                              }}
                            />
                          </div>
                        </Tooltip>
                      );
                    } else {
                      return t('Unknown');
                    }
                  })
                }
              </>
            );
          } else {
            return <span>-</span>;
          }
        },
        width: 10,
      },
      {
        field: 'asset_tags',
        value: (endpoint: EndpointOutput) => <ItemTags variant="reduced-view" tags={endpoint.asset_tags} />,
        width: 20,
      },
    ],
  }), [executorsMap]);

  // Pagination
  const [endpoints, setEndpoints] = useState<EndpointOutput[]>([]);

  const availableFilterNames = [
    'asset_tags',
    'endpoint_platform',
    'endpoint_arch',
  ];
  const quickFilter: FilterGroup = {
    mode: 'and',
    filters: [
      buildFilter('endpoint_platform', platforms ?? [], 'contains'),
    ],
  };
  // only add an architecture filter if the payload is not compatible with all archs
  if (quickFilter.filters && payloadArch && payloadArch !== 'ALL_ARCHITECTURES') {
    quickFilter.filters?.push(buildFilter('endpoint_arch', [payloadArch], 'contains'));
  }
  const { queryableHelpers, searchPaginationInput } = useQueryable(buildSearchPagination({ filterGroup: quickFilter }));

  const paginationComponent = (
    <PaginationComponentV2
      fetch={searchEndpoints}
      searchPaginationInput={searchPaginationInput}
      setContent={setEndpoints}
      setLoading={setIsLoading}
      entityPrefix="endpoint"
      availableFilterNames={availableFilterNames}
      queryableHelpers={queryableHelpers}
    />
  );

  return (
    <Dialog
      open={open}
      slots={{ transition: Transition }}
      onClose={handleClose}
      fullWidth
      maxWidth="lg"
      slotProps={{
        paper: {
          elevation: 1,
          sx: {
            minHeight: 580,
            maxHeight: 580,
          },
        },
      }}
    >
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Box sx={{ marginTop: 2 }}>
          <SelectList<EndpointOutput, Endpoint>
            values={endpoints}
            selectedValues={endpointValues}
            isLoadingValues={isLoading}
            elements={elements}
            onSelect={addEndpoint}
            onDelete={removeEndpoint}
            paginationComponent={paginationComponent}
            getId={element => element.asset_id}
            getName={element => element.asset_name}
          />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>{t('Cancel')}</Button>
        {!isLoading && (
          <Button color="secondary" onClick={handleSubmit}>
            {t('Update')}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default EndpointsDialogAdding;
