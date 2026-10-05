import { Checkbox } from '@filigran/design-system';
import { type CSSProperties } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

interface Props {
  name: string;
  label: string;
  style?: CSSProperties;
}

const CheckboxFieldController = ({ name, label, style }: Props) => {
  const { control } = useFormContext();

  return (
    <Controller
      name={name}
      control={control}
      render={({ field }) => (
        <div style={style}>
          <Checkbox
            ref={field.ref}
            name={field.name}
            label={label}
            checked={field.value ?? false}
            onCheckedChange={checked => field.onChange(checked === true)}
            onBlur={field.onBlur}
          />
        </div>
      )}
    />
  );
};

export default CheckboxFieldController;
