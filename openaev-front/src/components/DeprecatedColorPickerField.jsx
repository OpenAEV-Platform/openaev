import { Icon } from '@filigran/design-system';
import { Popover } from '@mui/material';
import { useState } from 'react';
import { SketchPicker } from 'react-color';
import { Field } from 'react-final-form';

import TextFieldFds from './fields/TextFieldFds';

const ColorPickerFieldBase = ({
  label,
  input: { onChange, ...inputProps },
  meta: { touched, invalid, error, submitError },
  ...others
}) => {
  const [anchorEl, setAnchorEl] = useState(null);
  const handleChange = (color) => {
    onChange(color && color.hex ? color.hex : '');
  };
  const message = touched && (error || submitError);
  const hasError = touched && invalid;
  const errorColor = 'var(--color-feedback-error-primary)';
  return (
    <>
      <TextFieldFds
        label={hasError ? <span style={{ color: errorColor }}>{label}</span> : label}
        // Show validation text without switching the end icon to the error glyph.
        error={undefined}
        helperText={hasError ? <span style={{ color: errorColor }}>{message}</span> : undefined}
        {...inputProps}
        onChange={onChange}
        onClick={event => setAnchorEl(event.currentTarget)}
        {...others}
        endIcon={{
          type: 'iconButton',
          icon: <Icon name="palette" size={16} aria-hidden />,
          label: 'open',
          onClick: event => setAnchorEl(event.currentTarget),
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
          color={inputProps.value || ''}
          onChangeComplete={color => handleChange(color)}
        />
      </Popover>
    </>
  );
};

/**
 * @deprecated The component use old form libnary react-final-form
 */
const DeprecatedColorPickerField = props => (
  <Field name={props.name} component={ColorPickerFieldBase} {...props} />
);

export default DeprecatedColorPickerField;
