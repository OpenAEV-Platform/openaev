import { AccountTreeOutlined, AutoAwesome, BoltOutlined, CancelOutlined, CheckCircleOutline, DownloadOutlined, ErrorOutline, ExtensionOutlined, HelpOutline, PauseCircleOutline, PlayCircleOutline, SendOutlined, VerifiedOutlined, WarningAmberOutlined } from '@mui/icons-material';
import { Alert, Box, Button, Chip, CircularProgress, Divider, IconButton, Paper, Stack, TextField, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router';

import {
  addAutonomousDirective,
  cancelAutonomousRun,
  fetchAutonomousRun,
  fetchAutonomousTimeline,
  pauseAutonomousRun,
  resumeAutonomousRun,
} from '../../../actions/autonomous/autonomous-actions';
import {
  type AutonomousEvent,
  type AutonomousEventType,
  type AutonomousRun as AutonomousRunModel,
  type AutonomousRunStatus,
} from '../../../actions/autonomous/autonomous-types';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import { SIMULATION_BASE_URL } from '../../../constants/BaseUrls';

const ACTIVE_STATUSES: AutonomousRunStatus[] = ['RUNNING', 'WAITING_INPUT'];
const POLL_INTERVAL_MS = 3000;

const statusColor = (status: AutonomousRunStatus): 'default' | 'info' | 'warning' | 'success' | 'error' => {
  switch (status) {
    case 'RUNNING':
      return 'info';
    case 'WAITING_INPUT':
    case 'PAUSED':
      return 'warning';
    case 'COMPLETED':
      return 'success';
    case 'FAILED':
    case 'CANCELED':
      return 'error';
    default:
      return 'default';
  }
};

const eventIcon = (type: AutonomousEventType): ReactNode => {
  switch (type) {
    case 'DECISION':
      return <BoltOutlined fontSize="small" color="primary" />;
    case 'TOOL_ACTION':
      return <ExtensionOutlined fontSize="small" color="info" />;
    case 'GAP':
      return <WarningAmberOutlined fontSize="small" color="warning" />;
    case 'QUESTION':
      return <HelpOutline fontSize="small" color="warning" />;
    case 'PROOF':
      return <CheckCircleOutline fontSize="small" color="success" />;
    case 'DIRECTIVE':
      return <SendOutlined fontSize="small" color="secondary" />;
    case 'HANDOVER':
      return <AccountTreeOutlined fontSize="small" color="info" />;
    case 'NARRATION':
      return <AutoAwesome fontSize="small" color="primary" />;
    default:
      return <AccountTreeOutlined fontSize="small" color="disabled" />;
  }
};

/**
 * Live view of an autonomous (AI-driven) attack-path run: the AI decision timeline, real-time
 * steering bar, capability-gap strip, and lifecycle controls. The timeline is polled with a sequence
 * cursor while the run is active; the animated attack map itself lives on the run's simulation
 * attack-path view (linked here) so we reuse the existing live graph rather than re-embedding it.
 */
const AutonomousRun: FunctionComponent = () => {
  const { runId } = useParams<{ runId: string }>();
  const { t, nsdt } = useFormatter();
  const theme = useTheme();

  const [run, setRun] = useState<AutonomousRunModel | null>(null);
  const [events, setEvents] = useState<AutonomousEvent[]>([]);
  const [directive, setDirective] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const cursorRef = useRef(0);

  const refreshRun = useCallback(() => {
    if (!runId) {
      return Promise.resolve();
    }
    return fetchAutonomousRun(runId)
      .then(res => setRun(res.data))
      .catch(() => {});
  }, [runId]);

  const pollTimeline = useCallback(() => {
    if (!runId) {
      return Promise.resolve();
    }
    return fetchAutonomousTimeline(runId, cursorRef.current)
      .then((res) => {
        const incoming = res.data ?? [];
        if (incoming.length > 0) {
          cursorRef.current = Math.max(
            cursorRef.current,
            ...incoming.map(e => e.autonomous_event_sequence),
          );
          setEvents(prev => [...prev, ...incoming]);
        }
      })
      .catch(() => {});
  }, [runId]);

  useEffect(() => {
    setLoading(true);
    Promise.all([refreshRun(), pollTimeline()]).finally(() => setLoading(false));
  }, [refreshRun, pollTimeline]);

  // Poll while the run is active. Status-driven so a completed/failed run stops hammering the API.
  useEffect(() => {
    if (!run || !ACTIVE_STATUSES.includes(run.autonomous_run_status)) {
      return undefined;
    }
    const interval = setInterval(() => {
      refreshRun();
      pollTimeline();
    }, POLL_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [run, refreshRun, pollTimeline]);

  const withBusy = (action: Promise<unknown>) => {
    setBusy(true);
    action.then(() => refreshRun()).finally(() => setBusy(false));
  };

  const handleSendDirective = () => {
    if (!runId || directive.trim().length === 0) {
      return;
    }
    const content = directive.trim();
    setDirective('');
    addAutonomousDirective(runId, content)
      .then(() => pollTimeline())
      .catch(() => {});
  };

  if (loading) {
    return <Loader />;
  }

  if (!run) {
    return <Alert severity="error">{t('Autonomous run not found')}</Alert>;
  }

  const status = run.autonomous_run_status;
  const isActive = ACTIVE_STATUSES.includes(status);
  const capabilityGaps = events.filter(e => e.autonomous_event_type === 'GAP');
  // Proof-of-exploitation case file: every PROOF event is a piece of evidence that an objective
  // (or a key milestone) was achieved. Aggregated here into an operator-facing report.
  const proofEvents = events.filter(e => e.autonomous_event_type === 'PROOF');

  // Assemble a self-contained Markdown proof-of-exploitation report from the run and its timeline.
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
  // When the orchestrator parks in WAITING_INPUT it raises a QUESTION event; surface the latest
  // one prominently so the operator knows exactly what to answer (e.g. which assets are in scope).
  const isWaitingInput = status === 'WAITING_INPUT';
  const pendingQuestion = isWaitingInput
    ? [...events].reverse().find(e => e.autonomous_event_type === 'QUESTION')
    : undefined;

  return (
    <>
      <Breadcrumbs
        variant="object"
        elements={[
          {
            label: t('Scenarios'),
            link: '/admin/scenarios',
          },
          {
            label: t('Autonomous attack'),
            current: true,
          },
        ]}
      />

      <Paper
        variant="outlined"
        sx={{
          padding: theme.spacing(2),
          marginBottom: theme.spacing(2),
        }}
      >
        <Stack sx={{
          flexDirection: 'row',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          gap: theme.spacing(2),
        }}
        >
          <Box sx={{ flex: 1 }}>
            <Stack sx={{
              flexDirection: 'row',
              alignItems: 'center',
              gap: theme.spacing(1),
              marginBottom: theme.spacing(1),
            }}
            >
              <AutoAwesome color="primary" />
              <Typography variant="h6" sx={{ margin: 0 }}>{t('Autonomous attack path')}</Typography>
              <Chip size="small" label={t(status)} color={statusColor(status)} variant="outlined" />
            </Stack>
            <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-wrap' }}>
              {run.autonomous_run_objective}
            </Typography>
            {run.autonomous_run_last_error && (
              <Alert severity="error" icon={<ErrorOutline />} sx={{ marginTop: theme.spacing(1) }}>
                {run.autonomous_run_last_error}
              </Alert>
            )}
          </Box>
          <Stack sx={{
            flexDirection: 'row',
            gap: theme.spacing(1),
            alignItems: 'center',
          }}
          >
            {run.autonomous_run_simulation_id && (
              <Button
                component={Link}
                to={`${SIMULATION_BASE_URL}/${run.autonomous_run_simulation_id}/attack-path`}
                startIcon={<AccountTreeOutlined />}
                size="small"
                variant="outlined"
              >
                {t('Open live attack map')}
              </Button>
            )}
            {status === 'RUNNING' && (
              <Tooltip title={t('Pause')}>
                <span>
                  <IconButton onClick={() => withBusy(pauseAutonomousRun(runId!))} disabled={busy}>
                    <PauseCircleOutline />
                  </IconButton>
                </span>
              </Tooltip>
            )}
            {status === 'PAUSED' && (
              <Tooltip title={t('Resume')}>
                <span>
                  <IconButton onClick={() => withBusy(resumeAutonomousRun(runId!))} disabled={busy} color="primary">
                    <PlayCircleOutline />
                  </IconButton>
                </span>
              </Tooltip>
            )}
            {isActive || status === 'PAUSED'
              ? (
                  <Tooltip title={t('Cancel')}>
                    <span>
                      <IconButton onClick={() => withBusy(cancelAutonomousRun(runId!))} disabled={busy} color="error">
                        <CancelOutlined />
                      </IconButton>
                    </span>
                  </Tooltip>
                )
              : null}
          </Stack>
        </Stack>
      </Paper>

      {capabilityGaps.length > 0 && (
        <Paper
          variant="outlined"
          sx={{
            padding: theme.spacing(2),
            marginBottom: theme.spacing(2),
            borderColor: theme.palette.warning.main,
          }}
        >
          <Stack sx={{
            flexDirection: 'row',
            alignItems: 'center',
            gap: theme.spacing(1),
            marginBottom: theme.spacing(1),
          }}
          >
            <WarningAmberOutlined color="warning" fontSize="small" />
            <Typography variant="subtitle2" sx={{ margin: 0 }}>{t('Capability gaps')}</Typography>
          </Stack>
          <Stack sx={{
            flexDirection: 'row',
            flexWrap: 'wrap',
            gap: theme.spacing(1),
          }}
          >
            {capabilityGaps.map(gap => (
              <Chip
                key={gap.autonomous_event_id}
                label={gap.autonomous_event_title ?? t('Capability gap')}
                color="warning"
                variant="outlined"
                size="small"
              />
            ))}
          </Stack>
        </Paper>
      )}

      {/* Proof-of-exploitation case file: the run's deliverable. */}
      {proofEvents.length > 0 && (
        <Paper
          variant="outlined"
          sx={{
            padding: theme.spacing(2),
            marginBottom: theme.spacing(2),
            borderColor: theme.palette.success.main,
          }}
        >
          <Stack sx={{
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: theme.spacing(1),
            marginBottom: theme.spacing(1),
          }}
          >
            <Stack sx={{
              flexDirection: 'row',
              alignItems: 'center',
              gap: theme.spacing(1),
            }}
            >
              <VerifiedOutlined color="success" fontSize="small" />
              <Typography variant="subtitle2" sx={{ margin: 0 }}>
                {t('Proof of exploitation')}
              </Typography>
              <Chip size="small" label={proofEvents.length} color="success" variant="outlined" />
            </Stack>
            <Button
              onClick={handleExportReport}
              startIcon={<DownloadOutlined />}
              size="small"
              variant="outlined"
            >
              {t('Export report')}
            </Button>
          </Stack>
          <Stack sx={{ gap: theme.spacing(1) }}>
            {proofEvents.map(proof => (
              <Box
                key={proof.autonomous_event_id}
                sx={{
                  paddingInlineStart: theme.spacing(1.5),
                  borderInlineStart: `2px solid ${theme.palette.success.main}`,
                }}
              >
                <Stack sx={{
                  flexDirection: 'row',
                  justifyContent: 'space-between',
                  gap: theme.spacing(1),
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
                  <Typography
                    variant="body2"
                    color="text.secondary"
                    sx={{ whiteSpace: 'pre-wrap' }}
                  >
                    {proof.autonomous_event_content}
                  </Typography>
                )}
              </Box>
            ))}
          </Stack>
        </Paper>
      )}

      {/* The AI is blocked and needs the operator (e.g. which assets are in scope). */}
      {isWaitingInput && (
        <Alert
          severity="warning"
          icon={<HelpOutline />}
          sx={{ marginBottom: theme.spacing(2) }}
        >
          <Typography variant="subtitle2" sx={{ margin: 0 }}>
            {pendingQuestion?.autonomous_event_title ?? t('The AI needs your input to continue')}
          </Typography>
          {pendingQuestion?.autonomous_event_content && (
            <Typography
              variant="body2"
              sx={{
                whiteSpace: 'pre-wrap',
                marginTop: theme.spacing(0.5),
              }}
            >
              {pendingQuestion.autonomous_event_content}
            </Typography>
          )}
          <Typography
            variant="caption"
            color="text.secondary"
            sx={{
              display: 'block',
              marginTop: theme.spacing(0.5),
            }}
          >
            {t('Answer below - your reply is sent to the orchestrator, which resumes and adapts the attack path.')}
          </Typography>
        </Alert>
      )}

      {/* Steering bar: inject a directive into the live run without stopping it. Doubles as the
          answer box when the run is waiting on the operator. */}
      <Paper
        variant="outlined"
        sx={{
          padding: theme.spacing(1.5),
          marginBottom: theme.spacing(2),
        }}
      >
        <Stack sx={{
          flexDirection: 'row',
          gap: theme.spacing(1),
          alignItems: 'center',
        }}
        >
          <TextField
            value={directive}
            onChange={event => setDirective(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                handleSendDirective();
              }
            }}
            placeholder={isWaitingInput
              ? t('Answer the question above (e.g. the web apps in scope are app-prod-01 and app-prod-02)')
              : t('Steer the AI in real time (e.g. focus on the finance subnet, avoid host X, try Kerberoasting)')}
            size="small"
            fullWidth
            disabled={!isActive}
          />
          <Button
            onClick={handleSendDirective}
            variant="contained"
            startIcon={<SendOutlined />}
            disabled={!isActive || directive.trim().length === 0}
          >
            {isWaitingInput ? t('Answer') : t('Steer')}
          </Button>
        </Stack>
        {!isActive && (
          <Typography variant="caption" color="text.secondary">
            {t('Steering is available while the run is active.')}
          </Typography>
        )}
      </Paper>

      {/* AI decision timeline. */}
      <Paper variant="outlined" sx={{ padding: theme.spacing(2) }}>
        <Stack sx={{
          flexDirection: 'row',
          alignItems: 'center',
          gap: theme.spacing(1),
          marginBottom: theme.spacing(1),
        }}
        >
          <Typography variant="subtitle2" sx={{ margin: 0 }}>{t('AI decision timeline')}</Typography>
          {isActive && <CircularProgress size={14} />}
        </Stack>
        {events.length === 0
          ? (
              <Typography variant="body2" color="text.secondary">
                {t('No decisions yet. The orchestrator is warming up.')}
              </Typography>
            )
          : (
              <Stack sx={{ gap: 0 }}>
                {events.map((event, index) => (
                  <Box key={event.autonomous_event_id}>
                    <Stack sx={{
                      flexDirection: 'row',
                      gap: theme.spacing(1.5),
                      paddingBlock: theme.spacing(1),
                    }}
                    >
                      <Box sx={{ marginTop: '2px' }}>{eventIcon(event.autonomous_event_type)}</Box>
                      <Box sx={{ flex: 1 }}>
                        <Stack sx={{
                          flexDirection: 'row',
                          justifyContent: 'space-between',
                          gap: theme.spacing(1),
                        }}
                        >
                          <Typography variant="subtitle2" sx={{ margin: 0 }}>
                            {event.autonomous_event_title ?? t(event.autonomous_event_type)}
                          </Typography>
                          {event.autonomous_event_created_at && (
                            <Typography variant="caption" color="text.secondary">
                              {nsdt(event.autonomous_event_created_at)}
                            </Typography>
                          )}
                        </Stack>
                        {event.autonomous_event_content && (
                          <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-wrap' }}>
                            {event.autonomous_event_content}
                          </Typography>
                        )}
                      </Box>
                    </Stack>
                    {index < events.length - 1 && <Divider />}
                  </Box>
                ))}
              </Stack>
            )}
      </Paper>
    </>
  );
};

export default AutonomousRun;
