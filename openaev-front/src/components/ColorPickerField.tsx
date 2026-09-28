import { Icon } from '@filigran/design-system';
import { Popover } from '@mui/material';
import { type MouseEvent as ReactMouseEvent, useState } from 'react';
// @ts-expect-error react-color does not have types
import { SketchPicker } from 'react-color';
import { type Control, type FieldPath, type FieldValues, useController } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import TextFieldFds, { type TextFieldFdsProps } from './fields/TextFieldFds';

type Props<TFieldValues extends FieldValues = FieldValues> = Omit<TextFieldFdsProps, 'name' | 'value' | 'onChange' | 'endIcon'> & {
  control: Control<TFieldValues>;
  name: FieldPath<TFieldValues>;
  showErrorStyle?: boolean;
  showErrorIcon?: boolean;
};

interface Color { hex: string }

const useStyles = makeStyles()(() => ({ errorLabel: { '& label': { color: 'var(--color-feedback-error-primary) !important' } } }));

const ColorPickerField = <TFieldValues extends FieldValues = FieldValues>({
  control,
  name,
  showErrorStyle = true,
  showErrorIcon = false,
  ...props
}: Props<TFieldValues>) => {
  const { classes, cx } = useStyles();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const hasError = Boolean(props.error);
  const helperText = hasError && showErrorStyle
    ? <span style={{ color: 'var(--color-feedback-error-primary)' }}>{props.helperText}</span>
    : props.helperText;
  const { field } = useController({
    name,
    control,
  });

  return (
    <>
      <TextFieldFds
        {...props}
        error={showErrorIcon ? props.error : undefined}
        helperText={helperText}
        className={cx(props.className, hasError && showErrorStyle && classes.errorLabel)}
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
