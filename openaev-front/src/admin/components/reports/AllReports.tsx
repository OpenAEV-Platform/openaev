import { Description } from '@mui/icons-material';
import { Box, Tab, Tabs, Typography } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';

import { searchExercises } from '../../../actions/Exercise';
import {
  downloadGeneratedReportUrl,
  downloadGlobalGeneratedReportUrl,
  downloadScenarioGeneratedReportUrl,
  fetchAllGeneratedReports,
} from '../../../actions/generated_reports/generatedreport-action';
import { searchScenarios } from '../../../actions/scenarios/scenario-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import { sendErrorToBackend } from '../../../utils/Action';
import { type GeneratedReport } from '../../../utils/api-types';
import GenerateGlobalReportButton from '../simulations/simulation/generated_reports/GenerateGlobalReportButton';
import ReportsScopeTab from './ReportsScopeTab';

type ReportScope = 'GLOBAL' | 'SIMULATION' | 'SCENARIO';

const scopeOf = (report: GeneratedReport): ReportScope => {
  if (report.generated_report_scenario) return 'SCENARIO';
  if (report.generated_report_exercise) return 'SIMULATION';
  return 'GLOBAL';
};

const downloadUrlFor = (report: GeneratedReport) => {
  const scope = scopeOf(report);
  if (scope === 'SIMULATION') return downloadGeneratedReportUrl(report.generated_report_exercise!, report.generated_report_id);
  if (scope === 'SCENARIO') return downloadScenarioGeneratedReportUrl(report.generated_report_scenario!, report.generated_report_id);
  return downloadGlobalGeneratedReportUrl(report.generated_report_id);
};

/**
 * Unified "Reports" page (left menu, under Findings): every report the
 * current user has generated, whatever its scope - Global, Simulation or
 * Scenario - split into 3 tabs, each with its own independent filter bar
 * (template / technical layout / trigger / status) and a working Download
 * action per row. Also hosts the persisted "Generate Global Report" action
 * (saved, unlike the Home page's ephemeral quick button).
 */
const AllReports: FunctionComponent = () => {
  const { t } = useFormatter();
  const [loading, setLoading] = useState(true);
  const [reports, setReports] = useState<GeneratedReport[]>([]);
  const [exerciseNames, setExerciseNames] = useState<Record<string, string>>({});
  const [scenarioNames, setScenarioNames] = useState<Record<string, string>>({});
  const [currentTab, setCurrentTab] = useState<ReportScope>('GLOBAL');

  const loadAll = () => {
    setLoading(true);
    Promise.all([
      fetchAllGeneratedReports(),
      searchExercises({
        page: 0,
        size: 1000,
        sorts: [],
      }),
      searchScenarios({
        page: 0,
        size: 1000,
        sorts: [],
      }),
    ])
      .then(([reportsRes, exercisesRes, scenariosRes]) => {
        setReports(reportsRes.data ?? []);
        const exNames: Record<string, string> = {};
        (exercisesRes.data?.content ?? []).forEach((exercise: {
          exercise_id: string;
          exercise_name: string;
        }) => {
          exNames[exercise.exercise_id] = exercise.exercise_name;
        });
        setExerciseNames(exNames);
        const scNames: Record<string, string> = {};
        (scenariosRes.data?.content ?? []).forEach((scenario: {
          scenario_id: string;
          scenario_name: string;
        }) => {
          scNames[scenario.scenario_id] = scenario.scenario_name;
        });
        setScenarioNames(scNames);
      })
      .catch((e) => {
        sendErrorToBackend(e as Error, { componentStack: 'AllReports' });
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadAll();
  }, []);

  if (loading) {
    return <Loader />;
  }

  const globalReports = reports.filter(r => scopeOf(r) === 'GLOBAL');
  const simulationReports = reports.filter(r => scopeOf(r) === 'SIMULATION');
  const scenarioReports = reports.filter(r => scopeOf(r) === 'SCENARIO');

  const simulationSourceLabel = (report: GeneratedReport) => exerciseNames[report.generated_report_exercise!] ?? report.generated_report_exercise ?? '';
  const scenarioSourceLabel = (report: GeneratedReport) => scenarioNames[report.generated_report_scenario!] ?? report.generated_report_scenario ?? '';
  const globalSourceLabel = () => t('All simulations (platform-wide)');

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Reports'),
          current: true,
        }]}
      />
      <Box sx={{
        borderBottom: 1,
        borderColor: 'divider',
        marginBottom: 2,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
      }}
      >
        <Tabs
          value={currentTab}
          onChange={(_, val) => setCurrentTab(val)}
        >
          <Tab label={`${t('Global')} (${globalReports.length})`} value="GLOBAL" />
          <Tab label={`${t('Simulation')} (${simulationReports.length})`} value="SIMULATION" />
          <Tab label={`${t('Scenario')} (${scenarioReports.length})`} value="SCENARIO" />
        </Tabs>
        {currentTab === 'GLOBAL' && <GenerateGlobalReportButton onGenerated={loadAll} />}
      </Box>
      {currentTab === 'GLOBAL' && (
        <ReportsScopeTab
          reports={globalReports}
          sourceLabel={globalSourceLabel}
          downloadUrlFor={downloadUrlFor}
          emptyLabel="No global report has been generated yet. Use the button above to generate one."
        />
      )}
      {currentTab === 'SIMULATION' && (
        <ReportsScopeTab
          reports={simulationReports}
          sourceLabel={simulationSourceLabel}
          downloadUrlFor={downloadUrlFor}
          emptyLabel="No simulation report has been generated yet. Generate one from a simulation's Access reports panel."
        />
      )}
      {currentTab === 'SCENARIO' && (
        <ReportsScopeTab
          reports={scenarioReports}
          sourceLabel={scenarioSourceLabel}
          downloadUrlFor={downloadUrlFor}
          emptyLabel="No scenario report has been generated yet. Generate one from a scenario's Access reports panel."
        />
      )}
      {reports.length === 0 && (
        <div style={{
          textAlign: 'center',
          padding: 40,
          color: 'gray',
        }}
        >
          <Description fontSize="large" />
          <Typography variant="body1" style={{ marginTop: 8 }}>
            {t('No report has been generated yet. Generate one from Simulations, Scenarios, or use the button above for a Global report.')}
          </Typography>
        </div>
      )}
    </>
  );
};

export default AllReports;
