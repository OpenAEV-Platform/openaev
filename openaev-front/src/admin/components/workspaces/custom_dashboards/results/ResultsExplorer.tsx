import { HelpOutlineOutlined, KeyboardArrowRight } from '@mui/icons-material';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemText, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { type AttackPatternHelper } from '../../../../../actions/attack_patterns/attackpattern-helper';
import { adHocEntities } from '../../../../../actions/dashboards/dashboard-action';
import { engineSchemas } from '../../../../../actions/schema/schema-action';
import FilterAutocomplete, { type OptionPropertySchema } from '../../../../../components/common/queryable/filter/FilterAutocomplete';
import FilterChips from '../../../../../components/common/queryable/filter/FilterChips';
import { availableOperators, buildFilter } from '../../../../../components/common/queryable/filter/FilterUtils';
import TablePaginationComponentV2 from '../../../../../components/common/queryable/pagination/TablePaginationComponentV2';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../../components/common/queryable/style/style';
import { useQueryable } from '../../../../../components/common/queryable/useQueryableWithLocalStorage';
import { type Header } from '../../../../../components/common/SortHeadersList';
import { useFormatter } from '../../../../../components/i18n';
import PaginatedListLoader from '../../../../../components/PaginatedListLoader';
import { useHelper } from '../../../../../store';
import {
  type AttackPattern,
  type EsBase,
  type EsEntities,
  type Filter,
  type ListConfiguration,
  type PropertySchemaDTO,
} from '../../../../../utils/api-types';
import { capitalize } from '../../../../../utils/String';
import { MITRE_FILTER_KEY } from '../../../common/filters/MitreFilter';
import getAuthorizedPerspectives from '../widgets/configuration/AuthorizedPerspectives';
import buildStyles from '../widgets/viz/list/elements/ColumnStyles';
import listConfigRenderer from '../widgets/viz/list/elements/ListColumnConfig';
import { getNavigationUrl } from '../widgets/viz/list/elements/ListNavigationHandler';
import { BASE_ENTITY_FILTER_KEY, excludeBaseEntities, getBaseEntities } from '../widgets/WidgetUtils';
import { buildEntityColumnStyles, entityDefaultRenderer, getEntityListConfig, SORTABLE_DATE_COLUMNS } from './resultsListConfig';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

interface ExplorerProps {
  listConfig: ListConfiguration;
  initialEntities?: EsEntities;
  /**
   * The widget's implicit time scope materialized as regular, editable date
   * filters (see DashboardResults). Seeded into the filter chips so the list
   * openly shows - and lets the user amend - the dashboard time range.
   */
  seedDateFilters?: Filter[];
}

/**
 * The standard, fully manipulable results list: the runtime-resolved widget
 * scope seeds a regular queryable state (chips can be edited / removed, more
 * filters added, columns sorted) and every refinement re-queries the ad-hoc
 * list endpoint with the amended filter group. Columns, renderers and row
 * icons mirror the entity's own left-menu list page (resultsListConfig).
 */
const ResultsExplorer: FunctionComponent<ExplorerProps> = ({ listConfig, initialEntities, seedDateFilters = [] }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();

  const { attackPatterns }: { attackPatterns: AttackPattern[] } = useHelper(
    (helper: AttackPatternHelper) => ({ attackPatterns: helper.getAttackPatterns() }),
  );

  const baseEntity = getBaseEntities(listConfig.perspective?.filter)?.[0] ?? '';
  // Canonical per-entity columns (mirroring the entity's own list page),
  // falling back to the widget-configured columns for unknown entities.
  const entityConfig = getEntityListConfig(baseEntity);
  const columns = useMemo(
    () => entityConfig?.columns.map(column => column.field) ?? listConfig.columns ?? [],
    [entityConfig, listConfig],
  );
  const columnStyles = useMemo(
    () => (entityConfig ? buildEntityColumnStyles(entityConfig) : buildStyles(columns, {})),
    [entityConfig, columns],
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

  // Canonical headers use the exact labels of the entity's own list page
  // ("Name", "Platform", "First seen"...); the widget-column fallback
  // translates the field name like the filter chips do.
  const headers: Header[] = useMemo(() => (
    entityConfig
      ? entityConfig.columns.map(column => ({
          field: column.field,
          label: column.label,
          isSortable: column.isSortable,
        }))
      : columns.map(column => ({
          field: column,
          label: column,
          isSortable: SORTABLE_DATE_COLUMNS.has(column),
        }))
  ), [entityConfig, columns]);

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

  const loaderIcon = entityConfig?.loaderIcon ?? HelpOutlineOutlined;

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
          // Real router link (not a JS navigate) so ctrl/cmd+click opens a new tab.
          const url = getNavigationUrl(element);
          const clickable = url !== null;
          return (
            <ListItem
              key={element.base_id}
              divider
              disablePadding
              secondaryAction={clickable ? <KeyboardArrowRight color="action" /> : <>&nbsp;</>}
            >
              <ListItemButton
                classes={{ root: classes.item }}
                {...(clickable
                  ? {
                      component: Link,
                      to: url,
                    }
                  : {})}
                sx={clickable ? undefined : { cursor: 'default' }}
              >
                <ListItemIcon>
                  {entityConfig ? entityConfig.rowIcon(element) : <HelpOutlineOutlined color="primary" />}
                </ListItemIcon>
                <ListItemText
                  primary={(
                    <div style={bodyItemsStyles.bodyItems}>
                      {columns.map((column) => {
                        const renderer = entityConfig?.renderers[column] ?? listConfigRenderer[column] ?? entityDefaultRenderer;
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

export default ResultsExplorer;
