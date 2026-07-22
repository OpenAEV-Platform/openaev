import { InsightsOutlined } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { useEffect, useState } from 'react';
import { useParams, useSearchParams } from 'react-router';

import { fetchExerciseExpectationResult, fetchExerciseInjectExpectationResults, searchExerciseInjects } from '../../../../../actions/exercises/exercise-action';
import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { SectionBlock } from '../../../../../components/common/detail/EntityDetailCommon';
import PostureGauges from '../../../../../components/common/detail/PostureGauges';
import { initSorting } from '../../../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import { useHelper } from '../../../../../store';
import { type Exercise, type ExpectationResultsByType, type InjectExpectationResultsByAttackPattern } from '../../../../../utils/api-types';
import InjectResultList from '../../../atomic_testings/InjectResultList';
import MitreCoverageMatrix from '../../../common/matrix/MitreCoverageMatrix';
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

  useEffect(() => {
    fetchExerciseExpectationResult(exerciseId).then((result: { data: ExpectationResultsByType[] }) => setResults(result.data));
    fetchExerciseInjectExpectationResults(exerciseId).then((result: { data: InjectExpectationResultsByAttackPattern[] }) => setInjectResults(result.data));
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
        <SectionBlock title={t('Results')}>
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            height: '100%',
          }}
          >
            {(() => {
              if (!results) return <Loader variant="inElement" />;
              if (results.length === 0) {
                return <OverviewPlaceholder message={t('Prevention, detection and vulnerability results will appear here once the simulation runs.')} />;
              }
              return <PostureGauges expectationResultsByTypes={results} humanValidationLink={`/admin/simulations/${exerciseId}/execution/validations`} />;
            })()}
          </Box>
        </SectionBlock>
      </Box>
      {injectResults && resultAttackPatternIds.length > 0 && (
        <SectionBlock title={t('MITRE ATT&CK Results')}>
          <MitreCoverageMatrix widgetId={`simulation-mitre-${exerciseId}`} injectResults={injectResults} />
        </SectionBlock>
      )}
      {exercise.exercise_status !== 'SCHEDULED'
        ? (
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
