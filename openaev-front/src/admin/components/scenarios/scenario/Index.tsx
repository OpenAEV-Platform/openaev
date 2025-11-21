import { NotificationsOutlined, UpdateOutlined } from '@mui/icons-material';
import { Alert, AlertTitle, Box, IconButton, Tab, Tabs, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import cronstrue from 'cronstrue';
import { type FunctionComponent, lazy, Suspense, useEffect, useState } from 'react';
import { Link, Route, Routes, useLocation, useParams } from 'react-router';

import { fetchScenario } from '../../../../actions/scenarios/scenario-actions';
import { type ScenariosHelper } from '../../../../actions/scenarios/scenario-helper';
import { findNotificationRuleByResource } from '../../../../actions/scenarios/scenario-notification-rules';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { errorWrapper } from '../../../../components/Error';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import NotFound from '../../../../components/NotFound';
import { useHelper } from '../../../../store';
import { type NotificationRuleOutput, type Scenario } from '../../../../utils/api-types';
import { parseCron, type ParsedCron } from '../../../../utils/Cron';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { INHERITED_CONTEXT } from '../../../../utils/permissions/types';
import useScenarioPermissions from '../../../../utils/permissions/useScenarioPermissions';
import { DocumentContext, type DocumentContextType, InjectContext, PermissionsContext, type PermissionsContextType } from '../../common/Context';
import ScenarioNotificationRulesDrawer from './notification_rule/ScenarioNotificationRulesDrawer';
import injectContextForScenario from './ScenarioContext';
import ScenarioHeader from './ScenarioHeader';

const ScenarioComponent = lazy(() => import('./Scenario'));
const ScenarioDefinition = lazy(() => import('./ScenarioDefinition'));
const Injects = lazy(() => import('./injects/ScenarioInjects'));
const Tests = lazy(() => import('./tests/ScenarioTests'));
const Lessons = lazy(() => import('./lessons/ScenarioLessons'));
const ScenarioFindings = lazy(() => import('./findings/ScenarioFindings'));
const ScenarioAnalysis = lazy(() => import('./analysis/ScenarioAnalysis'));

// eslint-disable-next-line no-underscore-dangle
const _MS_PER_DAY = 1000 * 60 * 60 * 24;

const IndexScenarioComponent: FunctionComponent<{ scenario: Scenario }> = ({ scenario }) => {
  const { t, ft, locale, fld } = useFormatter();
  const location = useLocation();
  const theme = useTheme();
  const permissionsContext: PermissionsContextType = {
    permissions: useScenarioPermissions(scenario.scenario_id),
    inherited_context: INHERITED_CONTEXT.SCENARIO,
  };
  const documentContext: DocumentContextType = {
    onInitDocument: () => ({
      document_tags: [],
      document_scenarios: scenario
        ? [{
            id: scenario.scenario_id,
            label: scenario.scenario_name,
          }]
        : [],
      document_exercises: [],
    }),
  };
  let tabValue = location.pathname;
  if (location.pathname.includes(`/admin/scenarios/${scenario.scenario_id}/definition`)) {
    tabValue = `/admin/scenarios/${scenario.scenario_id}/definition`;
  } else if (location.pathname.includes(`/admin/scenarios/${scenario.scenario_id}/tests`)) {
    tabValue = `/admin/scenarios/${scenario.scenario_id}/tests`;
  }
  const [openScenarioRecurringFormDialog, setOpenScenarioRecurringFormDialog] = useState<boolean>(false);
  const [openInstantiateSimulationAndStart, setOpenInstantiateSimulationAndStart] = useState<boolean>(false);
  const [selectRecurring, setSelectRecurring] = useState('noRepeat');
  const [cronExpression, setCronExpression] = useState<string | null>(scenario.scenario_recurrence || null);
  const [parsedCronExpression, setParsedCronExpression] = useState<ParsedCron | null>(scenario.scenario_recurrence ? parseCron(scenario.scenario_recurrence) : null);
  const noRepeat = !!scenario.scenario_recurrence_end && !!scenario.scenario_recurrence_start
    && new Date(scenario.scenario_recurrence_end).getTime() - new Date(scenario.scenario_recurrence_start).getTime() <= _MS_PER_DAY
    && ['noRepeat', 'daily'].includes(selectRecurring);
  const getHumanReadableScheduling = () => {
    if (!cronExpression || !parsedCronExpression) {
      return null;
    }
    let sentence: string;
    if (noRepeat) {
      sentence = `${fld(scenario.scenario_recurrence_start)} ${t('recurrence_at')} ${ft(new Date().setUTCHours(parsedCronExpression.h, parsedCronExpression.m, 0))}`;
    } else {
      const base = new Date();
      base.setUTCHours(parsedCronExpression.h, parsedCronExpression.m, 0, 0);

      const localHour = base.getHours();
      const localMinute = base.getMinutes();

      const rest = cronExpression.split(' ').slice(3).join(' ');

      sentence = cronstrue.toString(`0 ${localMinute} ${localHour} ${rest}`, {
        verbose: true,
        locale,
      });
      if (scenario.scenario_recurrence_end) {
        sentence += ` ${t('recurrence_from')} ${fld(scenario.scenario_recurrence_start)}`;
        sentence += ` ${t('recurrence_to')} ${fld(scenario.scenario_recurrence_end)}`;
      } else {
        sentence += ` ${t('recurrence_starting_from')} ${fld(scenario.scenario_recurrence_start)}`;
      }
    }
    return sentence;
  };
  const [openScenarioNotificationRuleDrawer, setOpenScenarioNotificationRuleDrawer] = useState(false);
  const [editNotification, setEditNotification] = useState<boolean>(false);
  const [notificationRule, setNotificationRule] = useState<NotificationRuleOutput>({
    notification_rule_id: '',
    notification_rule_resource_id: '',
    notification_rule_resource_type: '',
    notification_rule_subject: '',
    notification_rule_trigger: '',
  });

  useEffect(() => {
    findNotificationRuleByResource(scenario.scenario_id).then((result: { data: NotificationRuleOutput[] }) => {
      if (result.data.length > 0) {
        setEditNotification(true);
        setNotificationRule(result.data[0]);
      }
    });
  }, []);

  const onCreateNotification = (result: NotificationRuleOutput) => {
    setEditNotification(true);
    setNotificationRule(result);
  };

  const onDeleteNotification = () => {
    setEditNotification(false);
    setNotificationRule({
      notification_rule_id: '',
      notification_rule_resource_id: '',
      notification_rule_resource_type: '',
      notification_rule_subject: '',
      notification_rule_trigger: '',
    });
  };

  return (
    <PermissionsContext.Provider value={permissionsContext}>
      <DocumentContext.Provider value={documentContext}>
        <>
          <Breadcrumbs
            variant="list"
            elements={[
              {
                label: t('Scenarios'),
                link: '/admin/scenarios',
              },
              {
                label: scenario.scenario_name,
                current: true,
              },
            ]}
          />
          <ScenarioHeader
            setCronExpression={setCronExpression}
            setParsedCronExpression={setParsedCronExpression}
            setSelectRecurring={setSelectRecurring}
            selectRecurring={selectRecurring}
            setOpenScenarioRecurringFormDialog={setOpenScenarioRecurringFormDialog}
            openScenarioRecurringFormDialog={openScenarioRecurringFormDialog}
            setOpenInstantiateSimulationAndStart={setOpenInstantiateSimulationAndStart}
            openInstantiateSimulationAndStart={openInstantiateSimulationAndStart}
            noRepeat={noRepeat}
          />
          <Box
            sx={{
              borderBottom: 1,
              borderColor: 'divider',
              marginBottom: 2,
            }}
            display="flex"
            flexDirection="row"
            justifyContent="space-between"
          >
            <Tabs
              style={{ flex: 1 }}
              value={tabValue}
              variant="scrollable"
              scrollButtons="auto"
            >
              <Tab
                component={Link}
                to={`/admin/scenarios/${scenario.scenario_id}`}
                value={`/admin/scenarios/${scenario.scenario_id}`}
                label={t('Overview')}
              />
              <Tab
                component={Link}
                to={`/admin/scenarios/${scenario.scenario_id}/definition`}
                value={`/admin/scenarios/${scenario.scenario_id}/definition`}
                label={t('Definition')}
              />
              <Tab
                component={Link}
                to={`/admin/scenarios/${scenario.scenario_id}/injects`}
                value={`/admin/scenarios/${scenario.scenario_id}/injects`}
                label={t('Injects')}
              />
              <Tab
                component={Link}
                to={`/admin/scenarios/${scenario.scenario_id}/tests`}
                value={`/admin/scenarios/${scenario.scenario_id}/tests`}
                label={t('Tests')}
              />
              <Tab
                component={Link}
                to={`/admin/scenarios/${scenario.scenario_id}/lessons`}
                value={`/admin/scenarios/${scenario.scenario_id}/lessons`}
                label={t('Lessons learned')}
              />
              <Tab
                component={Link}
                to={`/admin/scenarios/${scenario.scenario_id}/findings`}
                value={`/admin/scenarios/${scenario.scenario_id}/findings`}
                label={t('Findings')}
              />
              <Tab
                component={Link}
                to={`/admin/scenarios/${scenario.scenario_id}/analysis`}
                value={`/admin/scenarios/${scenario.scenario_id}/analysis`}
                label={t('Analysis')}
              />
            </Tabs>
            <div style={{
              display: 'flex',
              flexDirection: 'row',
            }}
            >
              {
                permissionsContext.permissions.canManage && (
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                  }}
                  >
                    <IconButton
                      size="small"
                      style={{ marginRight: theme.spacing(1) }}
                      onClick={() => setOpenScenarioNotificationRuleDrawer(true)}
                    >
                      <NotificationsOutlined color={editNotification ? 'success' : 'primary'} />
                    </IconButton>
                    <Typography
                      variant="body1"
                      style={{ marginRight: theme.spacing(1) }}
                    >
                      {t('Notification rules')}
                    </Typography>
                    <ScenarioNotificationRulesDrawer
                      open={openScenarioNotificationRuleDrawer}
                      setOpen={setOpenScenarioNotificationRuleDrawer}
                      editing={editNotification}
                      onCreate={onCreateNotification}
                      onUpdate={result => setNotificationRule(result)}
                      onDelete={onDeleteNotification}
                      notificationRule={notificationRule}
                      scenarioId={scenario.scenario_id}
                      scenarioName={scenario.scenario_name}
                    />
                  </div>
                )
              }
              { permissionsContext.permissions.canManage && (
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                }}
                >
                  {!cronExpression && (
                    <IconButton size="small" onClick={() => setOpenScenarioRecurringFormDialog(true)} style={{ marginRight: theme.spacing(1) }}>
                      <UpdateOutlined color="primary" />
                    </IconButton>
                  )}
                  {cronExpression && !scenario.scenario_recurrence && (
                    <IconButton
                      size="small"
                      style={{
                        cursor: 'default',
                        marginRight: theme.spacing(1),
                      }}
                    >
                      <UpdateOutlined />
                    </IconButton>
                  )}
                  {cronExpression && scenario.scenario_recurrence && (
                    <Tooltip title={(t('Modify the scheduling'))}>
                      <IconButton size="small" onClick={() => setOpenScenarioRecurringFormDialog(true)} style={{ marginRight: theme.spacing(1) }}>
                        <UpdateOutlined color="primary" />
                      </IconButton>
                    </Tooltip>
                  )}
                  <span style={{ color: theme.palette.text?.disabled }}>{!cronExpression && t('Not scheduled')}</span>
                  {cronExpression && <span>{getHumanReadableScheduling()}</span>}
                </div>
              )}

            </div>

          </Box>
          <Suspense fallback={<Loader />}>
            <Routes>
              <Route path="" element={errorWrapper(ScenarioComponent)({ setOpenInstantiateSimulationAndStart })} />
              <Route path="definition" element={errorWrapper(ScenarioDefinition)()} />
              <Route path="injects" element={errorWrapper(Injects)()} />
              <Route path="tests/:statusId?" element={errorWrapper(Tests)()} />
              <Route path="lessons" element={errorWrapper(Lessons)()} />
              <Route path="findings" element={errorWrapper(ScenarioFindings)()} />
              <Route path="analysis" element={errorWrapper(ScenarioAnalysis)()} />
              {/* Not found */}
              <Route path="*" element={<NotFound />} />
            </Routes>
          </Suspense>
        </>
      </DocumentContext.Provider>
    </PermissionsContext.Provider>
  );
};

const Index = () => {
  // Standard hooks
  const dispatch = useAppDispatch();
  const [pristine, setPristine] = useState(true);
  const [loading, setLoading] = useState(true);
  const { t } = useFormatter();
  // Fetching data
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
  useDataLoader(() => {
    setLoading(true);
    dispatch(fetchScenario(scenarioId)).finally(() => {
      setPristine(false);
      setLoading(false);
    });
  });

  const scenarioInjectContext = injectContextForScenario(scenario);

  // avoid to show loader if something trigger useDataLoader
  if (pristine && loading) {
    return <Loader />;
  }
  if (!loading && !scenario) {
    return (
      <Alert severity="warning">
        <AlertTitle>{t('Warning')}</AlertTitle>
        {t('Scenario is currently unavailable or you do not have sufficient permissions to access it.')}
      </Alert>
    );
  }
  return (
    <InjectContext.Provider value={scenarioInjectContext}>
      <IndexScenarioComponent scenario={scenario} />
    </InjectContext.Provider>
  );
};

export default Index;
