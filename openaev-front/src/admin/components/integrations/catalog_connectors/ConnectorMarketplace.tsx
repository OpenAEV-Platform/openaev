import { OnlinePredictionOutlined, SmartButtonOutlined, TerminalOutlined } from '@mui/icons-material';
import { Chip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ReactNode, useMemo, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import {
  type CatalogFacetFilters,
  type CatalogSort,
  CONNECTOR_TYPE_ORDER,
  type ConnectorItem,
  EMPTY_FACET_FILTERS,
  type FacetGroupId,
  filterConnectors,
  hasActiveFacetFilters,
  sortConnectors,
} from './catalog-facets';
import CatalogActiveFilters from './CatalogActiveFilters';
import CatalogConnectorCard from './CatalogConnectorCard';
import CatalogEmptyState from './CatalogEmptyState';
import CatalogSidebar from './CatalogSidebar';
import CatalogToolbar from './CatalogToolbar';

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
 */
const ConnectorMarketplace = ({ items, renderFooterAction, searchPlaceholder }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const [filters, setFilters] = useState<CatalogFacetFilters>(EMPTY_FACET_FILTERS);
  const [keyword, setKeyword] = useState('');
  const [sort, setSort] = useState<CatalogSort>('name_asc');
  // SearchFilter is uncontrolled; bumping this key remounts it to clear its value.
  const [searchResetKey, setSearchResetKey] = useState(0);

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
                  label={section.items.length}
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
                {section.items.map(item => (
                  <CatalogConnectorCard
                    key={item.id}
                    connector={item}
                    footerAction={renderFooterAction?.(item)}
                  />
                ))}
              </div>
            </section>
          );
        })}
      </main>
    </div>
  );
};

export default ConnectorMarketplace;
