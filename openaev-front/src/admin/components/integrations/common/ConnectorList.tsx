import { Grid } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext } from 'react';

import { type CollectorHelper } from '../../../../actions/collectors/collector-helper';
import type { ExecutorHelper } from '../../../../actions/executors/executor-helper';
import { type InjectorHelper } from '../../../../actions/injectors/injector-helper';
import SearchFilter from '../../../../components/SearchFilter';
import { useHelper } from '../../../../store';
import type { CatalogConnector, CollectorOutput, ExecutorOutput, InjectorOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import useSearchAndFilter from '../../../../utils/SortingFiltering';
import ConnectorCard from '../common/ConnectorCard';
import { ConnectorContext, type ConnectorOutput } from './ConnectorContext';

const ConnectorList = () => {
  // Standard hooks
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const { connectorType, apiRequest, routes, normalizeSingle, logoUrl } = useContext(ConnectorContext);

  // Filter and sort hook
  const searchColumns = ['name', 'description'];
  const filtering = useSearchAndFilter(
    '', // Due to normalizeSingle
    'name',
    searchColumns,
  );

  // Fetching data - hooks must be called at top level unconditionally
  const { executors } = useHelper((helper: ExecutorHelper) => ({ executors: helper.getExecutorsIncludingPending() }));
  const { injectors } = useHelper((helper: InjectorHelper) => ({ injectors: helper.getInjectorsIncludingPending() }));
  const { collectors } = useHelper((helper: CollectorHelper) => ({ collectors: helper.getCollectorsIncludingPending() }));

  // Select the appropriate connectors based on connector type
  const getRawConnectors = (): (CollectorOutput | ExecutorOutput | InjectorOutput)[] => {
    switch (connectorType) {
      case 'executor':
        return executors;
      case 'injector':
        return injectors;
      case 'collector':
        return collectors;
      default:
        return [];
    }
  };

  const rawConnectors = getRawConnectors();
  const connectors = normalizeSingle
    ? rawConnectors.map((c: CollectorOutput | ExecutorOutput | InjectorOutput) => normalizeSingle(c))
    : rawConnectors;

  useDataLoader(() => {
    dispatch(apiRequest.fetchAll());
  });
  const sortedConnectors = filtering.filterAndSort(connectors);

  return (
    <>
      <SearchFilter
        variant="small"
        onChange={filtering.handleSearch}
        keyword={filtering.keyword}
      />
      <div className="clearfix" />
      <Grid container={true} spacing={3} style={{ marginTop: theme.spacing(2) }}>
        {sortedConnectors.map((connector: ConnectorOutput) => (
          <Grid key={connector.id} size={{ xs: 4 }}>
            <ConnectorCard
              connector={{
                connectorName: connector.name,
                connectorType: connectorType.toUpperCase() as CatalogConnector['catalog_connector_type'],
                connectorLogoName: connector.type,
                connectorLogoUrl: connector?.isExisting ? logoUrl(connector.type) : `/api/images/catalog/connectors/logos/${connector.catalog?.catalog_connector_logo_url}`,
                connectorDescription: connector.catalog?.catalog_connector_short_description,
                lastUpdatedAt: connector.updatedAt,
                isVerified: connector.isVerified,
                connectorUseCases: [],
                isExternal: connector.isExternal,
                connectorCurrentStatus: connector.currentStatus,
              }}
              cardActionUrl={routes.detail(connector.id)}
              isNotClickable={connector.catalog === null && connectorType !== 'injector'}
              showStatusOrLastUpdatedAt
            />
          </Grid>
        ))}
      </Grid>
    </>
  );
};

export default ConnectorList;
