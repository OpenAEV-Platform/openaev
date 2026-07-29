import { InsightsOutlined } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router';

import { fetchExerciseExpectationResult, fetchExerciseInjectExpectationResults, searchExerciseInjects } from '../../../../../actions/exercises/exercise-action';
import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { type InjectHelper } from '../../../../../actions/injects/inject-helper';
import { SectionBlock } from '../../../../../components/common/detail/EntityDetailCommon';
import PostureGauges from '../../../../../components/common/detail/PostureGauges';
import SAMPLE_POSTURE from '../../../../../components/common/detail/samplePosture';
import { initSorting } from '../../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import { useHelper } from '../../../../../store';
import { type Exercise, type ExpectationResultsByType, type Inject, type InjectExpectationResultsByAttackPattern } from '../../../../../utils/api-types';
import InjectResultList from '../../../atomic_testings/InjectResultList';
import MitreCoverageMatrix from '../../../common/matrix/MitreCoverageMatrix';
import { CONTEXTUAL_POSTURE_WIDGET_ID, contextualResultsUrl } from '../../../workspaces/custom_dashboards/results/contextualWidgets';
import SamplePreview from '../../../workspaces/custom_dashboards/widgets/viz/sample/SamplePreview';
import SimulationMainInformation from '../SimulationMainInformation';

// Empty-state placeholder shown inside a SectionBlock when a simulation has not
// produced results yet, instead of a raw loader or a stack of blank charts.
const OverviewPlaceholder = ({ message }: { message: string }) => {
  const theme = useTheme();
  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 1,
      minHeight: 160,
      height: '100%',
      textAlign: 'center',
      color: 'text.secondary',
    }}
    >
      <Box sx={{
        width: 44,
        height: 44,
        borderRadius: 1,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: theme.palette.primary.main,
        backgroundColor: alpha(theme.palette.primary.main, 0.1),
      }}
      >
        <InsightsOutlined />
      </Box>
      <Typography variant="body2" sx={{ maxWidth: 320 }}>{message}</Typography>
    </Box>
  );
};

type InjectCollectionLike = {
  length?: number;
  size?: number;
};

