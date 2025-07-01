import { CancelOutlined } from '@mui/icons-material';
import { Box, IconButton, TextField } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { engineSchemas } from '../../../../../actions/schema/schema-action';
import FilterAutocomplete, { type OptionPropertySchema } from '../../../../../components/common/queryable/filter/FilterAutocomplete';
import FilterChips from '../../../../../components/common/queryable/filter/FilterChips';
import { availableOperators, buildFilter } from '../../../../../components/common/queryable/filter/FilterUtils';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../../components/i18n';
import { type DateHistogramSeries, type PropertySchemaDTO, type StructuralHistogramSeries } from '../../../../../utils/api-types';
import { MITRE_FILTER_KEY } from '../../../common/filters/MitreFilter';
import FilterFieldBaseEntity from './FilterFieldBaseEntity';
import { BASE_ENTITY_FILTER_KEY, excludeBaseEntities } from './WidgetUtils';

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

const availableFilters = new Map([
  ['expectation-inject', ['base_created_at', 'inject_expectation_status', 'inject_expectation_type', 'base_updated_at']],
  ['finding', ['base_created_at', 'finding_type', 'base_updated_at', 'base_endpoint_side']],
  ['endpoint', ['endpoint_arch', 'endpoint_platform', 'endpoint_ips', 'endpoint_hostname']],
  ['vulnerable-endpoint', ['vulnerable_endpoint_architecture', 'vulnerable_endpoint_agents_active_status', 'vulnerable_endpoint_agents_privileges', 'vulnerable_endpoint_platform', 'base_simulation_side']],
]);

const WidgetCreationSeries: FunctionComponent<{
  index: number;
  series: DateHistogramSeries | StructuralHistogramSeries;
  onChange: (series: DateHistogramSeries | StructuralHistogramSeries) => void;
  onRemove: (index: number) => void;
}> = ({ index, series, onChange, onRemove }) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const theme = useTheme();

  const [label, setLabel] = useState<string>(series.name ?? '');
  const onChangeLabel = (label: string) => {
    setLabel(label);
    onChange({
      ...series,
      name: label,
    });
  };

  const [entity, setEntity] = useState<string | null>(series.filter?.filters?.find(f => f.key === BASE_ENTITY_FILTER_KEY)?.values?.[0] ?? null);
  const onChangeEntity = (entity: string | null) => {
    setEntity(entity);
    onChange({
      ...series,
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
    onRemove(index);
  };

  // Filters
  const { queryableHelpers, searchPaginationInput } = useQueryable({}, buildSearchPagination({ filterGroup: excludeBaseEntities(series.filter) }));
  useEffect(() => {
    onChange({
      ...series,
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
  const [pristine, setPristine] = useState(true);
  useEffect(() => {
    if (entity) {
      engineSchemas([entity]).then((response: { data: PropertySchemaDTO[] }) => {
        const available = availableFilters.get(entity) ?? [];
        const newOptions = response.data.filter(property => property.schema_property_name !== MITRE_FILTER_KEY)
          .filter(property => available.includes(property.schema_property_name))
          .map(property => (
            {
              id: property.schema_property_name,
              label: t(property.schema_property_label),
              operator: availableOperators(property)[0],
            } as OptionPropertySchema
          ))
          .sort((a, b) => a.label.localeCompare(b.label));
        setPropertyOptions(newOptions);
        setProperties(response.data);
      });
    }
  }, [entity]);

  return (
    <div className={classes.step_entity}>
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
      <Box padding={2}>
        <TextField
          variant="standard"
          fullWidth
          label={t('Label (entities)')}
          value={label}
          onChange={e => onChangeLabel(e.target.value)}
        />
        <div style={{ marginTop: theme.spacing(2) }}>
          <FilterFieldBaseEntity value={entity} onChange={onChangeEntity} />
        </div>
        <div style={{ marginTop: theme.spacing(2) }}>
          <FilterAutocomplete
            filterGroup={searchPaginationInput.filterGroup}
            helpers={queryableHelpers.filterHelpers}
            options={propertyOptions}
            setPristine={setPristine}
          />
          <FilterChips
            propertySchemas={properties}
            filterGroup={searchPaginationInput.filterGroup}
            helpers={queryableHelpers.filterHelpers}
            pristine={pristine}
          />
        </div>
      </Box>
    </div>
  );
};

export default WidgetCreationSeries;
