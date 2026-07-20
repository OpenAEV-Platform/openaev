import { KeyboardArrowRight, SearchOffOutlined, TravelExploreOutlined } from '@mui/icons-material';
import { Box, Chip, List, ListItemButton, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';

import { fullTextSearch, fullTextSearchByClass } from '../../../actions/fullTextSearch-action';
import Breadcrumbs from '../../../components/Breadcrumbs';
import PaginationComponent from '../../../components/common/pagination/PaginationComponent';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import Empty from '../../../components/Empty';
import { useFormatter } from '../../../components/i18n';
import ItemTags from '../../../components/ItemTags';
import SearchFilter from '../../../components/SearchFilter';
import { type FullTextSearchCountResult, type FullTextSearchResult } from '../../../utils/api-types';
import useEntityIcon from '../../../utils/hooks/useEntityIcon';
import useEntityLink from './useEntityLink';

// Shared grid template so the header row and every result row line up exactly:
// medallion | name | description | tags | chevron.
const RESULT_GRID = '36px minmax(0, 2fr) minmax(0, 2fr) minmax(0, 1.1fr) 24px';

interface CategoryRailProps {
  categories: FullTextSearchCountResult[];
  selected: string | null;
  onSelect: (clazz: string) => void;
}

// Left facet rail: one selectable row per entity type, with its icon, translated
// label and result count. Replaces the old cramped vertical MUI Tabs.
const CategoryRail: FunctionComponent<CategoryRailProps> = ({ categories, selected, onSelect }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  return (
    <Box
      component="nav"
      aria-label={t('Categories')}
      sx={{
        width: 240,
        flexShrink: 0,
        display: 'flex',
        flexDirection: 'column',
        gap: 0.5,
      }}
    >
      <Typography sx={{
        fontFamily: '"Geologica", sans-serif',
        fontWeight: 600,
        fontSize: 11,
        letterSpacing: '0.12em',
        textTransform: 'uppercase',
        color: 'text.secondary',
        marginBottom: 0.5,
      }}
      >
        {t('Categories')}
      </Typography>
      {categories.map((category) => {
        const isSelected = category.clazz === selected;
        return (
          <Box
            key={category.clazz}
            role="button"
            tabIndex={0}
            onClick={() => onSelect(category.clazz)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                onSelect(category.clazz);
              }
            }}
            sx={{
              'display': 'flex',
              'alignItems': 'center',
              'gap': 1.25,
              'paddingBlock': 0.75,
              'paddingInline': 1,
              'borderRadius': 1,
              'cursor': 'pointer',
              'borderLeft': `2px solid ${isSelected ? theme.palette.primary.main : 'transparent'}`,
              'backgroundColor': isSelected ? alpha(theme.palette.primary.main, 0.1) : 'transparent',
              'transition': 'background-color 120ms',
              '&:hover': { backgroundColor: isSelected ? alpha(theme.palette.primary.main, 0.14) : theme.palette.action.hover },
            }}
          >
            <Box
              aria-hidden
              sx={{
                'width': 28,
                'height': 28,
                'flexShrink': 0,
                'borderRadius': 1,
                'display': 'flex',
                'alignItems': 'center',
                'justifyContent': 'center',
                'backgroundColor': alpha(theme.palette.text.primary, 0.04),
                '& .MuiSvgIcon-root': { fontSize: 18 },
              }}
            >
              {useEntityIcon(category.clazz)}
            </Box>
            <Typography sx={{
              flex: 1,
              minWidth: 0,
              fontSize: 13.5,
              fontWeight: isSelected ? 600 : 500,
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
            >
              {t(category.clazz)}
            </Typography>
            <Chip
              label={category.count}
              size="small"
              sx={{
                height: 20,
                minWidth: 28,
                borderRadius: 0.5,
                fontSize: 11,
                fontWeight: 600,
                backgroundColor: isSelected ? alpha(theme.palette.primary.main, 0.2) : alpha(theme.palette.text.primary, 0.06),
                color: isSelected ? theme.palette.primary.main : theme.palette.text.secondary,
              }}
            />
          </Box>
        );
      })}
    </Box>
  );
};

interface ResultsPanelProps {
  clazz: string;
  textSearch: string;
}

const ResultsPanel: FunctionComponent<ResultsPanelProps> = ({ clazz, textSearch }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const [elements, setElements] = useState<FullTextSearchResult[]>([]);
  const [loading, setLoading] = useState(true);

  // Rebuild the pagination input whenever the query changes so the reused engine
  // refetches the selected category.
  const searchPaginationInput = useMemo(() => buildSearchPagination({ textSearch }), [textSearch]);

  const headerSx = {
    fontFamily: '"Geologica", sans-serif',
    fontWeight: 600,
    fontSize: 11,
    letterSpacing: '0.08em',
    textTransform: 'uppercase' as const,
    color: 'text.secondary',
  };

  return (
    <Box sx={{
      flex: 1,
      minWidth: 0,
    }}
    >
      <PaginationComponent
        fetch={(input) => {
          setLoading(true);
          return fullTextSearchByClass(clazz, {
            ...input,
            ...searchPaginationInput,
          }).finally(() => setLoading(false));
        }}
        searchPaginationInput={searchPaginationInput}
        setContent={setElements}
        searchEnable={false}
      />
      <Box sx={{
        border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
        borderRadius: 1,
        overflow: 'hidden',
      }}
      >
        <Box sx={{
          display: 'grid',
          gridTemplateColumns: RESULT_GRID,
          alignItems: 'center',
          gap: 1.5,
          paddingBlock: 1,
          paddingInline: 1.5,
          borderBottom: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
        }}
        >
          <span />
          <Typography sx={headerSx}>{t('Name')}</Typography>
          <Typography sx={headerSx}>{t('Description')}</Typography>
          <Typography sx={headerSx}>{t('Tags')}</Typography>
          <span />
        </Box>
        {(() => {
          if (!loading && elements.length === 0) {
            return <Empty message={t('No results found')} icon={SearchOffOutlined} />;
          }
          return (
            <List disablePadding>
              {elements.map((result) => {
                const to = useEntityLink(result.clazz, result.id, textSearch);
                return (
                  <ListItemButton
                    key={result.id}
                    component={Link}
                    to={to}
                    sx={{
                      'display': 'grid',
                      'gridTemplateColumns': RESULT_GRID,
                      'alignItems': 'center',
                      'gap': 1.5,
                      'paddingBlock': 1,
                      'paddingInline': 1.5,
                      '&:not(:last-of-type)': { borderBottom: `1px solid ${alpha(theme.palette.text.primary, 0.05)}` },
                    }}
                  >
                    <Box
                      aria-hidden
                      sx={{
                        'width': 28,
                        'height': 28,
                        'borderRadius': 1,
                        'display': 'flex',
                        'alignItems': 'center',
                        'justifyContent': 'center',
                        'backgroundColor': alpha(theme.palette.text.primary, 0.04),
                        '& .MuiSvgIcon-root': { fontSize: 18 },
                      }}
                    >
                      {useEntityIcon(result.clazz)}
                    </Box>
                    <Box sx={{ minWidth: 0 }}>
                      <Typography sx={{
                        fontSize: 13.5,
                        fontWeight: 600,
                        whiteSpace: 'nowrap',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                      }}
                      >
                        {result.name}
                      </Typography>
                      <Typography sx={{
                        fontSize: 11,
                        color: 'text.disabled',
                      }}
                      >
                        {t(result.clazz)}
                      </Typography>
                    </Box>
                    <Typography sx={{
                      fontSize: 12.5,
                      color: 'text.secondary',
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                    }}
                    >
                      {result.description || '-'}
                    </Typography>
                    <Box sx={{ minWidth: 0 }}>
                      <ItemTags variant="list" tags={result.tags} />
                    </Box>
                    <KeyboardArrowRight sx={{ color: 'text.disabled' }} />
                  </ListItemButton>
                );
              })}
            </List>
          );
        })()}
      </Box>
    </Box>
  );
};

const FullTextSearch = () => {
  const { t } = useFormatter();
  const theme = useTheme();

  const [searchParams, setSearchParams] = useSearchParams();
  const [search, setSearch] = useState(searchParams.get('search') ?? '');
  const [counts, setCounts] = useState<Record<string, FullTextSearchCountResult>>({});
  const [selected, setSelected] = useState<string | null>(null);
  const [loadingCounts, setLoadingCounts] = useState(true);

  useEffect(() => {
    setLoadingCounts(true);
    fullTextSearch(search)
      .then((result: { data: Record<string, FullTextSearchCountResult> }) => setCounts(result.data ?? {}))
      .finally(() => setLoadingCounts(false));
  }, [search]);

  // Categories sorted by count (most relevant first), so the busiest bucket leads.
  const categories = useMemo(
    () => Object.values(counts).filter(c => c.count > 0).sort((a, b) => b.count - a.count),
    [counts],
  );

  const total = useMemo(() => categories.reduce((acc, c) => acc + c.count, 0), [categories]);

  // Keep a valid selection: default to the busiest category, and re-point when the
  // current selection disappears after a query change.
  useEffect(() => {
    if (categories.length === 0) {
      setSelected(null);
      return;
    }
    if (!selected || !categories.some(c => c.clazz === selected)) {
      setSelected(categories[0].clazz);
    }
  }, [categories, selected]);

  const handleRefine = (value?: string) => {
    const next = value ?? '';
    setSearch(next);
    const params = new URLSearchParams(searchParams);
    if (next) {
      params.set('search', next);
    } else {
      params.delete('search');
    }
    setSearchParams(params, { replace: true });
  };

  return (
    <>
      <Breadcrumbs
        variant="object"
        elements={[
          {
            label: t('Search'),
            current: true,
          },
        ]}
      />
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
        paddingBottom: 4,
      }}
      >
        <Box sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
          padding: 2,
          borderRadius: 1,
          background: `linear-gradient(135deg, ${alpha(theme.palette.primary.main, 0.08)}, transparent 60%)`,
          border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
        }}
        >
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1.5,
            flexWrap: 'wrap',
          }}
          >
            <Typography variant="h1" sx={{ margin: 0 }}>{t('Search')}</Typography>
            {search && !loadingCounts && (
              <Chip
                label={t('{count} results', { count: total })}
                size="small"
                sx={{
                  borderRadius: 0.5,
                  height: 22,
                  fontSize: 12,
                  color: 'text.secondary',
                }}
              />
            )}
          </Box>
          <Box sx={{ maxWidth: 520 }}>
            <SearchFilter
              variant="topBar"
              keyword={search}
              onChange={handleRefine}
              onSubmit={handleRefine}
              placeholder={`${t('Search across OpenAEV')}...`}
            />
          </Box>
        </Box>

        {(() => {
          if (!search) {
            return (
              <Empty
                icon={TravelExploreOutlined}
                message={t('Search across OpenAEV')}
                hint={t('Find scenarios, simulations, assets, teams and more.')}
              />
            );
          }
          if (!loadingCounts && categories.length === 0) {
            return (
              <Empty
                icon={SearchOffOutlined}
                message={t('No results found')}
                hint={t('Try a different search term.')}
              />
            );
          }
          return (
            <Box sx={{
              display: 'flex',
              gap: 3,
              alignItems: 'flex-start',
            }}
            >
              <CategoryRail
                categories={categories}
                selected={selected}
                onSelect={setSelected}
              />
              {selected && <ResultsPanel clazz={selected} textSearch={search} />}
            </Box>
          );
        })()}
      </Box>
    </>
  );
};

export default FullTextSearch;
