import { type FunctionComponent, useEffect, useState } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { searchAssetGroupAsOption, searchAssetGroupByIdAsOption } from '../../actions/asset_groups/assetgroup-action';
import { type Option } from '../../utils/Option';
import { useFormatter } from '../i18n';
import SearchAutocompleteField from './SearchAutocompleteField';

interface Props {
  name: string;
  label: string;
  multiple: boolean;
  disabled?: boolean;
  required?: boolean;
}

const asIds = (value: unknown): string[] => {
  if (Array.isArray(value)) {
    return value.filter((id): id is string => typeof id === 'string' && id !== '');
  }
  return typeof value === 'string' && value !== '' ? [value] : [];
};

/**
 * Asset group picker of an "asset-group-reference" contract field: the selected asset group id
 * (or list of ids when multiple) is stored in the inject content under the field key.
 */
const AssetGroupReferenceFieldController: FunctionComponent<Props> = ({ name, label, multiple, disabled = false, required = false }) => {
  const { t } = useFormatter();
  const { control, getValues } = useFormContext();
  const [found, setFound] = useState<Option[]>([]);
  const [chosen, setChosen] = useState<Option[]>([]);
  const [text, setText] = useState('');

  // The names of the asset groups already stored, so their chips show a name and not an id.
  useEffect(() => {
    const ids = asIds(getValues(name));
    if (ids.length === 0) {
      return;
    }
    searchAssetGroupByIdAsOption(ids)
      .then((result: { data: Option[] }) => setChosen(result.data))
      .catch(() => setChosen([]));
  }, [name]);

  useEffect(() => {
    searchAssetGroupAsOption(text)
      .then((result: { data: Option[] }) => setFound(result.data))
      .catch(() => setFound([]));
  }, [text]);

  const options = [...chosen, ...found.filter(option => !chosen.some(c => c.id === option.id))];
  const remember = (ids: string[]) => setChosen(options.filter(option => ids.includes(option.id)));

  return (
    <Controller
      name={name}
      control={control}
      render={({ field: { onChange, value }, fieldState: { error } }) => {
        if (multiple) {
          return (
            <SearchAutocompleteField
              multiple
              label={label}
              placeholder={t('Search')}
              options={options}
              value={asIds(value)}
              onChange={(ids) => {
                remember(ids);
                onChange(ids);
              }}
              onInputChange={setText}
              required={required}
              disabled={disabled}
              error={!!error}
              helperText={error?.message}
            />
          );
        }
        return (
          <SearchAutocompleteField
            label={label}
            placeholder={t('Search')}
            options={options}
            value={asIds(value)[0]}
            onChange={(id) => {
              remember(id ? [id] : []);
              onChange(id ?? '');
            }}
            onInputChange={setText}
            required={required}
            disabled={disabled}
            error={!!error}
            helperText={error?.message}
          />
        );
      }}
    />
  );
};

export default AssetGroupReferenceFieldController;
