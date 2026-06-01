import { ColorLensOutlined } from '@mui/icons-material';
import { IconButton, InputAdornment, Popover, TextField as MuiTextField, type TextFieldProps } from '@mui/material';
import { type MouseEvent as ReactMouseEvent, useState } from 'react';
// @ts-expect-error react-color does not have types
import { SketchPicker } from 'react-color';
import { type Control, type FieldPath, type FieldValues, useController } from 'react-hook-form';

type Props<TFieldValues extends FieldValues = FieldValues> = Omit<TextFieldProps, 'name'> & {
  control: Control<TFieldValues>;
  name: FieldPath<TFieldValues>;
};

interface Color { hex: string }

const ColorPickerField = <TFieldValues extends FieldValues = FieldValues>(props: Props<TFieldValues>) => {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  const { field } = useController({
    name: props.name,
    control: props.control,
  });

  return (
    <>
      <MuiTextField
        InputProps={{
          endAdornment: (
            <InputAdornment position="end">
              <IconButton
                aria-label="open"
                onClick={(event: ReactMouseEvent<HTMLElement>) => setAnchorEl(event.currentTarget)}
                disabled={props.disabled}
              >
                <ColorLensOutlined />
              </IconButton>
            </InputAdornment>
          ),
        }}
        onChange={field.onChange}
        value={field.value || ''}
        {...props}
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
