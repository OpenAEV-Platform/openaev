import { Box, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useState } from 'react';
import { useParams } from 'react-router';

import { fetchExerciseChallenges } from '../../../../../actions/challenge-action';
import type { ArticlesHelper } from '../../../../../actions/channels/article-helper';
import { fetchExerciseDocuments } from '../../../../../actions/documents/documents-actions';
import { fetchExerciseInjectExpectations, fetchExerciseTeams } from '../../../../../actions/Exercise';
import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { reconcileExerciseInjects, updateInjectForExercise } from '../../../../../actions/Inject';
import { type InjectStore } from '../../../../../actions/injects/Inject';
import { type InjectHelper } from '../../../../../actions/injects/inject-helper';
import { fetchVariablesForExercise } from '../../../../../actions/variables/variable-actions';
import type { VariablesHelper } from '../../../../../actions/variables/variable-helper';
import { SECTION_LABEL_SX } from '../../../../../components/common/detail/detailStyles';
import { SectionBlock } from '../../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../../components/i18n';
import SearchFilter from '../../../../../components/SearchFilter';
import { useHelper } from '../../../../../store';
import { type Exercise, type Inject, type InjectExpectationOutput } from '../../../../../utils/api-types';
import { EndpointContext } from '../../../../../utils/context/endpoint/EndpointContext';
import endpointContextForExercise from '../../../../../utils/context/endpoint/EndpointContextForExercise';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import useSearchAndFilter from '../../../../../utils/SortingFiltering';
import { ArticleContext, ChallengeContext, TeamContext } from '../../../common/Context';
import TagsFilter from '../../../common/filters/TagsFilter';
import UpdateInject from '../../../common/injects/UpdateInject';
import SamplePreview from '../../../workspaces/custom_dashboards/widgets/viz/sample/SamplePreview';
import articleContextForExercise from '../articles/articleContextForExercise';
import ExecutionMenu from '../ExecutionMenu';
import { buildSimulationVariablesConfigurationUrl } from '../SimulationConfiguration';
import teamContextForExercise from '../teams/teamContextForExercise';
import AttackTimeline from './AttackTimeline';
import ExecutionBoard from './ExecutionBoard';
import ExecutionFlowStrip from './ExecutionFlowStrip';
import ExecutionHero from './ExecutionHero';
import { sampleTimelineInjects, sampleTimelineTeams } from './executionSampleData';
import { useNowTick } from './executionTime';

// Transient statuses of injects that have been dispatched but not concluded.
const IN_FLIGHT_STATUSES = new Set(['QUEUING', 'EXECUTING', 'PENDING']);

interface ExecutionOverviewProps {
  // When embedded outside the simulation route (e.g. the scenario Execution tab), the exercise id is
  // supplied explicitly instead of read from the URL. Falls back to the route param otherwise.
  exerciseId?: Exercise['exercise_id'];
  // The permanent right-hand execution menu (mails / logs / validations) only makes sense inside the
  // simulation route; embedded contexts drop it and show just the live overview.
  showMenu?: boolean;
}

