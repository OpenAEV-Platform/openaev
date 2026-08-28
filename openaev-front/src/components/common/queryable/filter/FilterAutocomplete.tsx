import {
  Combobox,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxInput,
  ComboboxTrigger,
  IconButton,
} from '@filigran/design-system';
import { FilterListOffOutlined } from '@mui/icons-material';
import { Tooltip } from '@mui/material';
import { type CSSProperties, type FunctionComponent, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type Filter, type FilterGroup } from '../../../../utils/api-types';
import { type Option } from '../../../../utils/Option';
import { useFormatter } from '../../../i18n';
import { type FilterHelpers } from './FilterHelpers';
import { buildEmptyFilter } from './FilterUtils';

const useStyles = makeStyles()(theme => ({
  container: {
    display: 'flex',
    // One axis, one gap: the field and the clear button are centred on the same
    // line as the search box, 8px from it and 8px from each other. Measured
    // before: 0px to the search box and 20px to the button.
    alignItems: 'center',
    gap: theme.spacing(1),
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
    // `style` used to be spread onto the clear button, where it leaked margins
    // and made the gap to it 20px. It belongs to the row.
    <div className={classes.container} style={style}>
      <div style={{ width: domains ? '95%' : 200 }}>
        <Combobox
          options={options}
          value={null}
          onValueChange={(selectOptionValue) => {
            const next = selectOptionValue as typeof options[number] | null;
            if (next) {
              handleChange(next.id, next.operator);
              // The filter is added elsewhere and this field holds no value, so
              // the text must go with it. MUI cleared it through its own `reset`
              // cause, which the `type`-only guard below drops.
              setInputValue('');
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
        >
          <ComboboxField>
            <ComboboxInput
              placeholder={domains
                ? t('Please choose a scenario or simulation, or leave this field blank to include all scenarios and atomic tests')
                : t('Add filter')}
            />
            <ComboboxControls>
              <ComboboxTrigger />
            </ComboboxControls>
          </ComboboxField>
          <ComboboxContent />
        </Combobox>
      </div>
      <Tooltip title={t('Clear filters')}>
        <IconButton
          size="md"
          priority="tertiary"
          data-testid="clear-filters"
          aria-label={t('Clear filters')}
          onClick={handleClearFilters}
          icon={<FilterListOffOutlined fontSize="small" />}
        />
      </Tooltip>
    </div>
  );
};

export default FilterAutocomplete;
