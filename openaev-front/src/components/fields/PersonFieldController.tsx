import {
  Combobox,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxHelperText,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
} from '@filigran/design-system';
import { type FunctionComponent } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { type UserHelper } from '../../actions/helper';
import { fetchPlayers } from '../../actions/users/User';
import { useHelper } from '../../store';
import { type User } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import useDataLoader from '../../utils/hooks/useDataLoader';
import { type Option } from '../../utils/Option';

interface Props {
  name: string;
  label: string;
}

const personLabel = (user: User): string => {
  const name = [user.user_firstname, user.user_lastname].filter(Boolean).join(' ').trim();
  return name || user.user_email || user.user_id;
};

const PersonFieldController: FunctionComponent<Props> = ({ name, label }) => {
  const dispatch = useAppDispatch();
  const { control } = useFormContext();
  const { usersMap } = useHelper((helper: UserHelper) => ({ usersMap: helper.getUsersMap() }));
  useDataLoader(() => {
    dispatch(fetchPlayers());
  });

  const options: Option[] = (Object.values(usersMap ?? {}) as User[]).map(user => ({
    id: user.user_id,
    label: personLabel(user),
  }));

  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState: { error } }) => (
        <Combobox<Option>
          options={options}
          value={options.find(o => o.id === field.value) ?? null}
          onValueChange={value => field.onChange((value as Option | null)?.id ?? null)}
          getOptionLabel={option => option.label}
          isOptionEqualToValue={(option, value) => option.id === value.id}
          error={!!error}
          renderOption={option => option.label}
        >
          <ComboboxLabel>{label}</ComboboxLabel>
          <ComboboxField>
            <ComboboxInput onBlur={field.onBlur} name={field.name} ref={field.ref} />
            <ComboboxControls>
              <ComboboxTrigger />
            </ComboboxControls>
          </ComboboxField>
          <ComboboxContent />
          {error?.message ? <ComboboxHelperText>{error.message}</ComboboxHelperText> : null}
        </Combobox>
      )}
    />
  );
};

export default PersonFieldController;
