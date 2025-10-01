import { type CSSProperties, type FunctionComponent, useEffect, useState } from 'react';

import { type FilterGroup, type PropertySchemaDTO } from '../../../../utils/api-types';
import { useFormatter } from '../../../i18n';
import FilterAutocomplete, { type OptionPropertySchema } from './FilterAutocomplete';
import FilterChips from './FilterChips';
import { type FilterHelpers } from './FilterHelpers';
import { availableOperators } from './FilterUtils';
import useFilterableProperties from './useFilterableProperties';

interface Props {
  entityPrefix: string;
  availableFilterNames?: string[];
  filterGroup: FilterGroup;
  helpers: FilterHelpers;
  style: CSSProperties;
  contextId?: string;
}

const FilterField: FunctionComponent<Props> = ({
  entityPrefix,
  availableFilterNames = [],
  filterGroup,
  helpers,
  style,
  contextId,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const [pristine, setPristine] = useState(true);

  const [properties, setProperties] = useState<PropertySchemaDTO[]>([]);
  const [options, setOptions] = useState<OptionPropertySchema[]>([]);

  useEffect(() => {
    useFilterableProperties(entityPrefix, availableFilterNames).then((propertySchemas: PropertySchemaDTO[]) => {
      const newOptions = propertySchemas.map(property => (
        {
          id: property.schema_property_name,
          label: t(property.schema_property_name),
          operator: availableOperators(property)[0],
        } as OptionPropertySchema
      ));
      setOptions(newOptions);
      setProperties(propertySchemas);
    });
  }, []);

  return (
    <>
      <FilterAutocomplete
        filterGroup={filterGroup}
        helpers={helpers}
        options={options}
        setPristine={setPristine}
        style={style}
      />
      <FilterChips
        propertySchemas={properties}
        filterGroup={filterGroup}
        availableFilterNames={availableFilterNames}
        helpers={helpers}
        pristine={pristine}
        contextId={contextId}
      />
    </>
  );
};

export default FilterField;
