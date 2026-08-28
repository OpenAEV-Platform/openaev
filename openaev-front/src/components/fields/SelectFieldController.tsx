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
  Select,
  SelectContent,
  SelectHelperText,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@filigran/design-system';
import { type CSSProperties } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

export interface Item<T extends string = string> {
  value: T;
  label: string;
}
interface Props {
  name: string;
  label: string;
  items: Item[];
  style?: CSSProperties;
  /** Kept from the MUI signature: the library trigger sizes to its content, so
   *  the width lands on the wrapper below rather than on the control. */
  fullWidth?: boolean;
  required?: boolean;
  disabled?: boolean;
  multiple?: boolean;
}

// eslint-disable-next-line react-refresh/only-export-components
export const createItems = <T extends string>(vals: readonly T[]): Item<T>[] =>
  vals.map(v => ({
    value: v,
    label: v,
  }));

const SelectFieldController = ({
  name,
  label,
  items,
  style,
  fullWidth,
  multiple = false,
  required,
  disabled,
}: Props) => {
  const { control } = useFormContext();

  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState: { error } }) => {
        // `w-full` on the trigger resolves against this wrapper, so a wrapper
        // that shrink-wraps its child makes the 100% circular.
        const wrapperStyle = fullWidth
          ? {
              width: '100%',
              ...style,
            }
          : style;
        // The library Select carries a single string value. A multiple field is
        // therefore a Combobox — the nearest component that holds a set — which
        // adds a text filter and loses nothing the MUI field did.
        if (multiple) {
          const selected = items.filter(item => (field.value ?? []).includes(item.value));
          return (
            <div style={wrapperStyle}>
              <Combobox<Item>
                multiple
                options={items}
                value={selected}
                onValueChange={next => field.onChange((next as Item[]).map(i => i.value))}
                getOptionLabel={item => item.label}
                isOptionEqualToValue={(a, b) => a.value === b.value}
                disabled={disabled}
                error={!!error}
              >
                <ComboboxLabel>
                  {label}
                  {required ? ' *' : ''}
                </ComboboxLabel>
                <ComboboxField>
                  <ComboboxChips />
                  <ComboboxInput name={field.name} onBlur={field.onBlur} />
                  <ComboboxControls>
                    <ComboboxClear />
                    <ComboboxTrigger />
                  </ComboboxControls>
                </ComboboxField>
                <ComboboxContent />
                {error?.message ? <ComboboxHelperText>{error.message}</ComboboxHelperText> : null}
              </Combobox>
            </div>
          );
        }
        return (
          <div style={wrapperStyle}>
            <Select
              value={field.value ?? ''}
              onValueChange={field.onChange}
              name={field.name}
              disabled={disabled}
              required={required}
              error={!!error}
            >
              <SelectLabel required={required}>{label}</SelectLabel>
              <SelectTrigger className="w-full">
                <SelectValue placeholder={label} />
              </SelectTrigger>
              <SelectContent>
                {items.map(item => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
              {error?.message ? <SelectHelperText>{error.message}</SelectHelperText> : null}
            </Select>
          </div>
        );
      }}
    />
  );
};

export default SelectFieldController;
