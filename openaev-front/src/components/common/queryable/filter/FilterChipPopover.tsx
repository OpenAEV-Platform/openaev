import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@filigran/design-system';
import { Popover } from '@mui/material';
import { type FunctionComponent } from 'react';

import { type Filter, type PropertySchemaDTO } from '../../../../utils/api-types';
import { useFormatter } from '../../../i18n';
import { FilterChipPopoverInput } from './FilterChipPopoverInput';
import { type FilterHelpers } from './FilterHelpers';
import { availableOperators, OperatorKeyValues } from './FilterUtils';
import ScenarioStatusFilter from './specific/ScenarioStatusFilter';
import ScenarioTypeFilter from './specific/ScenarioTypeFilter';

interface Props {
  filter: Filter;
  helpers: FilterHelpers;
  propertySchema: PropertySchemaDTO;
  open: boolean;
  onClose: () => void;
  anchorEl?: HTMLElement;
  contextId?: string;
}

const FilterChipPopover: FunctionComponent<Props> = ({
  filter,
  helpers,
  propertySchema,
  open,
  onClose,
  anchorEl,
  contextId,
}) => {
  // Standard hooks
  const { t } = useFormatter();

  const handleChangeOperator = (value: string) => {
    helpers.handleChangeOperatorById(filter.id, value as Filter['operator']);
  };

  const displayOperatorAndFilter = () => {
    // Specific field
    if (propertySchema.schema_property_name === 'scenario_recurrence') {
      return (<ScenarioStatusFilter propertySchema={propertySchema} helpers={helpers} filter={filter} />);
    }
    if (propertySchema.schema_property_name === 'scenario_type') {
      return (<ScenarioTypeFilter propertySchema={propertySchema} helpers={helpers} filter={filter} />);
    }

    const operators = availableOperators(propertySchema);
    return (
      <>
        <div style={{ marginBottom: 15 }}>
          <Select
            value={filter.operator ?? operators[0]}
            onValueChange={handleChangeOperator}
          >
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {operators.map(value => (
                <SelectItem key={value} value={value}>
                  {t(OperatorKeyValues[value])}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <FilterChipPopoverInput filter={filter} helpers={helpers} propertySchema={propertySchema} contextId={contextId} />
      </>
    );
  };

  return (
    <Popover
      open={open}
      anchorEl={anchorEl}
      onClose={onClose}
      anchorOrigin={{
        vertical: 'bottom',
        horizontal: 'left',
      }}
      PaperProps={{
        className: 'fds-filter-popover',
        elevation: 1,
        style: { marginTop: 10 },
      }}
    >
      <div
        style={{
          // Figma node 7346:48677: the panel stacks its fields with
          // `--spacing-2` between them inside `--spacing-4` of padding.
          display: 'flex',
          flexDirection: 'column',
          gap: 8,
          width: 250,
          padding: 16,
        }}
      >
        {displayOperatorAndFilter()}
      </div>
    </Popover>
  );
};
export default FilterChipPopover;
