import { Button, capitalize, Skeleton } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useContext, useEffect, useState } from 'react';

import { engineSchemas } from '../../../../../../../actions/schema/schema-action';
import { FilterContext } from '../../../../../../../components/common/queryable/filter/context';
import FilterAutocomplete, { type OptionPropertySchema } from '../../../../../../../components/common/queryable/filter/FilterAutocomplete';
import FilterChips from '../../../../../../../components/common/queryable/filter/FilterChips';
import { availableOperators } from '../../../../../../../components/common/queryable/filter/FilterUtils';
import { buildSearchPagination } from '../../../../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../../../../components/i18n';
import type { PropertySchemaDTO, Series } from '../../../../../../../utils/api-types';
import { type GroupOption } from '../../../../../../../utils/Option';
import { CustomDashboardContext } from '../../../CustomDashboardContext';
import { domainsEntityFilter, excludeBaseEntities, getDefaultValuesForType } from '../../WidgetUtils';
import getAuthorizedPerspectives from '../AuthorizedPerspectives';

interface Props {
  currentSeries: Series[];
  onChange: (series: Series[]) => void;
  entity: string | null;
  onSubmit: () => void;
}

const WidgetSecurityDomainsSeriesSelection: FunctionComponent<Props> = ({ onChange, onSubmit, entity, currentSeries }) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();
  const { customDashboard } = useContext(CustomDashboardContext);

  // Filters
  const serie = currentSeries?.[0] ?? {};
  const { queryableHelpers, searchPaginationInput } = useQueryable({}, buildSearchPagination({ filterGroup: excludeBaseEntities(serie.filter) }));

  useEffect(() => {
    onChange([
      {
        name: '',
        filter:
          {
            mode: 'and',
            filters: [
              domainsEntityFilter,
              ...searchPaginationInput.filterGroup?.filters ?? [],
            ],
          },
      },
    ]);
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
        const newOptions = response.data.filter(property => property.schema_property_name === 'base_simulation_side' || property.schema_property_name === 'base_scenario_side')
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

  const handleSubmit = () => {
    onSubmit();
  };

  return (
    <>
      <div style={{ marginTop: theme.spacing(2) }}>
        {propertyOptionsLoading ? <Skeleton height={35} /> : (
          <>
            <FilterAutocomplete
              filterGroup={searchPaginationInput.filterGroup}
              helpers={queryableHelpers.filterHelpers}
              options={propertyOptions}
              setPristine={setPristine}
              domains={true}
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
      <div style={{
        display: 'flex',
        justifyContent: 'center',
      }}
      >
        <Button
          variant="contained"
          color="primary"
          sx={{ marginTop: theme.spacing(2) }}
          onClick={handleSubmit}
        >
          {t('Validate')}
        </Button>
      </div>
    </>

  );
};

export default WidgetSecurityDomainsSeriesSelection;
