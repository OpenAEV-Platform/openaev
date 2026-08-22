import {
  Combobox,
  ComboboxChips,
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
}

const PlatformFieldController: FunctionComponent<Props> = ({
  name,
  label,
  required,
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
        <Combobox<Option>
          multiple
          openOnFocus
          required={required}
          error={!!error}
          options={platformsOptions}
          value={platformsOptions.filter(p => field.value?.map((v: string) => v)?.includes(p.id)) ?? []}
          onValueChange={(platform) => {
            field.onChange((platform as Option[]).map(p => p.id));
          }}
          getOptionLabel={option => option.label ?? ''}
          isOptionEqualToValue={(option, v) => option.id === v.id}
          // The MUI field hid its clear control via a `classes` override.
          clearable={false}
          renderOption={option => (
            <>
              <div className={classes.icon}>
                <PlatformIcon platform={option.id} width={15} />
              </div>
              <div className={classes.text}>{option.label}</div>
            </>
          )}
        >
          <ComboboxLabel>{t(label)}</ComboboxLabel>
          <ComboboxField>
            <ComboboxChips />
            <ComboboxInput />
            <ComboboxControls>
              <ComboboxTrigger />
            </ComboboxControls>
          </ComboboxField>
          <ComboboxContent emptyMessage={t('No available options')} />
          {error?.message ? <ComboboxHelperText>{error.message}</ComboboxHelperText> : null}
        </Combobox>
      )}
    />
  );
};

export default PlatformFieldController;
