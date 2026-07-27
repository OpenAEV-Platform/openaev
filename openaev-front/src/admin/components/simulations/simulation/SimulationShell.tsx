import { Box, Tab, Tabs } from '@mui/material';
import { type FunctionComponent, type ReactNode, useState } from 'react';
import { Link, useLocation } from 'react-router';

import { searchInjectTests } from '../../../../actions/inject_test/simulation-inject-test-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type SimulationDetails } from '../../../../utils/api-types';
import { isFeatureEnabled } from '../../../../utils/utils';
import useHasInjectTests from '../../injects/useHasInjectTests';
import ExerciseHeader from './ExerciseHeader';

// Shared simulation chrome: breadcrumbs + hero header + navigation tabs.
// Used by the simulation Index and by screens that must live OUTSIDE the Index
// route tree for route-ranking reasons (e.g. the full-page inject creation
// flow), so the user always keeps the simulation context on screen.
const SimulationShell: FunctionComponent<{
  exercise: SimulationDetails;
  children: ReactNode;
}> = ({ exercise, children }) => {
  const { t } = useFormatter();
  const location = useLocation();
  const [isLoading, setIsLoading] = useState(false);
  const isChainingFeatureEnabled = isFeatureEnabled('INJECT_CHAINING');
  const isAttackPathEnabled = isFeatureEnabled('ATTACK_PATH');
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

  const tabs: [string, string][] = isChainingFeatureEnabled && exercise.exercise_workflow_id
    ? [
        ['', t('Overview')],
        ['/scope', t('Scope')],
        ['/logic', t('Logic')],
        ['/execution', t('Execution')],
        ...(isAttackPathEnabled ? [['/attack-path', t('Attack path')] as [string, string]] : []),
        ['/statistics', t('Statistics')],
      ]
    : [
        // Attack path is a chained-simulation concept (workflow executions):
        // time-based simulations never get the tab.
        ['', t('Overview')],
        ['/injects', t('Injects')],
        ...(hasInjectTests ? [['/tests', t('Tests')] as [string, string]] : []),
        ['/execution', t('Execution')],
        // The lessons learned module is opt-in (simulation configuration).
        ...(exercise.exercise_lessons_enabled ? [['/lessons', t('Lessons learned')] as [string, string]] : []),
        ['/findings', t('Findings')],
        ['/statistics', t('Statistics')],
      ];

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
      <ExerciseHeader onLoading={setIsLoading} isLoading={isLoading} />
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
