import { ToggleButtonGroup } from '@mui/material';
import { useState } from 'react';

import { searchExercises } from '../../../actions/Exercise';
import Breadcrumbs from '../../../components/Breadcrumbs';
import ExportButton from '../../../components/common/ExportButton';
import { initSorting } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../components/i18n';
import { type ExerciseSimple, type SearchPaginationInput } from '../../../utils/api-types';
import { Can } from '../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import ImportUploaderExercise from './ImportUploaderExercise';
import ExerciseCreation from './simulation/ExerciseCreation';
import ExercisePopover from './simulation/ExercisePopover';
import SimulationList from './SimulationList';

const Simulations = () => {
  // Standard hooks
  const { t } = useFormatter();

  const [loading, setLoading] = useState<boolean>(true);
  const [exercises, setExercises] = useState<ExerciseSimple[]>([]);

  // Filters
  const availableFilterNames = [
    'exercise_kill_chain_phases',
    'exercise_name',
    'exercise_scenario',
    'exercise_start_date',
    'exercise_status',
    'exercise_tags',
    'exercise_updated_at',
  ];

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('simulations', buildSearchPagination({ sorts: initSorting('exercise_updated_at', 'DESC') }));

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

  const secondaryAction = (exercise: ExerciseSimple) => (
    <ExercisePopover
      // @ts-expect-error: should pass Exercise model IF we have update as action
      exercise={exercise}
      actions={['Duplicate', 'Export', 'Delete']}
      onDelete={result => setExercises(exercises.filter(e => (e.exercise_id !== result)))}
      inList
    />
  );

  const search = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchExercises(input).finally(() => {
      setLoading(false);
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
          <ToggleButtonGroup value="fake" exclusive>
            <ExportButton totalElements={queryableHelpers.paginationHelpers.getTotalElements()} exportProps={exportProps} />
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
              <ImportUploaderExercise />
            </Can>
          </ToggleButtonGroup>
        )}
      />
      <SimulationList
        exercises={exercises}
        queryableHelpers={queryableHelpers}
        secondaryAction={secondaryAction}
        loading={loading}
      />
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
        <ExerciseCreation />
      </Can>
    </>
  );
};

export default Simulations;
