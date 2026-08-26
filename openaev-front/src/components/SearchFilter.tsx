import { Search } from '@mui/icons-material';
import { InputAdornment, TextField } from '@mui/material';
import { type ChangeEvent, type FunctionComponent, useEffect, useRef, useState } from 'react';

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

  // Controlled value so external keyword changes (e.g. "Clear filters"
  // resetting the text search) are reflected in the input.
  const [value, setValue] = useState(keyword ?? '');
  const valueRef = useRef(value);
  valueRef.current = value;

  // Cancellable debounce (the shared helper has no cancellation): an external
  // reset must be able to drop an in-flight keystroke, see below.
  const timerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const cancelPendingChange = () => {
    if (timerRef.current !== undefined) {
      clearTimeout(timerRef.current);
      timerRef.current = undefined;
    }
  };
  useEffect(() => cancelPendingChange, []);

  // Sync from the outside without fighting in-flight typing: the debounced
  // onChange echoes the (trimmed) typed value back through `keyword`, so only
  // reset when the external keyword genuinely diverges from what is typed.
  useEffect(() => {
    if ((keyword ?? '') !== valueRef.current.trim()) {
      // An external reset (e.g. "Clear filters") also drops any pending
      // debounced keystroke, which would otherwise re-apply the cleared
      // keyword right after the reset.
      cancelPendingChange();
      setValue(keyword ?? '');
    }
  }, [keyword]);

  const handleChange = ({ target }: ChangeEvent<HTMLInputElement>) => {
    setValue(target.value);
    if (typeof onChange === 'function') {
      cancelPendingChange();
      timerRef.current = setTimeout(() => onChange(target.value), debounceMs ?? 500);
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
      value={value}
      variant="outlined"
      size="small"
      placeholder={placeholder ?? `${t('Search these results')}...`}
      onChange={handleChange}
      onKeyDown={handleKeyDown}
      sx={theme => ({
        '& .MuiOutlinedInput-root': {
          'borderRadius': '5px',
          'padding': '0 10px',
          // Measured at 38 against neighbours at 36 on every list toolbar. The
          // variant selectors below carry higher specificity, so `inDrawer` and
          // `thin` keep their own 30.
          'height': 36,
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
