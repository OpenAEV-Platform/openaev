import { useContext } from 'react';
import { useParams } from 'react-router';

import { addVariableForScenario, deleteVariableForScenario, fetchVariablesForScenario, updateVariableForScenario } from '../../../../../actions/variables/variable-actions';
import { type VariablesHelper } from '../../../../../actions/variables/variable-helper';
import { useHelper } from '../../../../../store';
import { type Scenario, type Variable, type VariableInput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import { PermissionsContext, VariableContext, type VariableContextType } from '../../../common/Context';
import CreateVariable from '../../../components/variables/CreateVariable';
import Variables from '../../../components/variables/Variables';
import ConfigurationFab from '../ConfigurationFab';

const ScenarioVariables = () => {
  // Standard hooks
  const dispatch = useAppDispatch();

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
      {/* No inner Paper / section title: the Configuration tab already labels
          this section, and the list sits directly on the drawer surface. */}
      {permissions.canManage && (
        <ConfigurationFab>
          <CreateVariable />
        </ConfigurationFab>
      )}
      <Variables variables={variables} />
    </VariableContext.Provider>
  );
};

export default ScenarioVariables;