type InjectCollectionSnapshot = {
  exerciseId: string;
  injectsCount: number;
};

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
  const { exercise, injects } = useHelper((helper: ExercisesHelper & InjectHelper) => ({
    exercise: helper.getExercise(exerciseId),
    injects: helper.getExerciseInjects(exerciseId) as Inject[],
  }));
  const [results, setResults] = useState<ExpectationResultsByType[] | null>(null);
  const [injectResults, setInjectResults] = useState<InjectExpectationResultsByAttackPattern[] | null>(null);
  const [injectListReloadCount, setInjectListReloadCount] = useState(0);
  const previousInjectCollectionRef = useRef<InjectCollectionSnapshot | null>(null);
  const injectsCount = (injects as unknown as InjectCollectionLike)?.length
    ?? (injects as unknown as InjectCollectionLike)?.size
    ?? 0;

  useEffect(() => {
    fetchExerciseExpectationResult(exerciseId).then((result: { data: ExpectationResultsByType[] }) => setResults(result.data));
    fetchExerciseInjectExpectationResults(exerciseId).then((result: { data: InjectExpectationResultsByAttackPattern[] }) => setInjectResults(result.data));
  }, [exerciseId]);

  // The inject hero counter is fed by referential/SSE updates; when its backing
  // collection changes for the CURRENT simulation, force the paginated results
  // list to refetch so new executions appear without a full page reload. Skip
  // mount and simulation switches to avoid an extra duplicate fetch.
  useEffect(() => {
    const previous = previousInjectCollectionRef.current;
    if (!previous) {
      previousInjectCollectionRef.current = {
        exerciseId,
        injectsCount,
      };
      return;
    }
    if (previous.exerciseId !== exerciseId) {
      previousInjectCollectionRef.current = {
        exerciseId,
        injectsCount,
      };
      return;
    }
    if (previous.injectsCount !== injectsCount) {
      previousInjectCollectionRef.current = {
        exerciseId,
        injectsCount,
      };
      setInjectListReloadCount(prev => prev + 1);
    }
  }, [exerciseId, injectsCount]);

  let resultAttackPatternIds = [];
  if (injectResults) {
    resultAttackPatternIds = R.uniq(
      injectResults
        .filter(injectResult => !!injectResult.inject_attack_pattern)
        .flatMap(injectResult => injectResult.inject_attack_pattern) as unknown as string[],
    );
  }

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('simulation-injects-results', buildSearchPagination({ sorts: initSorting('inject_updated_at', 'DESC') }));

  // Gauge clicks drill down to the expectations behind the ring, scoped to
  // this simulation (same actionability as the dashboard widgets).
  const navigate = useNavigate();
  const location = useLocation();
  const openPostureResults = useCallback((type: string) => {
    navigate(contextualResultsUrl(
      CONTEXTUAL_POSTURE_WIDGET_ID,
      'simulation',
      exerciseId,
      `${location.pathname}${location.search}`,
      { inject_expectation_type: [type] },
    ));
  }, [navigate, location, exerciseId]);

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
      <Box sx={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))',
        gap: 2,
        alignItems: 'stretch',
      }}
      >
        <SectionBlock title={t('Information')}>
          <SimulationMainInformation exercise={exercise} embedded />
        </SectionBlock>
        <SectionBlock title={t('Results')} centerContent>
          {/* Full-width child of the centering Paper: the gauges distribute
              across the section width while sitting vertically centered. */}
          <Box sx={{ width: '100%' }}>
            {(() => {
              if (!results) return <Loader variant="inElement" />;
              // No results yet (not run, or a run without expectations): preview
              // the exact gauges a real run produces with illustrative sample
              // data (greyed "Sample" chip), like the scenario overview - never
              // an empty placeholder.
              if (results.length === 0) {
                return (
                  <SamplePreview active variant="subtle">
                    <PostureGauges expectationResultsByTypes={SAMPLE_POSTURE} />
                  </SamplePreview>
                );
              }
              return (
                <PostureGauges
                  expectationResultsByTypes={results}
                  humanValidationLink={`/admin/simulations/${exerciseId}/execution/validations`}
                  onTypeClick={openPostureResults}
                />
              );
            })()}
          </Box>
        </SectionBlock>
      </Box>
      {injectResults && resultAttackPatternIds.length > 0 && (
        <SectionBlock title={t('Kill chain results')}>
          <MitreCoverageMatrix
            widgetId={`simulation-mitre-${exerciseId}`}
            injectResults={injectResults}
            defaultKillChain={exercise.exercise_default_kill_chain}
            resultsContext={{
              source: 'simulation',
              contextId: exerciseId,
            }}
          />
        </SectionBlock>
      )}
      {exercise.exercise_status !== 'SCHEDULED'
        ? (
            <div id="injects-results">
              <SectionBlock title={t('Injects results')}>
                {/* Keyed on the exercise id: the fetch closure captures exerciseId and
                    the pagination effect only re-runs on search input changes, so a
                    param-to-param navigation must remount the list to refetch. */}
                <InjectResultList
                  key={exerciseId}
                  fetchInjects={input => searchExerciseInjects(exerciseId, input)}
                  goTo={injectId => `/admin/simulations/${exerciseId}/injects/${injectId}`}
                  queryableHelpers={queryableHelpers}
                  searchPaginationInput={searchPaginationInput}
                  reloadContentCount={injectListReloadCount}
                  contextId={exercise.exercise_id}
                  // The simulation has been launched (this branch excludes SCHEDULED):
                  // injects without a status are waiting for dispatch, not drafts.
                  // On a canceled simulation they will never run, so keep DRAFT there.
                  displayDraftAsPending={exercise.exercise_status !== 'CANCELED'}
                />
              </SectionBlock>
            </div>
          )
        : (
            <SectionBlock title={t('Injects results')}>
              <OverviewPlaceholder message={t('This simulation is not running yet. Start it to collect inject results, or open the dashboard to build a custom analysis.')} />
            </SectionBlock>
          )}
    </Box>
  );
};

export default SimulationComponent;
