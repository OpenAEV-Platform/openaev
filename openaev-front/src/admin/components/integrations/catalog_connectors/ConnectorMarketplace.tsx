import { OnlinePredictionOutlined, SmartButtonOutlined, TerminalOutlined } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ReactNode, useMemo, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import {
  CONNECTOR_TYPE_ORDER,
  type ConnectorItem,
  filterConnectors,
  hasActiveFacetFilters,
  sortConnectors,
} from './catalog-facets';
import CatalogActiveFilters from './CatalogActiveFilters';
import CatalogConnectorCard from './CatalogConnectorCard';
import CatalogEmptyState from './CatalogEmptyState';
import CatalogSidebar from './CatalogSidebar';
import CatalogToolbar from './CatalogToolbar';
import useCatalogFilters from './useCatalogFilters';

interface Props {
  items: ConnectorItem[];
  /** Right side of each card footer (deploy button, instance status...). */
  renderFooterAction?: (item: ConnectorItem) => ReactNode;
  searchPlaceholder?: string;
}

/**
 * The shared faceted marketplace browser: sticky filter sidebar, toolbar
 * (search / sort / result count), active-filter chips and type-sectioned
 * connector cards. Used by both the Available (catalog) and Deployed tabs.
 * Filter state is persisted in the URL query string (per-tab, since each tab
 * has its own URL).
 */
const ConnectorMarketplace = ({ items, renderFooterAction, searchPlaceholder }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const { filters, keyword, setKeyword, sort, setSort, onToggleFacet, onClearFacets } = useCatalogFilters();
  // SearchFilter is uncontrolled; bumping this key remounts it to clear its value.
  const [searchResetKey, setSearchResetKey] = useState(0);

  const onResetAll = () => {
    onClearFacets();
    setKeyword('');
    setSearchResetKey(prev => prev + 1);
  };

  const filteredItems = useMemo(
    () => sortConnectors(filterConnectors(items, filters, keyword), sort),
    [items, filters, keyword, sort],
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
        items: filteredItems.filter(c => c.type === type),
      }))
      .filter(section => section.items.length > 0);
  }, [filteredItems, t]);

  return (
    <div style={{
      display: 'flex',
      gap: theme.spacing(3),
      alignItems: 'flex-start',
    }}
    >
      <CatalogSidebar
        connectors={items}
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
          resultCount={filteredItems.length}
          searchPlaceholder={searchPlaceholder}
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
                gap: theme.spacing(1.25),
              }}
              >
                <div style={{
                  width: 28,
                  height: 28,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  borderRadius: theme.shape.borderRadius,
                  border: `1px solid ${alpha(theme.palette.primary.main, 0.2)}`,
                  backgroundColor: alpha(theme.palette.primary.main, 0.1),
                }}
                >
                  <SectionIcon sx={{
                    fontSize: 16,
                    color: 'primary.main',
                  }}
                  />
                </div>
                <Typography
                  component="h2"
                  sx={{
                    fontFamily: theme.typography.h1.fontFamily,
                    fontSize: 16,
                    fontWeight: 600,
                    margin: 0,
                  }}
                >
                  {section.label}
                </Typography>
                <span style={{
                  padding: '1px 6px',
                  borderRadius: 2,
                  backgroundColor: alpha(theme.palette.text.primary, 0.06),
                  fontSize: 11,
                  fontWeight: 500,
                  fontVariantNumeric: 'tabular-nums',
                  color: theme.palette.text.secondary,
                }}
                >
                  {section.items.length}
                </span>
                <div style={{
                  flex: 1,
                  height: 1,
                  backgroundColor: alpha(theme.palette.text.primary, 0.05),
                }}
                />
              </header>
              {/* Capped at 4 columns like OpenCTI (Grid2 xs-12 md-6 lg-4 xl-3):
                  cards grow with the screen instead of multiplying columns. */}
              <Box
                sx={{
                  display: 'grid',
                  gap: 2,
                  gridTemplateColumns: {
                    xs: '1fr',
                    md: 'repeat(2, 1fr)',
                    lg: 'repeat(3, 1fr)',
                    xl: 'repeat(4, 1fr)',
                  },
                }}
              >
                {section.items.map(item => (
                  <CatalogConnectorCard
                    key={item.id}
                    connector={item}
                    footerAction={renderFooterAction?.(item)}
                  />
                ))}
              </Box>
            </section>
          );
        })}
      </main>
    </div>
  );
};

export default ConnectorMarketplace;
