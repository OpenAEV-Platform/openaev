import { FormControl, FormHelperText, InputLabel, MenuItem, Select } from '@mui/material';
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

const SelectFieldController = ({ name, label, items, style, multiple = false, required, disabled }: Props) => {
  const { control } = useFormContext();

  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState: { error } }) => (
        <FormControl fullWidth error={!!error}>
          <InputLabel id={`select-label-${name}`} error={!!error}>{`${label}${required ? ' *' : ''}`}</InputLabel>
          <Select
            {...field}
            labelId={`select-label-${name}`}
            id={`select-label-${name}`}
            style={style}
            disabled={disabled}
            multiple={multiple}
            renderValue={(v: string | string[]) => Array.isArray(v)
              ? items.filter(item => v.includes(item.value)).map(item => item.label).join(', ')
              : items.find(item => item.value === v)?.label ?? ''}
          >
            {items.map(item => (
              <MenuItem key={item.value} value={item.value}>
                {item.label}
              </MenuItem>
            ))}
          </Select>
          {error && <FormHelperText>{error.message}</FormHelperText>}
        </FormControl>
      )}
    />
  );
};

export default SelectFieldController;
