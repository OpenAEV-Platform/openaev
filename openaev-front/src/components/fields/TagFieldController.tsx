import { type CSSProperties } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import TagField from './TagField';

interface Props {
  name: string;
  label: string;
  style?: CSSProperties;
  disabled?: boolean;
  required?: boolean;
  /** Help line under the field, always visible; an error message takes its place. */
  helperText?: string;
}

const TagFieldController = ({ name, label, style = {}, required = false, disabled = false, helperText }: Props) => {
  const { control } = useFormContext();

  return (
    <Controller
      name={name}
      control={control}
      render={({ field: { onChange, value }, fieldState: { error } }) => (
        <TagField
          label={label}
          fieldValue={value ?? []}
          fieldOnChange={onChange}
          error={error}
          style={style}
          disabled={disabled}
          required={required}
          helperText={helperText}
        />
      )}
    />
  );
};

export default TagFieldController;
