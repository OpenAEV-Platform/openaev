import { SearchField } from '@filigran/design-system';
import { type ChangeEvent, type FunctionComponent, useEffect, useRef, useState } from 'react';

import { useFormatter } from './i18n';

interface Props {
  keyword?: string;
  onChange?: (value?: string) => void;
  onSubmit?: (value?: string) => void;
  /** `small` and `thin` take the library's compact size; anything else the default one. */
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

  // The clear cross and Escape both come through here; a cleared search applies at once.
  const handleClear = () => {
    cancelPendingChange();
    setValue('');
    onChange?.('');
  };

  return (
    <SearchField
      aria-label={placeholder ?? t('Search these results')}
      placeholder={placeholder ?? `${t('Search these results')}...`}
      size={variant === 'small' || variant === 'thin' ? 'sm' : 'md'}
      fullWidth={fullWidth}
      name="keyword"
      value={value}
      onChange={handleChange}
      onSubmit={submitted => onSubmit?.(submitted)}
      onClear={handleClear}
      autoComplete="off"
    />
  );
};

export default SearchInput;
