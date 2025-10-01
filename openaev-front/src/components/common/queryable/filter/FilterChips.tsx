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

  if (filters.length === 0) {
    return <></>;
  }

  return (
    <Box
      sx={{
        padding: '12px 4px',
        display: 'flex',
        flexWrap: 'wrap',
        gap: 1,
        minHeight: 56,
      }}
    >
      {filters.map((filter, idx) => {
        const property = propertySchema(filter);
        if (!property) {
          return <Fragment key={filter.key}></Fragment>;
        }
        return (
          <Fragment key={filter.key}>
            {idx !== 0 && <ClickableModeChip onClick={handleSwitchMode} mode={filterGroup?.mode} />}
            <FilterChip
              filter={filter}
              helpers={helpers}
              propertySchema={property}
              pristine={pristine}
              contextId={contextId}
            />
          </Fragment>
        );
      })}
    </Box>
  );
};

export default FilterChips;
