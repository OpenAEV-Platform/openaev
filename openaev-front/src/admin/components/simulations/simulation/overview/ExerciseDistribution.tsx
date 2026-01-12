import { GridLegacy, Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchExerciseInjectExpectations, fetchExerciseTeams } from '../../../../../actions/Exercise';
import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { fetchExerciseInjects } from '../../../../../actions/Inject';
import { type InjectHelper } from '../../../../../actions/injects/inject-helper';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import arrowDark from '../../../../../static/images/misc/arrow_dark.png';
import arrowLight from '../../../../../static/images/misc/arrow_light.png';
import { useHelper } from '../../../../../store';
import { type Exercise } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import ExerciseDistributionByInjectorContract from './ExerciseDistributionByInjectorContract';
import ExerciseDistributionScoreByInject from './ExerciseDistributionScoreByInject';
import ExerciseDistributionScoreByOrganization from './ExerciseDistributionScoreByOrganization';
import ExerciseDistributionScoreByPlayer from './ExerciseDistributionScoreByPlayer';
import ExerciseDistributionScoreByTeam from './ExerciseDistributionScoreByTeam';
import ExerciseDistributionScoreByTeamInPercentage from './ExerciseDistributionScoreByTeamInPercentage';
import ExerciseDistributionScoreOverTimeByInjectorContract from './ExerciseDistributionScoreOverTimeByInjectorContract';
import ExerciseDistributionScoreOverTimeByTeam from './ExerciseDistributionScoreOverTimeByTeam';
import ExerciseDistributionScoreOverTimeByTeamInPercentage from './ExerciseDistributionScoreOverTimeByTeamInPercentage';

const useStyles = makeStyles()(() => ({
  paperChart: {
    position: 'relative',
    padding: '0 20px 0 0',
    overflow: 'hidden',
    height: '100%',
  },
}));

interface Props {
  exerciseId: Exercise['exercise_id'];
  isReport?: boolean;
}

const ExerciseDistribution: FunctionComponent<Props> = ({
  exerciseId,
  isReport = false,
}) => {
  // Standard hooks
  const theme = useTheme();
  const { t } = useFormatter();
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const [loading, setLoading] = useState(true);

  useDataLoader(() => {
    setLoading(true);
    const fetchPromises = [
      dispatch(fetchExerciseInjectExpectations(exerciseId)),
      dispatch(fetchExerciseInjects(exerciseId)),
      dispatch(fetchExerciseTeams(exerciseId)),
    ];
    Promise.all(fetchPromises)
      .finally(() => {
        setLoading(false);
      });
  });

  const { injectExpectations, exercise } = useHelper((helper: InjectHelper & ExercisesHelper) => ({
    exercise: helper.getExercise(exerciseId),
    injectExpectations: helper.getExerciseInjectExpectations(exerciseId),
  }));

  if (exercise.exercise_status === 'SCHEDULED' && injectExpectations?.length === 0 && !isReport) {
    return (
      <div style={{
        marginTop: 100,
        textAlign: 'center',
      }}
      >
        <div style={{ fontSize: 20 }}>
          {t('This simulation is not running yet. Start now!')}
        </div>
        <img style={{ marginTop: 20 }} width={100} src={theme.palette.mode === 'dark' ? arrowDark : arrowLight} alt="arrow" />
      </div>
    );
  }

  return (
    <GridLegacy id="exercise_distribution" container spacing={3} sx={{ marginBottom: 1 }}>
      <GridLegacy item xs={6} style={{ marginTop: 25 }}>
        <Typography variant="h4">
          {t('Distribution of score by team (in % of expectations)')}
        </Typography>
        <Paper variant="outlined" classes={{ root: classes.paperChart }}>
          {
            loading
              ? <Loader variant="inElement" />
              : <ExerciseDistributionScoreByTeamInPercentage exerciseId={exerciseId} />
          }
        </Paper>
      </GridLegacy>
      <GridLegacy item xs={6} style={{ marginTop: 25 }}>
        <Typography variant="h4">
          {t('Teams scores over time (in % of expectations)')}
        </Typography>
        <Paper variant="outlined" classes={{ root: classes.paperChart }}>
          {
            loading
              ? <Loader variant="inElement" />
              : <ExerciseDistributionScoreOverTimeByTeamInPercentage exerciseId={exerciseId} />
          }
        </Paper>
      </GridLegacy>
      <GridLegacy item xs={6} style={{ marginTop: 25 }}>
        <Typography variant="h4">
          {t('Distribution of total score by team')}
        </Typography>
        <Paper variant="outlined" classes={{ root: classes.paperChart }}>
          {
            loading
              ? <Loader variant="inElement" />
              : <ExerciseDistributionScoreByTeam exerciseId={exerciseId} />
          }
        </Paper>
      </GridLegacy>
      <GridLegacy item xs={6} style={{ marginTop: 25 }}>
        <Typography variant="h4">{t('Teams scores over time')}</Typography>
        <Paper variant="outlined" classes={{ root: classes.paperChart }}>
          {
            loading
              ? <Loader variant="inElement" />
              : <ExerciseDistributionScoreOverTimeByTeam exerciseId={exerciseId} />
          }
        </Paper>
      </GridLegacy>
      <GridLegacy item xs={6} style={{ marginTop: 25 }}>
        <Typography variant="h4">
          {t('Distribution of total score by inject type')}
        </Typography>
        <Paper variant="outlined" classes={{ root: classes.paperChart }}>
          {
            loading
              ? <Loader variant="inElement" />
              : <ExerciseDistributionByInjectorContract exerciseId={exerciseId} />
          }
        </Paper>
      </GridLegacy>
      <GridLegacy item xs={6} style={{ marginTop: 25 }}>
        <Typography variant="h4">
          {t('Inject types scores over time')}
        </Typography>
        <Paper variant="outlined" classes={{ root: classes.paperChart }}>
          {
            loading
              ? <Loader variant="inElement" />
              : <ExerciseDistributionScoreOverTimeByInjectorContract exerciseId={exerciseId} />
          }
        </Paper>
      </GridLegacy>
      <GridLegacy item xs={6} style={{ marginTop: 25 }}>
        <Typography variant="h4">
          {t('Distribution of total score by organization')}
        </Typography>
        <Paper variant="outlined" classes={{ root: classes.paperChart }}>
          {
            loading
              ? <Loader variant="inElement" />
              : <ExerciseDistributionScoreByOrganization exerciseId={exerciseId} />
          }
        </Paper>
      </GridLegacy>
      <GridLegacy item xs={3} style={{ marginTop: 25 }}>
        <Typography variant="h4">
          {t('Distribution of total score by player')}
        </Typography>
        <Paper variant="outlined" classes={{ root: classes.paperChart }}>
          {
            loading
              ? <Loader variant="inElement" />
              : <ExerciseDistributionScoreByPlayer exerciseId={exerciseId} />
          }
        </Paper>
      </GridLegacy>
      <GridLegacy item xs={3} style={{ marginTop: 25 }}>
        <Typography variant="h4">
          {t('Distribution of total score by inject')}
        </Typography>
        <Paper variant="outlined" classes={{ root: classes.paperChart }}>
          {
            loading
              ? <Loader variant="inElement" />
              : <ExerciseDistributionScoreByInject exerciseId={exerciseId} />
          }
        </Paper>
      </GridLegacy>
    </GridLegacy>
  );
};
export default ExerciseDistribution;
