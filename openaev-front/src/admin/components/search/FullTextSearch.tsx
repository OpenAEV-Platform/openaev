import { AppsOutlined, KeyboardArrowRight } from '@mui/icons-material';
import { Box, Chip, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type CSSProperties, type FunctionComponent, type ReactNode, useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';

import { fullTextSearch, fullTextSearchByClass } from '../../../actions/fullTextSearch-action';
import Breadcrumbs from '../../../components/Breadcrumbs';
import PaginationComponent from '../../../components/common/pagination/PaginationComponent';
import { type Page } from '../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import Empty from '../../../components/Empty';
import { useFormatter } from '../../../components/i18n';
import ItemTags from '../../../components/ItemTags';
import { type FullTextSearchCountResult, type FullTextSearchResult, type SearchPaginationInput } from '../../../utils/api-types';
import useEntityIcon from '../../../utils/hooks/useEntityIcon';
import useEntityLink from './useEntityLink';

// "All" pseudo-category id: mixes results across every entity type.
const ALL = 'all';
// Cap per class when merging the "All" view (search result sets are small).
const ALL_FETCH_SIZE = 100;

interface Category {
  // Backend map key = fully-qualified class name, required by the by-class endpoint.
  key: string;
  // getSimpleName() (e.g. "AssetGroup") - used for label, icon and entity link.
  clazz: string;
  count: number;
}

const inlineStyles: Record<string, CSSProperties> = {
  result_name: { width: '30%' },
  result_type: { width: '15%' },
  result_description: { width: '35%' },
  result_tags: { width: '20%' },
};

interface CategoryRailProps {
  categories: Category[];
  total: number;
  selected: string;
  onSelect: (key: string) => void;
}

const CategoryRail: FunctionComponent<CategoryRailProps> = ({ categories, total, selected, onSelect }) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const renderRow = (key: string, clazz: string, count: number, icon: ReactNode) => {
    const isSelected = key === selected;
    return (
      <Box
        key={key}
        role="button"
        tabIndex={0}
        aria-current={isSelected ? 'true' : undefined}
        onClick={() => onSelect(key)}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            onSelect(key);
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
          {icon}
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
          {t(clazz)}
        </Typography>
        <Chip
          label={count}
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
  };

  return (
    <Box
      component="nav"
      aria-label={t('Categories')}
      sx={{
        width: 220,
        flexShrink: 0,
        display: 'flex',
        flexDirection: 'column',
        gap: 0.5,
      }}
    >
      {renderRow(ALL, 'All', total, <AppsOutlined />)}
      {categories.map(category => renderRow(category.key, category.clazz, category.count, useEntityIcon(category.clazz)))}
    </Box>
  );
};

const FullTextSearch = () => {
  const { t } = useFormatter();
  const bodyItemsStyles = useBodyItemsStyles();

  const [searchParams] = useSearchParams();
  const search = searchParams.get('search') ?? '';

  const [counts, setCounts] = useState<Record<string, FullTextSearchCountResult>>({});
  const [selected, setSelected] = useState<string>(ALL);
  const [elements, setElements] = useState<FullTextSearchResult[]>([]);

  useEffect(() => {
    let stale = false;
    fullTextSearch(search).then((result: { data: Record<string, FullTextSearchCountResult> }) => {
      if (!stale) setCounts(result.data ?? {});
    });
    setSelected(ALL);
    // Guard against out-of-order responses when the query changes rapidly.
    return () => {
      stale = true;
    };
  }, [search]);

  const categories: Category[] = useMemo(
    () => Object.entries(counts)
      .map(([key, value]) => ({
        key,
        clazz: value.clazz,
        count: value.count,
      }))
      .filter(category => category.count > 0)
      .sort((a, b) => b.count - a.count),
    [counts],
  );

  const total = useMemo(() => categories.reduce((acc, category) => acc + category.count, 0), [categories]);
  const categoryKeys = useMemo(() => categories.map(category => category.key), [categories]);

  // A new reference whenever the query or the selected category changes, so the
  // reused pagination engine refetches.
  const searchPaginationInput = useMemo(() => buildSearchPagination({ textSearch: search }), [search, selected]);

  // Single category: standard server-side pagination. "All": fan out to every
  // category and merge into one page (search result sets are small).
  const fetch = (input: SearchPaginationInput): Promise<{ data: Page<FullTextSearchResult> }> => {
    if (selected !== ALL) {
      return fullTextSearchByClass(selected, input);
    }
    return Promise.all(
      categoryKeys.map(key => fullTextSearchByClass(key, {
        ...input,
        page: 0,
        size: ALL_FETCH_SIZE,
      })),
    ).then((responses: { data: Page<FullTextSearchResult> }[]) => {
      const merged = responses.flatMap(response => response.data.content ?? []);
      const size = input.size ?? 20;
      const from = (input.page ?? 0) * size;
      return {
        data: {
          content: merged.slice(from, from + size),
          totalElements: merged.length,
          totalPages: Math.ceil(merged.length / size),
          pageable: { pageNumber: input.page ?? 0 },
        } as Page<FullTextSearchResult>,
      };
    });
  };

  const headerSx = {
    fontFamily: '"Geologica", sans-serif',
    fontWeight: 600,
    fontSize: 11,
    letterSpacing: '0.08em',
    textTransform: 'uppercase' as const,
    color: 'text.secondary',
  };

  const columns: {
    field: string;
    label: string;
    value: (r: FullTextSearchResult) => ReactNode;
  }[] = [
    {
      field: 'result_name',
      label: 'Name',
      value: r => <span style={{ fontWeight: 600 }}>{r.name}</span>,
    },
    {
      field: 'result_type',
      label: 'Type',
      value: r => <span>{t(r.clazz)}</span>,
    },
    {
      field: 'result_description',
      label: 'Description',
      value: r => <span>{r.description || '-'}</span>,
    },
    {
      field: 'result_tags',
      label: 'Tags',
      value: r => <ItemTags variant="list" tags={r.tags} />,
    },
  ];

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
        alignItems: 'baseline',
        gap: 1.5,
        marginBottom: 2,
      }}
      >
        <Typography variant="h1" sx={{ margin: 0 }}>{t('Search')}</Typography>
        {search && (
          <Typography sx={{
            fontSize: 13,
            color: 'text.secondary',
          }}
          >
            {t('{count} results', { count: total })}
          </Typography>
        )}
      </Box>

      {(() => {
        if (!search) {
          return <Empty message={t('Search across OpenAEV')} />;
        }
        if (categories.length === 0) {
          return <Empty message={t('No results found')} />;
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
              total={total}
              selected={selected}
              onSelect={setSelected}
            />
            <Box sx={{
              flex: 1,
              minWidth: 0,
            }}
            >
              <PaginationComponent
                // Keyed on the category AND the category set: the "All" fetch
                // fans out over categoryKeys, which only settles after the
                // counts request resolves - remount then so the merged page is
                // never built from the previous query's category set.
                key={`${selected}:${categoryKeys.join(',')}`}
                fetch={fetch}
                searchPaginationInput={searchPaginationInput}
                setContent={setElements}
                searchEnable={false}
              />
              <List>
                <ListItem
                  divider={false}
                  style={{
                    paddingTop: 0,
                    textTransform: 'uppercase',
                  }}
                  secondaryAction={<>&nbsp;</>}
                >
                  <ListItemIcon />
                  <ListItemText
                    primary={(
                      <div style={bodyItemsStyles.bodyItems}>
                        {columns.map(header => (
                          <Typography
                            key={header.field}
                            component="div"
                            sx={{
                              ...headerSx,
                              ...inlineStyles[header.field],
                            }}
                          >
                            {t(header.label)}
                          </Typography>
                        ))}
                      </div>
                    )}
                  />
                </ListItem>
                {elements.map((element) => {
                  const to = useEntityLink(element.clazz, element.id, search);
                  return (
                    <ListItemButton
                      key={element.id}
                      divider
                      component={Link}
                      to={to}
                      style={{ height: 50 }}
                    >
                      <ListItemIcon>
                        {useEntityIcon(element.clazz)}
                      </ListItemIcon>
                      <ListItemText
                        primary={(
                          <div style={bodyItemsStyles.bodyItems}>
                            {columns.map(column => (
                              <div
                                key={column.field}
                                style={{
                                  ...bodyItemsStyles.bodyItem,
                                  ...inlineStyles[column.field],
                                }}
                              >
                                {column.value(element)}
                              </div>
                            ))}
                          </div>
                        )}
                      />
                      <ListItemIcon style={{ justifyContent: 'flex-end' }}>
                        <KeyboardArrowRight sx={{ color: 'text.disabled' }} />
                      </ListItemIcon>
                    </ListItemButton>
                  );
                })}
                {elements.length === 0 && <Empty message={t('No results found')} />}
              </List>
            </Box>
          </Box>
        );
      })()}
    </>
  );
};

export default FullTextSearch;
