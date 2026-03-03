import { Grid } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type SyntheticEvent, useContext, useState } from 'react';
import { useOutletContext } from 'react-router';

import { fetchCatalogConnectors } from '../../../../actions/catalog/catalog-actions';
import { type CatalogConnectorsHelper } from '../../../../actions/catalog/catalog-helper';
import { type CollectorHelper } from '../../../../actions/collectors/collector-helper';
import type { ExecutorHelper } from '../../../../actions/executors/executor-helper';
import { type InjectorHelper } from '../../../../actions/injectors/injector-helper';
import useDialog from '../../../../components/common/dialog/useDialog';
import { useFormatter } from '../../../../components/i18n';
import SearchFilter from '../../../../components/SearchFilter';
import { useHelper } from '../../../../store';
import type {
  CatalogConnector,
  CatalogConnectorOutput,
  CollectorOutput,
  ExecutorOutput,
  InjectorOutput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import useSearchAndFilter from '../../../../utils/SortingFiltering';
import ConnectorCard from '../common/ConnectorCard';
import CreateConnectorInstanceDrawer from '../connector_instance/CreateConnectorInstanceDrawer';
import { ConnectorContext, type ConnectorOutput } from './ConnectorContext';
import { type ConnectorContextLayoutType } from './ConnectorLayout';

const ConnectorList = () => {
  // Standard hooks
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const { isXtmComposerUp } = useOutletContext<ConnectorContextLayoutType>();
  const { t } = useFormatter();
  const { connectorType, apiRequest, routes, normalizeSingle, logoUrl } = useContext(ConnectorContext);

  // Filter and sort hook
  const searchColumns = ['name', 'description'];
  const filtering = useSearchAndFilter(
    '', // Due to normalizeSingle
    'name',
    searchColumns,
  );

  useDataLoader(() => {
    dispatch(fetchCatalogConnectors());
    dispatch(apiRequest.fetchAll());
  });

  // Fetching data - hooks must be called at top level unconditionally
  const { executors } = useHelper((helper: ExecutorHelper) => ({ executors: helper.getExecutorsIncludingPending() }));
  const { injectors } = useHelper((helper: InjectorHelper) => ({ injectors: helper.getInjectorsIncludingPending() }));
  const { collectors } = useHelper((helper: CollectorHelper) => ({ collectors: helper.getCollectorsIncludingPending() }));
  const { catalogConnectors } = useHelper((helper: CatalogConnectorsHelper) => ({ catalogConnectors: helper.getCatalogConnectors() }));

  const [selectedCatalogConnector, setSelectedCatalogConnector] = useState<CatalogConnectorOutput>();
  const [selectedConnector, setSelectedConnector] = useState<ConnectorOutput>();
  const createInstanceDrawer = useDialog();

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

  const canMigrate = (connector: ConnectorOutput) => {
    return connector.isExternal && connector.connectorInstance === null && isXtmComposerUp;
  };

  const onMigrateBtnClick = (e: SyntheticEvent, connector: ConnectorOutput) => {
    e.preventDefault();
    e.stopPropagation();
    setSelectedConnector(connector);
    const catalogConnector = catalogConnectors.find(
      (catalogConnector: CatalogConnectorOutput) => catalogConnector.catalog_connector_id === connector.catalog?.catalog_connector_id,
    );
    setSelectedCatalogConnector(catalogConnector);
    createInstanceDrawer.handleOpen();
  };

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
                connectorLogoUrl: connector?.isExisting ? logoUrl(connector.type) : (connector.catalog?.catalog_connector_logo_url && `/api/images/catalog/connectors/logos/${connector.catalog?.catalog_connector_logo_url}`),
                connectorDescription: connector.catalog?.catalog_connector_short_description,
                lastUpdatedAt: connector.updatedAt,
                isVerified: connector.isVerified,
                connectorUseCases: [],
                isExternal: connector.isExternal,
                connectorCurrentStatus: connector.connectorInstance ? connector.connectorInstance.connector_instance_current_status : null,
              }}
              onMigrateBtnClick={canMigrate(connector) ? e => onMigrateBtnClick(e, connector) : undefined}
              cardActionUrl={routes.detail(connector.id)}
              isNotClickable={connector.catalog === null && connectorType !== 'injector'}
              showStatusOrLastUpdatedAt
            />
          </Grid>
        ))}
        <CreateConnectorInstanceDrawer
          open={createInstanceDrawer.open}
          catalogConnectorId={selectedCatalogConnector ? selectedCatalogConnector.catalog_connector_id : ''}
          catalogConnectorSlug={selectedCatalogConnector ? selectedCatalogConnector.catalog_connector_slug : ''}
          onClose={createInstanceDrawer.handleClose}
          connectorType={selectedCatalogConnector?.catalog_connector_type}
          disabled={!isXtmComposerUp && selectedCatalogConnector?.catalog_connector_manager_supported}
          migrationSource={selectedConnector?.id}
          disabledMessage={t('Deployment of this {catalogType} requires the installation of our Integration Manager.', { catalogType: selectedCatalogConnector ? selectedCatalogConnector.catalog_connector_type.toLowerCase() : '' })}
        />
      </Grid>
    </>
  );
};

export default ConnectorList;
