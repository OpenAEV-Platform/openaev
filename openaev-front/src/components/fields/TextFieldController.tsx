import { Visibility, VisibilityOff } from '@mui/icons-material';
import {
  IconButton,
  InputAdornment,
  TextField,
  type TextFieldVariants,
} from '@mui/material';
import { type CSSProperties, useState } from 'react';
import { Controller, useFormContext } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import DOTS from '../../constants/Strings';

interface Props {
  name: string;
  label?: string;
  multiline?: boolean;
  rows?: number;
  required?: boolean;
  disabled?: boolean;
  style?: CSSProperties;
  variant?: TextFieldVariants;
  placeholder?: string;
  size?: 'medium' | 'small';
  endAdornmentLabel?: string;
  startAdornmentLabel?: string;
  type?: 'number' | 'text' | 'password';
  defaultValue?: string;
  noHelperText?: boolean;
  writeOnly?: boolean;
}

const useStyles = makeStyles()(theme => ({ root: { '& .MuiOutlinedInput-root': { background: theme.palette.background.code } } }));

const TextFieldController = ({
  name,
  label = '',
  multiline = false,
  rows,
  required = false,
  disabled = false,
  style = {},
  variant = 'standard',
  placeholder = '',
  size = 'medium',
  endAdornmentLabel,
  startAdornmentLabel,
  type = 'text',
  defaultValue = '',
  noHelperText = false,
  writeOnly = false,
}: Props) => {
  const { control } = useFormContext();
  const { classes } = useStyles();

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

        const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
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
          <TextField
            {...field}
            type={showPassword ? 'text' : type}
            className={classes.root}
            label={required ? `${label}*` : label}
            fullWidth
            onChange={handleChange}
            error={!!error}
            helperText={!noHelperText && error ? error.message : null}
            multiline={multiline}
            rows={rows}
            aria-label={label}
            disabled={disabled}
            placeholder={placeholder}
            style={style}
            variant={variant}
            value={isMasked ? DOTS : field.value}
            size={size}
            slotProps={{
              input: {
                ...(type === 'password'
                  ? {
                      endAdornment: (
                        <IconButton
                          disabled={writeOnly && isOriginalValue}
                          aria-label={
                            showPassword ? 'Hide the password' : 'Display the password'
                          }
                          onClick={handleClickShowPassword}
                          edge="end"
                        >
                          {showPassword ? (<VisibilityOff fontSize="small" />) : (<Visibility fontSize="small" />)}
                        </IconButton>
                      ),
                    }
                  : {}),
                ...(endAdornmentLabel
                  ? {
                      endAdornment: (
                        <InputAdornment position="end">
                          {endAdornmentLabel}
                        </InputAdornment>
                      ),
                    }
                  : {}),
                ...(startAdornmentLabel
                  ? {
                      startAdornment: (
                        <InputAdornment sx={{ alignSelf: 'flex-start' }} position="start">
                          {startAdornmentLabel}
                        </InputAdornment>
                      ),
                    }
                  : {}),
              },
            }}
          />
        );
      }}
    />
  );
};

export default TextFieldController;
