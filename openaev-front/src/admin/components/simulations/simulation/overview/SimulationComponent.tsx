import { Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { useEffect, useState } from 'react';
import { useParams, useSearchParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchExerciseExpectationResult, fetchExerciseInjectExpectationResults, searchExerciseInjects } from '../../../../../actions/exercises/exercise-action';
import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { initSorting } from '../../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import { useHelper } from '../../../../../store';
import { type Exercise, type ExpectationResultsByType, type InjectExpectationResultsByAttackPattern } from '../../../../../utils/api-types';
import InjectResultList from '../../../atomic_testings/InjectResultList';
import ResponsePie from '../../../common/injects/ResponsePie';
import MitreMatrix from '../../../common/matrix/MitreMatrix';
import SimulationMainInformation from '../SimulationMainInformation';
import ExerciseDistribution from './ExerciseDistribution';

// Deprecated - https://mui.com/system/styles/basics/
// Do not use it for new code.
const useStyles = makeStyles()(theme => ({
  paper: {
    height: '100%',
    minHeight: '100%',
    padding: theme.spacing(2),
    borderRadius: 4,
  },
}));

const SimulationComponent = () => {
  // Standard hooks
  const { classes } = useStyles();
  const theme = useTheme();
  const { t } = useFormatter();
  const [scrolledToAnchor, setScrolledToAnchor] = useState<boolean>(false);

  // Fetching data
  const [searchParams] = useSearchParams();
  // We do not use the traditional anchor (`#`) as the pagination hook overrides it
  const anchor = searchParams.get('anchor');
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };
  const { exercise } = useHelper((helper: ExercisesHelper) => ({ exercise: helper.getExercise(exerciseId) }));
  const [results, setResults] = useState<ExpectationResultsByType[] | null>(null);
  const [injectResults, setInjectResults] = useState<InjectExpectationResultsByAttackPattern[] | null>(null);

  useEffect(() => {
    fetchExerciseExpectationResult(exerciseId).then((result: { data: ExpectationResultsByType[] }) => setResults(result.data));
    fetchExerciseInjectExpectationResults(exerciseId).then((result: { data: InjectExpectationResultsByAttackPattern[] }) => setInjectResults(result.data));
  }, [exerciseId]);

  const goToLink = `/admin/simulations/${exerciseId}/injects`;
  let resultAttackPatternIds = [];
  if (injectResults) {
    resultAttackPatternIds = R.uniq(
      injectResults
        .filter(injectResult => !!injectResult.inject_attack_pattern)
        .flatMap(injectResult => injectResult.inject_attack_pattern) as unknown as string[],
    );
  }

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('simulation-injects-results', buildSearchPagination({ sorts: initSorting('inject_updated_at', 'DESC') }));

  useEffect(() => {
    if (scrolledToAnchor) {
      return;
    }
    if (anchor && injectResults && resultAttackPatternIds.length > 0) {
      const element = document.getElementById(anchor);
      if (element) {
        const header = document.querySelector('header');
        const headerHeight = header ? header.offsetHeight : 0;
        const elementPosition = element.getBoundingClientRect().top + window.pageYOffset;
        const offsetPosition = elementPosition - headerHeight;

        setScrolledToAnchor(true);
        window.scrollTo({
          top: offsetPosition,
          behavior: 'smooth',
        });
      }
    }
  }, [anchor, injectResults, resultAttackPatternIds, scrolledToAnchor, setScrolledToAnchor]);

  return (
    <div style={{ paddingBottom: theme.spacing(5) }}>
      <div style={{
        display: 'grid',
        gap: `0px ${theme.spacing(3)}`,
        gridTemplateColumns: `calc((100% - ${theme.spacing(3)})/2) calc((100% - ${theme.spacing(3)})/2)`,
      }}
      >
        <Typography variant="h4">{t('Information')}</Typography>
        <Typography variant="h4">{t('Results')}</Typography>
        <SimulationMainInformation exercise={exercise} />
        <Paper
          variant="outlined"
          style={{
            display: 'flex',
            alignItems: 'center',
            height: '100%',
            justifyContent: 'center',
          }}
        >
          {!results
            ? <Loader variant="inElement" />
            : <ResponsePie expectationResultsByTypes={results} humanValidationLink={`/admin/simulations/${exerciseId}/animation/validations`} />}
        </Paper>
      </div>
      {injectResults && resultAttackPatternIds.length > 0 && (
        <div style={{
          display: 'grid',
          marginTop: theme.spacing(3),
          gap: `0px ${theme.spacing(3)}`,
          gridTemplateColumns: '1fr',
        }}
        >
          <Typography variant="h4">{t('MITRE ATT&CK Results')}</Typography>
          <Paper
            variant="outlined"
            classes={{ root: classes.paper }}
            style={{ minWidth: '100%' }}
          >
            <MitreMatrix goToLink={goToLink} injectResults={injectResults} />
          </Paper>
        </div>
      )}
      {exercise.exercise_status !== 'SCHEDULED' && (
        <div
          style={{
            display: 'grid',
            marginTop: theme.spacing(3),
            gap: `0px ${theme.spacing(3)}`,
            gridTemplateColumns: '1fr',
          }}
          id="injects-results"
        >
          <Typography variant="h4">{t('Injects results')}</Typography>
          <Paper classes={{ root: classes.paper }} variant="outlined">
            <InjectResultList
              fetchInjects={input => searchExerciseInjects(exerciseId, input)}
              goTo={injectId => `/admin/simulations/${exerciseId}/injects/${injectId}`}
              queryableHelpers={queryableHelpers}
              searchPaginationInput={searchPaginationInput}
              contextId={exercise.exercise_id}
            />
          </Paper>
        </div>
      )}
      <ExerciseDistribution exerciseId={exerciseId} />
    </div>
  );
};

export default SimulationComponent;
