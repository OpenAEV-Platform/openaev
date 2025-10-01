import { Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext } from 'react';
import { useParams } from 'react-router';

import { addVariableForScenario, deleteVariableForScenario, fetchVariablesForScenario, updateVariableForScenario } from '../../../../../actions/variables/variable-actions';
import { type VariablesHelper } from '../../../../../actions/variables/variable-helper';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type Scenario, type Variable, type VariableInput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { PermissionsContext, VariableContext, type VariableContextType } from '../../../common/Context';
import CreateVariable from '../../../components/variables/CreateVariable';
import Variables from '../../../components/variables/Variables';

const ScenarioVariables = () => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const theme = useTheme();

  // Fetching data
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const { permissions } = useContext(PermissionsContext);
  const { variables } = useHelper((helper: VariablesHelper) => ({ variables: helper.getScenarioVariables(scenarioId) }));
  useDataLoader(() => {
    dispatch(fetchVariablesForScenario(scenarioId));
  });

  const context: VariableContextType = {
    onCreateVariable: (data: VariableInput) => dispatch(addVariableForScenario(scenarioId, data)),
    onEditVariable: (variable: Variable, data: VariableInput) => dispatch(updateVariableForScenario(scenarioId, variable.variable_id, data)),
    onDeleteVariable: (variable: Variable) => dispatch(deleteVariableForScenario(scenarioId, variable.variable_id)),
  };

  return (
    <VariableContext.Provider value={context}>
      <div style={{
        display: 'grid',
        gap: `0 ${theme.spacing(3)}`,
        gridTemplateRows: 'min-content 1fr',
      }}
      >
        <Typography variant="h4">
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

export default ScenarioVariables;
