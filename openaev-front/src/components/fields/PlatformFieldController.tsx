import { Autocomplete, Box, TextField } from '@mui/material';
import { type CSSProperties, type FunctionComponent } from 'react';
import { Controller, useFormContext } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { type Option } from '../../utils/Option';
import { useFormatter } from '../i18n';
import PlatformIcon from '../PlatformIcon';

const useStyles = makeStyles()(theme => ({
  icon: { display: 'inline-block' },
  text: {
    display: 'inline-block',
    marginLeft: theme.spacing(2),
  },
  autoCompleteIndicator: { display: 'none' },
}));

interface Props {
  label: string;
  name: string;
  required?: boolean;
  style?: CSSProperties;
}

const PlatformFieldController: FunctionComponent<Props> = ({
  name,
  label,
  required,
  style = {},
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t } = useFormatter();
  const { control } = useFormContext();

  const platformsOptions: Option[] = [
    {
      id: 'Windows',
      label: 'Windows',
    },
    {
      id: 'Linux',
      label: 'Linux',
    },
    {
      id: 'MacOS',
      label: 'MacOS',
    },
  ];

  return (
    <Controller
      name={name}
      control={control}
      defaultValue=""
      rules={{ required: `${label} is required` }}
      render={({ field, fieldState: { error } }) => (
        <Autocomplete
          multiple
          options={platformsOptions}
          openOnFocus
          autoHighlight
          style={style}
          noOptionsText={t('No available options')}
          slotProps={{ chip: { sx: { maxHeight: '24px' } } }}
          renderInput={
            params => (
              <TextField
                {...params}
                label={t(label)}
                fullWidth
                required={required}
                error={!!error}
                helperText={error?.message}
              />
            )
          }
          value={platformsOptions.filter(p => field.value?.map((v: string) => v)?.includes(p.id)) ?? null}
          onChange={(_event, platform) => {
            field.onChange(platform.map(p => p.id));
          }}
          renderOption={(props, option) => (
            <Box component="li" {...props}>
              <div className={classes.icon}>
                <PlatformIcon platform={option.id} width={15} />
              </div>
              <div className={classes.text}>{option.label}</div>
            </Box>
          )}
          classes={{ clearIndicator: classes.autoCompleteIndicator }}
        />
      )}
    />
  );
};

export default PlatformFieldController;
