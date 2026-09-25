import { Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
import { type FunctionComponent, type ReactNode, useState } from 'react';
import { Link, useLocation } from 'react-router';

import { type AutonomousRun } from '../../../../actions/autonomous/autonomous-types';
import { searchInjectTests } from '../../../../actions/inject_test/simulation-inject-test-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type SimulationDetails } from '../../../../utils/api-types';
import useHasInjectTests from '../../injects/useHasInjectTests';
import ExerciseHeader from './ExerciseHeader';
import buildSimulationTabs from './simulationTabs';

// Shared simulation chrome: breadcrumbs + hero header + navigation tabs.
// Used by the simulation Index and by screens that must live OUTSIDE the Index
// route tree for route-ranking reasons (e.g. the full-page inject creation
// flow), so the user always keeps the simulation context on screen.
const SimulationShell: FunctionComponent<{
  exercise: SimulationDetails;
  children: ReactNode;
  /** Present when this simulation is an autonomous (AI-driven) run: swaps the manual chaining tabs
   *  (Scope, Logic) for the AI cockpit and turns the hero observe-only (control lives on the parent
   *  scenario). */
  autonomousRun?: AutonomousRun | null;
}> = ({ exercise, children, autonomousRun = null }) => {
  const { t } = useFormatter();
  const location = useLocation();
  const [isLoading, setIsLoading] = useState(false);
  // Mirror the route gate in Index.tsx exactly: the Attack path screen is workflow-backed, so its
  // tab must only appear when the route is actually registered. A plan-mode / dry-run simulation has
  // no workflow yet, so offering the tab here would route to NotFound (the 404 users were seeing).
  const hasWorkflow = !!exercise.exercise_workflow_id;
  const isAutonomous = !!autonomousRun;
  const base = `/admin/simulations/${exercise.exercise_id}`;
  // The Tests tab only exists for email/SMS injects that have actually been
  // tested; hide it entirely otherwise.
  const hasInjectTests = useHasInjectTests(searchInjectTests, exercise.exercise_id);

  let tabValue = location.pathname;
  if (location.pathname.includes(`${base}/injects`)) {
    tabValue = `${base}/injects`;
  } else if (location.pathname.includes(`${base}/execution`)) {
    tabValue = `${base}/execution`;
  } else if (location.pathname.includes(`${base}/tests`)) {
    tabValue = `${base}/tests`;
  }

  // Tab set depends on the simulation flavour:
  // - autonomous (AI-driven): the AI provisions and drives the attack path, so Scope and Logic are
  //   surfaced in read-only mode (inspection only) while the operator steers from the reasoning panel;
  // - chained (workflow-backed): Overview / Scope / Logic / Execution / Lessons / Attack path /
  //   Findings / Statistics;
  // - time-based: Overview / Injects / Tests / Execution / Lessons / Findings / Statistics.
  const tabs: [string, string][] = buildSimulationTabs({
    lessonsEnabled: exercise.exercise_lessons_enabled,
    isAutonomous,
    hasWorkflow,
    hasInjectTests,
    t,
  });

  // A value that matches no tab leaves every tab unselected: screens without a
  // dedicated tab (e.g. dashboard) keep the bar with nothing highlighted.
  const validTabValue = tabs.some(([suffix]) => `${base}${suffix}` === tabValue) ? tabValue : '';

  return (
    <>
      <Breadcrumbs
        variant="object"
        elements={[
          {
            label: t('Simulations'),
            link: '/admin/simulations',
          },
          {
            label: exercise.exercise_name,
            current: true,
          },
        ]}
      />
      <ExerciseHeader
        onLoading={setIsLoading}
        isLoading={isLoading}
        autonomousRun={autonomousRun}
      />
      {isLoading
        ? <Loader />
        : (
            <>
              <Tabs value={validTabValue} panels="external" style={{ marginBottom: 16 }}>
                <TabsList>
                  {tabs.map(([suffix, label]) => {
                    const path = `${base}${suffix}`;
                    return (
                      <TabsTrigger key={suffix} value={path} asChild>
                        <Link to={path} aria-current={validTabValue === path ? 'page' : undefined}>{label}</Link>
                      </TabsTrigger>
                    );
                  })}
                </TabsList>
              </Tabs>
              {children}
            </>
          )}
    </>
  );
};

export default SimulationShell;
