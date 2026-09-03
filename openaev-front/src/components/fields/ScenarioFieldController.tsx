import { Kayaking } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import { fetchScenarios } from '../../actions/scenarios/scenario-actions';
import { type ScenariosHelper } from '../../actions/scenarios/scenario-helper';
import { useHelper } from '../../store';
import { type Scenario } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import useDataLoader from '../../utils/hooks/useDataLoader';
import { type Option } from '../../utils/Option';
import EntityMultiSelectFieldController, { type EntityMultiSelectFieldControllerProps } from './EntityMultiSelectFieldController';

type Props = Omit<EntityMultiSelectFieldControllerProps, 'options' | 'icon'>;

const ScenarioFieldController: FunctionComponent<Props> = (props) => {
  const dispatch = useAppDispatch();

  const scenarios = useHelper((helper: ScenariosHelper) => helper.getScenarios());
  useDataLoader(() => {
    dispatch(fetchScenarios());
  });

  const options: Option[] = (scenarios ?? []).map((scenario: Scenario) => ({
    id: scenario.scenario_id,
    label: scenario.scenario_name,
  }));

  return (
    <EntityMultiSelectFieldController
      {...props}
      options={options}
      icon={<Kayaking />}
    />
  );
};

export default ScenarioFieldController;
