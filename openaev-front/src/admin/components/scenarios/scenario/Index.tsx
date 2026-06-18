import { NotificationsOutlined, UpdateOutlined } from '@mui/icons-material';
import { Alert, AlertTitle, Box, IconButton, Tab, Tabs, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, lazy, Suspense, useEffect, useState } from 'react';
import { Link, Route, Routes, useLocation, useParams } from 'react-router';

import { DATA_FETCH_SUCCESS } from '../../../../constants/ActionTypes';
import { fetchScenario } from '../../../../actions/scenarios/scenario-actions';
import { type ScenariosHelper } from '../../../../actions/scenarios/scenario-helper';
import { findNotificationRuleByResource } from '../../../../actions/scenarios/scenario-notification-rules';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { errorWrapper } from '../../../../components/Error';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import NotFound from '../../../../components/NotFound';
import { useHelper } from '../../../../store';
import {
  type NotificationRuleOutput,
  type Scenario,
  type ScenarioOutput,
} from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import handle from '../../../../utils/period/Period';
import { type PeriodExpressionHandler } from '../../../../utils/period/PeriodExpressionHandler';
import { INHERITED_CONTEXT } from '../../../../utils/permissions/types';
import useScenarioPermissions from '../../../../utils/permissions/useScenarioPermissions';
import { isFeatureEnabled } from '../../../../utils/utils';
import { MOCK_CHAINING_SCENARIO_IDS, MOCK_SCENARIO_LIST } from '../../simulations/simulation/attack_path/mockAttackPathData';
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
const ScenarioFindingsMock = lazy(() => import('./findings/ScenarioFindingsMock'));
const ScenarioLogicMock = lazy(() => import('./logic/ScenarioLogicMock'));
const ScenarioAnalysis = lazy(() => import('./analysis/ScenarioAnalysis'));
const ScenarioScope = lazy(() => import('./scope/ScenarioScope'));
const ScenarioLogic = lazy(() => import('./logic/ScenarioLogic'));
const ScenarioAttackPath = lazy(() => import('./attack_path/ScenarioAttackPath'));

const MS_PER_DAY = 1000 * 60 * 60 * 24;

