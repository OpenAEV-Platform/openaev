import { CancelOutlined } from '@mui/icons-material';
import { Box, IconButton, Skeleton, TextField } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { engineSchemas } from '../../../../../../actions/schema/schema-action';
import { FilterContext } from '../../../../../../components/common/queryable/filter/context';
import FilterAutocomplete, { type OptionPropertySchema } from '../../../../../../components/common/queryable/filter/FilterAutocomplete';
import FilterChips from '../../../../../../components/common/queryable/filter/FilterChips';
import { availableOperators, buildFilter } from '../../../../../../components/common/queryable/filter/FilterUtils';
import { buildSearchPagination } from '../../../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../../../components/i18n';
import { type FilterGroup, type PropertySchemaDTO } from '../../../../../../utils/api-types';
import { type GroupOption } from '../../../../../../utils/Option';
import { capitalize } from '../../../../../../utils/String';
import { MITRE_FILTER_KEY } from '../../../../common/filters/MitreFilter';
import { CustomDashboardContext } from '../../CustomDashboardContext';
import { BASE_ENTITY_FILTER_KEY, excludeBaseEntities, getDefaultValuesForType } from '../WidgetUtils';
import getAuthorizedPerspectives from './AuthorizedPerspectives';
import FilterFieldBaseEntity from './FilterFieldBaseEntity';

const useStyles = makeStyles()(theme => ({
  step_entity: {
    border: `1px solid ${theme.palette.secondary.main}`,
    borderRadius: 4,
    position: 'relative',
  },
  icon: {
    paddingTop: 4,
    display: 'inline-block',
  },
  text: {
    display: 'inline-block',
    flexGrow: 1,
    marginLeft: 10,
  },
}));

const WidgetSeriesSelection: FunctionComponent<{
  index: number;
  perspective?: {
    name?: string;
    filter?: FilterGroup;
  };
  onChange: (perspective: {
    name?: string;
    filter?: FilterGroup;
  }) => void;
  onRemove?: (index: number) => void;
  error?: boolean;
}> = ({ index, perspective, onChange, onRemove, error }) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const theme = useTheme();
  const { customDashboard } = useContext(CustomDashboardContext);

  const [label, setLabel] = useState<string>(perspective?.name ?? '');
  const onChangeLabel = (label: string) => {
    setLabel(label);
    onChange({
      ...perspective,
      name: label,
    });
  };

  const [entity, setEntity] = useState<string | null>(perspective?.filter?.filters?.find(f => f.key === BASE_ENTITY_FILTER_KEY)?.values?.[0] ?? null);
  const onChangeEntity = (entity: string | null) => {
    setEntity(entity);
    onChange({
      ...perspective,
      filter: entity === null
        ? undefined
        : {
            mode: 'and',
            filters: [
              buildFilter(BASE_ENTITY_FILTER_KEY, [entity], 'eq'),
            ],
          },
    });
  };

  const handleRemoveSeries = () => {
    if (onRemove) {
      onRemove(index);
    }
  };

  // Filters
  const { queryableHelpers, searchPaginationInput } = useQueryable({}, buildSearchPagination({ filterGroup: excludeBaseEntities(perspective?.filter) }));
  useEffect(() => {
    onChange({
      ...perspective,
      filter: entity === null
        ? undefined
        : {
            mode: 'and',
            filters: [
              buildFilter(BASE_ENTITY_FILTER_KEY, [entity], 'eq'),
              ...searchPaginationInput.filterGroup?.filters ?? [],
            ],
          },
    });
  }, [searchPaginationInput]);

  const [properties, setProperties] = useState<PropertySchemaDTO[]>([]);
  const [propertyOptions, setPropertyOptions] = useState<OptionPropertySchema[]>([]);
  const [propertyOptionsLoading, setPropertyOptionsLoading] = useState<boolean>(false);
  const [defaultValues, setDefaultValues] = useState<Map<string, GroupOption[]>>(new Map());
  const [pristine, setPristine] = useState(true);
  useEffect(() => {
    if (entity) {
      setPropertyOptionsLoading(true);
      engineSchemas([entity]).then((response: { data: PropertySchemaDTO[] }) => {
        const available = getAuthorizedPerspectives().get(entity) ?? [];
        const newOptions = response.data.filter(property => property.schema_property_name !== MITRE_FILTER_KEY)
          .filter(property => available.includes(property.schema_property_name))
          .map(property => (
            {
              id: property.schema_property_name,
              label: capitalize(t(property.schema_property_label)),
              operator: availableOperators(property)[0],
            } as OptionPropertySchema
          ))
          .sort((a, b) => a.label.localeCompare(b.label));
        setPropertyOptions(newOptions);
        setProperties(response.data);
        setPropertyOptionsLoading(false);
      });
    }
    (customDashboard?.custom_dashboard_parameters ?? []).forEach((p) => {
      if (p.custom_dashboards_parameter_type === 'simulation') {
        const newDefaultValues = getDefaultValuesForType(defaultValues, p, 'base_simulation_side');
        setDefaultValues(newDefaultValues);
      }
      if (p.custom_dashboards_parameter_type === 'scenario') {
        const newDefaultValues = getDefaultValuesForType(defaultValues, p, 'base_scenario_side');
        setDefaultValues(newDefaultValues);
      }
    });
  }, [entity]);

  return (
    <div className={classes.step_entity}>
      { onRemove
        && (
          <div style={{
            display: 'flex',
            justifyContent: 'flex-end',
            position: 'absolute',
            top: 0,
            right: 0,
            zIndex: 10,
          }}
          >
            <IconButton
              disabled={index === 0}
              aria-label="Delete"
              onClick={handleRemoveSeries}
              size="small"
            >
              <CancelOutlined fontSize="small" />
            </IconButton>
          </div>
        )}
      <Box padding={2}>
        <TextField
          variant="standard"
          fullWidth
          label={t('Label (entities)')}
          value={label}
          onChange={e => onChangeLabel(e.target.value)}
        />
        <div style={{ marginTop: theme.spacing(2) }}>
          <FilterFieldBaseEntity error={error} value={entity} onChange={onChangeEntity} />
        </div>
        {entity
          && (
            <div style={{ marginTop: theme.spacing(2) }}>
              {propertyOptionsLoading ? <Skeleton height={35} /> : (
                <>
                  <FilterAutocomplete
                    filterGroup={searchPaginationInput.filterGroup}
                    helpers={queryableHelpers.filterHelpers}
                    options={propertyOptions}
                    setPristine={setPristine}
                  />
                  <FilterContext.Provider value={{ defaultValues: defaultValues }}>
                    <FilterChips
                      propertySchemas={properties}
                      filterGroup={searchPaginationInput.filterGroup}
                      helpers={queryableHelpers.filterHelpers}
                      pristine={pristine}
                    />
                  </FilterContext.Provider>
                </>
              )}
            </div>
          )}
      </Box>
    </div>
  );
};

export default WidgetSeriesSelection;
