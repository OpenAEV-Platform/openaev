import { PlayArrowOutlined, RocketLaunchOutlined } from '@mui/icons-material';
import { Avatar, Box, Button, Paper, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type Dispatch, type SetStateAction, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';

import { type AgentHelper } from '../../../../actions/agents/agent-helper';
import type { CollectorHelper } from '../../../../actions/collectors/collector-helper';
import { fetchExerciseExpectationResult, fetchExerciseInjectExpectationResults } from '../../../../actions/exercises/exercise-action';
import { type ExercisesHelper } from '../../../../actions/exercises/exercise-helper';
import type { LoggedHelper } from '../../../../actions/helper';
import { fetchScenarioInjects } from '../../../../actions/Inject';
import { type InjectHelper } from '../../../../actions/injects/inject-helper';
import { searchScenarioExercises, searchScenarioHealthcheks } from '../../../../actions/scenarios/scenario-actions';
import { type ScenariosHelper } from '../../../../actions/scenarios/scenario-helper';
import { Field, SectionBlock } from '../../../../components/common/detail/EntityDetailCommon';
import KillChainTimeline from '../../../../components/common/detail/KillChainTimeline';
import PostureGauges from '../../../../components/common/detail/PostureGauges';
import SAMPLE_POSTURE from '../../../../components/common/detail/samplePosture';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import ExpandableMarkdown from '../../../../components/ExpandableMarkdown';
import { useFormatter } from '../../../../components/i18n';
import ItemCategory from '../../../../components/ItemCategory';
import ItemMainFocus from '../../../../components/ItemMainFocus';
import ItemSeverity from '../../../../components/ItemSeverity';
import ItemTags from '../../../../components/ItemTags';
import ItemTypeAffinity from '../../../../components/ItemTypeAffinity';
import PlatformIconGroup from '../../../../components/PlatformIconGroup';
import octiDark from '../../../../static/images/xtm/octi_dark.png';
import octiLight from '../../../../static/images/xtm/octi_light.png';
import { useHelper } from '../../../../store';
import {
  type Agent,
  type ExerciseSimple, type ExpectationResultsByType, type HealthCheck, type Inject,
  type InjectExpectationResultsByAttackPattern,
  type KillChainPhase,
  type Scenario as ScenarioType,
  type SearchPaginationInput,
  type SortField,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { isEmptyField, isFeatureEnabled } from '../../../../utils/utils';
import MitreCoverageMatrix from '../../common/matrix/MitreCoverageMatrix';
import ExercisePopover from '../../simulations/simulation/ExercisePopover';
import SimulationList from '../../simulations/SimulationList';
import { CONTEXTUAL_POSTURE_WIDGET_ID, contextualResultsUrl } from '../../workspaces/custom_dashboards/results/contextualWidgets';
import SamplePreview from '../../workspaces/custom_dashboards/widgets/viz/sample/SamplePreview';
import ScenarioDistributionByExercise from './ScenarioDistributionByExercise';

const Scenario = ({ setOpenInstantiateSimulationAndStart }: { setOpenInstantiateSimulationAndStart: Dispatch<SetStateAction<boolean>> }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const { scenarioId } = useParams() as { scenarioId: ScenarioType['scenario_id'] };
  const ability = useContext(AbilityContext);
  const dispatch = useAppDispatch();

  // Fetching data
  const {
    scenario,
    settings,
    injects,
    collectors,
    agents,
  } = useHelper((helper: ScenariosHelper & ExercisesHelper & LoggedHelper & InjectHelper & CollectorHelper & AgentHelper) => ({
    scenario: helper.getScenario(scenarioId),
    settings: helper.getPlatformSettings(),
    injects: helper.getScenarioInjects(scenarioId),
    collectors: helper.getExistingCollectors(),
    agents: helper.getAgents(),
  }));
  const areAnyExercisesInScenario = scenario.scenario_exercises?.length > 0;
  const sortByOrder = R.sortWith([R.ascend(R.prop('phase_order'))]);

  // Spy on modifications to reload healthchecks
  const [healthchecks, setHealthchecks] = useState<HealthCheck[]>([]);

  const isChainingFeatureEnabled = isFeatureEnabled('INJECT_CHAINING');
  const scenarioWorkflowId = (scenario as unknown as Record<string, unknown>).scenario_workflow_id as string | undefined;
  const isScenarioChaining = isChainingFeatureEnabled && !!scenarioWorkflowId;

  const isScopeMissing = isScenarioChaining
    && healthchecks.some((hc: HealthCheck) => hc.type === ('SCOPE_DEFINITION' as HealthCheck['type']) && hc.detail === 'EMPTY');

  const agentsActive = useMemo(() => {
    const injectAssetIds: string[] = injects.flatMap((inject: Inject) => inject.inject_assets);
    return agents
      .filter((agent: Agent) => injectAssetIds.includes(agent.agent_asset))
      .map((agent: Agent) => agent.agent_active);
  }, [agents, injects]);

  useDataLoader(() => {
    if (!injects) {
      dispatch(fetchScenarioInjects(scenarioId));
    }
  });

  useEffect(() => {
    searchScenarioHealthcheks(scenarioId).then((result: { data: HealthCheck[] }) => setHealthchecks(result.data));
  }, [
    settings?.smtp_service_available,
    settings?.imap_service_available,
    scenario,
    injects,
    collectors.length,
    agentsActive,
  ]);

  // Exercises
  const [loadingExercises, setLoadingExercises] = useState(true);
  const [exercises, setExercises] = useState<ExerciseSimple[]>([]);
  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(`scenario-${scenarioId}-simulations`, buildSearchPagination({ sorts: initSorting('exercise_updated_at', 'DESC') }));
  const search = (id: ScenarioType['scenario_id'], input: SearchPaginationInput) => {
    setLoadingExercises(true);
    return searchScenarioExercises(id, input).finally(() => {
      setLoadingExercises(false);
    });
  };
  const secondaryAction = (exercise: ExerciseSimple) => (
    <ExercisePopover
      // @ts-expect-error: should pass Exercise model IF we have update as action
      exercise={exercise}
      actions={isScenarioChaining ? ['Export', 'Delete'] : ['Duplicate', 'Export', 'Delete']}
      onDelete={result => setExercises(exercises.filter(e => (e.exercise_id !== result)))}
      inList
    />
  );

  // Latest finished simulation posture: the scenario overview reads as a live
  // AEV posture dashboard by surfacing the most recent run's prevention /
  // detection / vulnerability results + its MITRE ATT&CK coverage.
  const [lastSimulationId, setLastSimulationId] = useState<string | null>(null);
  const [lastResults, setLastResults] = useState<ExpectationResultsByType[] | null>(null);
  const [lastInjectResults, setLastInjectResults] = useState<InjectExpectationResultsByAttackPattern[] | null>(null);
  // Tracks whether the latest-run results fetch has settled, so we only fall back
  // to the illustrative sample preview once we know there is genuinely nothing to
  // show (never-run OR a run that produced no results) - never while still loading.
  const [lastResultsResolved, setLastResultsResolved] = useState<boolean>(false);
  useEffect(() => {
    if (!areAnyExercisesInScenario) {
      setLastSimulationId(null);
      setLastResults(null);
      setLastInjectResults(null);
      setLastResultsResolved(true);
      return () => {};
    }
    setLastResultsResolved(false);
    // Cancellation flag: a stale response must not overwrite the state reset by a
    // newer effect run, and every path (including failures) must resolve so the
    // sample fallback can kick in instead of silently dropping the sections.
    let cancelled = false;
    searchScenarioExercises(scenarioId, {
      size: 1,
      page: 0,
      sorts: [
        {
          property: 'exercise_end_date',
          direction: 'DESC',
          nullHandling: 'NULLS_LAST' as SortField['nullHandling'],
        },
        {
          property: 'exercise_updated_at',
          direction: 'DESC',
        },
      ],
    }).then((result: { data: { content?: ExerciseSimple[] } }) => {
      if (cancelled) {
        return;
      }
      const simulationId = result.data.content?.[0]?.exercise_id;
      if (!simulationId) {
        setLastResultsResolved(true);
        return;
      }
      setLastSimulationId(simulationId);
      Promise.all([
        fetchExerciseExpectationResult(simulationId).then((r: { data: ExpectationResultsByType[] }) => {
          if (!cancelled) setLastResults(r.data);
        }),
        fetchExerciseInjectExpectationResults(simulationId).then((r: { data: InjectExpectationResultsByAttackPattern[] }) => {
          if (!cancelled) setLastInjectResults(r.data);
        }),
      ]).finally(() => {
        if (!cancelled) setLastResultsResolved(true);
      });
    }).catch(() => {
      if (!cancelled) setLastResultsResolved(true);
    });
    return () => {
      cancelled = true;
    };
  }, [scenarioId, areAnyExercisesInScenario]);

  const lastAttackPatternIds = R.uniq(
    (lastInjectResults ?? [])
      .filter(injectResult => !!injectResult.inject_attack_pattern)
      .flatMap(injectResult => injectResult.inject_attack_pattern) as unknown as string[],
  );
  const hasMitreResults = !!lastInjectResults && lastAttackPatternIds.length > 0;
  const hasPosture = !!lastResults && lastResults.length > 0;

  // Render the SAME overview fed with illustrative sample data (greyed "Sample"
  // preview, like the home dashboard) both when the scenario has never run AND
  // when its latest run produced no results (e.g. a failed run) - so the overview
  // never degrades into a half-empty page with the posture / MITRE sections
  // silently dropped. Guarded by lastResultsResolved to avoid flashing the sample
  // during the initial results fetch.
  const hasNeverRun = !areAnyExercisesInScenario;
  const isSample = hasNeverRun || (lastResultsResolved && !hasPosture && !hasMitreResults);
  const canLaunch = ability.can(ACTIONS.LAUNCH, SUBJECTS.RESOURCE, scenario.scenario_id);
  const postureResults = isSample ? SAMPLE_POSTURE : lastResults;
  const showPosture = isSample || hasPosture;
  const showMitre = isSample || hasMitreResults;

  // Even without any run result we know which MITRE techniques the scenario's
  // injects target: feed them to the matrix with empty expectation results so
  // the REAL techniques render (muted, coverage unknown) instead of a greyed
  // empty sample.
  const plannedInjectResults: InjectExpectationResultsByAttackPattern[] = useMemo(
    () => (injects ?? []).flatMap((inject: Inject) =>
      (inject.inject_attack_patterns ?? []).map(attackPattern => ({
        inject_attack_pattern: attackPattern.attack_pattern_id,
        inject_expectation_results: [],
      }))),
    [injects],
  );

  const killChainPhases = sortByOrder(scenario.scenario_kill_chain_phases ?? []) as KillChainPhase[];
  const hasExternalUrl = !isEmptyField(scenario.scenario_external_url);

  // Posture / MITRE drill-downs: the overview surfaces the LATEST run's
  // results, so clicks land on the results explorer scoped to that simulation
  // (real results only - sample previews have nothing to drill into).
  const navigate = useNavigate();
  const location = useLocation();
  const openPostureResults = useCallback((type: string) => {
    if (!lastSimulationId) {
      return;
    }
    navigate(contextualResultsUrl(
      CONTEXTUAL_POSTURE_WIDGET_ID,
      'simulation',
      lastSimulationId,
      `${location.pathname}${location.search}`,
      { inject_expectation_type: [type] },
    ));
  }, [navigate, location, lastSimulationId]);

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
      paddingBottom: theme.spacing(5),
    }}
    >
      {isSample && (
        <Paper
          variant="outlined"
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 2,
            flexWrap: 'wrap',
            padding: 2,
            borderRadius: 1,
            border: `1px solid ${alpha(theme.palette.primary.main, 0.3)}`,
            background: `linear-gradient(135deg, ${alpha(theme.palette.primary.main, 0.1)}, transparent 60%)`,
          }}
        >
          <RocketLaunchOutlined color="primary" />
          <Box sx={{
            flex: 1,
            minWidth: 240,
          }}
          >
            <Typography sx={{
              fontWeight: 600,
              marginBottom: 0.25,
            }}
            >
              {hasNeverRun ? t('This scenario has not run yet') : t('No results to display yet')}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {t('The insights below are a sample preview. Launch a simulation to populate them with your real posture.')}
            </Typography>
          </Box>
          {canLaunch && (
            <Tooltip title={isScopeMissing ? t('A chained scenario requires a defined scope.') : ''}>
              <span style={{ display: 'inline-flex' }}>
                <Button
                  startIcon={<PlayArrowOutlined />}
                  variant="contained"
                  color="primary"
                  disabled={isScopeMissing}
                  onClick={() => setOpenInstantiateSimulationAndStart(true)}
                >
                  {t('Launch simulation now')}
                </Button>
              </span>
            </Tooltip>
          )}
        </Paper>
      )}

      {showPosture && (
        <SectionBlock title={t('Latest run posture')}>
          <SamplePreview active={isSample} variant="subtle">
            <PostureGauges
              expectationResultsByTypes={postureResults}
              humanValidationLink={!isSample && lastSimulationId ? `/admin/simulations/${lastSimulationId}/execution/validations` : undefined}
              onTypeClick={!isSample && lastSimulationId ? openPostureResults : undefined}
            />
          </SamplePreview>
        </SectionBlock>
      )}

      <Box sx={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))',
        gap: 2,
        alignItems: 'stretch',
      }}
      >
        <SectionBlock title={t('Information')}>
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
          }}
          >
            <Field label={t('Description')}>
              {scenario.scenario_description
                ? <ExpandableMarkdown source={scenario.scenario_description} limit={500} />
                : '-'}
            </Field>
            <Box sx={{
              display: 'grid',
              gridTemplateColumns: {
                xs: 'repeat(2, minmax(0, 1fr))',
                md: 'repeat(3, minmax(0, 1fr))',
              },
              columnGap: 3,
              rowGap: 2,
            }}
            >
              <Field label={t('Severity')}>
                <ItemSeverity severity={scenario.scenario_severity} label={t(scenario.scenario_severity ?? 'Unknown')} />
              </Field>
              <Field label={t('Category')}>
                <ItemCategory category={scenario.scenario_category} label={t(scenario.scenario_category ?? 'Unknown')} />
              </Field>
              <Field label={t('Main Focus')}>
                <ItemMainFocus mainFocus={scenario.scenario_main_focus} label={t(scenario.scenario_main_focus ?? 'Unknown')} />
              </Field>
              <Field label={t('Type Affinity')}>
                <ItemTypeAffinity typeAffinity={scenario?.scenario_type_affinity} />
              </Field>
              <Field label={t('Platforms')}>
                <PlatformIconGroup platforms={scenario.scenario_platforms} width={25} />
              </Field>
              <Field label={t('Tags')}>
                <ItemTags variant="list" tags={scenario.scenario_tags} limit={10} />
              </Field>
              <Box sx={{ gridColumn: '1 / -1' }}>
                <Field label={t('Kill Chain Phases')}>
                  <KillChainTimeline phases={killChainPhases} />
                </Field>
              </Box>
              {!isScenarioChaining && hasExternalUrl && (
                <Box sx={{ gridColumn: '1 / -1' }}>
                  <Field label={t('Threat intelligence')}>
                    <Button
                      component={Link}
                      to={scenario.scenario_external_url}
                      target="_blank"
                      size="small"
                      variant="outlined"
                      startIcon={(
                        <Avatar
                          style={{
                            width: 20,
                            height: 20,
                          }}
                          src={theme.palette.mode === 'dark' ? octiDark : octiLight}
                          alt="OCTI"
                        />
                      )}
                    >
                      {t('Open in OpenCTI')}
                    </Button>
                  </Field>
                </Box>
              )}
            </Box>
          </Box>
        </SectionBlock>

        <SectionBlock title={t('Posture trend')}>
          {/* The trend aggregates every past run, so it can show real history even
              when only the latest run has no results. It owns its own sample marking
              (greyed + "Sample" chip when it falls back to illustrative data), so it
              must NOT be wrapped in an outer SamplePreview here - that would either
              double-mark it or, worse, hide that its data is a sample. */}
          <ScenarioDistributionByExercise scenarioId={scenarioId} />
        </SectionBlock>
      </Box>

      {showMitre && (
        <SectionBlock title={t('Kill chain results')}>
          {/* In sample mode the matrix lists the REAL techniques targeted by
              the scenario's injects (muted boxes, coverage unknown): only grey
              it as an illustrative sample when even the techniques are not
              known yet (no injects with attack patterns). */}
          <SamplePreview active={isSample && plannedInjectResults.length === 0} variant="subtle">
            <MitreCoverageMatrix
              widgetId={`scenario-mitre-${scenarioId}`}
              injectResults={isSample ? plannedInjectResults : lastInjectResults}
              defaultKillChain={scenario.scenario_default_kill_chain}
              resultsContext={!isSample && lastSimulationId
                ? {
                    source: 'simulation',
                    contextId: lastSimulationId,
                  }
                : undefined}
            />
          </SamplePreview>
        </SectionBlock>
      )}

      {areAnyExercisesInScenario && (
        <SectionBlock title={t('Simulations')}>
          <PaginationComponentV2
            fetch={input => search(scenarioId, input)}
            searchPaginationInput={searchPaginationInput}
            setContent={setExercises}
            entityPrefix="exercise"
            availableFilterNames={['exercise_kill_chain_phases', 'exercise_name', 'exercise_tags']}
            queryableHelpers={queryableHelpers}
            searchEnable={false}
          />
          <SimulationList
            exercises={exercises}
            queryableHelpers={queryableHelpers}
            secondaryAction={secondaryAction}
            loading={loadingExercises}
            isGlobalScoreAsync={true}
          />
        </SectionBlock>
      )}
    </Box>
  );
};

export default Scenario;
