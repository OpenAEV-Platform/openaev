import { OnlinePredictionOutlined, SmartButtonOutlined, TerminalOutlined } from '@mui/icons-material';
import { Chip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type SyntheticEvent, useMemo, useState } from 'react';
import { useOutletContext } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { type CatalogConnectorOutput } from '../../../../utils/api-types';
import CreateConnectorInstanceDrawer from '../connector_instance/CreateConnectorInstanceDrawer';
import {
  type CatalogFacetFilters,
  type CatalogSort,
  CONNECTOR_TYPE_ORDER,
  EMPTY_FACET_FILTERS,
  type FacetGroupId,
  filterConnectors,
  hasActiveFacetFilters,
  sortConnectors,
} from './catalog-facets';
import CatalogActiveFilters from './CatalogActiveFilters';
import CatalogConnectorCard from './CatalogConnectorCard';
import CatalogEmptyState from './CatalogEmptyState';
import CatalogHero from './CatalogHero';
import { type CatalogContextType } from './CatalogLayout';
import CatalogSidebar from './CatalogSidebar';
import CatalogToolbar from './CatalogToolbar';

const Catalog = () => {
  // Standard hooks
  const theme = useTheme();
  const { t } = useFormatter();
  const { catalogConnectors, isXtmComposerUp } = useOutletContext<CatalogContextType>();

  const [filters, setFilters] = useState<CatalogFacetFilters>(EMPTY_FACET_FILTERS);
  const [keyword, setKeyword] = useState('');
  const [sort, setSort] = useState<CatalogSort>('name_asc');
  // SearchFilter is uncontrolled; bumping this key remounts it to clear its value.
  const [searchResetKey, setSearchResetKey] = useState(0);

  const [selectedConnector, setSelectedConnector] = useState<CatalogConnectorOutput>();
  const [openCreateConnectorInstanceDrawer, setOpenCreateConnectorInstanceDrawer] = useState(false);
  const onOpenCreateConnectorInstanceDrawer = () => setOpenCreateConnectorInstanceDrawer(true);
  const onCloseCreateConnectorInstanceDrawer = () => setOpenCreateConnectorInstanceDrawer(false);

  const onDeployBtnClick = (e: SyntheticEvent, catalogConnector: CatalogConnectorOutput) => {
    e.preventDefault();
    e.stopPropagation();
    setSelectedConnector(catalogConnector);
    onOpenCreateConnectorInstanceDrawer();
  };

  const onToggleFacet = (groupId: FacetGroupId, value: string) => {
    setFilters(prev => ({
      ...prev,
      [groupId]: prev[groupId].includes(value)
        ? prev[groupId].filter(v => v !== value)
        : [...prev[groupId], value],
    }));
  };

  const onClearFacets = () => setFilters(EMPTY_FACET_FILTERS);

  const onResetAll = () => {
    onClearFacets();
    setKeyword('');
    setSearchResetKey(prev => prev + 1);
  };

  const filteredConnectors = useMemo(
    () => sortConnectors(filterConnectors(catalogConnectors, filters, keyword), sort),
    [catalogConnectors, filters, keyword, sort],
  );

  const sections = useMemo(() => {
    const sectionMeta = {
      COLLECTOR: {
        label: t('Collectors'),
        icon: OnlinePredictionOutlined,
      },
      INJECTOR: {
        label: t('Injectors'),
        icon: SmartButtonOutlined,
      },
      EXECUTOR: {
        label: t('Executors'),
        icon: TerminalOutlined,
      },
    };
    return CONNECTOR_TYPE_ORDER
      .map(type => ({
        type,
        label: sectionMeta[type].label,
        icon: sectionMeta[type].icon,
        connectors: filteredConnectors.filter(c => c.catalog_connector_type === type),
      }))
      .filter(section => section.connectors.length > 0);
  }, [filteredConnectors, t]);

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(2),
    }}
    >
      <CatalogHero connectors={catalogConnectors} />
      <div style={{
        display: 'flex',
        gap: theme.spacing(3),
        alignItems: 'flex-start',
      }}
      >
        <CatalogSidebar
          connectors={catalogConnectors}
          filters={filters}
          keyword={keyword}
          onToggleFacet={onToggleFacet}
          onClearAll={onClearFacets}
        />
        <main style={{
          flex: 1,
          minWidth: 0,
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
        }}
        >
          <CatalogToolbar
            keyword={keyword}
            onSearch={value => setKeyword(value ?? '')}
            searchResetKey={searchResetKey}
            sort={sort}
            onSortChange={setSort}
            resultCount={filteredConnectors.length}
          />
          <CatalogActiveFilters
            filters={filters}
            onToggleFacet={onToggleFacet}
            onClearAll={onClearFacets}
          />
          {sections.length === 0 && (keyword !== '' || hasActiveFacetFilters(filters)) && (
            <CatalogEmptyState onResetFilters={onResetAll} />
          )}
          {sections.map((section) => {
            const SectionIcon = section.icon;
            return (
              <section
                key={section.type}
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: theme.spacing(1.5),
                }}
              >
                <header style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: theme.spacing(1),
                }}
                >
                  <SectionIcon sx={{
                    fontSize: 18,
                    color: 'primary.main',
                  }}
                  />
                  <Typography
                    component="h2"
                    sx={{
                      fontFamily: '"Geologica", sans-serif',
                      fontWeight: 600,
                      fontSize: 13,
                      textTransform: 'uppercase',
                      letterSpacing: '0.08em',
                      margin: 0,
                    }}
                  >
                    {section.label}
                  </Typography>
                  <Chip
                    size="small"
                    variant="outlined"
                    sx={{
                      borderRadius: 1,
                      height: 20,
                      fontSize: 11,
                    }}
                    label={section.connectors.length}
                  />
                  <div style={{
                    flex: 1,
                    borderBottom: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
                  }}
                  />
                </header>
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
                  gap: theme.spacing(2),
                }}
                >
                  {section.connectors.map(connector => (
                    <CatalogConnectorCard
                      key={connector.catalog_connector_id}
                      connector={connector}
                      onDeployBtnClick={e => onDeployBtnClick(e, connector)}
                    />
                  ))}
                </div>
              </section>
            );
          })}
        </main>
      </div>
      <CreateConnectorInstanceDrawer
        open={openCreateConnectorInstanceDrawer}
        catalogConnectorId={selectedConnector ? selectedConnector.catalog_connector_id : ''}
        catalogConnectorSlug={selectedConnector ? selectedConnector.catalog_connector_slug : ''}
        onClose={onCloseCreateConnectorInstanceDrawer}
        connectorType={selectedConnector?.catalog_connector_type}
        disabled={!isXtmComposerUp && selectedConnector?.catalog_connector_manager_supported}
        disabledMessage={t('Deployment of this {catalogType} requires the installation of our Integration Manager.', { catalogType: selectedConnector ? selectedConnector.catalog_connector_type.toLowerCase() : '' })}
      />
    </div>
  );
};

export default Catalog;
