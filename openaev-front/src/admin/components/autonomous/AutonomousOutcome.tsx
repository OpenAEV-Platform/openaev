import { AutoAwesome, BoltOutlined, DownloadOutlined, ErrorOutline, VerifiedOutlined, WarningAmberOutlined } from '@mui/icons-material';
import { Alert, Box, Button, Chip, Divider, Paper, Stack, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import * as R from 'ramda';
import { type FunctionComponent, type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { fetchAutonomousTimeline } from '../../../actions/autonomous/autonomous-actions';
import { type AutonomousEvent, type AutonomousRun, type AutonomousRunStatus } from '../../../actions/autonomous/autonomous-types';
import { HeroStat, HeroStats, SectionBlock } from '../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../components/i18n';
import GraphCardTooltip from '../chaining/logic/logic-graph/GraphCardTooltip';
import SamplePreview from '../workspaces/custom_dashboards/widgets/viz/sample/SamplePreview';
import { eventAccent, eventIcon, EventMarkdown, eventTypeLabel, isHeartbeatEvent, isLiveActivityEvent, sanitizeEventText, stripMarkdown } from './autonomousEventVisuals';
import AutonomousOutcomeDialog, { type OutcomeKind } from './AutonomousOutcomeDialog';

const ACTIVE_STATUSES: AutonomousRunStatus[] = ['PLANNING', 'RUNNING', 'WAITING_INPUT'];
const POLL_INTERVAL_MS = 5000;

// The orchestrator writes free prose in event titles/content; MITRE technique ids (T1190,
// T1505.003) and CVE ids (CVE-2021-26855) are the two structured primitives worth surfacing as
// tags, so we pull them back out with a regex rather than asking the model for structured JSON.
const TECHNIQUE_RE = /\bT\d{4}(?:\.\d{3})?\b/g;
const CVE_RE = /\bCVE-\d{4}-\d{4,7}\b/gi;

const extractTags = (...texts: (string | null | undefined)[]): {
  techniques: string[];
  cves: string[];
} => {
  const joined = texts.filter(Boolean).join(' ');
  const techniques = R.uniq(joined.match(TECHNIQUE_RE) ?? []);
  const cves = R.uniq((joined.match(CVE_RE) ?? []).map(value => value.toUpperCase()));
  return {
    techniques,
    cves,
  };
};

// Clamp long prose to a few lines so the outcome cards stay dense and scannable (metadata-forward)
// instead of turning into walls of text.
const clampSx = (lines: number) => ({
  display: '-webkit-box',
  WebkitLineClamp: lines,
  WebkitBoxOrient: 'vertical' as const,
  overflow: 'hidden',
  whiteSpace: 'pre-wrap' as const,
  wordBreak: 'break-word' as const,
});

interface GapItem {
  id: string;
  title: string;
  description?: string;
  createdAt?: string;
  sequence?: number;
  techniques: string[];
  cves: string[];
  // The backing event for real (non-sample) entries, so the card can open the
  // full-detail drill-down dialog. Undefined for illustrative sample cards.
  event?: AutonomousEvent;
}

interface ProofItem {
  id: string;
  title: string;
  content?: string;
  createdAt?: string;
  sequence?: number;
  techniques: string[];
  cves: string[];
  event?: AutonomousEvent;
}

// A single tinted count badge chip (MITRE technique / CVE) - dense, uppercase, design-system radius.
const MetaChip: FunctionComponent<{
  label: string;
  color: string;
}> = ({ label, color }) => (
  <Chip
    label={label}
    size="small"
    sx={{
      height: 18,
      fontSize: 10,
      fontWeight: 600,
      letterSpacing: '0.03em',
      borderRadius: 0.5,
      color,
      backgroundColor: alpha(color, 0.12),
    }}
  />
);

// Shared outcome card for a capability gap / proof entry: tone-tinted frame, leading icon (or a
// numbered badge for proofs), title + date on one line, tags row, and a clamped body. Keeps both
// columns visually identical and metadata-forward.
const OutcomeCard: FunctionComponent<{
  tone: string;
  icon: ReactNode;
  title: string;
  body?: string;
  createdAtLabel?: string;
  tags?: ReactNode;
  badge?: number;
  onClick?: () => void;
}> = ({ tone, icon, title, body, createdAtLabel, tags, badge, onClick }) => (
  <Stack
    onClick={onClick}
    role={onClick ? 'button' : undefined}
    tabIndex={onClick ? 0 : undefined}
    onKeyDown={onClick
      ? (keyEvent) => {
          if (keyEvent.key === 'Enter' || keyEvent.key === ' ') {
            keyEvent.preventDefault();
            onClick();
          }
        }
      : undefined}
    sx={{
      'flexDirection': 'row',
      'alignItems': 'flex-start',
      'gap': 1.25,
      'padding': 1.25,
      'borderRadius': 1,
      'border': `1px solid ${alpha(tone, 0.35)}`,
      'backgroundColor': alpha(tone, 0.06),
      'cursor': onClick ? 'pointer' : 'default',
      'transition': 'background-color 120ms, border-color 120ms',
      '&:hover': onClick
        ? {
            backgroundColor: alpha(tone, 0.12),
            borderColor: alpha(tone, 0.6),
          }
        : undefined,
    }}
  >
    {badge !== undefined
      ? (
          <Box sx={{
            flex: '0 0 auto',
            width: 24,
            height: 24,
            borderRadius: '50%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 12,
            fontWeight: 700,
            color: tone,
            backgroundColor: alpha(tone, 0.15),
          }}
          >
            {badge}
          </Box>
        )
      : (
          <Box sx={{
            display: 'inline-flex',
            marginTop: '2px',
          }}
          >
            {icon}
          </Box>
        )}
    <Box sx={{
      minWidth: 0,
      flex: 1,
    }}
    >
      <Stack sx={{
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'flex-start',
        gap: 1,
      }}
      >
        <Typography
          sx={{
            margin: 0,
            fontSize: 13,
            fontWeight: 600,
            lineHeight: 1.35,
            ...clampSx(2),
          }}
        >
          {title}
        </Typography>
        {createdAtLabel && (
          <Typography
            variant="caption"
            color="text.secondary"
            sx={{
              flex: '0 0 auto',
              whiteSpace: 'nowrap',
            }}
          >
            {createdAtLabel}
          </Typography>
        )}
      </Stack>
      {tags && <Box sx={{ marginTop: 0.5 }}>{tags}</Box>}
      {body && (
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{
            marginTop: 0.5,
            ...clampSx(3),
          }}
        >
          {stripMarkdown(body)}
        </Typography>
      )}
    </Box>
  </Stack>
);

interface AutonomousOutcomeProps {
  run: AutonomousRun;
  // Live cockpit polls the timeline while the run is active; the durable read on a settled scenario
  // fetches once and never polls. Defaults to live. Ignored when `sharedEvents` is provided.
  live?: boolean;
  // When the parent already owns a live timeline (the reasoning panel), reuse it instead of
  // starting a second full-from-cursor-0 poll of the same endpoint.
  sharedEvents?: AutonomousEvent[];
}

/**
 * The durable, exportable read of an autonomous run's outcome: the mission (objective, status hero
 * stats), a compact horizontal "decision timeline" of the orchestrator's events, and the capability
 * gaps + proof-of-exploitation case file it produced side by side. This is the half of the old
 * AutonomousOverview that survives a settled run - the scenario page embeds it read-only once the
 * run is done, and the simulation cockpit renders it live above the run posture / MITRE coverage.
 *
 * Plan mode is fundamentally non-executing (no injects run, so no findings and no proof can exist):
 * the proofs column, the "Proofs" hero stat and the sample PROOF timeline node are all dropped, and
 * the gaps column takes the full width.
 */
const AutonomousOutcome: FunctionComponent<AutonomousOutcomeProps> = ({ run, live = true, sharedEvents }) => {
  const theme = useTheme();
  const { t, nsdt, vnsdt } = useFormatter();
  const accent = theme.palette.ai?.main ?? theme.palette.primary.main;
  // When the scenario's logic was AI-built but not yet run, the mission card itself carries the
  // "planned" signal (orange, like the OCTI draft): the mission is the natural anchor, so we tint
  // it instead of stacking a separate banner.
  const isPlanMode = run.autonomous_run_plan_mode;
  const missionAccent = isPlanMode ? theme.palette.warning.main : accent;
  const runId = run.autonomous_run_id;
  const status = run.autonomous_run_status;
  const simulationId = run.autonomous_run_simulation_id;

  const [events, setEvents] = useState<AutonomousEvent[]>(sharedEvents ?? []);
  // The gap / proof card the operator drilled into, with the tags already
  // extracted so the dialog does not re-parse them.
  const [selectedOutcome, setSelectedOutcome] = useState<{
    kind: OutcomeKind;
    event: AutonomousEvent;
    techniques: string[];
    cves: string[];
  } | null>(null);
  // Scroll container of the horizontal decision timeline. New events append on
  // the right; we tail-follow them unless the operator has scrolled back to
  // inspect earlier decisions (see the scroll handler + effect below).
  const timelineScrollRef = useRef<HTMLDivElement | null>(null);
  const timelinePinnedRef = useRef(true);
  // Bumped on every timeline scroll and chained into the tooltip dismiss key: the timeline
  // auto-scrolls under the live poll (tail-follow), which slides a node out from under a stationary
  // cursor with no mouseleave - the exact stuck-tooltip trap the graph cards already guard against.
  const [timelineScrollNonce, setTimelineScrollNonce] = useState(0);

  const usesSharedTimeline = sharedEvents !== undefined;
  useEffect(() => {
    if (sharedEvents !== undefined) {
      setEvents(sharedEvents);
    }
  }, [sharedEvents]);

  const reload = useCallback(() => fetchAutonomousTimeline(runId, 0)
    .then(res => setEvents(res.data ?? []))
    .catch(() => {}), [runId]);

  useEffect(() => {
    if (!usesSharedTimeline) {
      reload();
    }
  }, [reload, usesSharedTimeline]);

  const isActive = ACTIVE_STATUSES.includes(status);
  useEffect(() => {
    if (usesSharedTimeline || !live || !isActive) {
      return undefined;
    }
    const interval = setInterval(() => {
      reload();
    }, POLL_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [live, isActive, reload, usesSharedTimeline]);

  // Keep timelinePinnedRef in sync with how close the operator is to the right
  // edge, so we only tail-follow when they are already watching the latest.
  const handleTimelineScroll = useCallback(() => {
    const node = timelineScrollRef.current;
    if (!node) {
      return;
    }
    const distanceFromRight = node.scrollWidth - node.clientWidth - node.scrollLeft;
    timelinePinnedRef.current = distanceFromRight < 48;
    // Dismiss any open node tooltip: the card just moved under the cursor.
    setTimelineScrollNonce(nonce => nonce + 1);
  }, []);

  const proofEvents = useMemo(() => events.filter(e => e.autonomous_event_type === 'PROOF'), [events]);
  const capabilityGaps = useMemo(() => events.filter(e => e.autonomous_event_type === 'GAP'), [events]);
  // Heartbeats (~45s freshness pings) and live-activity NARRATIONs (the per-iteration "what it is
  // doing right now" lines the worker streams to keep the cockpit thinking window scrolling) are
  // both cockpit-liveness signals, not decisions. Neither must appear as a node on the decision
  // timeline nor inflate the decision count, so every timeline-facing read works off the filtered
  // stream (the reasoning panel filters them the same way - single source of truth in
  // autonomousEventVisuals).
  const decisionEvents = useMemo(
    () => events.filter(e => !isHeartbeatEvent(e) && !isLiveActivityEvent(e)),
    [events],
  );

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

  // Illustrative fallbacks so the outcome lists / timeline are never a blank box while the run is
  // still warming up (mirrors the greyed sample the posture gauges show). Rendered greyed +
  // "Sample"-tagged via SamplePreview and replaced the instant the orchestrator records real data.
  const gapsAreSample = capabilityGaps.length === 0;
  const gapItems: GapItem[] = gapsAreSample
    ? [
        {
          id: 'sample-gap-0',
          title: t('Kerberoasting payload not in arsenal'),
          description: t('No installed injector can request and crack service tickets - add a Kerberoasting connector to close it.'),
          techniques: ['T1558.003'],
          cves: [],
        },
        {
          id: 'sample-gap-1',
          title: t('SMB relay capability unavailable'),
          description: t('Lateral movement via NTLM relay needs a relay injector that is not deployed in this environment.'),
          techniques: ['T1557.001', 'T1210'],
          cves: [],
        },
        {
          id: 'sample-gap-2',
          title: t('Cloud IAM enumeration collector missing'),
          description: t('Enumerating cloud identities requires a cloud IAM collector - install one to map the cloud attack surface.'),
          techniques: ['T1580'],
          cves: [],
        },
      ]
    : capabilityGaps.map(gap => ({
        id: gap.autonomous_event_id,
        title: gap.autonomous_event_title ?? t('Capability gap'),
        description: gap.autonomous_event_content ?? undefined,
        createdAt: gap.autonomous_event_created_at,
        sequence: gap.autonomous_event_sequence,
        event: gap,
        ...extractTags(gap.autonomous_event_title, gap.autonomous_event_content),
      }));

  const proofsAreSample = proofEvents.length === 0;
  const proofItems: ProofItem[] = proofsAreSample
    ? [
        {
          id: 'sample-proof-0',
          title: t('Remote code execution on a web server'),
          content: t('Chained an unauthenticated file upload into a shell on the target host.'),
          techniques: ['T1190'],
          cves: [],
        },
        {
          id: 'sample-proof-1',
          title: t('Cloud credential theft via SSRF'),
          content: t('Abused a server-side request forgery to read the metadata endpoint and exfiltrate cloud keys.'),
          techniques: ['T1190', 'T1552.005'],
          cves: [],
        },
      ]
    : proofEvents.map(proof => ({
        id: proof.autonomous_event_id,
        title: proof.autonomous_event_title ?? t('Proof'),
        content: proof.autonomous_event_content ?? undefined,
        createdAt: proof.autonomous_event_created_at,
        sequence: proof.autonomous_event_sequence,
        event: proof,
        ...extractTags(proof.autonomous_event_title, proof.autonomous_event_content),
      }));

  // A small greyed sample storyline so the timeline reads as a timeline before the first real
  // events land, matching the sample fallbacks used everywhere else on this tab. In plan mode the
  // PROOF node is dropped - a plan never executes, so it can never prove exploitation.
  const timelineIsSample = decisionEvents.length === 0;
  const sampleTimeline: AutonomousEvent[] = useMemo(() => {
    const base = [
      {
        id: 's0',
        type: 'STATUS' as const,
        title: t('Run created'),
      },
      {
        id: 's1',
        type: 'DECISION' as const,
        title: t('Plan reconnaissance'),
      },
      {
        id: 's2',
        type: 'TOOL_ACTION' as const,
        title: t('Enumerate endpoints'),
      },
      {
        id: 's3',
        type: 'GAP' as const,
        title: t('Capability gap'),
      },
      ...(isPlanMode
        ? []
        : [{
            id: 's4',
            type: 'PROOF' as const,
            title: t('Exploitation proven'),
          }]),
    ];
    return base.map((e, index) => ({
      autonomous_event_id: e.id,
      autonomous_event_run_id: runId,
      autonomous_event_sequence: index,
      autonomous_event_type: e.type,
      autonomous_event_title: e.title,
    }));
  }, [t, runId, isPlanMode]);
  const timelineEvents = timelineIsSample ? sampleTimeline : decisionEvents;

  // When new decision-timeline nodes append on the right, follow them to the
  // tail - but only if the operator has not scrolled back to inspect earlier
  // decisions (timelinePinnedRef, maintained by handleTimelineScroll).
  useEffect(() => {
    const node = timelineScrollRef.current;
    if (node && timelinePinnedRef.current) {
      node.scrollLeft = node.scrollWidth;
    }
  }, [timelineEvents.length]);

  const renderTags = (techniques: string[], cves: string[]): ReactNode => {
    if (techniques.length === 0 && cves.length === 0) {
      return null;
    }
    return (
      <Stack sx={{
        flexDirection: 'row',
        flexWrap: 'wrap',
        gap: 0.5,
      }}
      >
        {cves.map(cve => (
          <MetaChip key={cve} label={cve} color={theme.palette.error.main} />
        ))}
        {techniques.map(technique => (
          <MetaChip key={technique} label={technique} color={theme.palette.info.main} />
        ))}
      </Stack>
    );
  };

  const gapsSection = (
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
            <OutcomeCard
              key={gap.id}
              tone={theme.palette.warning.main}
              icon={<WarningAmberOutlined color="warning" fontSize="small" />}
              title={gap.title}
              body={gap.description}
              createdAtLabel={gap.createdAt ? nsdt(gap.createdAt) : undefined}
              tags={renderTags(gap.techniques, gap.cves)}
              onClick={gap.event
                ? () => setSelectedOutcome({
                    kind: 'GAP',
                    event: gap.event!,
                    techniques: gap.techniques,
                    cves: gap.cves,
                  })
                : undefined}
            />
          ))}
        </Stack>
      </SamplePreview>
    </SectionBlock>
  );

  const proofsSection = (
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
            <OutcomeCard
              key={proof.id}
              tone={theme.palette.success.main}
              badge={index + 1}
              icon={<VerifiedOutlined color="success" fontSize="small" />}
              title={proof.title}
              body={proof.content}
              createdAtLabel={proof.createdAt ? nsdt(proof.createdAt) : undefined}
              tags={renderTags(proof.techniques, proof.cves)}
              onClick={proof.event
                ? () => setSelectedOutcome({
                    kind: 'PROOF',
                    event: proof.event!,
                    techniques: proof.techniques,
                    cves: proof.cves,
                  })
                : undefined}
            />
          ))}
        </Stack>
      </SamplePreview>
    </SectionBlock>
  );

  return (
    <Stack sx={{ gap: 2 }}>
      {/* Mission card. In plan mode it turns orange (OCTI draft tone) and its title carries the
          "(plan mode)" marker, so the dedicated banner is dropped to save vertical space. */}
      <Paper
        variant="outlined"
        sx={{
          padding: 2,
          borderColor: alpha(missionAccent, 0.4),
          background: `linear-gradient(180deg, ${alpha(missionAccent, 0.08)} 0%, ${alpha(missionAccent, 0)} 100%)`,
        }}
      >
        <Stack sx={{
          flexDirection: 'row',
          alignItems: 'center',
          gap: 1,
          marginBottom: 1,
        }}
        >
          <AutoAwesome fontSize="small" sx={{ color: missionAccent }} />
          {/* Run status lives in the hero (single control surface); not repeated here. The live
              attack map is one click away in this view's own "Attack path" tab, so no button is
              duplicated at the top of the mission card. */}
          <Typography variant="h6" sx={{ margin: 0 }}>
            {isPlanMode ? t('Mission (planned)') : t('Mission')}
          </Typography>
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
            {/* Start date is not repeated here - the page hero already carries it. The mission card
                keeps only the live progress stats (short numeric values the HeroStat is designed
                for), not a long datetime that overflows the 18px value slot. */}
            <HeroStat
              icon={BoltOutlined}
              label={t('Decisions')}
              value={decisionEvents.length}
              color={accent}
            />
            {/* Proof of exploitation is impossible in plan mode (nothing executes), so the stat is
                dropped there rather than showing a permanent zero. */}
            {!isPlanMode && (
              <HeroStat
                icon={VerifiedOutlined}
                label={t('Proofs')}
                value={proofEvents.length}
                color={theme.palette.success.main}
              />
            )}
            <HeroStat
              icon={WarningAmberOutlined}
              label={t('Capability gaps')}
              value={capabilityGaps.length}
              color={theme.palette.warning.main}
            />
          </HeroStats>
        </Box>
      </Paper>

      {/* Decision timeline: a compact horizontal storyline of the orchestrator's events, sitting
          right above the outcomes. Never a blank box - a greyed sample storyline stands in
          until the first real events land. */}
      <SectionBlock
        title={t('Decision timeline')}
        action={timelineIsSample
          ? null
          : (
              <Chip
                size="small"
                label={decisionEvents.length}
                variant="outlined"
                sx={{
                  borderRadius: 1,
                  borderColor: alpha(accent, 0.4),
                  color: accent,
                }}
              />
            )}
      >
        <SamplePreview active={timelineIsSample} variant="subtle">
          <Box
            ref={timelineScrollRef}
            onScroll={handleTimelineScroll}
            sx={{
              'overflowX': 'auto',
              'paddingBottom': 0.5,
              'maskImage': 'linear-gradient(to right, transparent 0, black 24px, black calc(100% - 24px), transparent 100%)',
              'WebkitMaskImage': 'linear-gradient(to right, transparent 0, black 24px, black calc(100% - 24px), transparent 100%)',
              'scrollbarWidth': 'thin',
              '&::-webkit-scrollbar': { height: 6 },
              '&::-webkit-scrollbar-thumb': {
                backgroundColor: theme.palette.divider,
                borderRadius: 3,
              },
            }}
          >
            <Box sx={{
              display: 'inline-flex',
              gap: 2,
              position: 'relative',
              paddingInline: 1.5,
            }}
            >
              {/* The rail behind the nodes, at the vertical center of the 30px circles. */}
              <Box sx={{
                position: 'absolute',
                left: 28,
                right: 28,
                top: 15,
                height: 2,
                borderRadius: 1,
                backgroundColor: alpha(accent, 0.25),
              }}
              />
              {timelineEvents.map((event) => {
                const color = eventAccent(event, theme);
                const time = event.autonomous_event_created_at ? vnsdt(event.autonomous_event_created_at) : undefined;
                const eventTitle = sanitizeEventText(event.autonomous_event_title);
                const eventContent = sanitizeEventText(event.autonomous_event_content);
                const { techniques: tipTechniques, cves: tipCves } = extractTags(
                  eventTitle,
                  eventContent,
                );
                const tipTags = renderTags(tipTechniques, tipCves);
                return (
                  <GraphCardTooltip
                    key={event.autonomous_event_id}
                    // Controlled tooltip shared with the causal graph: it force-closes on press, on
                    // wheel-zoom, and whenever the dismiss key changes. The key folds the timeline
                    // length (a poll appended/removed a node) and a scroll nonce (the auto-scroll
                    // slid the node under the cursor), so the rich card can never stick open over the
                    // live-polling timeline the way the old uncontrolled Tooltip did.
                    dismissKey={`${timelineEvents.length}:${timelineScrollNonce}`}
                    // Render the tooltip as a proper card (matches the panel/dialog surface) rather
                    // than the cramped default: header with the event-type chip + timestamp, a
                    // right-sized title, MITRE/CVE tags, then the reasoning body clamped so a long
                    // prose block stays a readable card instead of a wall of text.
                    slotProps={{
                      tooltip: {
                        sx: {
                          maxWidth: 360,
                          padding: theme.spacing(1.25, 1.5),
                          backgroundColor: theme.palette.background.paper,
                          color: theme.palette.text.primary,
                          border: `1px solid ${theme.palette.divider}`,
                          borderRadius: 1,
                          boxShadow: theme.shadows[8],
                        },
                      },
                      arrow: { sx: { color: theme.palette.background.paper } },
                    }}
                    title={(
                      <Box>
                        <Stack sx={{
                          flexDirection: 'row',
                          alignItems: 'center',
                          gap: 0.75,
                        }}
                        >
                          <Box sx={{
                            color,
                            display: 'inline-flex',
                          }}
                          >
                            {eventIcon(event)}
                          </Box>
                          <Chip
                            label={t(eventTypeLabel(event.autonomous_event_type))}
                            size="small"
                            sx={{
                              height: 18,
                              fontSize: 10,
                              fontWeight: 700,
                              letterSpacing: '0.04em',
                              borderRadius: 0.5,
                              color,
                              backgroundColor: alpha(color, 0.12),
                            }}
                          />
                          <Box sx={{ flex: 1 }} />
                          {time && (
                            <Typography variant="caption" color="text.secondary" sx={{ whiteSpace: 'nowrap' }}>
                              {time}
                            </Typography>
                          )}
                        </Stack>
                        <Typography
                          sx={{
                            marginTop: 0.75,
                            fontSize: '0.8125rem',
                            fontWeight: 600,
                            lineHeight: 1.35,
                          }}
                        >
                          {eventTitle || t(eventTypeLabel(event.autonomous_event_type))}
                        </Typography>
                        {tipTags && <Box sx={{ marginTop: 0.5 }}>{tipTags}</Box>}
                        {eventContent && (
                          <>
                            <Divider sx={{
                              marginBlock: 0.75,
                              borderColor: theme.palette.divider,
                            }}
                            />
                            <Box sx={{
                              maxHeight: 240,
                              overflow: 'hidden',
                              maskImage: 'linear-gradient(to bottom, black 88%, transparent 100%)',
                            }}
                            >
                              <EventMarkdown content={eventContent} fontSize={11} />
                            </Box>
                          </>
                        )}
                      </Box>
                    )}
                  >
                    <Stack sx={{
                      width: 116,
                      alignItems: 'center',
                      gap: 0.5,
                      position: 'relative',
                    }}
                    >
                      <Box sx={{
                        width: 30,
                        height: 30,
                        borderRadius: '50%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color,
                        backgroundColor: theme.palette.background.paper,
                        border: `2px solid ${color}`,
                        boxShadow: `0 0 0 3px ${alpha(color, 0.12)}`,
                      }}
                      >
                        {eventIcon(event)}
                      </Box>
                      <Chip
                        label={t(eventTypeLabel(event.autonomous_event_type))}
                        size="small"
                        sx={{
                          height: 16,
                          fontSize: 9,
                          fontWeight: 700,
                          letterSpacing: '0.04em',
                          borderRadius: 0.5,
                          color,
                          backgroundColor: alpha(color, 0.12),
                        }}
                      />
                      <Typography
                        variant="caption"
                        sx={{
                          textAlign: 'center',
                          lineHeight: 1.2,
                          ...clampSx(2),
                        }}
                      >
                        {eventTitle || t(eventTypeLabel(event.autonomous_event_type))}
                      </Typography>
                      {time && (
                        <Typography variant="caption" color="text.secondary" sx={{ fontSize: 10 }}>
                          {time}
                        </Typography>
                      )}
                    </Stack>
                  </GraphCardTooltip>
                );
              })}
            </Box>
          </Box>
        </SamplePreview>
      </SectionBlock>

      {/* Outcomes: capability gaps (arsenal shortfalls the orchestrator hit) and - for a live run
          only - the proof-of-exploitation case file. A plan never executes, so it can never prove
          exploitation: in plan mode the proofs column is dropped and the gaps take the full width.
          Each column is always shown - a greyed illustrative sample stands in until the run records
          real data. */}
      <Box sx={{
        display: 'grid',
        gridTemplateColumns: isPlanMode
          ? '1fr'
          : {
              xs: '1fr',
              md: '1fr 1fr',
            },
        gap: 2,
        alignItems: 'stretch',
      }}
      >
        {gapsSection}
        {!isPlanMode && proofsSection}
      </Box>

      <AutonomousOutcomeDialog
        kind={selectedOutcome?.kind ?? 'GAP'}
        event={selectedOutcome?.event ?? null}
        simulationId={simulationId}
        techniques={selectedOutcome?.techniques ?? []}
        cves={selectedOutcome?.cves ?? []}
        onClose={() => setSelectedOutcome(null)}
      />
    </Stack>
  );
};

export default AutonomousOutcome;
