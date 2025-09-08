import { KeyboardArrowRight } from '@mui/icons-material';
import { Link } from 'react-router';

import { SCENARIO_BASE_URL, SIMULATION_BASE_URL } from '../../../../../../../../../constants/BaseUrls';
import { type EsInject, type EsScenario, type EsSimulation } from '../../../../../../../../../utils/api-types';

type Props = { element: EsInject | EsSimulation | EsScenario };

const InjectElementSecondaryAction = (props: Props) => {
  if (props.element.base_entity === 'scenario') {
    const scenarioUrl = `${SCENARIO_BASE_URL}/${props.element.base_id}`;
    return (<Link to={scenarioUrl} className="noDrag"><KeyboardArrowRight color="action" /></Link>);
  } else if (props.element.base_entity === 'simulation') {
    const simulationUrl = `${SIMULATION_BASE_URL}/${props.element.base_id}`;
    return (<Link to={simulationUrl} className="noDrag"><KeyboardArrowRight color="action" /></Link>);
  } else {
    return <></>;
  // TODO #3524 redirect to atomic testing, atomic testing from simulation or atomic testing from scenario
  // const atomicTestingUrl = `${ATOMIC_BASE_URL}/${props.element.base_id}`;
  // return (<Link to={atomicTestingUrl} className="noDrag"><KeyboardArrowRight color="action" /></Link>);
  }
};

export default InjectElementSecondaryAction;
