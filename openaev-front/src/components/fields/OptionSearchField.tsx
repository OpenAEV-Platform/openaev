import { type ComboboxLabelPosition } from '@filigran/design-system';
import { type FunctionComponent, useEffect, useState } from 'react';

import { type Option } from '../../utils/Option';
import { useFormatter } from '../i18n';
import SearchAutocompleteField from './SearchAutocompleteField';

interface Props {
  label: string;
  labelPosition?: ComboboxLabelPosition;
  // The chosen options, with their names: their chips keep them whatever the search finds.
  value: Option[];
  onChange: (value: Option[]) => void;
  // The options matching a text, searched again on every input change.
  search: (text: string) => Promise<Option[]>;
  multiple?: boolean;
  required?: boolean;
  disabled?: boolean;
  error?: boolean;
  helperText?: string;
}

/**
 * Search field over a server-side list: the options are searched by text, and the chosen
 * options stay among them so their chips show a name and not an id.
 */
const OptionSearchField: FunctionComponent<Props> = ({
  label,
  labelPosition,
  value,
  onChange,
  search,
  multiple = true,
  required = false,
  disabled = false,
  error = false,
  helperText,
}) => {
  const { t } = useFormatter();
  const [found, setFound] = useState<Option[]>([]);
  const [text, setText] = useState('');

  useEffect(() => {
    search(text).then(setFound).catch(() => setFound([]));
  }, [text]);

  const options = [...value, ...found.filter(option => !value.some(chosen => chosen.id === option.id))];
  const toOptions = (ids: string[]) => ids.flatMap(id => options.filter(option => option.id === id));
  const common = {
    label,
    labelPosition,
    placeholder: t('Search'),
    options,
    onInputChange: setText,
    required,
    disabled,
    error,
    helperText,
  };
  if (multiple) {
    return (
      <SearchAutocompleteField
        {...common}
        multiple
        value={value.map(option => option.id)}
        onChange={(ids: string[]) => onChange(toOptions(ids))}
      />
    );
  }
  return (
    <SearchAutocompleteField
      {...common}
      value={value[0]?.id}
      onChange={(id: string | undefined) => onChange(id ? toOptions([id]) : [])}
    />
  );
};

export default OptionSearchField;
