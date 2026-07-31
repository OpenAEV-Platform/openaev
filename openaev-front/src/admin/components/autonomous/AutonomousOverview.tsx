import { AccountTreeOutlined, AutoAwesome, DownloadOutlined, ErrorOutline, VerifiedOutlined, WarningAmberOutlined } from '@mui/icons-material';
import { Alert, Box, Button, Chip, Paper, Stack, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent, useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';

import { fetchAutonomousTimeline } from '../../../actions/autonomous/autonomous-actions';
import { type AutonomousEvent, type AutonomousRun, type AutonomousRunStatus } from '../../../actions/autonomous/autonomous-types';
import { fetchExerciseExpectationResult, fetchExerciseInjectExpectationResults } from '../../../actions/exercises/exercise-action';
import { SectionBlock } from '../../../components/common/detail/EntityDetailCommon';
import PostureGauges from '../../../components/common/detail/PostureGauges';
import SAMPLE_POSTURE from '../../../components/common/detail/samplePosture';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import { SCENARIO_BASE_URL } from '../../../constants/BaseUrls';
import { type ExpectationResultsByType, type InjectExpectationResultsByAttackPattern } from '../../../utils/api-types';
import MitreCoverageMatrix from '../common/matrix/MitreCoverageMatrix';
import { CONTEXTUAL_POSTURE_WIDGET_ID, contextualResultsUrl } from '../workspaces/custom_dashboards/results/contextualWidgets';
import SamplePreview from '../workspaces/custom_dashboards/widgets/viz/sample/SamplePreview';

const ACTIVE_STATUSES: AutonomousRunStatus[] = ['RUNNING', 'WAITING_INPUT'];
const POLL_INTERVAL_MS = 5000;

interface AutonomousOverviewProps { run: AutonomousRun }

/**
 * Overview tab of an autonomous (AI-driven) run: the mission (objective, status, scope), the run
 * posture gauges and MITRE kill-chain coverage of its single simulation (the same widgets the manual
 * overview surfaces, minus the multi-run trend - a one-shot has no history to trend), the capability
 * gaps the orchestrator hit, and the proof-of-exploitation case file it produced (with a
 * self-contained Markdown export). The live reasoning stream lives in the always-open right panel,
 * so this tab is the durable, exportable read of the run's outcome.
 */
const AutonomousOverview: FunctionComponent<AutonomousOverviewProps> = ({ run }) => {
  const theme = useTheme();
  const { t, nsdt } = useFormatter();
  const navigate = useNavigate();
  const location = useLocation();
  const accent = theme.palette.ai?.main ?? theme.palette.primary.main;
  const runId = run.autonomous_run_id;
  const status = run.autonomous_run_status;
  const simulationId = run.autonomous_run_simulation_id;

  const [events, setEvents] = useState<AutonomousEvent[]>([]);
  // An autonomous run owns exactly one simulation, so its posture and MITRE coverage are simply that
  // simulation's expectation results - the same widgets the manual overview surfaces, minus the
  // multi-run trend (a one-shot has no history to trend).
  const [postureResults, setPostureResults] = useState<ExpectationResultsByType[] | null>(null);
  const [injectResults, setInjectResults] = useState<InjectExpectationResultsByAttackPattern[] | null>(null);

  const reload = useCallback(() => fetchAutonomousTimeline(runId, 0)
    .then(res => setEvents(res.data ?? []))
    .catch(() => {}), [runId]);

  const reloadResults = useCallback(() => {
    if (!simulationId) {
      return Promise.resolve();
    }
    return Promise.all([
      fetchExerciseExpectationResult(simulationId)
        .then((res: { data: ExpectationResultsByType[] }) => setPostureResults(res.data))
        .catch(() => {}),
      fetchExerciseInjectExpectationResults(simulationId)
        .then((res: { data: InjectExpectationResultsByAttackPattern[] }) => setInjectResults(res.data))
        .catch(() => {}),
    ]);
  }, [simulationId]);

  useEffect(() => {
    reload();
    reloadResults();
  }, [reload, reloadResults]);

  const isActive = ACTIVE_STATUSES.includes(status);
  useEffect(() => {
    if (!isActive) {
      return undefined;
    }
    const interval = setInterval(() => {
      reload();
      reloadResults();
    }, POLL_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [isActive, reload, reloadResults]);

  const proofEvents = events.filter(e => e.autonomous_event_type === 'PROOF');
  const capabilityGaps = events.filter(e => e.autonomous_event_type === 'GAP');

  const attackPatternIds = injectResults
    ? R.uniq(
        injectResults
          .filter(injectResult => !!injectResult.inject_attack_pattern)
          .flatMap(injectResult => injectResult.inject_attack_pattern) as unknown as string[],
      )
    : [];
  const hasMitreResults = !!injectResults && attackPatternIds.length > 0;

  // Posture gauge clicks drill down to the expectations behind the ring, scoped to the run's single
  // simulation - same actionability as the manual simulation overview.
  const openPostureResults = useCallback((type: string) => {
    if (!simulationId) {
      return;
    }
    navigate(contextualResultsUrl(
      CONTEXTUAL_POSTURE_WIDGET_ID,
      'simulation',
      simulationId,
      `${location.pathname}${location.search}`,
      { inject_expectation_type: [type] },
    ));
  }, [navigate, location, simulationId]);

  const buildProofReport = (): string => {
    const lines: string[] = [];
    lines.push('# Autonomous attack path - proof of exploitation report');
    lines.push('');
    lines.push(`- Status: ${status}`);
    lines.push(`- Run id: ${run.autonomous_run_id}`);
    if (run.autonomous_run_created_at) {
      lines.push(`- Started: ${nsdt(run.autonomous_run_created_at)}`);
    }
    lines.push('');
    lines.push('## Objective');
    lines.push('');
    lines.push(run.autonomous_run_objective);
    lines.push('');
    lines.push(`## Evidence (${proofEvents.length})`);
    lines.push('');
    if (proofEvents.length === 0) {
      lines.push('_No proof-of-exploitation evidence was recorded._');
    } else {
      proofEvents.forEach((event, index) => {
        lines.push(`### ${index + 1}. ${event.autonomous_event_title ?? t('Proof')}`);
        if (event.autonomous_event_created_at) {
          lines.push(`_${nsdt(event.autonomous_event_created_at)}_`);
        }
        lines.push('');
        if (event.autonomous_event_content) {
          lines.push(event.autonomous_event_content);
          lines.push('');
        }
        if (event.autonomous_event_data) {
          lines.push('```json');
          lines.push(event.autonomous_event_data);
          lines.push('```');
          lines.push('');
        }
      });
    }
    if (capabilityGaps.length > 0) {
      lines.push(`## Capability gaps (${capabilityGaps.length})`);
      lines.push('');
      capabilityGaps.forEach((gap) => {
        lines.push(`- ${gap.autonomous_event_title ?? t('Capability gap')}`
          + (gap.autonomous_event_content ? `: ${gap.autonomous_event_content}` : ''));
      });
      lines.push('');
    }
    return lines.join('\n');
  };

  const handleExportReport = () => {
    const report = buildProofReport();
    const blob = new Blob([report], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `autonomous-attack-${run.autonomous_run_id}-proof-report.md`;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    URL.revokeObjectURL(url);
  };

  return (
    <Stack sx={{ gap: 2 }}>
      {/* Mission card. */}
      <Paper
        variant="outlined"
        sx={{
          padding: 2,
          borderColor: alpha(accent, 0.4),
          background: `linear-gradient(180deg, ${alpha(accent, 0.08)} 0%, ${alpha(accent, 0)} 100%)`,
        }}
      >
        <Stack sx={{
          flexDirection: 'row',
          alignItems: 'center',
          gap: 1,
          marginBottom: 1,
        }}
        >
          <AutoAwesome fontSize="small" sx={{ color: accent }} />
          {/* Run status lives in the hero (single control surface); not repeated here. */}
          <Typography variant="h6" sx={{ margin: 0 }}>{t('Mission')}</Typography>
          <Box sx={{ flex: 1 }} />
          {/* Stay in the scenario cockpit: open the scenario's own Attack path tab (its picker is
              scoped to this scenario's live simulation), not the standalone simulation page. */}
          {run.autonomous_run_scenario_id && (
            <Button
              component={Link}
              to={`${SCENARIO_BASE_URL}/${run.autonomous_run_scenario_id}/attack-path`}
              startIcon={<AccountTreeOutlined />}
              size="small"
              variant="outlined"
            >
              {t('Open live attack map')}
            </Button>
          )}
        </Stack>
        <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap' }}>
          {run.autonomous_run_objective}
        </Typography>
        {run.autonomous_run_last_error && (
          <Alert severity="error" icon={<ErrorOutline />} sx={{ marginTop: 1 }}>
            {run.autonomous_run_last_error}
          </Alert>
        )}
        <Stack sx={{
          flexDirection: 'row',
          flexWrap: 'wrap',
          gap: 3,
          marginTop: 2,
        }}
        >
          {run.autonomous_run_created_at && (
            <Box>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{t('Started')}</Typography>
              <Typography variant="body2">{nsdt(run.autonomous_run_created_at)}</Typography>
            </Box>
          )}
          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{t('Decisions')}</Typography>
            <Typography variant="body2">{events.length}</Typography>
          </Box>
          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{t('Proofs')}</Typography>
            <Typography variant="body2">{proofEvents.length}</Typography>
          </Box>
          <Box>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{t('Capability gaps')}</Typography>
            <Typography variant="body2">{capabilityGaps.length}</Typography>
          </Box>
        </Stack>
      </Paper>

      {/* Run posture: the single simulation's prevention / detection / vulnerability / human-response
          gauges. Never a blank box - falls back to an illustrative greyed sample while the run has
          not produced results yet (still warming up), like the manual overview. */}
      {simulationId && (
        <SectionBlock title={t('Run posture')}>
          {(() => {
            if (!postureResults) {
              return <Loader variant="inElement" />;
            }
            if (postureResults.length === 0) {
              return (
                <SamplePreview active variant="subtle">
                  <PostureGauges expectationResultsByTypes={SAMPLE_POSTURE} />
                </SamplePreview>
              );
            }
            return (
              <PostureGauges
                expectationResultsByTypes={postureResults}
                humanValidationLink={`/admin/simulations/${simulationId}/execution/validations`}
                onTypeClick={openPostureResults}
              />
            );
          })()}
        </SectionBlock>
      )}

      {/* Kill chain results: the MITRE ATT&CK coverage of the run's simulation. Only shown once the
          run has produced technique-level results (a one-shot has no planned matrix to preview). */}
      {simulationId && hasMitreResults && (
        <SectionBlock title={t('Kill chain results')}>
          <MitreCoverageMatrix
            widgetId={`autonomous-mitre-${simulationId}`}
            injectResults={injectResults}
            resultsContext={{
              source: 'simulation',
              contextId: simulationId,
            }}
          />
        </SectionBlock>
      )}

      {/* Capability gaps. */}
      {capabilityGaps.length > 0 && (
        <Paper
          variant="outlined"
          sx={{
            padding: 2,
            borderColor: theme.palette.warning.main,
          }}
        >
          <Stack sx={{
            flexDirection: 'row',
            alignItems: 'center',
            gap: 1,
            marginBottom: 1,
          }}
          >
            <WarningAmberOutlined color="warning" fontSize="small" />
            <Typography variant="subtitle2" sx={{ margin: 0 }}>{t('Capability gaps')}</Typography>
          </Stack>
          <Stack sx={{
            flexDirection: 'row',
            flexWrap: 'wrap',
            gap: 1,
          }}
          >
            {capabilityGaps.map(gap => (
              <Chip
                key={gap.autonomous_event_id}
                label={gap.autonomous_event_title ?? t('Capability gap')}
                color="warning"
                variant="outlined"
                size="small"
                sx={{ borderRadius: 1 }}
              />
            ))}
          </Stack>
        </Paper>
      )}

      {/* Proof of exploitation. */}
      {proofEvents.length > 0 && (
        <Paper
          variant="outlined"
          sx={{
            padding: 2,
            borderColor: theme.palette.success.main,
          }}
        >
          <Stack sx={{
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 1,
            marginBottom: 1,
          }}
          >
            <Stack sx={{
              flexDirection: 'row',
              alignItems: 'center',
              gap: 1,
            }}
            >
              <VerifiedOutlined color="success" fontSize="small" />
              <Typography variant="subtitle2" sx={{ margin: 0 }}>{t('Proof of exploitation')}</Typography>
              <Chip size="small" label={proofEvents.length} color="success" variant="outlined" sx={{ borderRadius: 1 }} />
            </Stack>
            <Button onClick={handleExportReport} startIcon={<DownloadOutlined />} size="small" variant="outlined">
              {t('Export report')}
            </Button>
          </Stack>
          <Stack sx={{ gap: 1 }}>
            {proofEvents.map(proof => (
              <Box
                key={proof.autonomous_event_id}
                sx={{
                  paddingInlineStart: 1.5,
                  borderInlineStart: `2px solid ${theme.palette.success.main}`,
                }}
              >
                <Stack sx={{
                  flexDirection: 'row',
                  justifyContent: 'space-between',
                  gap: 1,
                }}
                >
                  <Typography variant="subtitle2" sx={{ margin: 0 }}>
                    {proof.autonomous_event_title ?? t('Proof')}
                  </Typography>
                  {proof.autonomous_event_created_at && (
                    <Typography variant="caption" color="text.secondary">
                      {nsdt(proof.autonomous_event_created_at)}
                    </Typography>
                  )}
                </Stack>
                {proof.autonomous_event_content && (
                  <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-wrap' }}>
                    {proof.autonomous_event_content}
                  </Typography>
                )}
              </Box>
            ))}
          </Stack>
        </Paper>
      )}
    </Stack>
  );
};

export default AutonomousOverview;
