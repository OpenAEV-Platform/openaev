import {
  Combobox,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
} from '@filigran/design-system';
import { FilterListOffOutlined } from '@mui/icons-material';
import { IconButton, Tooltip } from '@mui/material';
import { type CSSProperties, type FunctionComponent, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type Filter, type FilterGroup } from '../../../../utils/api-types';
import { type Option } from '../../../../utils/Option';
import { useFormatter } from '../../../i18n';
import { type FilterHelpers } from './FilterHelpers';
import { buildEmptyFilter } from './FilterUtils';

const useStyles = makeStyles()(() => ({
  container: {
    display: 'flex',
    gap: 10,
  },
}));

export type OptionPropertySchema = Option & { operator: Filter['operator'] };

interface Props {
  filterGroup?: FilterGroup;
  options: OptionPropertySchema[];
  helpers: FilterHelpers;
  setPristine: (pristine: boolean) => void;
  style?: CSSProperties;
  domains?: boolean;
  // Called on top of handleClearAllFilters when the clear button is pressed,
  // so callers with an associated text search input can reset it too.
  onClear?: () => void;
}

const FilterAutocomplete: FunctionComponent<Props> = ({
  options,
  helpers,
  setPristine,
  style,
  domains,
  onClear,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();

  const [inputValue, setInputValue] = useState('');

  const handleChange = (value: string, operator: Filter['operator']) => {
    setPristine(false);
    helpers.handleAddFilterWithEmptyValue(buildEmptyFilter(value, operator));
  };
  const handleClearFilters = () => {
    setPristine(true);
    helpers.handleClearAllFilters();
    onClear?.();
  };

  return (
    <div className={classes.container}>
      <div style={{ width: domains ? '95%' : 200 }}>
        <Combobox
          options={options}
          value={null}
          onValueChange={(selectOptionValue) => {
            const next = selectOptionValue as typeof options[number] | null;
            if (next) {
              handleChange(next.id, next.operator);
            }
          }}
          inputValue={inputValue}
          onInputChange={(newValue, meta) => {
            // MUI reported `reason === 'reset'` here to protect the typed text
            // from a programmatic reset; the library states the same cause.
            if (meta.cause !== 'type') {
              return;
            }
            setInputValue(newValue);
          }}
          getOptionLabel={option => option.label}
          renderOption={option => option.label}
        >
          <ComboboxLabel>
            {domains
              ? t('Please choose a scenario or simulation, or leave this field blank to include all scenarios and atomic tests')
              : t('Add filter')}
          </ComboboxLabel>
          <ComboboxField>
            <ComboboxInput />
            <ComboboxControls>
              <ComboboxTrigger />
            </ComboboxControls>
          </ComboboxField>
          <ComboboxContent />
        </Combobox>
      </div>
      <Tooltip title={t('Clear filters')}>
        <IconButton
          style={{
            ...style,
            maxHeight: 40,
          }}
          color="primary"
          onClick={handleClearFilters}
          size="small"
        >
          <FilterListOffOutlined fontSize="small" />
        </IconButton>
      </Tooltip>
    </div>
  );
};

export default FilterAutocomplete;
