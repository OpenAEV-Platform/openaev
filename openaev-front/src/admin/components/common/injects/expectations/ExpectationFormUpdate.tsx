import { Alert, Button, InputLabel, MenuItem, Select as MUISelect, TextField as MuiTextField, TextField, Typography } from '@mui/material';
import { type FunctionComponent, type SyntheticEvent } from 'react';
import { type SubmitHandler, useForm } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../components/i18n';
import ScaleBar from '../../../../../components/scalebar/ScaleBar';
import { splitDuration } from '../../../../../utils/Time';
import { type ExpectationInput, type ExpectationInputForm } from './Expectation';
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
        <InputLabel id="input-type">{t('Type')}</InputLabel>
        <MUISelect
          disabled
          labelId="input-type"
          value={getValues().expectation_type}
          variant="standard"
          fullWidth
          error={!!errors.expectation_type}
          inputProps={register('expectation_type')}
        >
          <MenuItem value={getValues().expectation_type}>{t(getValues().expectation_type)}</MenuItem>
        </MUISelect>
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
          inputProps={register('expiration_time_days')}
        />
        <TextField
          variant="standard"
          type="number"
          label={t('Hours')}
          style={{ width: '20%' }}
          inputProps={register('expiration_time_hours')}
        />
        <TextField
          variant="standard"
          type="number"
          label={t('Minutes')}
          style={{ width: '20%' }}
          inputProps={register('expiration_time_minutes')}
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
        inputProps={register('expectation_score')}
      />
      <ExpectationGroupField isTechnicalExpectation={isTechnicalExpectation(getValues().expectation_type)} control={control} />
      <div className={classes.buttons}>
        <Button
          onClick={handleClose}
          disabled={isSubmitting}
        >
          {t('Cancel')}
        </Button>
        <Button
          color="secondary"
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
