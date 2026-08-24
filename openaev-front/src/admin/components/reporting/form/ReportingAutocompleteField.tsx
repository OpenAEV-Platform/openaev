import {
  Combobox,
  ComboboxChips,
  ComboboxClear,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxHelperText,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
} from '@filigran/design-system';
import { type FunctionComponent, useMemo } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type Option } from '../../../../utils/Option';

/**
 * Search-driven autocomplete matching the platform's standard form fields
 * (full-size `variant="standard"`, like PersonFieldController) - the shared
 * AutocompleteField is outlined + small and visually off inside drawers.
 */
interface BaseProps {
  label: string;
  options: Option[];
  onInputChange: (search: string) => void;
  required?: boolean;
  error?: boolean;
  helperText?: string;
}

interface SingleProps extends BaseProps {
  multiple?: false;
  value: string | undefined;
  onChange: (value: string | undefined) => void;
}

interface MultipleProps extends BaseProps {
  multiple: true;
  value: string[];
  onChange: (value: string[]) => void;
}

type Props = SingleProps | MultipleProps;

const ReportingAutocompleteField: FunctionComponent<Props> = (props) => {
  const { label, options, onInputChange, required = false, error = false, helperText } = props;
  const { t } = useFormatter();

  const selected = useMemo(() => {
    if (props.multiple) {
      // Selected ids missing from the current (narrowed) search results keep a
      // stub option so their chip never disappears.
      return props.value.map(id => options.find(option => option.id === id) ?? {
        id,
        label: id,
      });
    }
    return options.find(option => option.id === props.value) ?? null;
  }, [props.multiple, props.value, options]);

  return (
    <Combobox<Option>
      openOnFocus
      multiple={props.multiple === true}
      options={options}
      value={selected}
      getOptionLabel={option => option.label ?? ''}
      isOptionEqualToValue={(option, value) => option.id === value.id}
      onInputChange={(search, meta) => {
        // MUI reported `reason === 'input'` to keep a programmatic reset from
        // firing the server search; the library states the same cause.
        if (meta.cause === 'type') {
          onInputChange(search);
        }
      }}
      onValueChange={(newValue) => {
        if (props.multiple) {
          props.onChange(((newValue ?? []) as Option[]).map(option => option.id));
        } else {
          props.onChange((newValue as Option | null)?.id);
        }
      }}
      required={required}
      error={error}
      renderOption={option => option.label}
    >
      <ComboboxLabel>{label}</ComboboxLabel>
      <ComboboxField>
        {props.multiple === true ? <ComboboxChips /> : null}
        <ComboboxInput />
        <ComboboxControls>
          <ComboboxClear />
          <ComboboxTrigger />
        </ComboboxControls>
      </ComboboxField>
      <ComboboxContent emptyMessage={t('No available options')} />
      {helperText ? <ComboboxHelperText>{helperText}</ComboboxHelperText> : null}
    </Combobox>
  );
};

export default ReportingAutocompleteField;
