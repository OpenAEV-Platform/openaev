import { Box, ToggleButtonGroup } from '@mui/material';
import { useContext, useState } from 'react';

import { bulkDeleteExercises, searchExercises } from '../../../actions/Exercise';
import Breadcrumbs from '../../../components/Breadcrumbs';
import ExportButton from '../../../components/common/ExportButton';
import { initSorting } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../components/i18n';
import { type ExerciseSimple, type SearchPaginationInput } from '../../../utils/api-types';
import useEntityToggle from '../../../utils/hooks/useEntityToggle';
import { AbilityContext, Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../utils/utils';
import useAutonomousRunsIndex from '../autonomous/useAutonomousRunsIndex';
import ToolBar from '../common/ToolBar';
import ImportUploaderExercise from './ImportUploaderExercise';
import ExerciseCreation from './simulation/ExerciseCreation';
import ExercisePopover from './simulation/ExercisePopover';
import SimulationList from './SimulationList';

const Simulations = () => {
  // Standard hooks
  const { t } = useFormatter();

  const [loading, setLoading] = useState<boolean>(true);
  const [exercises, setExercises] = useState<ExerciseSimple[]>([]);
  const isChainingFeatureEnabled = isFeatureEnabled('INJECT_CHAINING');
  // Index of the tenant's autonomous runs so each row's popover can mirror the simulation cockpit:
  // an AI-driven simulation is observe-only, so its overflow exposes only a read-only Export
  // (deletion tears the run down and is a parent-scenario control).
  const autonomousRuns = useAutonomousRunsIndex();

  // Filters
  const availableFilterNames = [
    'exercise_kill_chain_phases',
    'exercise_name',
    'exercise_scenario',
    'exercise_severity',
    'exercise_start_date',
    'exercise_status',
    'exercise_tags',
    'exercise_updated_at',
  ];

  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage('simulations', buildSearchPagination({ sorts: initSorting('exercise_updated_at', 'DESC') }));

  // Export
  const exportProps = {
    exportType: 'exercise',
    exportKeys: [
      'exercise_name',
      'exercise_subtitle',
      'exercise_description',
      'exercise_status',
      'exercise_tags',
    ],
    exportData: exercises,
    exportFileName: `${t('Simulations')}.csv`,
  };

  const secondaryAction = (exercise: ExerciseSimple) => {
    const isChaining = isChainingFeatureEnabled && !!(exercise as unknown as Record<string, unknown>).exercise_workflow_id;
    const isAutonomous = !!autonomousRuns.bySimulation(exercise.exercise_id);

    let exerciseActions: ('Duplicate' | 'Update' | 'Delete' | 'Export')[] = ['Duplicate', 'Export', 'Delete'];
    if (isAutonomous) {
      exerciseActions = ['Export'];
    } else if (isChaining) {
      exerciseActions = ['Export', 'Delete'];
    }

    return (
      <ExercisePopover
        // @ts-expect-error: should pass Exercise model IF we have update as action
        exercise={exercise}
        actions={exerciseActions}
        onDelete={result => setExercises(exercises.filter(e => (e.exercise_id !== result)))}
        inList
      />
    );
  };

  const search = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchExercises(input).finally(() => {
      setLoading(false);
    });
  };

  // Bulk selection
  const ability = useContext(AbilityContext);
  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
  const entityToggle = useEntityToggle<ExerciseSimple>('exercise', exercises, queryableHelpers.paginationHelpers.getTotalElements());
  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    numberOfSelectedElements,
  } = entityToggle;

  const bulkDelete = () => {
    bulkDeleteExercises({
      search_pagination_input: selectAll ? searchPaginationInput : undefined,
      exercise_ids_to_process: selectAll ? undefined : Object.keys(selectedElements),
      exercise_ids_to_ignore: Object.keys(deSelectedElements),
    }).then((result) => {
      const deletedIds: string[] = result.data ?? [];
      const newTotal = Math.max(0, queryableHelpers.paginationHelpers.getTotalElements() - deletedIds.length);
      setExercises(exercises.filter(e => !deletedIds.includes(e.exercise_id)));
      queryableHelpers.paginationHelpers.handleChangeTotalElements(newTotal);
      handleClearSelectedElements();
    });
  };

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Simulations'),
          current: true,
        }]}
      />
      <PaginationComponentV2
        fetch={search}
        searchPaginationInput={searchPaginationInput}
        setContent={setExercises}
        entityPrefix="exercise"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        topBarButtons={(
          <Box display="flex" gap={1} alignItems="center">
            <ToggleButtonGroup value="fake" exclusive>
              <ExportButton
                totalElements={queryableHelpers.paginationHelpers.getTotalElements()}
                exportProps={exportProps}
              />
              <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
                <ImportUploaderExercise />
              </Can>
            </ToggleButtonGroup>
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
              <ExerciseCreation />
            </Can>
          </Box>
        )}
      />
      <SimulationList
        exercises={exercises}
        queryableHelpers={queryableHelpers}
        secondaryAction={secondaryAction}
        loading={loading}
        entityToggle={canManage ? entityToggle : undefined}
        toolBar={(
          <ToolBar
            numberOfSelectedElements={numberOfSelectedElements}
            handleClearSelectedElements={handleClearSelectedElements}
            handleBulkDelete={bulkDelete}
            canManage={canManage}
            deleteConfirmationSingular={t('Do you want to delete this simulation?')}
            deleteConfirmationPlural={t('Do you want to delete these {count} simulations?', { count: String(numberOfSelectedElements) })}
          />
        )}
      />
    </>
  );
};

export default Simulations;
