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
  /** Technical filter keys to hide from the "Add filter" picker (e.g. programmatic relation filters). */
  excludedFilterNames?: string[];
  filterGroup: FilterGroup;
  helpers: FilterHelpers;
  style: CSSProperties;
  contextId?: string;
}

const FilterField: FunctionComponent<Props> = ({
  entityPrefix,
  availableFilterNames = [],
  excludedFilterNames = [],
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

  // Serialized keys keep the effect stable when callers pass inline array literals,
  // while still refreshing the options when the actual filter names change.
  const availableFilterNamesKey = availableFilterNames.join('|');
  const excludedFilterNamesKey = excludedFilterNames.join('|');

  useEffect(() => {
    let cancelled = false;
    useFilterableProperties(entityPrefix, availableFilterNames).then((propertySchemas: PropertySchemaDTO[]) => {
      if (cancelled) {
        return;
      }
      const retainedProperties = propertySchemas.filter(property => !excludedFilterNames.includes(property.schema_property_name));
      const newOptions = retainedProperties.map(property => (
        {
          id: property.schema_property_name,
          label: t(property.schema_property_name),
          operator: availableOperators(property)[0],
        } as OptionPropertySchema
      ));
      setOptions(newOptions);
      setProperties(retainedProperties);
    });
    return () => {
      cancelled = true;
    };
    // `t` is recreated on every render (non-memoized formatter) and must stay out of the deps.
  }, [entityPrefix, availableFilterNamesKey, excludedFilterNamesKey]);

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
