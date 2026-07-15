import { useEffect, useState } from 'react';

import { searchAiTargetAsOption, searchAiTargetByIdAsOption } from '../../../../actions/assets/aiTarget-actions';
import AutocompleteField from '../../../../components/fields/AutocompleteField';
import { type Option } from '../../../../utils/Option';

interface Props {
  label: string;
  value: string | undefined;
  onChange: (value: string | undefined) => void;
  required?: boolean;
  disabled?: boolean;
  error?: boolean;
}

/**
 * Single-select autocomplete for picking a pre-configured AI target asset, backed by the
 * platform `/ai_targets/options` endpoint. Mirrors the asset / asset group pickers so the
 * inject form offers a proper entity selector instead of a free-text id field.
 */
const AiTargetAutocompleteField = ({ label, value, onChange, required = false, disabled = false, error = false }: Props) => {
  const [options, setOptions] = useState<Option[]>([]);

  const searchOptions = async (searchText: string) => {
    const res = await searchAiTargetAsOption(searchText);
    setOptions(res.data as Option[]);
  };

  useEffect(() => {
    searchOptions('');
  }, []);

  // Resolve the label of a preselected value (edit mode) that may not be in the current page.
  useEffect(() => {
    let active = true;
    if (value) {
      searchAiTargetByIdAsOption([value]).then((res) => {
        if (!active) {
          return;
        }
        const resolved = res.data as Option[];
        setOptions((prev) => {
          const existingIds = new Set(prev.map(o => o.id));
          return [...resolved.filter(o => !existingIds.has(o.id)), ...prev];
        });
      });
    }
    return () => {
      active = false;
    };
  }, [value]);

  return (
    <AutocompleteField
      label={label}
      value={value}
      required={required}
      error={error}
      options={options}
      onChange={v => onChange(v)}
      onInputChange={searchOptions}
      variant="standard"
      disabled={disabled}
    />
  );
};

export default AiTargetAutocompleteField;
