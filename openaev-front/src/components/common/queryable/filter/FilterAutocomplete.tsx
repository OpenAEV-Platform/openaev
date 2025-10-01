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
  },
}));

export type OptionPropertySchema = Option & { operator: Filter['operator'] };

interface Props {
  filterGroup?: FilterGroup;
  options: OptionPropertySchema[];
  helpers: FilterHelpers;
  setPristine: (pristine: boolean) => void;
  style?: CSSProperties;
}

const FilterAutocomplete: FunctionComponent<Props> = ({
  filterGroup,
  options,
  helpers,
  setPristine,
  style,
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
  };

  const computeOptions = () => {
    return options.filter(o => !filterGroup?.filters?.map(f => f.key).includes(o.id));
  };

  return (
    <div className={classes.container}>
      <MuiAutocomplete
        options={computeOptions()}
        sx={{ width: 200 }}
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
            label={t('Add filter')}
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