// The Execution tab landing screen: a live operations view of the simulation
// execution. Top to bottom: the live hero (status beacon, elapsed clock, next
// inject countdown, headline stats, progress track), the scoping toolbar, the
// attack timeline with its animated "now" cursor and the actual-sends strip,
// and the live execution board where injects flow from "up next" to
// "completed" in real time. Exposure validation posture intentionally lives
// on the Overview tab only.
const ExecutionOverview = ({ exerciseId: exerciseIdProp, showMenu = true }: ExecutionOverviewProps = {}) => {
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const params = useParams() as { exerciseId?: Exercise['exercise_id'] };
  const exerciseId = (exerciseIdProp ?? params.exerciseId) as Exercise['exercise_id'];
  const { t } = useFormatter();
  const [selectedInjectId, setSelectedInjectId] = useState<string | null>(null);

  const {
    exercise,
    injects,
    teams,
    articles,
    variables,
    injectExpectations,
  } = useHelper((helper: InjectHelper & ExercisesHelper & ArticlesHelper & VariablesHelper) => {
    return {
      exercise: helper.getExercise(exerciseId),
      injects: helper.getExerciseInjects(exerciseId),
      teams: helper.getExerciseTeams(exerciseId),
      articles: helper.getExerciseArticles(exerciseId),
      variables: helper.getExerciseVariables(exerciseId),
      injectExpectations: helper.getExerciseInjectExpectations(exerciseId),
    };
  });

  // Fetching Data
  useDataLoader(() => {
    // Reconcile (not just fetch): injects can be deleted server-side out of band - deleting a
    // phishing landing page cascade-deletes the injects built on its contract - and the merge-only
    // store would otherwise keep the ghosts on this screen as "completed" until a full reload.
    dispatch(reconcileExerciseInjects(exerciseId));
    dispatch(fetchExerciseTeams(exerciseId));
    dispatch(fetchVariablesForExercise(exerciseId));
    dispatch(fetchExerciseDocuments(exerciseId));
    dispatch(fetchExerciseInjectExpectations(exerciseId));
  });

  // Shared 1-second clock driving every live element of the screen.
  const now = useNowTick();
  const running = exercise?.exercise_status === 'RUNNING';

  // Sort
  const searchColumns = ['title', 'description', 'content'];
  const filtering = useSearchAndFilter(
    'inject',
    'depends_duration',
    searchColumns,
  );

  const isEnable = (inject: InjectStore): boolean => !!inject.inject_enabled;
  const filteredInjects: InjectStore[] = filtering.filterAndSort(injects.filter((inject: InjectStore) => isEnable(inject)));
  // filteredInjects is already filtered and sorted (soonest first); the board
  // lanes are plain partitions of it.
  const isInFlight = (inject: InjectStore): boolean => !!inject.inject_status && IN_FLIGHT_STATUSES.has(inject.inject_status.status_name);
  const pendingInjects: InjectStore[] = filteredInjects.filter((inject: InjectStore) => inject.inject_status === null);
  const inFlightInjects: InjectStore[] = filteredInjects.filter(isInFlight);
  const completedInjects: InjectStore[] = filteredInjects
    .filter((inject: InjectStore) => inject.inject_status !== null && !isInFlight(inject))
    .sort((a: InjectStore, b: InjectStore) => {
      const sentA = a.inject_status?.tracking_sent_date ? new Date(a.inject_status.tracking_sent_date).getTime() : 0;
      const sentB = b.inject_status?.tracking_sent_date ? new Date(b.inject_status.tracking_sent_date).getTime() : 0;
      return sentB - sentA;
    });

  // Headline metrics computed on the WHOLE simulation (unaffected by the
  // search / tags filters that scope the timeline and board below).
  const enabledInjects: InjectStore[] = injects.filter((inject: InjectStore) => isEnable(inject));
  const completedCount = enabledInjects.filter((inject: InjectStore) => inject.inject_status !== null && !isInFlight(inject)).length;
  const inFlightCount = enabledInjects.filter(isInFlight).length;
  const errorCount = enabledInjects.filter((inject: InjectStore) => inject.inject_status?.status_name === 'ERROR').length;
  const pendingValidations = (injectExpectations ?? []).filter(
    (expectation: InjectExpectationOutput) => expectation.inject_expectation_type === 'MANUAL' && expectation.inject_expectation_status === 'PENDING',
  ).length;

  // Next planned inject, computed from the injects list with the same formula
  // as the attack timeline (start date + depends_duration). Computed here
  // rather than read from exercise_next_inject_date because that field only
  // exists on the raw Exercise entity returned by mutations - the
  // SimulationDetails DTO of GET /exercises/{id} does not carry it, so the
  // countdown would silently disappear after a page reload.
  const exerciseStartTime = exercise?.exercise_start_date ? new Date(exercise.exercise_start_date).getTime() : null;
  const nextInjectTime = exerciseStartTime === null
    ? null
    : enabledInjects
        .filter((inject: InjectStore) => inject.inject_status === null)
        .map((inject: InjectStore) => exerciseStartTime + (inject.inject_depends_duration ?? 0) * 1000)
        .filter((time: number) => time >= now)
        .reduce((min: number | null, time: number) => (min === null || time < min ? time : min), null);

  const onUpdateInject = async (inject: Inject) => {
    if (selectedInjectId) {
      await dispatch(updateInjectForExercise(exerciseId, selectedInjectId, inject));
    }
  };

  const teamContext = teamContextForExercise(exerciseId, []);
  const articleContext = articleContextForExercise(exerciseId);
  const endpointContext = endpointContextForExercise(exerciseId);
  const challengeContext = { fetchChallenges: () => dispatch(fetchExerciseChallenges(exerciseId)) };

  return (
    <div>
      {showMenu && <ExecutionMenu exerciseId={exerciseId} />}
      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
        paddingBottom: 5,
      }}
      >
        {/* Live execution hero */}
        <ExecutionHero
          exercise={exercise}
          exerciseId={exerciseId}
          totalCount={enabledInjects.length}
          completedCount={completedCount}
          inFlightCount={inFlightCount}
          errorCount={errorCount}
          pendingValidations={pendingValidations}
          now={now}
          nextInjectTime={nextInjectTime}
        />

        {/* Scoping toolbar for the timeline and board below */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: theme.spacing(1.5),
        }}
        >
          <SearchFilter
            variant="small"
            onChange={filtering.handleSearch}
            keyword={filtering.keyword}
          />
          <TagsFilter
            onAddTag={filtering.handleAddTag}
            onRemoveTag={filtering.handleRemoveTag}
            currentTags={filtering.tags}
          />
        </div>

        {/* Attack timeline (planned schedule + live cursor) with the actual
            sends strip as its footer - previews greyed sample data (like every
            widget of the platform) while the simulation has no injects yet. */}
        <SectionBlock title={t('Attack timeline')}>
          {filteredInjects.length > 0 ? (
            <AttackTimeline
              injects={filteredInjects}
              teams={teams}
              onSelectInject={(id: string) => setSelectedInjectId(id)}
              startDate={exercise?.exercise_start_date}
              running={running}
              now={now}
            />
          ) : (
            <SamplePreview active>
              <AttackTimeline
                injects={sampleTimelineInjects}
                teams={sampleTimelineTeams}
                onSelectInject={() => {}}
                now={now}
              />
            </SamplePreview>
          )}
          <Box sx={{
            marginTop: 2,
            paddingTop: 2,
            borderTop: `1px dashed ${alpha(theme.palette.text.primary, 0.08)}`,
          }}
          >
            <Typography sx={{
              ...SECTION_LABEL_SX,
              fontSize: 10,
              marginBottom: 1,
            }}
            >
              {t('Sent injects over time')}
            </Typography>
            <ExecutionFlowStrip injects={filteredInjects} />
          </Box>
        </SectionBlock>

        {/* Live execution board: up next / in flight / completed */}
        <ExecutionBoard
          pendingInjects={pendingInjects}
          inFlightInjects={inFlightInjects}
          completedInjects={completedInjects}
          exercise={exercise}
          exerciseId={exerciseId}
          now={now}
          setSelectedInjectId={setSelectedInjectId}
        />
      </Box>
      {selectedInjectId && (
        <ArticleContext.Provider value={articleContext}>
          <TeamContext.Provider value={teamContext}>
            <EndpointContext.Provider value={endpointContext}>
              <ChallengeContext.Provider value={challengeContext}>
                <UpdateInject
                  open
                  handleClose={() => setSelectedInjectId(null)}
                  onUpdateInject={onUpdateInject}
                  injectId={selectedInjectId}
                  isAtomic={false}
                  injects={injects}
                  articlesFromExerciseOrScenario={articles}
                  uriVariable={buildSimulationVariablesConfigurationUrl(exerciseId, `/admin/simulations/${exerciseId}/execution`)}
                  variablesFromExerciseOrScenario={variables}
                />
              </ChallengeContext.Provider>
            </EndpointContext.Provider>
          </TeamContext.Provider>
        </ArticleContext.Provider>
      )}
    </div>
  );
};

export default ExecutionOverview;
