import { BoltOutlined, DevicesOtherOutlined, HelpOutlineOutlined, HubOutlined, KeyboardArrowRight, MovieFilterOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemText, type SvgIconProps, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type ComponentType, type FunctionComponent, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';
import { useLocalStorage } from 'usehooks-ts';

import { type AttackPatternHelper } from '../../../actions/attack_patterns/attackpattern-helper';
import { adHocEntities, adHocEntitiesRuntime } from '../../../actions/dashboards/dashboard-action';
import { engineSchemas } from '../../../actions/schema/schema-action';
import Breadcrumbs from '../../../components/Breadcrumbs';
import FilterAutocomplete, { type OptionPropertySchema } from '../../../components/common/queryable/filter/FilterAutocomplete';
import FilterChips from '../../../components/common/queryable/filter/FilterChips';
import { availableOperators, buildFilter } from '../../../components/common/queryable/filter/FilterUtils';
import TablePaginationComponentV2 from '../../../components/common/queryable/pagination/TablePaginationComponentV2';
import { DEFAULT_ROWS_PER_PAGE } from '../../../components/common/queryable/pagination/usePaginationState';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { useQueryable } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../components/common/SortHeadersList';
import FindingIcon from '../../../components/FindingIcon';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import { useHelper } from '../../../store';
import {
  type AttackPattern,
  type EsBase,
  type EsEntities,
  type EsFinding,
  type Filter,
  type ListConfiguration,
  type PropertySchemaDTO,
} from '../../../utils/api-types';
import { capitalize } from '../../../utils/String';
import { MITRE_FILTER_KEY } from '../common/filters/MitreFilter';
import getAuthorizedPerspectives from '../workspaces/custom_dashboards/widgets/configuration/AuthorizedPerspectives';
import AssetElementStyles from '../workspaces/custom_dashboards/widgets/viz/list/elements/AssetElementStyles';
import buildStyles from '../workspaces/custom_dashboards/widgets/viz/list/elements/ColumnStyles';
import DefaultElementStyles from '../workspaces/custom_dashboards/widgets/viz/list/elements/DefaultElementStyles';
import listConfigRenderer, { defaultRenderer } from '../workspaces/custom_dashboards/widgets/viz/list/elements/ListColumnConfig';
import navigationHandlers from '../workspaces/custom_dashboards/widgets/viz/list/elements/ListNavigationHandler';
import { BASE_ENTITY_FILTER_KEY, excludeBaseEntities, getBaseEntities } from '../workspaces/custom_dashboards/widgets/WidgetUtils';
import { buildDefaultHomeWidgets, type DefaultTimeRange, timeRangeStartDate } from './defaultHomeWidgets';

const RESERVED_PARAMS = ['widget_id', 'series_index'];

// Same row icon as the entity's own list page, so the drill-down feels native.
const ENTITY_ICONS: Record<string, ComponentType<SvgIconProps>> = {
  'asset': DevicesOtherOutlined,
  'vulnerable-endpoint': DevicesOtherOutlined,
  'scenario': MovieFilterOutlined,
  'simulation': HubOutlined,
  'inject': BoltOutlined,
  'expectation-inject': TrackChangesOutlined,
};

const rowIcon = (element: EsBase) => {
  if (element.base_entity === 'finding') {
    return <FindingIcon findingType={(element as EsFinding).finding_type ?? ''} tooltip />;
  }
  const Icon = ENTITY_ICONS[element.base_entity ?? ''] ?? HelpOutlineOutlined;
  return <Icon color="primary" />;
};

// ES sorting is only safe on date fields here (text fields have no sortable mapping).
const SORTABLE_COLUMNS = new Set(['base_created_at', 'base_updated_at', 'execution_date']);

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

interface ExplorerProps {
  listConfig: ListConfiguration;
  initialEntities?: EsEntities;
  /**
   * The widget's implicit time scope materialized as regular, editable date
   * filters (see DefaultHomeResults). Seeded into the filter chips so the list
   * openly shows - and lets the user amend - the dashboard time range.
   */
  seedDateFilters?: Filter[];
}

/**
 * The standard, fully manipulable results list: the runtime-resolved widget
 * scope seeds a regular queryable state (chips can be edited / removed, more
 * filters added, columns sorted) and every refinement re-queries the ad-hoc
 * list endpoint with the amended filter group.
 */
const ResultsExplorer: FunctionComponent<ExplorerProps> = ({ listConfig, initialEntities, seedDateFilters = [] }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const navigate = useNavigate();
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();

  const { attackPatterns }: { attackPatterns: AttackPattern[] } = useHelper(
    (helper: AttackPatternHelper) => ({ attackPatterns: helper.getAttackPatterns() }),
  );

  const baseEntity = getBaseEntities(listConfig.perspective?.filter)?.[0] ?? '';
  const columns = useMemo(() => listConfig.columns ?? [], [listConfig]);
  const columnStyles = useMemo(
    () => buildStyles(columns, baseEntity === 'asset' ? AssetElementStyles : DefaultElementStyles),
    [columns, baseEntity],
  );

  // The clicked widget scope becomes the initial filter state (including the
  // materialized dashboard time range); clearing the filters falls back to the
  // whole entity perspective, all time.
  const scopedFilterGroup = excludeBaseEntities(listConfig.perspective?.filter);
  const { queryableHelpers, searchPaginationInput } = useQueryable({}, buildSearchPagination({
    filterGroup: {
      mode: scopedFilterGroup?.mode ?? 'and',
      filters: [...(scopedFilterGroup?.filters ?? []), ...seedDateFilters],
    },
    sorts: (listConfig.sorts ?? []).map(sort => ({
      property: sort.fieldName,
      direction: sort.direction,
    })),
  }));

  // Filterable properties come from the ES engine schema of the drilled entity,
  // restricted to the same allowlist as the custom dashboard widget builder.
  const [pristine, setPristine] = useState(true);
  const [properties, setProperties] = useState<PropertySchemaDTO[]>([]);
  const [propertyOptions, setPropertyOptions] = useState<OptionPropertySchema[]>([]);
  useEffect(() => {
    if (!baseEntity) {
      return;
    }
    engineSchemas([baseEntity]).then((response: { data: PropertySchemaDTO[] }) => {
      const available = getAuthorizedPerspectives().get(baseEntity) ?? [];
      const options = response.data
        .filter(property => property.schema_property_name !== MITRE_FILTER_KEY)
        .filter(property => available.includes(property.schema_property_name))
        .map(property => ({
          id: property.schema_property_name,
          label: capitalize(t(property.schema_property_label)),
          operator: availableOperators(property)[0],
        } as OptionPropertySchema))
        .sort((a, b) => a.label.localeCompare(b.label));
      setPropertyOptions(options);
      setProperties(response.data);
    });
  }, [baseEntity]);

  // Column headers are translated from the field name (like the filter chips
  // and every hand-written list page: "base_created_at" -> "Created at"), not
  // from the raw lowercase engine schema label.
  const headers: Header[] = useMemo(() => columns.map(column => ({
    field: column,
    label: column,
    isSortable: SORTABLE_COLUMNS.has(column),
  })), [columns]);

  const [elements, setElements] = useState<EsBase[]>(initialEntities?.es_datas ?? []);
  const [loading, setLoading] = useState(false);
  // Monotonic request id: a slow earlier response must never overwrite a newer one.
  const requestIdRef = useRef(0);
  // The first page already came back with the runtime seed call. The queryable
  // child hooks all fire their mount-time onChange with a NEW (but content-equal)
  // input object, so identity-based skipping is not enough: compare the
  // serialized query instead, or the seeded rows flash into a skeleton and the
  // exact same page-0 query is re-issued right after mount.
  const lastFetchKeyRef = useRef<string | null>(
    initialEntities != null ? JSON.stringify(searchPaginationInput) : null,
  );

  useEffect(() => {
    queryableHelpers.paginationHelpers.handleChangeTotalElements(initialEntities?.total ?? 0);
  }, []);

  useEffect(() => {
    const fetchKey = JSON.stringify(searchPaginationInput);
    if (fetchKey === lastFetchKeyRef.current) {
      return;
    }
    lastFetchKeyRef.current = fetchKey;
    requestIdRef.current += 1;
    const requestId = requestIdRef.current;
    setLoading(true);
    // Re-issue the list query with the user-amended filters and sorts. The
    // widget's implicit time scope is materialized as an editable date chip
    // (seedDateFilters), so it is neutralized here: otherwise editing or
    // removing the chip would silently keep the original window applied.
    const config: ListConfiguration = {
      ...listConfig,
      time_range: 'ALL_TIME',
      start: null,
      end: null,
      perspective: {
        ...listConfig.perspective,
        filter: {
          mode: searchPaginationInput.filterGroup?.mode ?? 'and',
          filters: [
            buildFilter(BASE_ENTITY_FILTER_KEY, [baseEntity], 'eq'),
            ...(searchPaginationInput.filterGroup?.filters ?? []),
          ],
        },
      },
      sorts: (searchPaginationInput.sorts ?? [])
        .filter(sort => sort.property)
        .map(sort => ({
          fieldName: sort.property as string,
          direction: sort.direction === 'ASC' ? 'ASC' as const : 'DESC' as const,
        })),
    };
    adHocEntities(config, undefined, {
      page: searchPaginationInput.page,
      size: searchPaginationInput.size,
    }).then(({ data }: { data: EsEntities }) => {
      if (requestId !== requestIdRef.current) return;
      setElements(data.es_datas);
      queryableHelpers.paginationHelpers.handleChangeTotalElements(data.total);
      if (searchPaginationInput.page > 0 && data.total <= searchPaginationInput.page * searchPaginationInput.size) {
        queryableHelpers.paginationHelpers.handleChangePage(0);
      }
      setLoading(false);
    }).catch(() => {
      if (requestId !== requestIdRef.current) return;
      setElements([]);
      setLoading(false);
    });
  }, [searchPaginationInput]);

  const loaderIcon = ENTITY_ICONS[baseEntity] ?? HelpOutlineOutlined;

  return (
    <>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <FilterAutocomplete
          filterGroup={searchPaginationInput.filterGroup}
          helpers={queryableHelpers.filterHelpers}
          options={propertyOptions}
          setPristine={setPristine}
        />
        <TablePaginationComponentV2
          page={searchPaginationInput.page}
          size={searchPaginationInput.size}
          paginationHelpers={queryableHelpers.paginationHelpers}
        />
      </div>
      <FilterChips
        propertySchemas={properties}
        filterGroup={searchPaginationInput.filterGroup}
        helpers={queryableHelpers.filterHelpers}
        pristine={pristine}
      />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          style={{ paddingTop: 0 }}
          secondaryAction={<>&nbsp;</>}
        >
          <ListItemIcon />
          <ListItemText
            primary={(
              <SortHeadersComponentV2
                headers={headers}
                inlineStylesHeaders={columnStyles}
                sortHelpers={queryableHelpers.sortHelpers}
              />
            )}
          />
        </ListItem>
        {loading && <PaginatedListLoader Icon={loaderIcon} headers={headers} headerStyles={columnStyles} />}
        {!loading && elements.length === 0 && (
          <Typography
            variant="subtitle1"
            align="center"
            sx={{
              marginTop: 4,
              color: theme.palette.text.secondary,
            }}
          >
            {t('No data to display')}
          </Typography>
        )}
        {!loading && elements.map((element) => {
          const handler = navigationHandlers[element.base_entity ?? ''];
          const clickable = handler !== undefined;
          return (
            <ListItem
              key={element.base_id}
              divider
              disablePadding
              secondaryAction={clickable ? <KeyboardArrowRight color="action" /> : <>&nbsp;</>}
            >
              <ListItemButton
                classes={{ root: classes.item }}
                onClick={clickable ? () => handler(element, navigate) : undefined}
                sx={clickable ? undefined : { cursor: 'default' }}
              >
                <ListItemIcon>
                  {rowIcon(element)}
                </ListItemIcon>
                <ListItemText
                  primary={(
                    <div style={bodyItemsStyles.bodyItems}>
                      {columns.map((column) => {
                        const renderer = listConfigRenderer[column as keyof typeof listConfigRenderer] ?? defaultRenderer;
                        const value = element[column as keyof typeof element] as string | boolean | string[] | boolean[];
                        return (
                          <div
                            key={column}
                            style={{
                              ...bodyItemsStyles.bodyItem,
                              ...columnStyles[column],
                            }}
                          >
                            {renderer(value, {
                              element,
                              attackPatterns,
                            })}
                          </div>
                        );
                      })}
                    </div>
                  )}
                />
              </ListItemButton>
            </ListItem>
          );
        })}
      </List>
    </>
  );
};

/**
 * Full-page drill-down for the built-in home dashboard: every click on a
 * score, bar, gate or gauge lands here with the widget id and the clicked
 * scope in the URL. The ad-hoc runtime endpoint converts the widget into a
 * scoped entity list once, then the standard queryable machinery (filter
 * chips, sorting, pagination) takes over.
 */
const DefaultHomeResults = () => {
  const { t, locale } = useFormatter();
  const [searchParams] = useSearchParams();

  const widgetId = searchParams.get('widget_id');
  const seriesIndex = Number(searchParams.get('series_index') ?? 0);

  // Values are carried as one URL param per value (repeatable keys).
  const filterValues = useMemo(() => Object.fromEntries(
    [...new Set(searchParams.keys())]
      .filter(key => !RESERVED_PARAMS.includes(key))
      .map(key => [key, searchParams.getAll(key).filter(value => value !== '')]),
  ), [searchParams]);

  // Same time range as the home dashboard so the list matches what was clicked.
  const [timeRange] = useLocalStorage<DefaultTimeRange>('default-home-dashboard-time-range', 'LAST_QUARTER');
  // `t` from useFormatter() is a NEW function on every render, so it must NOT be
  // a dependency here: it would give `widget` (and thus the seed effect) a fresh
  // identity every render, re-running the fetch on every render and looping the
  // ad-hoc endpoint. `locale` is the stable signal that t's output changed, so a
  // runtime language switch still refreshes the localized titles.
  const widget = useMemo(
    () => buildDefaultHomeWidgets(timeRange, t).find(w => w.widget_id === widgetId),
    [timeRange, widgetId, locale],
  );

  const [seed, setSeed] = useState<{
    listConfig: ListConfiguration;
    entities?: EsEntities;
  } | null>(null);
  const [seedError, setSeedError] = useState(false);

  // One runtime call resolves the clicked widget scope into a list
  // configuration (+ its first page); the explorer below owns everything else.
  useEffect(() => {
    if (!widget) {
      return undefined;
    }
    let cancelled = false;
    setSeed(null);
    setSeedError(false);
    adHocEntitiesRuntime(widget.widget_type, widget.widget_config, {
      filter_values_map: filterValues,
      series_index: seriesIndex,
      parameters: {},
      pagination: {
        page: 0,
        size: DEFAULT_ROWS_PER_PAGE,
      },
    }).then(({ data }) => {
      if (cancelled) return;
      if (data.list_configuration == null) {
        setSeedError(true);
        return;
      }
      setSeed({
        listConfig: data.list_configuration,
        entities: data.es_entities,
      });
    }).catch(() => {
      if (!cancelled) setSeedError(true);
    });
    return () => {
      cancelled = true;
    };
  }, [widget, filterValues, seriesIndex]);

  // The widget scope is implicitly time-bounded (its time_range is applied
  // server-side when resolving the drill-down). Materialize that bound as
  // regular date filter chips so the list openly shows the dashboard time
  // range and lets the user widen or narrow it. Temporal bucket clicks come
  // back as a CUSTOM range (the clicked interval) and take precedence over
  // the dashboard-wide range.
  const seedDateFilters = useMemo((): Filter[] => {
    if (seed == null) {
      return [];
    }
    const dateAttribute = seed.listConfig.date_attribute ?? 'base_created_at';
    if (seed.listConfig.time_range === 'CUSTOM') {
      return [
        ...(seed.listConfig.start ? [buildFilter(dateAttribute, [seed.listConfig.start], 'gte')] : []),
        ...(seed.listConfig.end ? [buildFilter(dateAttribute, [seed.listConfig.end], 'lte')] : []),
      ];
    }
    const start = timeRangeStartDate(timeRange);
    return start ? [buildFilter(dateAttribute, [start], 'gte')] : [];
  }, [seed, timeRange]);

  // Titles are already localized at build time (buildDefaultHomeWidgets).
  const widgetTitle = widget?.widget_config.title ?? t('Results');

  if (!widgetId || !widget) {
    return (
      <Typography variant="subtitle1" align="center" sx={{ marginTop: 6 }}>
        {t('No data to display')}
      </Typography>
    );
  }

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[
          {
            label: t('Home'),
            link: '/admin',
          },
          {
            label: widgetTitle,
            current: true,
          },
        ]}
      />
      {seedError && (
        <Typography variant="subtitle1" align="center" sx={{ marginTop: 6 }}>
          {t('No data to display')}
        </Typography>
      )}
      {!seedError && seed == null && <Loader variant="inElement" />}
      {!seedError && seed != null && (
        <ResultsExplorer
          key={`${widgetId}-${seriesIndex}`}
          listConfig={seed.listConfig}
          initialEntities={seed.entities}
          seedDateFilters={seedDateFilters}
        />
      )}
    </>
  );
};

export default DefaultHomeResults;
