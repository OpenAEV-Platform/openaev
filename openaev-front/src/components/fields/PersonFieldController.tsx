import { Autocomplete as MuiAutocomplete, Box, TextField } from '@mui/material';
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
        <MuiAutocomplete
          value={options.find(o => o.id === field.value) || null}
          fullWidth
          options={options}
          onChange={(_, value) => field.onChange(value?.id ?? null)}
          getOptionLabel={option => option.label}
          isOptionEqualToValue={(option, value) => option.id === value.id}
          renderOption={(props, option) => (
            <Box component="li" {...props} key={option.id}>
              {option.label}
            </Box>
          )}
          renderInput={params => (
            <TextField {...params} label={label} variant="standard" error={!!error} helperText={error?.message} />
          )}
        />
      )}
    />
  );
};

export default PersonFieldController;
