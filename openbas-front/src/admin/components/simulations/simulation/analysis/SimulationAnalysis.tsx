import { useContext, useEffect, useState } from 'react';
import { useParams } from 'react-router';

import { fetchCustomDashboard } from '../../../../../actions/custom_dashboards/customdashboard-action';
import { fetchExercise } from '../../../../../actions/Exercise';
import type { ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import Loader from '../../../../../components/Loader';
import { useHelper } from '../../../../../store';
import { type Exercise } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import CustomDashboardComponent from '../../../workspaces/custom_dashboards/CustomDashboard';
import { CustomDashboardContext } from '../../../workspaces/custom_dashboards/CustomDashboardContext';

const SimulationAnalysis = () => {
  const dispatch = useAppDispatch();
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };
  const [loading, setLoading] = useState(true);
  const exercise = useHelper((helper: ExercisesHelper) => {
    return helper.getExercise(exerciseId);
  });
  useDataLoader(() => {
    dispatch(fetchExercise(exerciseId));
  });

  const { customDashboard, setCustomDashboard, setCustomDashboardParameters } = useContext(CustomDashboardContext);

  useEffect(() => {
    if (exercise.exercise_custom_dashboard != '-') {
      fetchCustomDashboard(exercise.exercise_custom_dashboard).then((response) => {
        if (response.data) {
          const dashboard = response.data;
          // FIXME: Revise the parameter definition to indicate a hidden filter
          setCustomDashboard(dashboard);

          const params: Record<string, string> = {};
          dashboard.custom_dashboard_parameters?.forEach((p: {
            custom_dashboards_parameter_type: string;
            custom_dashboards_parameter_id: string;
          }) => {
            // FIXME: Rework the parameter definition to flag it as a hidden filter, which should also be treated as contextual and not displayed
            if ('simulation' === p.custom_dashboards_parameter_type) {
              params[p.custom_dashboards_parameter_id] = exerciseId;
            }
          });
          setCustomDashboardParameters(params);

          setLoading(false);
        }
      });
    } else {
      setCustomDashboard(undefined);
      setLoading(false);
    }
  }, [exercise]);

  if (loading) {
    return <Loader />;
  }

  return (
    <>
      {customDashboard && <CustomDashboardComponent readOnly />}
    </>
  );
};

export default SimulationAnalysis;
