import { Typography } from '@mui/material';
import { useContext, useState } from 'react';

import { fetchExercise } from '../../../../../../../actions/Exercise';
import { type ExercisesHelper } from '../../../../../../../actions/exercises/exercise-helper';
import { useFormatter } from '../../../../../../../components/i18n';
import Loader from '../../../../../../../components/Loader';
import { useHelper } from '../../../../../../../store';
import { type EsAttackPath } from '../../../../../../../utils/api-types';
import type { StructuralHistogramWidget } from '../../../../../../../utils/api-types-custom';
import { useAppDispatch } from '../../../../../../../utils/hooks';
import useDataLoader from '../../../../../../../utils/hooks/useDataLoader';
import { CustomDashboardContext } from '../../../CustomDashboardContext';
import AttackPath from './AttackPath';

interface Props {
  attackPathsData: EsAttackPath[];
  widgetId: string;
  widgetConfig: StructuralHistogramWidget;
}

const AttackPathContextLayer = ({ attackPathsData, widgetId, widgetConfig }: Props) => {
  const dispatch = useAppDispatch();
  const [loading, setLoading] = useState<boolean>(false);
  const { t } = useFormatter();

  const { customDashboard, customDashboardParameters } = useContext(CustomDashboardContext);

  const simulationParamIdFromSerie = (((widgetConfig.series[0] || []).filter?.filters || []).find(f => f.key == 'base_simulation_side')?.values ?? [])[0];
  const dashboardParameterId = customDashboard?.custom_dashboard_parameters?.find(p => p.custom_dashboards_parameter_type === 'simulation' && p.custom_dashboards_parameter_id === simulationParamIdFromSerie)?.custom_dashboards_parameter_id;
  const simulationIdContext = dashboardParameterId == simulationParamIdFromSerie ? customDashboardParameters[dashboardParameterId].value : simulationParamIdFromSerie;

  const { exercise } = useHelper((helper: ExercisesHelper) => ({ exercise: helper.getExercise(simulationIdContext) }));

  useDataLoader(() => {
    if (!simulationIdContext) {
      return;
    }
    setLoading(true);
    dispatch(fetchExercise(simulationIdContext)).finally(() => {
      setLoading(false);
    });
  }, []);

  if (!simulationIdContext) {
    return (
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        height: '80%',
      }}
      >
        <Typography variant="h5" sx={{ textAlign: 'center' }}>
          {t('You must set a simulation')}
        </Typography>

      </div>
    );
  }

  if (loading || !exercise) {
    return <Loader />;
  }

  return (
    <AttackPath
      data={attackPathsData}
      widgetId={widgetId}
      simulationId={simulationIdContext}
      simulationStartDate={exercise?.exercise_start_date}
      simulationEndDate={exercise?.exercise_end_date}
    />
  );
};

export default AttackPathContextLayer;
