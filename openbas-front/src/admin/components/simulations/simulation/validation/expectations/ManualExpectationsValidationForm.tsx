import { zodResolver } from '@hookform/resolvers/zod';
import {
  Button,
  Chip,
  Grid,
  MenuItem,
  Select,
  Slider,
  TextField as MuiTextField,
  Typography,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';
import { z } from 'zod';

import { updateInjectExpectation } from '../../../../../../actions/Exercise';
import { type UserHelper } from '../../../../../../actions/helper';
import { fetchTeams } from '../../../../../../actions/teams/team-actions';
import { type TeamsHelper } from '../../../../../../actions/teams/team-helper';
import { fetchPlayers } from '../../../../../../actions/User';
import { useFormatter } from '../../../../../../components/i18n';
import { useHelper } from '../../../../../../store';
import { type Team, type User } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import useDataLoader from '../../../../../../utils/hooks/useDataLoader';
import { computeColorStyle } from '../../../../../../utils/statusColors';
import { computeLabel, resolveUserName, truncate } from '../../../../../../utils/String';
import { zodImplement } from '../../../../../../utils/Zod';
import { type InjectExpectationsStore } from '../../../../common/injects/expectations/Expectation';

const useStyles = makeStyles()(theme => ({
  marginTop_2: { marginTop: theme.spacing(2) },
  scoreAcc: { marginTop: theme.spacing(0) },
  buttons: {
    display: 'flex',
    placeContent: 'end',
    gap: theme.spacing(2),
  },
  chipInList: {
    height: 30,
    borderRadius: 4,
    textTransform: 'uppercase',
    width: 150,
    float: 'right',
  },
}));

interface FormProps {
  expectation: InjectExpectationsStore;
  onUpdate?: () => void;
  withSummary?: boolean;
  isDisabled?: boolean;
}

const ManualExpectationsValidationForm: FunctionComponent<FormProps> = ({ expectation, onUpdate, withSummary = true, isDisabled }) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const theme = useTheme();

  const { teamsMap, usersMap }: {
    teamsMap: Record<string, Team>;
    usersMap: Record<string, User>;
  } = useHelper((helper: TeamsHelper & UserHelper) => {
    return ({
      teamsMap: helper.getTeamsMap(),
      usersMap: helper.getUsersMap(),
    });
  });
  const dispatch = useAppDispatch();
  useDataLoader(() => {
    dispatch(fetchPlayers());
    dispatch(fetchTeams());
  });
  const onSubmit = (data: { expectation_score: number }) => {
    dispatch(updateInjectExpectation(expectation.inject_expectation_id, {
      ...data,
      source_id: 'ui',
      source_type: 'ui',
      source_name: 'User input',
    })).then(() => {
      onUpdate?.();
    });
  };

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<{ expectation_score: number }>({
    mode: 'onTouched',
    resolver: zodResolver(zodImplement<{ expectation_score: number }>().with({ expectation_score: z.coerce.number() })),
    defaultValues: { expectation_score: expectation.inject_expectation_score ?? expectation.inject_expectation_expected_score ?? 0 },
  });
  useEffect(() => {
    reset({ expectation_score: expectation.inject_expectation_score ?? expectation.inject_expectation_expected_score ?? 0 });
  }, [expectation, reset]);

  const targetLabel = (expectationToProcess: InjectExpectationsStore) => {
    if (expectationToProcess.inject_expectation_user && usersMap[expectationToProcess.inject_expectation_user]) {
      return truncate(resolveUserName(usersMap[expectationToProcess.inject_expectation_user]), 22);
    }
    if (expectationToProcess.inject_expectation_team) {
      return teamsMap[expectationToProcess.inject_expectation_team]?.team_name;
    }
    return t('Unknown');
  };

  return (
    <div style={{ marginTop: 10 }}>
      <form id="expectationForm" onSubmit={handleSubmit(onSubmit)}>
        {withSummary && (
          <Chip
            classes={{ root: classes.chipInList }}
            style={computeColorStyle(expectation.inject_expectation_status)}
            label={t(computeLabel(expectation.inject_expectation_status))}
          />
        )}
        {withSummary && (<Typography variant="h3">{expectation.inject_expectation_user ? t('Player') : t('Team')}</Typography>)}
        {withSummary && targetLabel(expectation)}
        <Grid container spacing={3} className={withSummary ? classes.marginTop_2 : classes.scoreAcc}>
          <Grid size={{ xs: 6 }}>
            <MuiTextField
              variant="standard"
              fullWidth
              label={t('Score')}
              type="number"
              error={!!errors.expectation_score}
              disabled={isDisabled}
              helperText={errors.expectation_score && errors.expectation_score?.message ? errors.expectation_score?.message : `${t('Expected score:')} ${expectation.inject_expectation_expected_score}`}
              {...register('expectation_score')}
              InputProps={{
                inputProps: {
                  min: 0,
                  max: 100,
                },
              }}
            />
          </Grid>
          <Grid size={{ xs: 6 }}>
            <Select
              fullWidth
              value={watch('expectation_score') < expectation.inject_expectation_expected_score ? 'Failed' : 'Success'}
              onChange={event => setValue('expectation_score', event.target.value === 'Success' ? 100 : 0)}
              renderValue={(value) => {
                return value;
              }}
              sx={{ marginTop: 2 }}
              disabled={isDisabled}
            >
              <MenuItem value="Success">{t('Success')}</MenuItem>
              <MenuItem value="Failed">{t('Failed')}</MenuItem>
            </Select>
          </Grid>
        </Grid>
        <Slider
          size="small"
          value={watch('expectation_score')}
          onChange={(_, value) => setValue('expectation_score', value as number)}
          style={{ color: watch('expectation_score') < expectation.inject_expectation_expected_score ? theme.palette.error.main : theme.palette.success.main }}
          sx={{ width: '99%' }}
          disabled={isDisabled}
        />
        <div className={classes.buttons}>
          <Button
            type="submit"
            disabled={isSubmitting || isDisabled}
            variant="contained"
          >
            {t('Validate')}
          </Button>
        </div>
      </form>
    </div>
  );
};

export default ManualExpectationsValidationForm;
