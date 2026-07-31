import { AutoAwesome, BoltOutlined, DownloadOutlined, ErrorOutline, ScheduleOutlined, VerifiedOutlined, WarningAmberOutlined } from '@mui/icons-material';
import { Alert, Box, Button, Chip, Paper, Stack, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent, useCallback, useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';

import { fetchAutonomousTimeline } from '../../../actions/autonomous/autonomous-actions';
import { type AutonomousEvent, type AutonomousRun, type AutonomousRunStatus } from '../../../actions/autonomous/autonomous-types';
import { fetchExerciseExpectationResult, fetchExerciseInjectExpectationResults } from '../../../actions/exercises/exercise-action';
import { HeroStat, HeroStats, SectionBlock } from '../../../components/common/detail/EntityDetailCommon';
import PostureGauges from '../../../components/common/detail/PostureGauges';
import SAMPLE_POSTURE from '../../../components/common/detail/samplePosture';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
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

  // Illustrative fallbacks so the outcome lists are never a blank box while the run is still warming
  // up (mirrors the greyed sample the posture gauges show). Rendered greyed + "Sample"-tagged via
  // SamplePreview and replaced the instant the orchestrator records a real gap / proof.
  const gapsAreSample = capabilityGaps.length === 0;
  const gapItems = gapsAreSample
    ? [
        {
          id: 'sample-gap-0',
          title: t('Kerberoasting payload not in arsenal'),
          description: t('No installed injector can request and crack service tickets - add a Kerberoasting connector to close it.'),
        },
        {
          id: 'sample-gap-1',
          title: t('SMB relay capability unavailable'),
          description: t('Lateral movement via NTLM relay needs a relay injector that is not deployed in this environment.'),
        },
        {
          id: 'sample-gap-2',
          title: t('Cloud IAM enumeration collector missing'),
          description: t('Enumerating cloud identities requires a cloud IAM collector - install one to map the cloud attack surface.'),
        },
      ]
    : capabilityGaps.map(gap => ({
        id: gap.autonomous_event_id,
        title: gap.autonomous_event_title ?? t('Capability gap'),
        description: gap.autonomous_event_content ?? undefined,
      }));

  const proofsAreSample = proofEvents.length === 0;
  const proofItems = proofsAreSample
    ? [
        {
          id: 'sample-proof-0',
          title: t('Remote code execution on a web server'),
          content: t('Chained an unauthenticated file upload into a shell on the target host.'),
          createdAt: undefined as string | undefined,
        },
        {
          id: 'sample-proof-1',
          title: t('Cloud credential theft via SSRF'),
          content: t('Abused a server-side request forgery to read the metadata endpoint and exfiltrate cloud keys.'),
          createdAt: undefined as string | undefined,
        },
      ]
    : proofEvents.map(proof => ({
        id: proof.autonomous_event_id,
        title: proof.autonomous_event_title ?? t('Proof'),
        content: proof.autonomous_event_content,
        createdAt: proof.autonomous_event_created_at,
      }));

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
          {/* Run status lives in the hero (single control surface); not repeated here. The live
              attack map is one click away in this view's own "Attack path" tab, so no button is
              duplicated at the top of the mission card. */}
          <Typography variant="h6" sx={{ margin: 0 }}>{t('Mission')}</Typography>
        </Stack>
        <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap' }}>
          {run.autonomous_run_objective}
        </Typography>
        {run.autonomous_run_last_error && (
          <Alert severity="error" icon={<ErrorOutline />} sx={{ marginTop: 1 }}>
            {run.autonomous_run_last_error}
          </Alert>
        )}
        <Box sx={{ marginTop: 2 }}>
          <HeroStats>
            {run.autonomous_run_created_at && (
              <HeroStat
                icon={ScheduleOutlined}
                label={t('Started')}
                value={nsdt(run.autonomous_run_created_at)}
              />
            )}
            <HeroStat
              icon={BoltOutlined}
              label={t('Decisions')}
              value={events.length}
              color={accent}
            />
            <HeroStat
              icon={VerifiedOutlined}
              label={t('Proofs')}
              value={proofEvents.length}
              color={theme.palette.success.main}
            />
            <HeroStat
              icon={WarningAmberOutlined}
              label={t('Capability gaps')}
              value={capabilityGaps.length}
              color={theme.palette.warning.main}
            />
          </HeroStats>
        </Box>
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

      {/* Capability gaps: the arsenal shortfalls the orchestrator hit. Wrapped in the shared
          SectionBlock so it reads like the Run posture / Kill chain papers. Always shown - a greyed
          illustrative sample stands in until the run records a real gap, so the outcome read is
          never a blank box. */}
      <SectionBlock
        title={t('Capability gaps')}
        action={gapsAreSample
          ? null
          : (
              <Chip size="small" label={capabilityGaps.length} color="warning" variant="outlined" sx={{ borderRadius: 1 }} />
            )}
      >
        <SamplePreview active={gapsAreSample} variant="subtle">
          <Stack sx={{ gap: 1 }}>
            {gapItems.map(gap => (
              <Stack
                key={gap.id}
                sx={{
                  flexDirection: 'row',
                  alignItems: 'flex-start',
                  gap: 1.25,
                  padding: 1.25,
                  borderRadius: 1,
                  border: `1px solid ${alpha(theme.palette.warning.main, 0.35)}`,
                  backgroundColor: alpha(theme.palette.warning.main, 0.06),
                }}
              >
                <WarningAmberOutlined color="warning" fontSize="small" sx={{ marginTop: '2px' }} />
                <Box sx={{ minWidth: 0 }}>
                  <Typography variant="subtitle2" sx={{ margin: 0 }}>
                    {gap.title}
                  </Typography>
                  {gap.description && (
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{
                        display: 'block',
                        whiteSpace: 'pre-wrap',
                      }}
                    >
                      {gap.description}
                    </Typography>
                  )}
                </Box>
              </Stack>
            ))}
          </Stack>
        </SamplePreview>
      </SectionBlock>

      {/* Proof of exploitation: the case file the run produced. Wrapped in the shared SectionBlock,
          with the Markdown export as its header action (only once there is real evidence). Always
          shown - a greyed illustrative sample stands in until the run records a real proof, so the
          operator can preview what the evidence card will look like. */}
      <SectionBlock
        title={t('Proof of exploitation')}
        action={proofsAreSample
          ? null
          : (
              <Stack sx={{
                flexDirection: 'row',
                alignItems: 'center',
                gap: 1,
              }}
              >
                <Chip size="small" label={proofEvents.length} color="success" variant="outlined" sx={{ borderRadius: 1 }} />
                <Button onClick={handleExportReport} startIcon={<DownloadOutlined />} size="small" variant="outlined">
                  {t('Export report')}
                </Button>
              </Stack>
            )}
      >
        <SamplePreview active={proofsAreSample} variant="subtle">
          <Stack sx={{ gap: 1 }}>
            {proofItems.map((proof, index) => (
              <Stack
                key={proof.id}
                sx={{
                  flexDirection: 'row',
                  alignItems: 'flex-start',
                  gap: 1.25,
                  padding: 1.25,
                  borderRadius: 1,
                  border: `1px solid ${alpha(theme.palette.success.main, 0.35)}`,
                  backgroundColor: alpha(theme.palette.success.main, 0.06),
                }}
              >
                <Box
                  sx={{
                    flex: '0 0 auto',
                    width: 24,
                    height: 24,
                    borderRadius: '50%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: 12,
                    fontWeight: 700,
                    color: theme.palette.success.main,
                    backgroundColor: alpha(theme.palette.success.main, 0.15),
                  }}
                >
                  {index + 1}
                </Box>
                <Box sx={{
                  minWidth: 0,
                  flex: 1,
                }}
                >
                  <Stack sx={{
                    flexDirection: 'row',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    gap: 1,
                  }}
                  >
                    <Stack sx={{
                      flexDirection: 'row',
                      alignItems: 'center',
                      gap: 0.75,
                      minWidth: 0,
                    }}
                    >
                      <VerifiedOutlined color="success" fontSize="small" />
                      <Typography variant="subtitle2" sx={{ margin: 0 }}>
                        {proof.title}
                      </Typography>
                    </Stack>
                    {proof.createdAt && (
                      <Typography variant="caption" color="text.secondary" sx={{ flex: '0 0 auto' }}>
                        {nsdt(proof.createdAt)}
                      </Typography>
                    )}
                  </Stack>
                  {proof.content && (
                    <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-wrap' }}>
                      {proof.content}
                    </Typography>
                  )}
                </Box>
              </Stack>
            ))}
          </Stack>
        </SamplePreview>
      </SectionBlock>
    </Stack>
  );
};

export default AutonomousOverview;
