import { FilterListOffOutlined } from '@mui/icons-material';
import { Autocomplete as MuiAutocomplete, IconButton, TextField, Tooltip } from '@mui/material';
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
    // In a width-constrained toolbar the autocomplete is allowed to shrink
    // (see minWidth on the input below) instead of pushing the actions on the
    // right out of view (#7340).
    minWidth: 0,
    flexShrink: 1,
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
      <MuiAutocomplete
        options={options}
        sx={{
          // 200px when space allows, shrinkable down to 120px in a tight
          // toolbar (the label truncates but the control stays usable).
          width: domains ? '95%' : 200,
          minWidth: domains ? undefined : 120,
          flexShrink: 1,
        }}
        value={null}
        onChange={(_, selectOptionValue) => {
          if (selectOptionValue) {
            handleChange(selectOptionValue.id, selectOptionValue.operator);
          }
        }}
        inputValue={inputValue}
        onInputChange={(_, newValue, reason) => {
          if (reason === 'reset') {
            return;
          }
          setInputValue(newValue);
        }}
        renderInput={params => (
          <TextField
            {...params}
            variant="outlined"
            size="small"
            label={domains ? t('Please choose a scenario or simulation, or leave this field blank to include all scenarios and atomic tests') : t('Add filter')}
            style={style}
          />
        )}
        renderOption={(props, option) => <li {...props} key={props.key}>{option.label}</li>}
      />
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
