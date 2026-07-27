import { useParams } from 'react-router';

import { type ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { useHelper } from '../../../../../store';
import { type Scenario } from '../../../../../utils/api-types';
import ScenarioGeneratedReports from '../../../simulations/simulation/generated_reports/ScenarioGeneratedReports';

/**
 * Scenario "Access Reports" tab: generation trigger + history of previously
 * generated scenario reports (Executive/Technical, aggregated across every
 * run within the selected comparison window). Reuses the generic
 * `ScenarioGeneratedReports` component built alongside the per-simulation
 * and global structured-PDF reports.
 */
const ScenarioReports = () => {
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const scenario = useHelper((helper: ScenariosHelper) => helper.getScenario(scenarioId));

  if (!scenario) return null;
  return <ScenarioGeneratedReports scenario={scenario} />;
};

export default ScenarioReports;
