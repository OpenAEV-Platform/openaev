import { Search } from '@mui/icons-material';
import { InputAdornment, TextField } from '@mui/material';
import { type ChangeEvent, type FunctionComponent, useCallback } from 'react';
import { makeStyles } from 'tss-react/mui';

import { debounce } from '../utils/utils';
import { useFormatter } from './i18n';

const useStyles = makeStyles()(theme => ({
  searchRoot: {
    'borderRadius': 4,
    'padding': '0 10px',
    'backgroundColor': theme.palette.background.secondary,
    '&.inDrawer': { height: 30 },
    '&.topBar': {
      marginRight: 5,
      minWidth: 550,
      width: '50%',
    },
    '&.thin': { height: 30 },
  },
  searchInput: {
    'transition': theme.transitions.create('width'),
    'width': 200,
    '&:focus': { width: 350 },
    '&.small, &.thin': {
      'width': 150,
      '&:focus': { width: 250 },
    },
  },
}));

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
  const { classes } = useStyles();
  const { t } = useFormatter();

  const classRoot = `${classes.searchRoot} ${variant ?? ''}`.trim();
  const inputClass = `${classes.searchInput} ${variant ?? ''}`.trim();

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
      sx={{
        '& .MuiOutlinedInput-root': {
          '& fieldset': { borderColor: 'transparent' },
          '&:hover fieldset': { borderColor: 'rgba(255, 255, 255, 0.7)' },
          '&.Mui-focused fieldset': { borderColor: 'primary.main' },
          '&.Mui-focused:hover fieldset': { borderColor: 'primary.main' },
        },
        '& .MuiInputBase-input::placeholder': { opacity: 0.7 },
      }}
      InputProps={{
        startAdornment: (
          <InputAdornment position="start">
            <Search sx={{
              fontSize: 20,
              opacity: 0.7,
            }}
            />
          </InputAdornment>
        ),
        classes: {
          root: classRoot,
          input: inputClass,
        },
      }}
      autoComplete="off"
    />
  );
};

export default SearchInput;
