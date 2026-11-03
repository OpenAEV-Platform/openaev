import { Button, Chip } from '@filigran/design-system';
import { GridViewOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { cloneElement, type ReactElement, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { engineSchemas } from '../../../actions/schema/schema-action';
import KillChainSelect from '../../../admin/components/common/filters/KillChainSelect';
import MitreFilter, { MITRE_FILTER_KEY } from '../../../admin/components/common/filters/MitreFilter';
import useKillChains from '../../../admin/components/common/filters/useKillChains';
import { type AttackPattern, type Filter, type PropertySchemaDTO, type SearchPaginationInput } from '../../../utils/api-types';
import { useFormatter } from '../../i18n';
import ClickableModeChip from '../chips/ClickableModeChip';
import Drawer from '../Drawer';
import FilterAutocomplete, { type OptionPropertySchema } from './filter/FilterAutocomplete';
import FilterChips from './filter/FilterChips';
import { availableOperators, isEmptyFilter } from './filter/FilterUtils';
import useFilterableProperties from './filter/useFilterableProperties';
import TablePaginationComponentV2 from './pagination/TablePaginationComponentV2';
import { type QueryableHelpers } from './QueryableHelpers';
import TextSearchComponent from './textSearch/TextSearchComponent';

const useStyles = makeStyles<{ topPagination?: boolean }>()((theme, props) => ({
  topbar: {
    display: 'flex',
    alignItems: 'center',
    // The pagination control and the action buttons are two groups: 8px between them.
    gap: theme.spacing(1),
    // The primary actions (pagination + create button) must never be the part
    // that gives way when the toolbar runs out of width (#7340): the filter
    // row on the left is the one that compresses.
    flexShrink: 0,
  },
  topPagination: { display: 'block' },
  // 16px between this row and the list under it, measured box to box: the
  // list's own top padding is inside its box and does not count towards the
  // gap, so the whole 16 sits here.
  parameters: {
    display: props.topPagination ? 'block' : 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: theme.spacing(2),
  },
  parametersWithoutPagination: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: theme.spacing(2),
  },
  TTPMitreContainer: {
    padding: theme.spacing(2),
    overflow: 'auto',
    height: 'calc(100vh - 65px)', // 65px equal to the header height
  },
}));

export interface QueryableToolbarProps {
  searchPaginationInput: SearchPaginationInput;
  searchEnable?: boolean;
  disablePagination?: boolean;
  disableFilters?: boolean;
  entityPrefix?: string;
  // For Elasticsearch-backed lists: the engine model name (e.g.
  // 'expectation-inject') whose schema drives the filter options, instead of
  // the JPA class resolved from entityPrefix.
  engineEntityName?: string;
  availableFilterNames?: string[];
  queryableHelpers: QueryableHelpers;
  topBarButtons?: ReactElement | null;
  leftSlot?: ReactElement | null;
  // Rendered inline at the end of the filter row, right after the clear-filters
  // ("empty") icon of the filter autocomplete. Used e.g. for the card-view sort
  // control so it sits in the filter toolbar instead of the page hero.
  filtersEndSlot?: ReactElement | null;
  attackPatterns?: AttackPattern[];
  contextId?: string;
  topPagination?: boolean;
}

/**
 * The controls of a paginated list: text search, filters (and their chips), the attack matrix
 * filter, the page selector and the caller's buttons. It reads and changes the list input through
 * `queryableHelpers` and never fetches the list: `PaginationComponentV2` fetches around it,
 * `usePaginatedQuery` reads the page from a query.
 */
const QueryableToolbar = ({
  searchPaginationInput,
  searchEnable = true,
  disablePagination,
  disableFilters,
  entityPrefix,
  engineEntityName,
  availableFilterNames = [],
  queryableHelpers,
  attackPatterns,
  topBarButtons,
  leftSlot,
  filtersEndSlot,
  contextId,
  topPagination = false,
}: QueryableToolbarProps) => {
  // Standard hooks
  const { classes } = useStyles({ topPagination });
  const { t } = useFormatter();

  const [properties, setProperties] = useState<PropertySchemaDTO[]>([]);
  const [options, setOptions] = useState<OptionPropertySchema[]>([]);

  // Stable key for the names array (callers pass literals, so identity changes
  // every render): refetch the schemas when the actual content changes.
  const availableFilterNamesKey = availableFilterNames.join(',');

  useEffect(() => {
    // ES-backed lists resolve their filterable properties from the engine
    // schema (the JPA schema may not expose ES-only computed fields such as
    // inject_expectation_status); JPA-backed lists keep using entityPrefix.
    const fetchProperties: Promise<PropertySchemaDTO[]> | null = (() => {
      if (engineEntityName) {
        return engineSchemas([engineEntityName]).then((result: { data: PropertySchemaDTO[] }) =>
          result.data.filter(p => availableFilterNames.length === 0 || availableFilterNames.includes(p.schema_property_name)));
      }
      if (entityPrefix) {
        return useFilterableProperties(entityPrefix, availableFilterNames);
      }
      return null;
    })();
    fetchProperties?.then((propertySchemas: PropertySchemaDTO[]) => {
      const newOptions = propertySchemas.filter(property => property.schema_property_name !== MITRE_FILTER_KEY)
        .map(property => (
          {
            id: property.schema_property_name,
            label: t(property.schema_property_name),
            operator: availableOperators(property)[0],
          } as OptionPropertySchema
        ))
        .sort((a, b) => a.label.localeCompare(b.label));
      setOptions(newOptions);
      setProperties(propertySchemas);
    });
  }, [entityPrefix, engineEntityName, availableFilterNamesKey]);

  // Filters
  const [pristine, setPristine] = useState(true);
  const [openMitreFilter, setOpenMitreFilter] = useState(false);
  // Kill chain switcher lives in the attack matrix drawer header, so the matrix
  // body stays free of chrome.
  const { killChains, activeKillChain, selectKillChain } = useKillChains();

  // A matrix click filters by a top-level technique AND its sub-techniques (so the
  // result matches the technique's count). Collapse those external ids back to the
  // parent technique name(s) so the active-filter chip stays readable.
  const computeAttackPatternNameForFilter = () => {
    const values = searchPaginationInput.filterGroup?.filters?.filter(
      (f: Filter) => f.key === MITRE_FILTER_KEY,
    )?.[0]?.values ?? [];
    const names = values
      .map((externalId: string) => {
        const parentExternalId = externalId.split('.')[0];
        return attackPatterns?.find(
          (a: AttackPattern) => a.attack_pattern_external_id === parentExternalId,
        )?.attack_pattern_name;
      })
      .filter((name): name is string => !!name);
    return [...new Set(names)].join(', ');
  };

  // TopBarChildren
  let topBarButtonComponent;
  if (topBarButtons) {
    topBarButtonComponent = cloneElement(topBarButtons as ReactElement);
  }

  return (
    <>
      <div className={disablePagination ? classes.parametersWithoutPagination : classes.parameters}>
        {topPagination
          && (
            <div className={classes.topPagination}>
              {!disablePagination && (
                <TablePaginationComponentV2
                  page={searchPaginationInput.page}
                  size={searchPaginationInput.size}
                  paginationHelpers={queryableHelpers.paginationHelpers}
                />
              )}
              {!!topBarButtonComponent && topBarButtonComponent}
            </div>
          )}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          // Single-row toolbar contract (#7340): when width runs out this
          // filter section is the one that compresses (its items may shrink),
          // never the actions on the right, and nothing wraps to a second row.
          minWidth: 0,
          flexShrink: 1,
        }}
        >
          {leftSlot}
          {searchEnable && (
            <TextSearchComponent
              textSearch={searchPaginationInput.textSearch}
              textSearchHelpers={queryableHelpers.textSearchHelpers}
            />
          )}
          {!disableFilters && (
            <FilterAutocomplete
              filterGroup={searchPaginationInput.filterGroup}
              helpers={queryableHelpers.filterHelpers}
              options={options}
              setPristine={setPristine}
              style={{ marginLeft: (searchEnable || leftSlot) ? 10 : 0 }}
              // "Clear filters" also resets the associated text search input.
              onClear={() => queryableHelpers.textSearchHelpers.handleTextSearch('')}
            />
          ) }

          {queryableHelpers.filterHelpers && availableFilterNames?.includes('injector_contract_attack_patterns') && (
            <>
              <Button
                type="button"
                priority="secondary"
                startIcon={<GridViewOutlined fontSize="small" />}
                onClick={() => setOpenMitreFilter(true)}
                style={{
                  marginLeft: (searchEnable || leftSlot) ? 1.25 : 0,
                  whiteSpace: 'nowrap',
                }}
              >
                {t('Matrix')}
              </Button>
              <Drawer
                open={openMitreFilter}
                handleClose={() => setOpenMitreFilter(false)}
                title={t('Attack matrix')}
                variant="full"
                containerStyle={{
                  padding: 0,
                  maxHeight: '100%',
                }}
                headerActions={(
                  <KillChainSelect
                    killChains={killChains}
                    value={activeKillChain}
                    onChange={selectKillChain}
                  />
                )}
              >
                <MitreFilter
                  className={classes.TTPMitreContainer}
                  helpers={queryableHelpers.filterHelpers}
                  killChain={activeKillChain}
                  onClick={() => setOpenMitreFilter(false)}
                />
              </Drawer>
            </>
          )}
          {filtersEndSlot}
        </div>
        {!topPagination
          && (
            <div className={classes.topbar}>
              {!disablePagination && (
                <TablePaginationComponentV2
                  page={searchPaginationInput.page}
                  size={searchPaginationInput.size}
                  paginationHelpers={queryableHelpers.paginationHelpers}
                />
              )}
              {!!topBarButtonComponent && topBarButtonComponent}
            </div>
          )}
      </div>
      {/* Handle Mitre Filter */}
      {queryableHelpers.filterHelpers && searchPaginationInput.filterGroup && (
        <>
          {!isEmptyFilter(searchPaginationInput.filterGroup, MITRE_FILTER_KEY) && (
            <Box
              sx={{
                paddingTop: 1,
                paddingBottom: 0,
                paddingInline: 0.5,
                display: 'flex',
                flexWrap: 'wrap',
                gap: 1,
              }}
            >
              <Chip
                label={`${t('Attack Pattern')} = ${computeAttackPatternNameForFilter()}`}
                onDelete={() => queryableHelpers.filterHelpers.handleRemoveFilterByKey(MITRE_FILTER_KEY)}
                deleteLabel={t('Remove')}
              />
              {(searchPaginationInput.filterGroup?.filters?.filter(f => availableFilterNames?.filter(n => n !== MITRE_FILTER_KEY).includes(f.key)).length ?? 0) > 0 && (
                <ClickableModeChip
                  onClick={queryableHelpers.filterHelpers.handleSwitchMode}
                  mode={searchPaginationInput.filterGroup.mode}
                />
              )}
            </Box>
          )}
        </>
      )}
      <FilterChips
        propertySchemas={properties}
        filterGroup={searchPaginationInput.filterGroup}
        availableFilterNames={availableFilterNames?.filter(n => n !== MITRE_FILTER_KEY)}
        helpers={queryableHelpers.filterHelpers}
        pristine={pristine}
        contextId={contextId}
      />
    </>
  );
};

export default QueryableToolbar;
