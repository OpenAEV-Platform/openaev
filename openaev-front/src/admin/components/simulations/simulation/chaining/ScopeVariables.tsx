import { Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext } from 'react';
import { useParams } from 'react-router';

import {
  addVariableForExercise,
  deleteVariableForExercise,
  fetchVariablesForExercise,
  updateVariableForExercise,
} from '../../../../../actions/variables/variable-actions';
import { type VariablesHelper } from '../../../../../actions/variables/variable-helper';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Exercise, type Variable, type VariableInput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { PermissionsContext, VariableContext, type VariableContextType } from '../../../common/Context';
import CreateVariable from '../../../components/variables/CreateVariable';
import Variables from '../../../components/variables/Variables';

const ScopeVariables = () => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const theme = useTheme();

  // Fetching data
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };
  const { permissions } = useContext(PermissionsContext);
  const variables = useHelper((helper: VariablesHelper) => helper.getExerciseVariables(exerciseId));
  useDataLoader(() => {
    dispatch(fetchVariablesForExercise(exerciseId));
  });

  const context: VariableContextType = {
    onCreateVariable: (data: VariableInput) => dispatch(addVariableForExercise(exerciseId, data)),
    onEditVariable: (variable: Variable, data: VariableInput) => dispatch(updateVariableForExercise(exerciseId, variable.variable_id, data)),
    onDeleteVariable: (variable: Variable) => dispatch(deleteVariableForExercise(exerciseId, variable.variable_id)),
  };

  return (
    <VariableContext.Provider value={context}>
      <div style={{
        display: 'grid',
        gridTemplateRows: 'min-content 1fr',
        gap: theme.spacing(1),
      }}
      >
        <Typography
          variant="h4"
          sx={{
            display: 'flex',
            alignItems: 'center',
          }}
        >
          {t('Variables')}
          {permissions.canManage && (<CreateVariable />)}
        </Typography>
        <Paper sx={{ padding: theme.spacing(2) }} variant="outlined">
          <Variables variables={variables} />
        </Paper>
      </div>
    </VariableContext.Provider>
  );
};

export default ScopeVariables;
