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
import { type FunctionComponent, type SyntheticEvent, useEffect, useState } from 'react';
import { Controller, type SubmitHandler, useForm } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { type LoggedHelper } from '../../../../../actions/helper';
import { useFormatter } from '../../../../../components/i18n';
import ItemSecurityPlatformType from '../../../../../components/ItemSecurityPlatformType';
import ScaleBar from '../../../../../components/scalebar/ScaleBar';
import { useHelper } from '../../../../../store';
import { type InjectExpectationOutput, type PlatformSettings } from '../../../../../utils/api-types';
import { splitDuration } from '../../../../../utils/Time';
import { type ExpectationInput, type ExpectationInputForm, SECURITY_PLATFORM_TYPES } from './Expectation';
import { formProps, infoMessage } from './ExpectationFormUtils';
import { isTechnicalExpectation } from './ExpectationUtils';
import ExpectationGroupField from './field/ExpectationGroupField';
import useExpectationExpirationTime from './useExpectationExpirationTime';

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
  availableExpectations: ExpectationInput[];
  onSubmit: SubmitHandler<ExpectationInputForm>;
  handleClose: () => void;
}

const ExpectationFormCreate: FunctionComponent<Props> = ({
  availableExpectations = [],
  onSubmit,
  handleClose,
}) => {
  const { t } = useFormatter();
  const { classes } = useStyles();

  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));
  const availableTypes = Array.from(new Set(availableExpectations.map(e => e.expectation_type)));
  const initialType = availableTypes[0] ?? 'MANUAL';
  const [expectationType, setExpectationType] = useState<string>(initialType);

  const expectationExpirationTime = useExpectationExpirationTime(initialType as InjectExpectationOutput['inject_expectation_type']);

  const getExpectationDefaultScoreByType = (expectationType: string): number => {
    if (expectationType === 'MANUAL') {
      return settings.expectation_manual_default_score_value;
    } else {
      return 100;
    }
  };

  const computeValuesFromType = (type: string): ExpectationInputForm => {
    const expectationDefinition = availableExpectations.find(pe => pe.expectation_type === type);
    if (expectationDefinition) {
      const expirationTime = splitDuration(expectationDefinition.expectation_expiration_time || 0);
      return {
        expectation_type: expectationDefinition.expectation_type ?? '',
        expectation_name: expectationDefinition.expectation_name ?? '',
        expectation_description: expectationDefinition.expectation_description ?? '',
        expectation_score: expectationDefinition.expectation_score > 0
          ? expectationDefinition.expectation_score
          : getExpectationDefaultScoreByType(expectationDefinition.expectation_type),
        expectation_expectation_group: expectationDefinition.expectation_expectation_group ?? false,
        expectation_expected_security_platform_types:
          expectationDefinition.expectation_expected_security_platform_types ?? [],
        expiration_time_days: Number.parseInt(expirationTime.days, 10),
        expiration_time_hours: Number.parseInt(expirationTime.hours, 10),
        expiration_time_minutes: Number.parseInt(expirationTime.minutes, 10),
        expectation_is_predefined: expectationDefinition.expectation_is_predefined,
      };
    }
    const expirationTime = splitDuration(expectationExpirationTime || 0);
    return {
      expectation_type: expectationType,
      expectation_name: '',
      expectation_description: '',
      expectation_score: getExpectationDefaultScoreByType(expectationType),
      expectation_expectation_group: false,
      expectation_expected_security_platform_types: [],
      expiration_time_days: Number.parseInt(expirationTime.days, 10),
      expiration_time_hours: Number.parseInt(expirationTime.hours, 10),
      expiration_time_minutes: Number.parseInt(expirationTime.minutes, 10),
      expectation_is_predefined: false,
    };
  };

  const initialValues: ExpectationInputForm = computeValuesFromType(initialType);

  const {
    control,
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isValid },
    watch,
    reset,
    setValue,
    getValues,
  } = useForm<ExpectationInputForm>(formProps(initialValues, t));
  const watchType = watch('expectation_type');

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmit(onSubmit)(e);
  };

  useEffect(() => {
    if (watchType) {
      reset(computeValuesFromType(watchType));
    }
  }, [watchType, reset, availableExpectations]);

  useEffect(() => {
    if (!watchType && initialType) {
      setValue('expectation_type', initialType, { shouldValidate: true });
    }
  }, [watchType, initialType, setValue]);

  return (
    <form id="expectationForm" onSubmit={handleSubmitWithoutPropagation}>
      <div>
        <Select
          value={expectationType}
          onValueChange={(next) => {
            const selectedType = next;
            setExpectationType(selectedType);
            setValue('expectation_type', selectedType, { shouldValidate: true });
          }}
          error={!!errors.expectation_type}
        >
          <SelectLabel>{t('Type')}</SelectLabel>
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {availableTypes.map(type => (<SelectItem key={type} value={type}>{t(type)}</SelectItem>))}
          </SelectContent>
        </Select>
      </div>
      {(watchType === 'ARTICLE' || watchType === 'CHALLENGE')
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
        helperText={errors.expectation_name?.message}
        slotProps={{
          htmlInput: { ...register('expectation_name') },
          inputLabel: { required: true },
        }}
      />
      <MuiTextField
        variant="standard"
        fullWidth
        label={t('Description')}
        className={classes.marginTop_2}
        multiline
        error={!!errors.expectation_description}
        helperText={errors.expectation_description?.message}
        slotProps={{ htmlInput: { ...register('expectation_description') } }}
      />
      {(watchType !== 'VULNERABILITY') && (
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
            slotProps={{ htmlInput: { ...register('expiration_time_hours', { valueAsNumber: true }) } }}
            type="number"
            label={t('Hours')}
            style={{ width: '20%' }}
          />
          <TextField
            variant="standard"
            slotProps={{ htmlInput: { ...register('expiration_time_minutes', { valueAsNumber: true }) } }}
            type="number"
            label={t('Minutes')}
            style={{ width: '20%' }}
          />
        </div>
      )}

      <div style={{ marginTop: 20 }}>
        <Typography variant="h4">{t('Scores')}</Typography>
        <ScaleBar expectationType={watchType} expectationExpectedScore={watch('expectation_score')} />
      </div>
      <MuiTextField
        variant="standard"
        fullWidth
        label={t('Success score')}
        type="number"
        className={classes.marginTop_2}
        error={!!errors.expectation_score}
        helperText={
          errors.expectation_score?.message
        }
        slotProps={{
          htmlInput: {
            ...register('expectation_score', { valueAsNumber: true }),
            min: 0,
            max: 100,
          },
        }}
      />
      {isTechnicalExpectation(watchType) && (
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
      {(watchType !== 'VULNERABILITY') && (
        <ExpectationGroupField isTechnicalExpectation={isTechnicalExpectation(watchType)} control={control} />
      )}
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
          {t('Create')}
        </Button>
      </div>
    </form>
  );
};

export default ExpectationFormCreate;
