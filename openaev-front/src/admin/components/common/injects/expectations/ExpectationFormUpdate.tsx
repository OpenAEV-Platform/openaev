import {
  Combobox,
  ComboboxChips,
  ComboboxClear,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
  Select,
  SelectContent,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@filigran/design-system';
import { Alert, Button, TextField as MuiTextField, TextField, Typography } from '@mui/material';
import { type FunctionComponent, type SyntheticEvent } from 'react';
import { Controller, type SubmitHandler, useForm } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../components/i18n';
import ItemSecurityPlatformType from '../../../../../components/ItemSecurityPlatformType';
import ScaleBar from '../../../../../components/scalebar/ScaleBar';
import { splitDuration } from '../../../../../utils/Time';
import { type ExpectationInput, type ExpectationInputForm, SECURITY_PLATFORM_TYPES } from './Expectation';
import { formProps, infoMessage } from './ExpectationFormUtils';
import { isTechnicalExpectation } from './ExpectationUtils';
import ExpectationGroupField from './field/ExpectationGroupField';

const useStyles = makeStyles()(theme => ({
  marginTop_2: { marginTop: theme.spacing(2) },
  buttons: {
    display: 'flex',
    placeContent: 'end',
    gap: theme.spacing(2),
    marginTop: theme.spacing(2),
  },
  duration: {
    marginTop: 20,
    width: '100%',
    display: 'flex',
    justifyContent: 'space-between',
    border: `1px solid ${theme.palette.primary.main}`,
    borderRadius: 4,
    padding: 15,
  },
  trigger: {
    fontFamily: 'Consolas, monaco, monospace',
    fontSize: 12,
    paddingTop: 15,
    color: theme.palette.primary.main,
  },
}));

interface Props {
  onSubmit: SubmitHandler<ExpectationInputForm>;
  handleClose: () => void;
  initialValues: ExpectationInput;
}

const ExpectationFormUpdate: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  initialValues,
}) => {
  const { t } = useFormatter();
  const { classes } = useStyles();

  const expirationTime = splitDuration(initialValues.expectation_expiration_time || 0);
  const formInitialValues: ExpectationInputForm = {
    ...initialValues,
    expiration_time_days: parseInt(expirationTime.days, 10),
    expiration_time_hours: parseInt(expirationTime.hours, 10),
    expiration_time_minutes: parseInt(expirationTime.minutes, 10),
  };
  const {
    control,
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting, isValid },
    getValues,
  } = useForm<ExpectationInputForm>(formProps(formInitialValues, t));
  const watchType = watch('expectation_type');

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmit(onSubmit)(e);
  };

  return (
    <form id="expectationForm" onSubmit={handleSubmitWithoutPropagation}>
      <div>
        <Select
          disabled
          value={getValues().expectation_type}
          error={!!errors.expectation_type}
        >
          <SelectLabel>{t('Type')}</SelectLabel>
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={getValues().expectation_type}>{t(getValues().expectation_type)}</SelectItem>
          </SelectContent>
        </Select>
      </div>
      {(getValues().expectation_type === 'ARTICLE' || getValues().expectation_type === 'CHALLENGE')
        && (
          <Alert
            severity="info"
            className={classes.marginTop_2}
          >
            {infoMessage(getValues().expectation_type, t)}
          </Alert>
        )}
      <MuiTextField
        variant="standard"
        fullWidth
        label={t('Name')}
        className={classes.marginTop_2}
        error={!!errors.expectation_name}
        helperText={
          errors.expectation_name && errors.expectation_name?.message
        }
        inputProps={register('expectation_name')}
      />
      <MuiTextField
        variant="standard"
        fullWidth
        label={t('Description')}
        className={classes.marginTop_2}
        multiline
        error={!!errors.expectation_description}
        helperText={
          errors.expectation_description && errors.expectation_description?.message
        }
        inputProps={register('expectation_description')}
      />
      <div className={classes.duration}>
        <div className={classes.trigger}>
          {t('Expiration time')}
        </div>
        <TextField
          variant="standard"
          type="number"
          label={t('Days')}
          style={{ width: '20%' }}
          slotProps={{ htmlInput: { ...register('expiration_time_days', { valueAsNumber: true }) } }}
        />
        <TextField
          variant="standard"
          type="number"
          label={t('Hours')}
          style={{ width: '20%' }}
          slotProps={{ htmlInput: { ...register('expiration_time_hours', { valueAsNumber: true }) } }}
        />
        <TextField
          variant="standard"
          type="number"
          label={t('Minutes')}
          style={{ width: '20%' }}
          slotProps={{ htmlInput: { ...register('expiration_time_minutes', { valueAsNumber: true }) } }}
        />
      </div>
      <div style={{ marginTop: 20 }}>
        <Typography variant="h4">{t('Scores')}</Typography>
        <ScaleBar expectationType={watchType} expectationExpectedScore={initialValues.expectation_score} />
      </div>
      <MuiTextField
        variant="standard"
        fullWidth
        label={t('Success score')}
        type="number"
        className={classes.marginTop_2}
        error={!!errors.expectation_score}
        helperText={
          errors.expectation_score && errors.expectation_score?.message
        }
        slotProps={{
          htmlInput: {
            ...register('expectation_score', { valueAsNumber: true }),
            min: 0,
            max: 100,
          },
        }}
      />
      {isTechnicalExpectation(getValues().expectation_type) && (
        <div className={classes.marginTop_2}>
          <Controller
            name="expectation_expected_security_platform_types"
            control={control}
            render={({ field }) => (
              <Combobox<string>
                multiple
                options={SECURITY_PLATFORM_TYPES}
                value={field.value ?? []}
                onValueChange={next => field.onChange(next as string[])}
                getOptionLabel={type => type}
                isOptionEqualToValue={(a, b) => a === b}
                renderOption={type => <ItemSecurityPlatformType type={type} />}
                clearable={false}
              >
                <ComboboxLabel>{t('Expected security platforms')}</ComboboxLabel>
                <ComboboxField>
                  <ComboboxChips />
                  {/* An empty selection used to read "Any security platform" in
                      the trigger; with chips the same sentence is the input's
                      placeholder, so it shows exactly when nothing is chosen. */}
                  <ComboboxInput
                    name={field.name}
                    placeholder={t('Any security platform')}
                  />
                  <ComboboxControls>
                    <ComboboxClear />
                    <ComboboxTrigger />
                  </ComboboxControls>
                </ComboboxField>
                <ComboboxContent />
              </Combobox>
            )}
          />
        </div>
      )}
      <ExpectationGroupField isTechnicalExpectation={isTechnicalExpectation(getValues().expectation_type)} control={control} />
      <div className={classes.buttons}>
        <Button
          variant="outlined"
          color="primary"
          onClick={handleClose}
          disabled={isSubmitting}
        >
          {t('Cancel')}
        </Button>
        <Button
          variant="contained"
          color="primary"
          type="submit"
          disabled={!isValid || isSubmitting}
        >
          {t('Update')}
        </Button>
      </div>
    </form>
  );
};

export default ExpectationFormUpdate;
