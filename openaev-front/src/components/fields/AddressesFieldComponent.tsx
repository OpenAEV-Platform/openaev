import { TravelExploreOutlined } from '@mui/icons-material';
import { CircularProgress, FormHelperText, IconButton, InputAdornment, TextField, Tooltip } from '@mui/material';
import { type CSSProperties, type FormEventHandler, useState } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { useFormatter } from '../i18n';

interface Props {
  name: string;
  label: string;
  style?: CSSProperties;
  helperText: string;
  disabled?: boolean;
  required?: boolean;
  /** When provided, renders a button that resolves values (e.g. IP from hostname) and merges them in. */
  onResolve?: () => Promise<string[]>;
  resolveDisabled?: boolean;
  resolveTooltip?: string;
}

const AddressesFieldComponent = ({ name, label, style = {}, disabled = false, required = false, helperText, onResolve, resolveDisabled = false, resolveTooltip }: Props) => {
  const { control } = useFormContext();
  const { t } = useFormatter();
  const [resolving, setResolving] = useState(false);

  return (
    <Controller
      control={control}
      name={name}
      render={({ field: { onChange, onBlur, value }, fieldState: { error } }) => {
        const value2 = value?.reduce((accumulator: string, current: string) => (accumulator === '' ? current : `${accumulator}\n${current}`), '');
        const onChange2: FormEventHandler<HTMLTextAreaElement | HTMLInputElement> = (event) => {
          if (event.currentTarget.value === '') {
            onChange([]);
          } else {
            onChange(event.currentTarget.value.split('\n'));
          }
        };
        const handleResolve = async () => {
          if (!onResolve) {
            return;
          }
          setResolving(true);
          try {
            const resolved = await onResolve();
            if (resolved && resolved.length > 0) {
              const current: string[] = Array.isArray(value) ? value.filter((v: string) => v !== '') : [];
              onChange(Array.from(new Set([...current, ...resolved])));
            }
          } finally {
            setResolving(false);
          }
        };
        return (
          <>
            <TextField
              variant="standard"
              fullWidth
              multiline
              rows={3}
              label={label}
              style={style}
              error={!!error}
              disabled={disabled}
              helperText={error ? error.message : null}
              onChange={onChange2}
              onBlur={onBlur}
              value={value2}
              required={required}
              slotProps={onResolve
                ? {
                    input: {
                      endAdornment: (
                        <InputAdornment
                          position="end"
                          sx={{
                            alignSelf: 'flex-start',
                            marginTop: 1,
                          }}
                        >
                          <Tooltip title={resolveTooltip ?? ''}>
                            <span>
                              <IconButton
                                size="small"
                                edge="end"
                                disabled={disabled || resolveDisabled || resolving}
                                onClick={handleResolve}
                                aria-label={resolveTooltip ?? t('Resolve')}
                              >
                                {resolving ? (<CircularProgress size={16} />) : (<TravelExploreOutlined fontSize="small" />)}
                              </IconButton>
                            </span>
                          </Tooltip>
                        </InputAdornment>
                      ),
                    },
                  }
                : undefined}
            />
            <FormHelperText>{t(helperText)}</FormHelperText>
          </>
        );
      }}
    />
  );
};

export default AddressesFieldComponent;
