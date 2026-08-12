import { Box, Tab, Tabs } from '@mui/material';
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
  const isAttackPathEnabled = !!exercise.exercise_workflow_id;
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
  // - chained (workflow-backed): Overview / Scope / Logic / Execution / Attack path / Findings / Statistics;
  // - time-based: Overview / Injects / Tests / Execution / Lessons / Findings / Statistics.
  const buildTabs = (): [string, string][] => {
    if (isAutonomous) {
      // Scope and Logic are ALWAYS surfaced (read-only) for an autonomous run: their routes are
      // registered unconditionally in Index.tsx, and they must stay visible even before the AI has
      // provisioned a workflow (e.g. a plan-mode / dry-run simulation that has no workflow yet), so
      // the operator can always inspect the perimeter and the designed logic. Only Attack path is
      // workflow-backed, so it alone is gated on the workflow existing (its route is too).
      return [
        ['', t('Overview')],
        ['/scope', t('Scope')],
        ['/logic', t('Logic')],
        ...(isAttackPathEnabled ? [['/attack-path', t('Attack Path')] as [string, string]] : []),
        ['/execution', t('Execution')],
        ['/findings', t('Findings')],
        ['/statistics', t('Statistics')],
      ];
    }
    if (exercise.exercise_workflow_id) {
      return [
        ['', t('Overview')],
        ['/scope', t('Scope')],
        ['/logic', t('Logic')],
        ['/execution', t('Execution')],
        ...(isAttackPathEnabled ? [['/attack-path', t('Attack Path')] as [string, string]] : []),
        ['/findings', t('Findings')],
        ['/statistics', t('Statistics')],
      ];
    }
    return [
      ['', t('Overview')],
      ['/injects', t('Injects')],
      ...(hasInjectTests ? [['/tests', t('Tests')] as [string, string]] : []),
      ['/execution', t('Execution')],
      // The lessons learned module is opt-in (simulation configuration).
      ...(exercise.exercise_lessons_enabled ? [['/lessons', t('Lessons learned')] as [string, string]] : []),
      ['/findings', t('Findings')],
      ['/statistics', t('Statistics')],
    ];
  };
  const tabs: [string, string][] = buildTabs();

  // MUI Tabs requires the value to match one of the rendered tabs; screens
  // without a dedicated tab (e.g. dashboard) deselect all tabs instead.
  const validTabValue = tabs.some(([suffix]) => `${base}${suffix}` === tabValue) ? tabValue : false;

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
              <Box
                sx={{
                  borderBottom: 1,
                  borderColor: 'divider',
                  marginBottom: 2,
                }}
              >
                <Tabs value={validTabValue}>
                  {tabs.map(([suffix, label]) => (
                    <Tab
                      key={suffix}
                      component={Link}
                      to={`${base}${suffix}`}
                      value={`${base}${suffix}`}
                      label={label}
                    />
                  ))}
                </Tabs>
              </Box>
              {children}
            </>
          )}
    </>
  );
};

export default SimulationShell;
