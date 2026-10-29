import {
  Combobox,
  ComboboxChips,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxHelperText,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
} from '@filigran/design-system';
import { type FunctionComponent, type ReactNode } from 'react';
import { type GlobalError } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { type Option } from '../../utils/Option';
import { useFormatter } from '../i18n';

const useStyles = makeStyles()(() => ({
  icon: {
    paddingTop: 4,
    display: 'inline-block',
  },
  text: {
    display: 'inline-block',
    flexGrow: 1,
    marginLeft: 10,
  },
}));

interface Props {
  label: string;
  options: Option[];
  fieldValue: string[];
  fieldOnChange: (values: string[]) => void;
  icon?: ReactNode;
  error?: GlobalError;
  placeholder?: string;
  disabled?: boolean;
  required?: boolean;
}

/**
 * Presentational multi-selector over a list of entities identified by their id.
 * Holds no data-fetching and no form binding: callers provide the options and
 * the value/onChange pair, exactly like TagField.
 */
const EntityMultiSelectField: FunctionComponent<Props> = ({
  label,
  options,
  fieldValue,
  fieldOnChange,
  icon,
  error,
  placeholder = '',
  disabled = false,
  required = false,
}) => {
  const { classes } = useStyles();
  const { t } = useFormatter();

  return (
    <Combobox<Option>
      multiple
      disabled={disabled}
      required={required}
      error={!!error}
      options={options}
      value={options.filter(option => fieldValue.includes(option.id))}
      onValueChange={value => fieldOnChange((value as Option[]).map(option => option.id))}
      getOptionLabel={option => option.label ?? ''}
      isOptionEqualToValue={(option, v) => option.id === v.id}
      // The MUI field carried `disableClearable` AND hid the clear indicator
      // through a `classes` override — two ways of saying the same thing.
      clearable={false}
      // `clearOnBlur={false}` on the MUI field: the typed text survives a blur
      // instead of being re-synced from the selection.
      keepInputOnBlur
      renderOption={option => (
        <>
          {icon && <div className={classes.icon}>{icon}</div>}
          <div className={classes.text}>{option.label}</div>
        </>
      )}
    >
      <ComboboxLabel>{label}</ComboboxLabel>
      <ComboboxField>
        <ComboboxChips />
        <ComboboxInput placeholder={placeholder} />
        <ComboboxControls>
          <ComboboxTrigger />
        </ComboboxControls>
      </ComboboxField>
      <ComboboxContent emptyMessage={t('No available options')} />
      {error?.message ? <ComboboxHelperText>{error.message}</ComboboxHelperText> : null}
    </Combobox>
  );
};

export default EntityMultiSelectField;
