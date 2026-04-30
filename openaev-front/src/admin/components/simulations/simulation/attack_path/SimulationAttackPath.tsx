import { Alert, Box, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router';

import { fetchExerciseInjectExpectations } from '../../../../../actions/Exercise';
import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { type ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import { useHelper } from '../../../../../store';
import type { Exercise, InjectExpectation } from '../../../../../utils/api-types';
import type { Workflow, WorkflowStep } from '../../../../../utils/api-types-custom';
import { useAppDispatch } from '../../../../../utils/hooks';
import { isFeatureEnabled } from '../../../../../utils/utils';
import { fetchWorkflow } from '../../../../../actions/chaining/workflow-actions';
import AttackPathFlow from './AttackPathFlow';
import AttackPathFeed from './AttackPathFeed';

import '@xyflow/react/dist/style.css';

const SimulationAttackPath: FunctionComponent = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };

  const attackPathEnabled = isFeatureEnabled('CHAINING_ATTACK_PATH');

  const exercise = useHelper((helper: ExercisesHelper) => helper.getExercise(exerciseId));
  const expectations: InjectExpectation[] = useHelper((helper: ExercisesHelper) =>
    helper.getExerciseInjectExpectations(exerciseId),
  ) ?? [];

  const scenarioId = exercise?.exercise_scenario;
  const scenario = useHelper((helper: ScenariosHelper) =>
    scenarioId ? helper.getScenario(scenarioId) : null,
  );
  const workflowId = scenario?.scenario_workflow_id;

  const [workflow, setWorkflow] = useState<Workflow | null>(null);
  const [loading, setLoading] = useState(true);
  const [selectedStepId, setSelectedStepId] = useState<string | null>(null);

  // Fetch expectations into Redux store
  useEffect(() => {
    dispatch(fetchExerciseInjectExpectations(exerciseId));
  }, [exerciseId]);

  // Fetch workflow using the resolved workflowId
  useEffect(() => {
    if (!workflowId) {
      setLoading(false);
      return;
    }
    setLoading(true);
    fetchWorkflow(workflowId)
      .then((result: Workflow) => setWorkflow(result))
      .catch(() => setWorkflow(null))
      .finally(() => setLoading(false));
  }, [workflowId]);

  // Poll expectations every 10s for live updates during execution
  useEffect(() => {
    if (exercise?.exercise_status !== 'RUNNING') return undefined;
    const interval = setInterval(() => {
      dispatch(fetchExerciseInjectExpectations(exerciseId));
    }, 10_000);
    return () => clearInterval(interval);
  }, [exerciseId, exercise?.exercise_status]);

  const handleSelectStep = useCallback((stepId: string | null) => {
    setSelectedStepId(stepId);
  }, []);

  if (!attackPathEnabled) {
    return null;
  }

  if (loading) return <Loader />;

  const steps: WorkflowStep[] = workflow?.workflow_steps ?? [];

  if (!workflow || steps.length === 0) {
    return (
      <Alert severity="info" sx={{ mt: 2 }}>
        {t('No chaining workflow configured for this simulation. Configure the attack chain in the scenario logic tab first.')}
      </Alert>
    );
  }

  return (
    <Box sx={{ display: 'flex', height: 'calc(100vh - 260px)', overflow: 'hidden' }}>
      {/* Left panel ÔÇö live execution feed */}
      <AttackPathFeed
        steps={steps}
        expectations={expectations}
        injectStatuses={{}}
        selectedStepId={selectedStepId}
        onSelectStep={handleSelectStep}
      />

      {/* Right panel ÔÇö ReactFlow graph */}
      <Box sx={{ flex: 1, position: 'relative' }}>
        {/* Banner */}
        <Box
          sx={{
            position: 'absolute',
            top: 8,
            left: '50%',
            transform: 'translateX(-50%)',
            zIndex: 10,
            backgroundColor: 'rgba(255, 193, 7, 0.15)',
            border: '1px solid rgba(255, 193, 7, 0.4)',
            borderRadius: 1,
            px: 2,
            py: 0.5,
            pointerEvents: 'none',
          }}
        >
          <Typography variant="caption" sx={{ color: theme.palette.text.secondary }}>
            {t('Click a node to highlight the attack path')}
          </Typography>
        </Box>

        <AttackPathFlow
          steps={steps}
          expectations={expectations}
          selectedStepId={selectedStepId}
          onSelectStep={handleSelectStep}
        />
      </Box>
    </Box>
  );
};

export default SimulationAttackPath;
