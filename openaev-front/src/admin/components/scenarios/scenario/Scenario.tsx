import { GroupsOutlined, HubOutlined, PersonOutlined, PlayArrowOutlined, TrackChangesOutlined } from '@mui/icons-material';
import { Avatar, Box, Button, Chip, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type Dispatch, type SetStateAction, useContext, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router';

import { type AgentHelper } from '../../../../actions/agents/agent-helper';
import type { CollectorHelper } from '../../../../actions/collectors/collector-helper';
import { fetchExerciseExpectationResult, fetchExerciseInjectExpectationResults } from '../../../../actions/exercises/exercise-action';
import { type ExercisesHelper } from '../../../../actions/exercises/exercise-helper';
import type { LoggedHelper } from '../../../../actions/helper';
import { fetchScenarioInjects } from '../../../../actions/Inject';
import { type InjectHelper } from '../../../../actions/injects/inject-helper';
import { searchScenarioExercises, searchScenarioHealthcheks } from '../../../../actions/scenarios/scenario-actions';
import { type ScenariosHelper } from '../../../../actions/scenarios/scenario-helper';
import { Field, MetricGrid, MetricTile, SectionBlock } from '../../../../components/common/detail/EntityDetailCommon';
import PostureGauges from '../../../../components/common/detail/PostureGauges';
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
import PlatformIconGroup from '../../../../components/PlatformIconGroup';
import TypeAffinityChip from '../../../../components/TypeAffinityChip';
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
import Healthchecks from '../../common/healthchecks/Healthchecks';
import MitreCoverageMatrix from '../../common/matrix/MitreCoverageMatrix';
import ExercisePopover from '../../simulations/simulation/ExercisePopover';
import SimulationList from '../../simulations/SimulationList';
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
  useEffect(() => {
    if (!areAnyExercisesInScenario) {
      setLastSimulationId(null);
      setLastResults(null);
      setLastInjectResults(null);
      return;
    }
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
      const simulationId = result.data.content?.[0]?.exercise_id;
      if (!simulationId) return;
      setLastSimulationId(simulationId);
      fetchExerciseExpectationResult(simulationId).then((r: { data: ExpectationResultsByType[] }) => setLastResults(r.data));
      fetchExerciseInjectExpectationResults(simulationId).then((r: { data: InjectExpectationResultsByAttackPattern[] }) => setLastInjectResults(r.data));
    });
  }, [scenarioId, areAnyExercisesInScenario]);

  const lastAttackPatternIds = R.uniq(
    (lastInjectResults ?? [])
      .filter(injectResult => !!injectResult.inject_attack_pattern)
      .flatMap(injectResult => injectResult.inject_attack_pattern) as unknown as string[],
  );
  const hasMitreResults = !!lastInjectResults && lastAttackPatternIds.length > 0;
  const hasPosture = !!lastResults && lastResults.length > 0;

  const killChainPhases = sortByOrder(scenario.scenario_kill_chain_phases ?? []) as KillChainPhase[];
  const hasExternalUrl = !isEmptyField(scenario.scenario_external_url);
  const injectsCount = scenario.scenario_injects?.length ?? 0;
  const simulationsCount = scenario.scenario_exercises?.length ?? 0;
  const teamsCount = scenario.scenario_teams?.length ?? 0;
  const playersCount = scenario.scenario_all_users_number ?? scenario.scenario_users_number ?? 0;

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
      paddingBottom: theme.spacing(5),
    }}
    >
      {!!healthchecks?.length && (
        <Healthchecks
          healthchecks={healthchecks}
          scenarioId={scenarioId}
        />
      )}

      <MetricGrid>
        <MetricTile icon={TrackChangesOutlined} label={t('Injects')} value={injectsCount} />
        <MetricTile icon={HubOutlined} label={t('Simulations')} value={simulationsCount} />
        <MetricTile icon={GroupsOutlined} label={t('Teams')} value={teamsCount} />
        <MetricTile icon={PersonOutlined} label={t('Players')} value={playersCount} />
      </MetricGrid>

      {hasPosture && (
        <SectionBlock title={t('Latest run posture')}>
          <PostureGauges
            expectationResultsByTypes={lastResults}
            humanValidationLink={lastSimulationId ? `/admin/simulations/${lastSimulationId}/animation/validations` : undefined}
          />
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
              gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
              gap: 1.5,
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
                <TypeAffinityChip affinity_text={scenario?.scenario_type_affinity} />
              </Field>
              <Field label={t('Platforms')}>
                <PlatformIconGroup platforms={scenario.scenario_platforms} width={25} />
              </Field>
              <Field label={t('Tags')}>
                <ItemTags variant="list" tags={scenario.scenario_tags} limit={10} />
              </Field>
              <Box sx={{ gridColumn: '1 / -1' }}>
                <Field label={t('Kill Chain Phases')}>
                  {killChainPhases.length === 0 ? '-' : (
                    <Box sx={{
                      display: 'flex',
                      flexWrap: 'wrap',
                      gap: 0.5,
                    }}
                    >
                      {killChainPhases.map(killChainPhase => (
                        <Chip
                          key={killChainPhase.phase_id}
                          variant="outlined"
                          color="error"
                          size="small"
                          sx={{
                            borderRadius: 1,
                            textTransform: 'uppercase',
                            fontSize: 11,
                          }}
                          label={killChainPhase.phase_name}
                        />
                      ))}
                    </Box>
                  )}
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
          <ScenarioDistributionByExercise scenarioId={scenarioId} />
        </SectionBlock>
      </Box>

      {hasMitreResults && (
        <SectionBlock title={t('MITRE ATT&CK Results')}>
          <MitreCoverageMatrix
            widgetId={`scenario-mitre-${scenarioId}`}
            injectResults={lastInjectResults}
          />
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
      {!areAnyExercisesInScenario && !scenario.scenario_recurrence && ability.can(ACTIONS.LAUNCH, SUBJECTS.RESOURCE, scenario.scenario_id) && (
        <div style={{
          marginTop: 100,
          textAlign: 'center',
        }}
        >
          <div style={{ fontSize: 20 }}>
            {t('This scenario has never run, schedule or run it now!')}
          </div>
          <Tooltip title={isScopeMissing ? t('A Chaining Scenario requires a defined scope.') : ''}>
            <span style={{
              display: 'inline-flex',
              marginTop: theme.spacing(2),
            }}
            >
              <Button
                startIcon={<PlayArrowOutlined />}
                variant="contained"
                color="primary"
                size="large"
                disabled={isScopeMissing}
                onClick={() => setOpenInstantiateSimulationAndStart(true)}
              >
                {t('Launch simulation now')}
              </Button>
            </span>
          </Tooltip>
        </div>
      )}
      {!areAnyExercisesInScenario && scenario.scenario_recurrence && (
        <div style={{
          marginTop: 100,
          textAlign: 'center',
        }}
        >
          <div style={{ fontSize: 20 }}>
            {t('This scenario is scheduled to run, results will appear soon.')}
          </div>
        </div>
      )}
    </Box>
  );
};

export default Scenario;
