import { Box } from '@mui/material';
import { Fragment, type FunctionComponent, useCallback } from 'react';

import { type Filter, type FilterGroup, type PropertySchemaDTO } from '../../../../utils/api-types';
import ClickableModeChip from '../../chips/ClickableModeChip';
import FilterChip from './FilterChip';
import { type FilterHelpers } from './FilterHelpers';

interface Props {
  propertySchemas: PropertySchemaDTO[];
  filterGroup?: FilterGroup;
  availableFilterNames?: string[];
  helpers: FilterHelpers;
  pristine: boolean;
  contextId?: string;
}

const FilterChips: FunctionComponent<Props> = ({
  propertySchemas,
  filterGroup,
  availableFilterNames = [],
  helpers,
  pristine,
  contextId,
}) => {
  const filters = filterGroup?.filters?.filter(f => availableFilterNames.length === 0 || availableFilterNames.includes(f.key)) ?? [];

  const propertySchema = useCallback((filter: Filter) => {
    return propertySchemas.find(p => p.schema_property_name === filter.key);
  }, [propertySchemas]);

  const handleSwitchMode = () => helpers.handleSwitchMode();

  // Only filters whose property schema resolved can produce a chip. Rendering
  // the padded container for schema-less filters would leave an invisible
  // 56px spacer between the toolbar and the list.
  const renderableFilters = filters
    .map(f => ({
      filter: f,
      property: propertySchema(f),
    }))
    .filter((entry): entry is {
      filter: Filter;
      property: PropertySchemaDTO;
    } => !!entry.property);

  if (renderableFilters.length === 0) {
    return <></>;
  }

  return (
    <Box
      data-testid="toolbar-chips-row"
      sx={{
        // The toolbar above already puts 8px between its rows, so this one adds
        // no padding of its own — it only spaces its own chips by the same 8px.
        display: 'flex',
        flexWrap: 'wrap',
        gap: 1,
      }}
    >
      {renderableFilters.map(({ filter, property }, idx) => (
        <Fragment key={filter.id}>
          {idx !== 0 && <ClickableModeChip onClick={handleSwitchMode} mode={filterGroup?.mode} />}
          <FilterChip
            filter={filter}
            helpers={helpers}
            propertySchema={property}
            pristine={pristine}
            contextId={contextId}
          />
        </Fragment>
      ))}
    </Box>
  );
};

export default FilterChips;
