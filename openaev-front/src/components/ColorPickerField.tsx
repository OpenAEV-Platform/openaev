import { Icon } from '@filigran/design-system';
import { Popover } from '@mui/material';
import { type MouseEvent as ReactMouseEvent, useState } from 'react';
// @ts-expect-error react-color does not have types
import { SketchPicker } from 'react-color';
import { type Control, type FieldPath, type FieldValues, useController } from 'react-hook-form';

import TextFieldFds, { type TextFieldFdsProps } from './fields/TextFieldFds';

type Props<TFieldValues extends FieldValues = FieldValues> = Omit<TextFieldFdsProps, 'name' | 'value' | 'onChange' | 'endIcon'> & {
  control: Control<TFieldValues>;
  name: FieldPath<TFieldValues>;
};

interface Color { hex: string }

const ColorPickerField = <TFieldValues extends FieldValues = FieldValues>({ control, name, ...props }: Props<TFieldValues>) => {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  const { field } = useController({
    name,
    control,
  });

  return (
    <>
      <TextFieldFds
        {...props}
        name={name}
        onChange={field.onChange}
        onBlur={field.onBlur}
        value={field.value || ''}
        endIcon={{
          type: 'iconButton',
          icon: <Icon name="palette" size={16} aria-hidden />,
          label: 'open',
          onClick: (event: ReactMouseEvent<HTMLElement>) => setAnchorEl(event.currentTarget),
        }}
      />
      <Popover
        open={Boolean(anchorEl)}
        anchorEl={anchorEl}
        onClose={() => setAnchorEl(null)}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'center',
        }}
        transformOrigin={{
          vertical: 'top',
          horizontal: 'center',
        }}
      >
        <SketchPicker
          color={field.value || ''}
          onChange={(color: Color) => field.onChange(color.hex)}
        />
      </Popover>
    </>
  );
};

export default ColorPickerField;
