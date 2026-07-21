import { Search } from '@mui/icons-material';
import { InputAdornment, TextField } from '@mui/material';
import { type ChangeEvent, type FunctionComponent, useCallback } from 'react';

import { debounce } from '../utils/utils';
import { useFormatter } from './i18n';

interface Props {
  keyword?: string;
  onChange?: (value?: string) => void;
  onSubmit?: (value?: string) => void;
  variant?: string;
  fullWidth?: boolean;
  placeholder?: string;
  debounceMs?: number;
}

const SearchInput: FunctionComponent<Props> = ({
  onChange,
  onSubmit,
  variant,
  keyword,
  fullWidth,
  placeholder,
  debounceMs,
}) => {
  // Standard hooks
  const { t } = useFormatter();

  // The variant name doubles as a CSS class on the input root and input
  // element, driving the variant-specific selectors in the sx below.
  const variantClass = variant ?? '';

  const debouncedChangeHandler = useCallback(
    debounce((value?: string) => onChange?.(value), debounceMs),
    [onChange, debounceMs],
  );

  const handleChange = ({ target }: ChangeEvent<HTMLInputElement>) => {
    if (typeof onChange === 'function') {
      debouncedChangeHandler(target.value);
    }
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      const { target } = event as unknown as ChangeEvent<HTMLInputElement>;
      onSubmit?.(target.value);
    }
  };

  return (
    <TextField
      fullWidth={fullWidth}
      name="keyword"
      defaultValue={keyword}
      variant="outlined"
      size="small"
      placeholder={placeholder ?? `${t('Search these results')}...`}
      onChange={handleChange}
      onKeyDown={handleKeyDown}
      sx={theme => ({
        '& .MuiOutlinedInput-root': {
          'borderRadius': '5px',
          'padding': '0 10px',
          'backgroundColor': theme.palette.background.paper,
          // OpenCTI-aligned icon-to-text gap: a tight 4px between the leading
          // search icon and the placeholder, instead of MUI's default 8px
          // adornment margin.
          '& .MuiInputAdornment-positionStart': { marginRight: theme.spacing(0.5) },
          '&.inDrawer': { height: 30 },
          // OpenCTI-aligned top bar field: fills its wrapper (the TopBar
          // constrains min/max width), sits on the secondary background with
          // no visible border.
          '&.topBar': {
            'marginRight': '5px',
            'width': '100%',
            'borderRadius': '4px',
            'backgroundColor': theme.palette.background.secondary ?? theme.palette.background.paper,
            '& fieldset': { borderColor: 'transparent' },
          },
          '&.thin': { height: 30 },
        },
        '& .MuiOutlinedInput-input': {
          'transition': theme.transitions.create('width'),
          'width': 200,
          '&:focus': { width: 350 },
          '&.topBar': { width: '100%' },
          '&.small, &.thin': {
            'width': 150,
            '&:focus': { width: 250 },
          },
        },
      })}
      InputProps={{
        startAdornment: (
          <InputAdornment position="start">
            <Search fontSize="small" />
          </InputAdornment>
        ),
        className: variantClass,
      }}
      inputProps={{ className: variantClass }}
      autoComplete="off"
    />
  );
};

export default SearchInput;
