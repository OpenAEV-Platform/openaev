import { DevicesOtherOutlined } from '@mui/icons-material';
import { Tooltip } from '@mui/material';
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
import SelectListPicker, { type SelectListPickerElements } from '../../../../components/common/SelectListPicker';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import PlatformIcon from '../../../../components/PlatformIcon';
import * as Constants from '../../../../constants/ActionTypes';
import { useHelper } from '../../../../store';
import { type Endpoint, type EndpointOutput, type FilterGroup } from '../../../../utils/api-types';
import { getActiveMsgTooltip, getExecutorsCount } from '../../../../utils/endpoints/utils';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import AssetStatus from '../AssetStatus';

interface Props {
  initialState: string[];
  open: boolean;
  onClose: () => void;
  onSubmit: (endpointIds: string[]) => void;
  title: string;
  platforms?: string[];
  payloadArch?: string;
}

// Always rendered as an inline dialog: every context that picks endpoints
// (inject form, asset group management, payload drawers) is itself an overlay,
// and the design system never stacks a drawer over a drawer.
const EndpointsPicker: FunctionComponent<Props> = ({
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

  const selectedIds = useMemo(() => endpointValues.map(v => v.asset_id), [endpointValues]);

  const toggleEndpoint = (endpointId: string, endpoint: EndpointOutput) => {
    if (selectedIds.includes(endpointId)) {
      setEndpointValues(endpointValues.filter(v => v.asset_id !== endpointId));
    } else {
      setEndpointValues([...endpointValues, endpoint]);
    }
  };

  // Drawer
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
  const elements: SelectListPickerElements<EndpointOutput> = useMemo(() => ({
    icon: { value: () => <DevicesOtherOutlined color="primary" /> },
    headers: [
      // Widths must total 100: each cell renders as `width: N%` in a flex row,
      // so any excess pushes the last column (tags) out of the row.
      {
        field: 'asset_name',
        label: 'Name',
        isSortable: true,
        value: (endpoint: EndpointOutput) => endpoint.asset_name,
        width: 30,
      },
      {
        field: 'endpoint_active',
        label: 'Status',
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
        width: 15,
      },
      {
        field: 'endpoint_platform',
        label: 'Platform',
        isSortable: true,
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
        label: 'Architecture',
        isSortable: true,
        value: (endpoint: EndpointOutput) => endpoint.endpoint_arch,
        width: 15,
      },
      {
        field: 'endpoint_agents_executor',
        label: 'Executors',
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
                              src={buildTenantApiPath(`/api/images/executors/icons/${executorType}`)}
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
        label: 'Tags',
        // Single chip + "+N" counter so the fixed-height cell never wraps.
        value: (endpoint: EndpointOutput) => <ItemTags variant="list" limit={1} tags={endpoint.asset_tags} />,
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
  // endpoint_platform / endpoint_arch are single-value enums: their only valid
  // operator is 'eq' (the filter UI exposes eq/not_eq/empty/not_empty - 'contains'
  // is not selectable). Only add each scoping filter when the caller actually
  // restricts the selection (e.g. an inject payload); the asset-group flow passes
  // no platforms, so it must open with no predefined filter at all.
  const quickFilter: FilterGroup = {
    mode: 'and',
    filters: [],
  };
  if (platforms && platforms.length > 0) {
    quickFilter.filters?.push(buildFilter('endpoint_platform', platforms, 'eq'));
  }
  // only add an architecture filter if the payload is not compatible with all archs
  if (payloadArch && payloadArch !== 'ALL_ARCHITECTURES') {
    quickFilter.filters?.push(buildFilter('endpoint_arch', [payloadArch], 'eq'));
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
    <SelectListPicker<EndpointOutput>
      open={open}
      onClose={handleClose}
      onSubmit={handleSubmit}
      title={title}
      inline
      headerComponent={paginationComponent}
      values={endpoints}
      elements={elements}
      sortHelpers={queryableHelpers.sortHelpers}
      selectedIds={selectedIds}
      onToggle={toggleEndpoint}
      getId={element => element.asset_id}
      isLoading={isLoading}
    />
  );
};

export default EndpointsPicker;
