import { Icon } from '@filigran/design-system';
import { type CSSProperties, type ReactNode, useState } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import DOTS from '../../constants/Strings';
import TextFieldFds from './TextFieldFds';

interface Props {
  name: string;
  label?: string;
  multiline?: boolean;
  rows?: number;
  required?: boolean;
  disabled?: boolean;
  readOnly?: boolean;
  style?: CSSProperties;
  placeholder?: string;
  helperText?: string;
  type?: 'number' | 'text' | 'password';
  min?: number;
  max?: number;
  step?: number;
  defaultValue?: string;
  noHelperText?: boolean;
  writeOnly?: boolean;
  /** Unit or suffix drawn inside the field (library `endText`; dropped by the library on a number field). */
  endAdornmentLabel?: string;
  /** Content of the info bubble next to the label. */
  infoTooltip?: ReactNode;
}

const TextFieldController = ({
  name,
  label = '',
  multiline = false,
  rows,
  required = false,
  disabled = false,
  readOnly = false,
  style = {},
  placeholder = '',
  helperText,
  type = 'text',
  min,
  max,
  step,
  defaultValue = '',
  noHelperText = false,
  writeOnly = false,
  endAdornmentLabel,
  infoTooltip,
}: Props) => {
  const { control } = useFormContext();

  const [isOriginalValue, setIsOriginalValue] = useState(true);
  const [showPassword, setShowPassword] = useState(false);
  const handleClickShowPassword = () => setShowPassword(show => !show);

  // Remove mask dots to keep only user input
  const stripDots = (s: string) => s.replaceAll('•', '');

  return (
    <Controller
      name={name}
      control={control}
      defaultValue={defaultValue}
      render={({ field, fieldState: { error } }) => {
        const isMasked = writeOnly && isOriginalValue && !!field.value;

        const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
          // First user input replaces the masked value
          if (isMasked) {
            const next = stripDots(e.target.value);
            field.onChange(next);
          } else {
            field.onChange(e);
          }
          setIsOriginalValue(false);
        };

        return (
          <TextFieldFds
            {...field}
            type={showPassword ? 'text' : type}
            label={label || undefined}
            required={required}
            onChange={handleChange}
            error={!noHelperText && error ? error.message : !!error}
            helperText={helperText}
            multiline={multiline}
            rows={rows}
            disabled={disabled}
            placeholder={placeholder}
            style={style}
            value={isMasked ? DOTS : field.value}
            readOnly={readOnly}
            min={min}
            max={max}
            step={step}
            endText={endAdornmentLabel}
            infoTooltip={infoTooltip}
            endIcon={type === 'password'
              ? {
                  type: 'iconButton',
                  icon: <Icon name={showPassword ? 'eye-off' : 'eye'} size={16} aria-hidden />,
                  disabled,
                  onClick: handleClickShowPassword,
                  label: showPassword ? 'Hide the password' : 'Display the password',
                }
              : undefined}
          />
        );
      }}
    />
  );
};

export default TextFieldController;
