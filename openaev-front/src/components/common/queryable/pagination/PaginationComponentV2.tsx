import { Box, Button, Chip } from '@mui/material';
import { cloneElement, type ReactElement, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import MitreFilter, { MITRE_FILTER_KEY } from '../../../../admin/components/common/filters/MitreFilter';
import mitreAttack from '../../../../static/images/misc/attack.png';
import { type AttackPattern, type Filter, type PropertySchemaDTO, type SearchPaginationInput } from '../../../../utils/api-types';
import { useFormatter } from '../../../i18n';
import ClickableModeChip from '../../chips/ClickableModeChip';
import Drawer from '../../Drawer';
import FilterAutocomplete, { type OptionPropertySchema } from '../filter/FilterAutocomplete';
import FilterChips from '../filter/FilterChips';
import { availableOperators, isEmptyFilter } from '../filter/FilterUtils';
import useFilterableProperties from '../filter/useFilterableProperties';
import { type Page } from '../Page';
import { type QueryableHelpers } from '../QueryableHelpers';
import TextSearchComponent from '../textSearch/TextSearchComponent';
import TablePaginationComponentV2 from './TablePaginationComponentV2';

const useStyles = makeStyles<{ topPagination?: boolean }>()((theme, props) => ({
  topbar: {
    display: 'flex',
    alignItems: 'center',
  },
  topPagination: { display: 'block' },
  parameters: {
    marginTop: -10,
    display: props.topPagination ? 'block' : 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  parametersWithoutPagination: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  TTPMitreContainer: {
    padding: theme.spacing(2),
    overflow: 'scroll',
    height: 'calc(100vh - 65px)', // 65px equal to the header height
  },
}));

interface Props<T> {
  fetch: (input: SearchPaginationInput) => Promise<{ data: Page<T> }>;
  searchPaginationInput: SearchPaginationInput;
  setContent: (data: T[]) => void;
  setLoading?: (loading: boolean) => void;
  searchEnable?: boolean;
  disablePagination?: boolean;
  disableFilters?: boolean;
  entityPrefix?: string;
  availableFilterNames?: string[];
  queryableHelpers: QueryableHelpers;
  topBarButtons?: ReactElement | null;
  attackPatterns?: AttackPattern[];
  reloadContentCount?: number;
  contextId?: string;
  topPagination?: boolean;
}

const PaginationComponentV2 = <T extends object>({
  fetch,
  searchPaginationInput,
  setContent,
  setLoading,
  searchEnable = true,
  disablePagination,
  disableFilters,
  entityPrefix,
  availableFilterNames = [],
  queryableHelpers,
  attackPatterns,
  topBarButtons,
  reloadContentCount = 0,
  contextId,
  topPagination = false,
}: Props<T>) => {
  // Standard hooks
  const { classes } = useStyles({ topPagination });
  const { t } = useFormatter();

  const [properties, setProperties] = useState<PropertySchemaDTO[]>([]);
  const [options, setOptions] = useState<OptionPropertySchema[]>([]);

  useEffect(() => {
    if (entityPrefix) {
      useFilterableProperties(entityPrefix, availableFilterNames).then((propertySchemas: PropertySchemaDTO[]) => {
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
    }
  }, [entityPrefix]);

  useEffect(() => {
    // Modify URI
    if (queryableHelpers.uriHelpers) {
      queryableHelpers.uriHelpers.updateUri();
    }

    // Fetch datas
    setLoading?.(true);
    fetch(searchPaginationInput).then((result: { data: Page<T> }) => {
      const { data } = result;
      setContent(data.content);
      queryableHelpers.paginationHelpers.handleChangeTotalElements(data.totalElements);
      if (data.totalPages <= data.pageable.pageNumber) {
        queryableHelpers.paginationHelpers.handleChangePage(0);
      }
      setLoading?.(false);
    });
  }, [searchPaginationInput, reloadContentCount]);

  // Filters
  const [pristine, setPristine] = useState(true);
  const [openMitreFilter, setOpenMitreFilter] = useState(false);

  const computeAttackPatternNameForFilter = () => {
    return searchPaginationInput.filterGroup?.filters?.filter(
      (f: Filter) => f.key === MITRE_FILTER_KEY,
    )?.[0]?.values?.map(
      (externalId: string) => attackPatterns?.find(
        (a: AttackPattern) => a.attack_pattern_external_id === externalId,
      )?.attack_pattern_name,
    );
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
        }}
        >
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
              style={{ marginLeft: searchEnable ? 10 : 0 }}
            />
          ) }

          {queryableHelpers.filterHelpers && availableFilterNames?.includes('injector_contract_attack_patterns') && (
            <>
              <div style={{ cursor: 'pointer' }} onClick={() => setOpenMitreFilter(true)}>
                <Button
                  variant="outlined"
                  style={{
                    marginLeft: searchEnable ? 10 : 0,
                    border: '1px solid #c74227',
                  }}
                >
                  <img src={mitreAttack} alt="MITRE ATT&CK" style={{ width: 60 }} />
                </Button>
              </div>
              <Drawer
                open={openMitreFilter}
                handleClose={() => setOpenMitreFilter(false)}
                title={t('ATT&CK Matrix')}
                variant="full"
                containerStyle={{
                  padding: 0,
                  maxHeight: '100%',
                }}
              >
                <MitreFilter
                  className={classes.TTPMitreContainer}
                  helpers={queryableHelpers.filterHelpers}
                  onClick={() => setOpenMitreFilter(false)}
                />
              </Drawer>
            </>
          )}
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
                padding: '12px 4px',
                display: 'flex',
                flexWrap: 'wrap',
                gap: 1,
              }}
            >
              <Chip
                style={{ borderRadius: 4 }}
                label={(
                  <>
                    <strong>{t('Attack Pattern')}</strong>
                    {' '}
                    =
                    {' '}
                    {computeAttackPatternNameForFilter()}
                  </>
                )}
                onDelete={() => queryableHelpers.filterHelpers.handleRemoveFilterByKey(MITRE_FILTER_KEY)}
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

export default PaginationComponentV2;
