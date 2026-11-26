import { type FunctionComponent, useEffect, useState } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { searchAssetGroupAsOption, searchAssetGroupByIdAsOption } from '../../actions/asset_groups/assetgroup-action';
import { type Option } from '../../utils/Option';
import OptionSearchField from './OptionSearchField';

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

const searchAssetGroups = (text: string) => searchAssetGroupAsOption(text)
  .then((result: { data: Option[] }) => result.data);

/**
 * Asset group picker of an "asset-group-reference" contract field: the selected asset group id
 * (or list of ids when multiple) is stored in the inject content under the field key.
 */
const AssetGroupReferenceFieldController: FunctionComponent<Props> = ({ name, label, multiple, disabled = false, required = false }) => {
  const { control, getValues } = useFormContext();
  const [chosen, setChosen] = useState<Option[]>([]);

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

  return (
    <Controller
      name={name}
      control={control}
      render={({ field: { onChange, value }, fieldState: { error } }) => {
        const ids = asIds(value);
        return (
          <OptionSearchField
            label={label}
            multiple={multiple}
            value={ids.map(id => chosen.find(option => option.id === id) ?? {
              id,
              label: id,
            })}
            onChange={(options) => {
              setChosen(options);
              const chosenIds = options.map(option => option.id);
              onChange(multiple ? chosenIds : (chosenIds[0] ?? ''));
            }}
            search={searchAssetGroups}
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
