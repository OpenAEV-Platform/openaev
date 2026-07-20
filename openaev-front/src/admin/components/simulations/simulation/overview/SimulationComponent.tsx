import { GroupsOutlined, PersonOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { useEffect, useState } from 'react';
import { useParams, useSearchParams } from 'react-router';

import { searchExerciseHealthchecks } from '../../../../../actions/Exercise';
import { fetchExerciseExpectationResult, fetchExerciseInjectExpectationResults, searchExerciseInjects } from '../../../../../actions/exercises/exercise-action';
import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { MetricGrid, MetricTile, SectionBlock } from '../../../../../components/common/detail/EntityDetailCommon';
import PostureGauges from '../../../../../components/common/detail/PostureGauges';
import { initSorting } from '../../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import { useHelper } from '../../../../../store';
import { type Exercise, type ExpectationResultsByType, type HealthCheck, type InjectExpectationResultsByAttackPattern } from '../../../../../utils/api-types';
import { isFeatureEnabled } from '../../../../../utils/utils';
import InjectResultList from '../../../atomic_testings/InjectResultList';
import Healthchecks from '../../../common/healthchecks/Healthchecks';
import MitreCoverageMatrix from '../../../common/matrix/MitreCoverageMatrix';
import SimulationMainInformation from '../SimulationMainInformation';
import ExerciseDistribution from './ExerciseDistribution';

const SimulationComponent = () => {
  // Standard hooks
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
  const [healthchecks, setHealthchecks] = useState<HealthCheck[]>([]);

  const isChainingFeatureEnabled = isFeatureEnabled('INJECT_CHAINING');
  const exerciseWorkflowId = exercise.exercise_workflow_id as string | undefined;
  const isSimulationChaining = isChainingFeatureEnabled && !!exerciseWorkflowId;

  useEffect(() => {
    fetchExerciseExpectationResult(exerciseId).then((result: { data: ExpectationResultsByType[] }) => setResults(result.data));
    fetchExerciseInjectExpectationResults(exerciseId).then((result: { data: InjectExpectationResultsByAttackPattern[] }) => setInjectResults(result.data));
    if (isSimulationChaining) {
      searchExerciseHealthchecks(exerciseId).then((result: { data: HealthCheck[] }) => setHealthchecks(result.data));
    }
  }, [exerciseId]);

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
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
      paddingBottom: theme.spacing(5),
    }}
    >
      {isSimulationChaining && !!healthchecks?.length && (
        <Healthchecks
          healthchecks={healthchecks}
          exerciseId={exerciseId}
        />
      )}

      <MetricGrid>
        <MetricTile icon={TrackChangesOutlined} label={t('Injects')} value={exercise.exercise_injects?.length ?? 0} />
        <MetricTile icon={GroupsOutlined} label={t('Teams')} value={exercise.exercise_teams?.length ?? 0} />
        <MetricTile icon={PersonOutlined} label={t('Players')} value={exercise.exercise_all_users_number ?? exercise.exercise_users_number ?? 0} />
      </MetricGrid>

      <Box sx={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))',
        gap: 2,
        alignItems: 'stretch',
      }}
      >
        <SectionBlock title={t('Information')}>
          <SimulationMainInformation exercise={exercise} />
        </SectionBlock>
        <SectionBlock title={t('Results')}>
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            height: '100%',
          }}
          >
            {!results
              ? <Loader variant="inElement" />
              : <PostureGauges expectationResultsByTypes={results} humanValidationLink={`/admin/simulations/${exerciseId}/animation/validations`} />}
          </Box>
        </SectionBlock>
      </Box>
      {injectResults && resultAttackPatternIds.length > 0 && (
        <SectionBlock title={t('MITRE ATT&CK Results')}>
          <MitreCoverageMatrix widgetId={`simulation-mitre-${exerciseId}`} injectResults={injectResults} />
        </SectionBlock>
      )}
      {exercise.exercise_status !== 'SCHEDULED' && (
        <div id="injects-results">
          <SectionBlock title={t('Injects results')}>
            <InjectResultList
              fetchInjects={input => searchExerciseInjects(exerciseId, input)}
              goTo={injectId => `/admin/simulations/${exerciseId}/injects/${injectId}`}
              queryableHelpers={queryableHelpers}
              searchPaginationInput={searchPaginationInput}
              contextId={exercise.exercise_id}
            />
          </SectionBlock>
        </div>
      )}
      <ExerciseDistribution exerciseId={exerciseId} />
    </Box>
  );
};

export default SimulationComponent;