const IndexScenarioComponent: FunctionComponent<{ scenario: ScenarioOutput }> = ({ scenario }) => {
  const { t, locale, fld } = useFormatter();
  const location = useLocation();
  const theme = useTheme();
  const isChainingFeatureEnabled = isFeatureEnabled('INJECT_CHAINING');
  const isAttackPathMockEnabled = isFeatureEnabled('CHAINING_ATTACK_PATH');
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
  const [cronObject, setCronObject] = useState<PeriodExpressionHandler | null>(handle(scenario.scenario_recurrence));
  const noRepeat = !!scenario.scenario_recurrence_end && !!scenario.scenario_recurrence_start
    && new Date(scenario.scenario_recurrence_end).getTime() - new Date(scenario.scenario_recurrence_start).getTime() <= MS_PER_DAY
    && ['noRepeat', 'daily'].includes(selectRecurring);
  const getHumanReadableScheduling = () => {
    if (!cronObject?.isValid()) {
      return null;
    }
    // process time

    let sentence: string;
    sentence = `${cronObject.toTranslatableStringArray(locale).map(element => t(element)).join(' ')}`;
    if (scenario.scenario_recurrence_end) {
      sentence += ` ${t('recurrence_from')} ${fld(scenario.scenario_recurrence_start)}`;
      sentence += ` ${t('recurrence_to')} ${fld(scenario.scenario_recurrence_end)}`;
    } else {
      sentence += ` ${t('recurrence_starting_from')} ${fld(scenario.scenario_recurrence_start)}`;
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
    if (MOCK_CHAINING_SCENARIO_IDS.has(scenario.scenario_id)) return;
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
            cronObject={cronObject}
            setCronObject={setCronObject}
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
            {
              (isChainingFeatureEnabled || isAttackPathMockEnabled) && (scenario.scenario_workflow_id || isAttackPathMockEnabled) ? (
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
                    to={`/admin/scenarios/${scenario.scenario_id}/scope`}
                    value={`/admin/scenarios/${scenario.scenario_id}/scope`}
                    label={t('Scope Definition')}
                  />
                  <Tab
                    component={Link}
                    to={`/admin/scenarios/${scenario.scenario_id}/logic`}
                    value={`/admin/scenarios/${scenario.scenario_id}/logic`}
                    label={t('Logic')}
                  />
                  <Tab
                    component={Link}
                    to={`/admin/scenarios/${scenario.scenario_id}/analysis`}
                    value={`/admin/scenarios/${scenario.scenario_id}/analysis`}
                    label={t('Analysis')}
                  />
                  <Tab
                    component={Link}
                    to={`/admin/scenarios/${scenario.scenario_id}/findings`}
                    value={`/admin/scenarios/${scenario.scenario_id}/findings`}
                    label={t('Findings')}
                  />
                  <Tab
                    component={Link}
                    to={`/admin/scenarios/${scenario.scenario_id}/attack_path`}
                    value={`/admin/scenarios/${scenario.scenario_id}/attack_path`}
                    label={t('Attack Path')}
                  />
                </Tabs>
              ) : (
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
              )
            }

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
                  {!cronObject?.isValid() && (
                    <IconButton size="small" onClick={() => setOpenScenarioRecurringFormDialog(true)} style={{ marginRight: theme.spacing(1) }}>
                      <UpdateOutlined color="primary" />
                    </IconButton>
                  )}
                  {cronObject?.isValid() && !scenario.scenario_recurrence && (
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
                  {cronObject?.isValid() && scenario.scenario_recurrence && (
                    <Tooltip title={(t('Modify the scheduling'))}>
                      <IconButton size="small" onClick={() => setOpenScenarioRecurringFormDialog(true)} style={{ marginRight: theme.spacing(1) }}>
                        <UpdateOutlined color="primary" />
                      </IconButton>
                    </Tooltip>
                  )}
                  <span style={{ color: theme.palette.text?.disabled }}>{!cronObject?.isValid() && t('Not scheduled')}</span>
                  {cronObject?.isValid() && <span>{getHumanReadableScheduling()}</span>}
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
              <Route path="findings" element={errorWrapper(isAttackPathMockEnabled && MOCK_CHAINING_SCENARIO_IDS.has(scenario.scenario_id) ? ScenarioFindingsMock : ScenarioFindings)()} />
              <Route path="analysis" element={errorWrapper(ScenarioAnalysis)()} />
              <Route path="scope" element={errorWrapper(ScenarioScope)()} />
              <Route path="logic" element={errorWrapper(isAttackPathMockEnabled && MOCK_CHAINING_SCENARIO_IDS.has(scenario.scenario_id) ? ScenarioLogicMock : ScenarioLogic)()} />
              <Route path="attack_path" element={errorWrapper(ScenarioAttackPath)()} />
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
  const isMockScenario = MOCK_CHAINING_SCENARIO_IDS.has(scenarioId);

  // For mock scenarios that exist only client-side, synthesize a Scenario so
  // all useHelper(getScenario) consumers throughout the tree get a valid object.
  const mockEntry = isMockScenario
    ? MOCK_SCENARIO_LIST.find(s => s.scenario_id === scenarioId)
    : undefined;
  const mockScenario: Scenario | undefined = mockEntry
    ? {
        scenario_id: mockEntry.scenario_id,
        scenario_name: mockEntry.scenario_name,
        scenario_created_at: mockEntry.scenario_created_at,
        scenario_updated_at: mockEntry.scenario_updated_at,
        scenario_mail_from: mockEntry.scenario_mail_from,
        scenario_severity: mockEntry.scenario_severity,
        scenario_category: mockEntry.scenario_category,
        scenario_platforms: mockEntry.scenario_platforms as string[],
        scenario_tags: mockEntry.scenario_tags,
      } as unknown as Scenario
    : undefined;

  const { scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));
  useDataLoader(() => {
    if (isMockScenario) {
      // Skip API call for client-side mock scenarios to avoid 404.
      // Inject mock scenario into the Redux store so all useHelper(getScenario)
      // consumers (e.g. ScenarioHeader) return a valid object instead of crashing.
      if (mockScenario) {
        dispatch({
          type: DATA_FETCH_SUCCESS,
          payload: { entities: { scenarios: { [scenarioId]: mockScenario } } },
        });
      }
      setPristine(false);
      setLoading(false);
      return;
    }
    setLoading(true);
    dispatch(fetchScenario(scenarioId)).finally(() => {
      setPristine(false);
      setLoading(false);
    });
  });

  const effectiveScenario: Scenario | undefined = scenario ?? mockScenario;
  const scenarioInjectContext = injectContextForScenario(effectiveScenario as Scenario);

  // avoid to show loader if something trigger useDataLoader
  if (pristine && loading) {
    return <Loader />;
  }
  if (!loading && !effectiveScenario) {
    return (
      <Alert severity="warning">
        <AlertTitle>{t('Warning')}</AlertTitle>
        {t('Scenario is currently unavailable or you do not have sufficient permissions to access it.')}
      </Alert>
    );
  }
  return (
    <InjectContext.Provider value={scenarioInjectContext}>
      <IndexScenarioComponent scenario={effectiveScenario as unknown as ScenarioOutput} />
    </InjectContext.Provider>
  );
};

export default Index;
