import {
  AutoAwesome,
  ErrorOutline,
  HelpOutline,
  HourglassEmpty,
  SendOutlined,
  WarningAmber,
} from '@mui/icons-material';
import { Box, Chip, CircularProgress, FormControlLabel, IconButton, Radio, RadioGroup, Stack, TextField, Typography } from '@mui/material';
import type { Theme } from '@mui/material/styles';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, useCallback, useEffect, useMemo, useRef, useState } from 'react';

import {
  addAutonomousDirective,
  fetchAutonomousRun,
  fetchAutonomousTimeline,
} from '../../../actions/autonomous/autonomous-actions';
import {
  type AutonomousEvent,
  type AutonomousRun,
  type AutonomousRunStatus,
} from '../../../actions/autonomous/autonomous-types';
import { useFormatter } from '../../../components/i18n';
import { computeBannerSettings } from '../../../public/components/systembanners/utils';
import useAuth from '../../../utils/hooks/useAuth';
import { useChatbot, useChatbotContentMargin } from '../ariane/useChatbotHooks';
import { eventAccent, eventIcon, EventMarkdown, eventTypeLabel, isHeartbeatEvent, isLiveActivityEvent, sanitizeEventText, stripMarkdown } from './autonomousEventVisuals';
import { AUTONOMOUS_PANEL_WIDTH } from './useAutonomousPanelWidth';

// An "active" run is one the orchestrator is currently driving, so the panel keeps polling the
// timeline, shows the live thinking/hourglass indicator, and enables steering. PLANNING (the AI is
// still building the scenario's logic) MUST be here alongside RUNNING/WAITING_INPUT: it is an
// in-progress phase where the AI is authoring the logic, so without it the right panel would sit
// frozen with no indicator and never refresh until a question flips the run to WAITING_INPUT.
// PLANNED is settled (logic done), so it is intentionally NOT active.
const ACTIVE_STATUSES: AutonomousRunStatus[] = ['PLANNING', 'RUNNING', 'WAITING_INPUT'];
const POLL_INTERVAL_MS = 3000;
// A live ("active") caption keeps pulsing as if the orchestrator were computing right now. If the
// newest timeline event is older than this, it is NOT: the run is parked between cycles, waiting on
// a directive re-check, or an upstream cycle stalled. Past this age we settle any active caption
// into a calm idle one so the cockpit never lies with a frozen "Deciding the next move" for minutes
// (the reported stuck-cockpit symptom). A fresh event re-animates it. Sized above a normal cycle's
// event cadence so an ordinary long action does not flip it to idle prematurely.
const STALE_CAPTION_AFTER_MS = 180000;

// The stream auto-follows the newest event only while the operator is within this distance (px) of
// the bottom - anything further means they deliberately scrolled up to read an older decision.
const STICK_TO_BOTTOM_THRESHOLD_PX = 80;

// Cap the proposed one-click choices so the callout stays scannable: at most this many radio options
// are ever shown, and the always-present free-text composer below is the escape hatch for anything
// the operator would rather type.
const MAX_QUESTION_CHOICES = 3;

// Event types that prove the orchestrator is actively WORKING (deciding, acting, analyzing,
// delegating, narrating) - as opposed to STATUS bookkeeping or an operator QUESTION/DIRECTIVE. The
// thinking-window caption keys off the latest one of these.
const ACTIVITY_EVENT_TYPES = ['DECISION', 'TOOL_ACTION', 'PROOF', 'GAP', 'HANDOVER', 'AGENT_DELEGATION', 'NARRATION'] as const;
const isActivityType = (type: string | undefined): boolean => (ACTIVITY_EVENT_TYPES as readonly string[]).includes(type ?? '');

// A heartbeat older than this is treated as stale: the orchestrator only emits heartbeats WHILE a
// decision cycle is actively running (~45s cadence), so a fresh one is positive proof the run is
// grinding right now. Sized at ~2.5 cycles so one dropped beat or a slow poll never flips a working
// run to idle, while a genuinely stopped cycle (park / stall) settles within a few seconds.
const HEARTBEAT_FRESH_MS = 120000;

interface QuestionChoice {
  id: string;
  label: string;
  /** The text actually sent to the orchestrator when this choice is picked (defaults to label). */
  value: string;
}

// A QUESTION event may carry structured choices in its JSON `data` ({"options": [...]}) so the panel
// can render a beautiful radio selection instead of forcing free text. Options are lenient: a bare
// string, or an object with any of id/label/value. Anything unparseable yields no choices (the panel
// then just shows the free-text answer box).
const parseQuestionChoices = (data?: string | null): QuestionChoice[] => {
  if (!data) {
    return [];
  }
  try {
    const parsed = JSON.parse(data) as { options?: unknown };
    const options = parsed?.options;
    if (!Array.isArray(options)) {
      return [];
    }
    return options
      .map((option, index): QuestionChoice | null => {
        if (typeof option === 'string') {
          return {
            id: `opt-${index}`,
            label: option,
            value: option,
          };
        }
        if (option && typeof option === 'object') {
          const candidate = option as {
            id?: string;
            label?: string;
            value?: string;
          };
          const label = candidate.label ?? candidate.value ?? candidate.id;
          if (!label) {
            return null;
          }
          return {
            id: candidate.id ?? `opt-${index}`,
            label,
            value: candidate.value ?? candidate.label ?? label,
          };
        }
        return null;
      })
      .filter((choice): choice is QuestionChoice => choice !== null);
  } catch {
    return [];
  }
};

interface ThinkingPhase {
  key: string;
  label: string;
  color: string;
  // Whether the orchestrator is actively working (mid-cycle) vs. idle/parked (awaiting a
  // human-timescale event or the operator's answer). Only an ACTIVE phase pulses and streams the
  // live thought echo; an idle phase settles into a calm, static waiting indicator so a parked run
  // does not look like it is still computing.
  active: boolean;
}

// Tail-of-stream status window. While the orchestrator is actively working (active phase) it shows
// three pulsing dots plus its most recent reasoning line, faintly shimmering, so the panel feels
// alive between activity events (mirrors the XTM One scrolling thinking window). When the run is
// idle - parked on a status awaiting a human-timescale event, or waiting on the operator - it
// settles into a STATIC hourglass + calm caption with no pulsing and no thought echo, so a parked
// run stops looking like it is still thinking. The label + colour reflect the CURRENT phase and
// animate on every phase change.
// Turn a millisecond gap into a compact "still working" clock: "12s", "1m 20s". Kept short so it
// sits inline next to the phase caption without wrapping.
const formatElapsed = (ms: number): string => {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  if (totalSeconds < 60) return `${totalSeconds}s`;
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}m ${seconds.toString().padStart(2, '0')}s`;
};

const ThinkingBubble: FunctionComponent<{
  phase: ThinkingPhase;
  theme: Theme;
  lines: string[];
  /** Timestamp of the most recent activity, so the window can tick a live "working for Ns" clock.
   *  This is what turns a silent stretch (e.g. the orchestrator grinding through tool retries with
   *  no new narration) from a frozen caption into a visibly advancing counter. */
  activitySince?: string | number | null;
}> = ({
  phase,
  theme,
  lines,
  activitySince,
}) => {
  const accent = phase.color;
  const active = phase.active;
  // Tick a 1s clock ONLY while actively working, so the elapsed counter advances live and the
  // interval is torn down the moment the run parks/waits.
  const [now, setNow] = useState<number>(() => Date.now());
  useEffect(() => {
    if (!active) return undefined;
    setNow(Date.now());
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, [active, phase.key]);
  const sinceMs = activitySince != null ? new Date(activitySince).getTime() : Number.NaN;
  const elapsedLabel = active && Number.isFinite(sinceMs) ? formatElapsed(now - sinceMs) : null;
  // A parked/waiting phase never streams the live thought echo (there is no live thought - the run
  // is idle), and its dots do not pulse.
  const showLatest = active && lines.length > 0;
  // Keep the window pinned to the bottom as the reasoning grows, so it visibly "defiles" like the
  // XTM One thinking window: each new thought line is appended at the bottom and older lines scroll
  // up out of view under the fade mask, instead of a single static block that never moves.
  const textRef = useRef<HTMLDivElement | null>(null);
  const streamText = lines.join('\n');
  useEffect(() => {
    const node = textRef.current;
    if (node) {
      node.scrollTop = node.scrollHeight;
    }
  }, [streamText]);

  return (
    <Box
      sx={{
        'marginTop': 0.5,
        'paddingLeft': 2,
        'position': 'relative',
        '@keyframes aevThinkingShimmer': {
          '0%': { opacity: 0.35 },
          '50%': { opacity: 0.9 },
          '100%': { opacity: 0.35 },
        },
        '@keyframes aevThinkingDot': {
          '0%, 80%, 100%': {
            transform: 'scale(0.6)',
            opacity: 0.3,
          },
          '40%': {
            transform: 'scale(1)',
            opacity: 1,
          },
        },
        '@keyframes aevPhaseIn': {
          '0%': {
            opacity: 0,
            transform: 'translateY(4px)',
          },
          '100%': {
            opacity: 1,
            transform: 'translateY(0)',
          },
        },
      }}
    >
      <Stack sx={{
        flexDirection: 'row',
        alignItems: 'center',
        gap: 0.75,
      }}
      >
        {active ? (
          <Stack sx={{
            flexDirection: 'row',
            gap: 0.4,
            alignItems: 'center',
          }}
          >
            {[0, 1, 2].map(i => (
              <Box
                key={i}
                sx={{
                  width: 6,
                  height: 6,
                  borderRadius: '50%',
                  backgroundColor: accent,
                  transition: theme.transitions.create('background-color'),
                  animation: 'aevThinkingDot 1.4s infinite ease-in-out both',
                  animationDelay: `${i * 0.16}s`,
                }}
              />
            ))}
          </Stack>
        ) : (
          // Idle/parked: a still hourglass, not pulsing dots, so the run reads as waiting - not working.
          <HourglassEmpty sx={{
            fontSize: 16,
            color: accent,
          }}
          />
        )}
        {/* Key on the phase so a new phase remounts and replays the fade/slide-in transition. */}
        <Typography
          key={phase.key}
          variant="caption"
          sx={{
            color: accent,
            fontWeight: 600,
            letterSpacing: '0.02em',
            transition: theme.transitions.create('color'),
            animation: 'aevPhaseIn 0.35s ease',
          }}
        >
          {phase.label}
        </Typography>
        {elapsedLabel && (
          <Typography
            variant="caption"
            sx={{
              color: alpha(theme.palette.text.secondary, 0.7),
              fontVariantNumeric: 'tabular-nums',
            }}
          >
            {`· ${elapsedLabel}`}
          </Typography>
        )}
      </Stack>
      {showLatest && (
        <Box
          ref={textRef}
          sx={{
            'maxHeight': 132,
            'overflowY': 'auto',
            'marginTop': 0.75,
            // Fade the top edge so older lines appear to scroll up out of view; no visible scrollbar.
            'maskImage': 'linear-gradient(to bottom, transparent 0, black 28px)',
            'WebkitMaskImage': 'linear-gradient(to bottom, transparent 0, black 28px)',
            'scrollbarWidth': 'none',
            '&::-webkit-scrollbar': { display: 'none' },
          }}
        >
          {lines.map((line, index) => {
            const isLast = index === lines.length - 1;
            return (
              <Typography
                key={`${index}-${line.slice(0, 24)}`}
                variant="caption"
                sx={{
                  display: 'block',
                  fontStyle: 'italic',
                  lineHeight: 1.5,
                  color: alpha(theme.palette.text.secondary, isLast ? 0.95 : 0.5),
                  whiteSpace: 'pre-wrap',
                  // Only the newest line shimmers - the "live" thought; older lines settle, dimmed.
                  animation: isLast ? 'aevThinkingShimmer 2.4s ease-in-out infinite' : undefined,
                }}
              >
                {line}
              </Typography>
            );
          })}
        </Box>
      )}
    </Box>
  );
};

interface AutonomousReasoningPanelProps {
  run: AutonomousRun;
  /** Lift status transitions up so the hero + tab set stay in sync without a second poll loop. */
  onRunUpdate?: (run: AutonomousRun) => void;
  /** Share the incrementally polled timeline with a sibling (the overview outcome layer) so that
   *  layer does not start a second full-from-cursor-0 poll of the same endpoint. */
  onTimelineEvents?: (events: AutonomousEvent[]) => void;
  /** Live panel width (px), owned by the parent via {@link useAutonomousPanelWidth} so the content
   *  padding follows the drag. */
  width?: number;
  /** Commit a new width while the user drags the left edge (clamped by the hook). */
  onWidthChange?: (width: number) => void;
  /** Observe-only mode: the reasoning stream stays visible but the steering box is hidden. Used on
   *  the simulation cockpit, where all control (incl. steering) lives on the parent scenario. */
  readOnly?: boolean;
}

/**
 * Always-open right cockpit for an autonomous (AI-driven) simulation: the orchestrator's live
 * reasoning stream (narration, decisions, tool actions, handovers, proofs, capability gaps),
 * inline questions when it parks for input, and a chatbot-style steering box at the bottom.
 * Modelled on the Ask Ariane side panel - fixed to the right edge, under the app bar - but scoped
 * to a single run and topped by the scenario/simulation hero it sits beside. Lifecycle controls
 * (pause / resume / stop) live in the hero, not here, so operators keep the same control surface as
 * a normal run.
 */
const AutonomousReasoningPanel: FunctionComponent<AutonomousReasoningPanelProps> = ({
  run: initialRun,
  onRunUpdate,
  onTimelineEvents,
  width = AUTONOMOUS_PANEL_WIDTH,
  onWidthChange,
  readOnly = false,
}) => {
  const theme = useTheme();
  const { t, nsdt } = useFormatter();
  const { settings } = useAuth();
  // Slide the panel left by exactly the width the Ask Ariane sidebar pushes the main content, so the
  // two never overlap: the app content already gets marginRight = chatbotMargin, and pinning the
  // panel to right = chatbotMargin keeps it flush with the content's new right edge.
  const chatbotMargin = useChatbotContentMargin();
  const { isResizing } = useChatbot();
  const [dragging, setDragging] = useState(false);

  const [run, setRun] = useState<AutonomousRun>(initialRun);
  const [events, setEvents] = useState<AutonomousEvent[]>([]);
  useEffect(() => {
    onTimelineEvents?.(events);
  }, [events, onTimelineEvents]);
  const [directive, setDirective] = useState('');
  // Which proposed one-click choice the operator picked (null = none; they can still type freely in
  // the always-visible composer, which takes precedence over a selected choice).
  const [selectedChoice, setSelectedChoice] = useState<string | null>(null);
  // The question the operator just answered - suppressed immediately (optimistic) so the callout
  // does not linger and hog space while we wait for the status poll to flip the run to RUNNING.
  const [answeredQuestionId, setAnsweredQuestionId] = useState<string | null>(null);
  // Guards against a double submit: Enter and a click (or two fast Enters) both fire
  // handleComposerSubmit in the same tick, and each reads the same pre-clear `directive`, so without
  // this the operator's answer is sent twice. Held until the request settles.
  const sendingRef = useRef(false);
  const cursorRef = useRef(0);
  const scrollRef = useRef<HTMLDivElement | null>(null);
  // Whether the operator is at (or near) the bottom of the stream, sampled in the onScroll handler
  // - i.e. BEFORE new content grows the list. Measuring inside the pin effect below would run
  // after render, when a tall new decision has already pushed the bottom away by more than the
  // threshold, silently breaking the auto-follow exactly when the operator was reading live.
  // Starts true so a fresh stream pins to the newest event.
  const stickToBottomRef = useRef(true);
  // Identity of the run/simulation the stream is currently populated for. The reset-and-reload
  // effect below keys off this so it fires ONLY on a genuine run or simulation switch (navigation /
  // restart) - never spuriously on a re-render, which would blank the live stream to empty (and drop
  // the thinking phase to its "Thinking through the next move" default) before the re-poll repaints.
  const streamKeyRef = useRef<string | null>(null);
  // Latest run pushed down by the parent, read by the identity-change sync effect below without
  // making the whole object a dependency (which would re-fire on every parent re-render).
  const initialRunRef = useRef(initialRun);
  initialRunRef.current = initialRun;
  const runId = run.autonomous_run_id;
  const simulationId = run.autonomous_run_simulation_id;

  // Drag the left edge to widen the panel. Deltas are measured from the drag origin so the math is
  // independent of the chatbot offset; the parent hook clamps to [min, 1/3 viewport].
  const handleResizeStart = useCallback((event: React.MouseEvent) => {
    if (!onWidthChange) {
      return;
    }
    event.preventDefault();
    const startX = event.clientX;
    const startWidth = width;
    setDragging(true);
    document.body.style.userSelect = 'none';
    const onMove = (moveEvent: MouseEvent) => onWidthChange(startWidth + (startX - moveEvent.clientX));
    const onUp = () => {
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
      document.body.style.userSelect = '';
      setDragging(false);
    };
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  }, [onWidthChange, width]);

  const { bannerHeightNumber } = computeBannerSettings(settings);
  const topOffset = 64 + bannerHeightNumber;
  const accent = theme.palette.ai?.main ?? theme.palette.primary.main;

  const applyRun = useCallback((next: AutonomousRun) => {
    setRun(next);
    onRunUpdate?.(next);
  }, [onRunUpdate]);

  const refreshRun = useCallback(() => fetchAutonomousRun(runId)
    .then(res => applyRun(res.data))
    .catch(() => {}), [runId, applyRun]);

  const pollTimeline = useCallback(() => fetchAutonomousTimeline(runId, cursorRef.current)
    .then((res) => {
      const incoming = res.data ?? [];
      if (incoming.length > 0) {
        cursorRef.current = Math.max(cursorRef.current, ...incoming.map(e => e.autonomous_event_sequence));
        // Dedupe by event id: the mount reset-poll and the interval poll can both fetch from the
        // same cursor before it advances (and dev StrictMode double-invokes the mount effect), so a
        // naive append renders every event twice. Only add ids we have not seen yet.
        setEvents((prev) => {
          const seen = new Set(prev.map(e => e.autonomous_event_id));
          const fresh = incoming.filter(e => !seen.has(e.autonomous_event_id));
          return fresh.length > 0 ? [...prev, ...fresh] : prev;
        });
      }
    })
    .catch(() => {}), [runId]);

  // Always call the latest pollTimeline without making it a dependency of the reset effect - so that
  // effect keys purely on the run/simulation identity and cannot be re-triggered by an incidental
  // pollTimeline re-creation.
  const pollTimelineRef = useRef(pollTimeline);
  pollTimelineRef.current = pollTimeline;

  // A restart reuses the SAME autonomous run id but provisions a fresh simulation and flips the run
  // back to RUNNING, while wiping the server-side timeline. useState(initialRun) only captures the
  // run at mount, so without re-syncing from the parent the panel would keep showing the torn-down
  // (terminal) run - and, because refreshRun only polls while active, never recover until a full
  // page reload. Re-sync whenever the parent's run or its underlying simulation changes.
  //
  // The status is a dependency too: lifecycle controls live in the hero, not here, so a hero-driven
  // pause/resume changes only the parent's run. When the run is not active the panel does not poll,
  // so a resume would otherwise never reach the panel - it would stay frozen as PAUSED with a live
  // composer until a full remount. Keying on status makes a hero transition reflect immediately.
  useEffect(() => {
    setRun(initialRunRef.current);
  }, [initialRun.autonomous_run_id, initialRun.autonomous_run_simulation_id, initialRun.autonomous_run_status]);

  // Reset and reload ONLY when the run OR its underlying simulation actually changes: navigating
  // between simulations reuses the panel, and a restart swaps the simulation under the same run id
  // and clears the timeline server-side - keying on that identity makes the stream drop the stale
  // events instead of stranding them (which previously required a manual full reload). Guarded by
  // streamKeyRef so it fires strictly on a real identity change: clearing to [] on any other re-run
  // blanked the whole reasoning stream for a frame (it fell back to the "Thinking through the next
  // move" default), then repainted - a jarring, inconsistent flicker on every poll. A transient run
  // payload that momentarily lost its simulation id is ignored so it never blanks a live stream.
  useEffect(() => {
    if (!runId) {
      return;
    }
    const nextKey = `${runId}::${simulationId ?? ''}`;
    if (streamKeyRef.current === nextKey) {
      return;
    }
    if (streamKeyRef.current !== null && !simulationId) {
      return;
    }
    streamKeyRef.current = nextKey;
    cursorRef.current = 0;
    // A fresh stream should follow its newest event even if the operator had scrolled up in the
    // previous run's timeline.
    stickToBottomRef.current = true;
    setEvents([]);
    pollTimelineRef.current();
  }, [runId, simulationId]);

  const status = run.autonomous_run_status;
  const isActive = ACTIVE_STATUSES.includes(status);

  useEffect(() => {
    if (!isActive) {
      return undefined;
    }
    const interval = setInterval(() => {
      refreshRun();
      pollTimeline();
    }, POLL_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [isActive, refreshRun, pollTimeline]);

  // The operator-facing decision feed excludes heartbeats (freshness pings, not decisions) AND
  // live-activity NARRATIONs (the per-iteration "what it is doing right now" lines the worker
  // streams). Both keep the cockpit alive without being decisions: heartbeats drive the freshness
  // clock, live-activity lines flow into `thinkingLines` below (the scrolling thinking window) - so
  // neither should ever appear as a row in the operator decision feed, or a long silent burst would
  // fill the timeline with "Working"/activity noise. The freshness/caption logic below still keys
  // off the raw `events` (including both) so the cockpit stays animated. Memoised: the predicates
  // JSON-parse the payloads, so they should run once per new batch of events, not on every render.
  const visibleEvents = useMemo(
    () => events.filter(e => !isHeartbeatEvent(e) && !isLiveActivityEvent(e)),
    [events],
  );

  const isWaitingInput = status === 'WAITING_INPUT';
  // Newest-first lookups over the stream. findLast scans backwards without cloning, and the
  // results are memoised so the scans (and isHeartbeatEvent's JSON.parse) run once per new batch
  // of events - not on every incidental re-render (typing in the composer, the 3s status poll).
  const newestQuestion = useMemo(
    () => events.findLast(e => e.autonomous_event_type === 'QUESTION'),
    [events],
  );
  // Only surface it while the run is actually parked on the operator.
  const latestQuestion = isWaitingInput ? newestQuestion : undefined;
  // A question is ALREADY answered if the operator's reply (a DIRECTIVE event, "Operator directive
  // queued") was recorded after it in the timeline. Deriving this from the stream - not only from the
  // optimistic local answeredQuestionId - is what keeps an answered question from re-surfacing: local
  // state is lost on any remount (switching Overview/Scope/Logic tabs re-mounts the panel) and the
  // backend can linger in WAITING_INPUT, so a purely-local guard let a stale re-park re-show the same
  // question. A genuinely NEW question has no directive after it yet, so it still shows.
  const questionAnsweredInTimeline = !!latestQuestion
    && events.some(e => e.autonomous_event_type === 'DIRECTIVE'
      && e.autonomous_event_sequence > latestQuestion.autonomous_event_sequence);
  // Also hide it the moment it is answered (optimistic), before the DIRECTIVE event is polled back.
  const pendingQuestion = latestQuestion
    && latestQuestion.autonomous_event_id !== answeredQuestionId
    && !questionAnsweredInTimeline
    ? latestQuestion
    : undefined;
  // Sanitize the LLM-authored labels at derivation (not render) time and DROP a choice whose label
  // sanitizes to nothing: falling back to the raw text would leak the exact tool markup
  // sanitizeEventText strips, and an unlabeled radio is unpickable anyway - the free-text composer
  // remains as the answer path. The `value` (what is sent back to the orchestrator) stays raw.
  const questionChoices = (pendingQuestion ? parseQuestionChoices(pendingQuestion.autonomous_event_data) : [])
    .map(choice => ({
      ...choice,
      label: sanitizeEventText(choice.label),
    }))
    .filter(choice => choice.label.length > 0)
    .slice(0, MAX_QUESTION_CHOICES);
  const hasChoices = questionChoices.length > 0;

  // The pending question renders as a dedicated in-flow callout at the tail of the scrollable stream
  // (with its one-click choices), so drop its plain timeline row from the feed to avoid showing it
  // twice. Once answered it is no longer pending and reappears as a normal history row.
  const streamEvents = pendingQuestion
    ? visibleEvents.filter(event => event.autonomous_event_id !== pendingQuestion.autonomous_event_id)
    : visibleEvents;

  let composerPlaceholder = t('Steer the AI live (e.g. focus on the finance subnet, avoid host X, try Kerberoasting)');
  if (hasChoices) {
    composerPlaceholder = t('Or type your own answer');
  } else if (isWaitingInput && pendingQuestion) {
    // Only prompt for an answer while a question is actually pending. Once the operator answers we
    // optimistically clear pendingQuestion, so revert to the default steer placeholder immediately
    // instead of leaving "Answer the AI..." stuck until the backend flips out of WAITING_INPUT.
    composerPlaceholder = t('Answer the AI (e.g. the web apps in scope are app-prod-01 and app-prod-02)');
  }

  // Reset the composer + choice selection ONLY when a genuinely NEW question arrives. Keyed on the
  // last question id seen (not on every pendingQuestion identity change): after a failed send the
  // SAME question resurfaces (the optimistic dismissal is rolled back), and an unconditional reset
  // would fire on that undefined -> same-id transition and wipe the restored answer text right
  // after the catch put it back - stranding the operator exactly like the bug this PR fixes.
  const lastQuestionIdRef = useRef<string | null>(null);
  useEffect(() => {
    const questionId = pendingQuestion?.autonomous_event_id ?? null;
    if (questionId !== null && questionId !== lastQuestionIdRef.current) {
      lastQuestionIdRef.current = questionId;
      setSelectedChoice(null);
      setDirective('');
    }
  }, [pendingQuestion?.autonomous_event_id]);

  // Keep the stream pinned to its tail as it grows - but only while the operator was already at (or
  // near) the bottom before the new content rendered (see stickToBottomRef), so a new event never
  // yanks them away from an older decision they scrolled up to read. Keyed on the VISIBLE feed
  // length (filtered-out heartbeats do not change the rendered stream) AND on the pending-question
  // identity, so the now in-flow question callout + one-click choices scroll into view the moment
  // the run parks on the operator - even when the status flip and the QUESTION event land in
  // different poll cycles.
  useEffect(() => {
    const node = scrollRef.current;
    if (node && stickToBottomRef.current) {
      node.scrollTop = node.scrollHeight;
    }
  }, [visibleEvents.length, pendingQuestion?.autonomous_event_id]);

  const sendDirective = useCallback((content: string) => {
    const trimmed = content.trim();
    if (trimmed.length === 0 || !isActive || sendingRef.current) {
      return;
    }
    sendingRef.current = true;
    const dismissedQuestionId = pendingQuestion?.autonomous_event_id ?? null;
    setDirective('');
    setSelectedChoice(null);
    // Optimistically dismiss the current question so its callout stops occupying space right away.
    if (dismissedQuestionId) {
      setAnsweredQuestionId(dismissedQuestionId);
    }
    addAutonomousDirective(runId, trimmed)
      .then(() => pollTimeline())
      .catch(() => {
        // The send failed (network / backend). Roll back the optimistic dismissal so the question
        // re-surfaces, and restore the operator's text - otherwise the callout is gone for good and
        // the run is stranded in WAITING_INPUT with no way to answer it. Restore ONLY while the
        // composer is still empty: the field stays live during the in-flight request, so the
        // operator may already be typing something new and the rollback must not clobber it.
        setAnsweredQuestionId(current => (current === dismissedQuestionId ? null : current));
        setDirective(current => (current.trim().length === 0 ? trimmed : current));
      })
      .finally(() => {
        sendingRef.current = false;
      });
  }, [isActive, pendingQuestion, runId, pollTimeline]);

  // One submit path for the always-visible composer + optional one-click choices: a typed answer
  // ALWAYS wins (it is the operator's explicit words); otherwise send the picked choice. This is
  // why the free-text input lives directly next to the send button - it is the primary answer field,
  // with the radio options as shortcuts above it, not a hidden "custom answer" mode.
  const canSubmitAnswer = directive.trim().length > 0 || (hasChoices && selectedChoice !== null);
  const handleComposerSubmit = useCallback(() => {
    const typed = directive.trim();
    if (typed.length > 0) {
      sendDirective(typed);
      return;
    }
    const chosen = questionChoices.find(choice => choice.id === selectedChoice);
    if (chosen) {
      sendDirective(chosen.value);
    }
  }, [directive, questionChoices, selectedChoice, sendDirective]);

  // Feed the live thinking window a SCROLLING transcript of the orchestrator's recent reasoning
  // (not a single frozen line): keep the tail of narration/decision/tool prose in chronological
  // order so each new cycle appends a line at the bottom and older ones scroll up under the fade
  // mask, mirroring the XTM One thinking window. Operators read its train of thought, not a spinner.
  // Memoised: the map runs the regex-heavy stripMarkdown over the tail of the stream, so it should
  // recompute once per new batch of events - not on every keystroke in the composer (which re-renders
  // this component through the `directive` state).
  const thinkingLines = useMemo(
    () => events
      .filter(e => (['NARRATION', 'DECISION', 'TOOL_ACTION'] as const).includes(
        e.autonomous_event_type as 'NARRATION' | 'DECISION' | 'TOOL_ACTION',
      ))
      .map(e => stripMarkdown(sanitizeEventText(e.autonomous_event_content ?? e.autonomous_event_title)))
      .filter((line): line is string => Boolean(line))
      .slice(-8),
    [events],
  );

  // Current orchestrator phase for the thinking window. Derived from status + the latest activity
  // event rather than the raw run status, so the caption narrates what the run is doing and animates
  // as it moves (deciding -> acting -> analyzing ...). Crucially, once the operator answers we flip
  // to "Processing your answer" immediately -- the backend status stays WAITING_INPUT until the next
  // 3s poll, so keying off status alone would freeze on "Waiting for your input".
  const lastActivityEvent = useMemo(
    () => events.findLast(e => isActivityType(e.autonomous_event_type)),
    [events],
  );
  const lastActivityType = lastActivityEvent?.autonomous_event_type;

  // A fresh heartbeat is positive proof the orchestrator is grinding a cycle RIGHT NOW (they only
  // fire while the agent chat is running). It is the signal that turns an in-progress delegation
  // from a static "Waiting for X" into a live, pulsing "Consulting X" - the reported "the right
  // panel never shows it is actually working" symptom. Filtered out of the feed everywhere else,
  // here it is read only for its timestamp.
  const lastHeartbeatAt = useMemo(
    () => events.findLast(isHeartbeatEvent)?.autonomous_event_created_at,
    [events],
  );
  const heartbeatFresh = lastHeartbeatAt
    ? Date.now() - new Date(lastHeartbeatAt).getTime() < HEARTBEAT_FRESH_MS
    : false;
  const workingByHeartbeat = isActive && !isWaitingInput && heartbeatFresh;

  // The live "working for Nm Ns" clock should measure the current move, not the 45s heartbeat
  // cadence: anchor it to the newest REAL activity event (e.g. the delegation start) so a long
  // consult reads "Consulting X - 3m 20s" instead of resetting every heartbeat.
  const activitySince = lastActivityEvent?.autonomous_event_created_at
    ?? (events.length > 0 ? events[events.length - 1].autonomous_event_created_at : undefined);

  // The latest agent-delegation event drives the "delegating to / waiting for <agent>" caption. A
  // 'start' phase with no following 'result' reads as waiting (static), mirroring the parked model.
  const lastDelegation = useMemo(
    () => events.findLast(e => e.autonomous_event_type === 'AGENT_DELEGATION'),
    [events],
  );
  const delegationInfo = (() => {
    if (!lastDelegation) {
      return null;
    }
    try {
      const data = lastDelegation.autonomous_event_data
        ? JSON.parse(lastDelegation.autonomous_event_data) as {
          agent_name?: string;
          phase?: string;
        }
        : null;
      return {
        agentName: data?.agent_name,
        waiting: data?.phase === 'start',
      };
    } catch {
      return {
        agentName: undefined,
        waiting: false,
      };
    }
  })();
  // A STATUS event is the orchestrator's end-of-cycle "settled state" marker (e.g. "Phishing lure
  // in flight - awaiting human interaction"): the run stays RUNNING but is now PARKED, idle until a
  // human-timescale event or the next cycle. So when the newest event is a STATUS, the orchestrator
  // is NOT computing - the thinking window must stop pulsing and settle into a calm wait. As soon
  // as the next cycle emits an activity event, the newest event is no longer a STATUS and the
  // window animates again.
  const newestEvent = events.length > 0 ? events[events.length - 1] : undefined;
  // A STATUS stamped `{"phase":"engaged"}` (start / restart-then-start / resume) means the
  // orchestrator has JUST engaged and is actively working - it can churn for minutes (building
  // arsenal, resolving contracts) before its first DECISION lands. That is the opposite of a park,
  // so it must NOT read as the calm "Awaiting the next event": the cockpit looked frozen for that
  // whole window even though a burst of work had already begun the instant the operator clicked.
  const engagedOnStatus = (() => {
    if (newestEvent?.autonomous_event_type !== 'STATUS' || !newestEvent.autonomous_event_data) {
      return false;
    }
    try {
      return (JSON.parse(newestEvent.autonomous_event_data) as { phase?: string }).phase === 'engaged';
    } catch {
      return false;
    }
  })();
  // A heartbeat STATUS means the orchestrator is actively grinding a long cycle - the opposite of a
  // park. It must NOT read as the calm "Awaiting the next event"; instead we fall through to the
  // switch below so the caption keeps animating over the last real activity.
  const heartbeatOnStatus = isHeartbeatEvent(newestEvent);
  // Parked only when the newest STATUS is a genuine end-of-cycle wait (NOT an engagement marker and
  // NOT a still-working heartbeat).
  const parkedOnStatus = newestEvent?.autonomous_event_type === 'STATUS'
    && !engagedOnStatus && !heartbeatOnStatus;
  // Has the orchestrator RESUMED since the operator answered? The backend run status stays
  // WAITING_INPUT until a later 3s poll flips it, so it lies about "still waiting" for a while. The
  // truthful signal is a fresh activity event: once the newest event is a DECISION / TOOL_ACTION /
  // ... the AI is demonstrably working again, so the caption must narrate THAT instead of freezing
  // on "Processing your answer" (the exact "it says processing while it is already executing" bug).
  const newestIsActivity = isActivityType(newestEvent?.autonomous_event_type);
  // How long since the newest event? A stale timeline means the orchestrator is not actively
  // working, whatever the last event type was - used below to stop an active caption pulsing over a
  // frozen cockpit.
  const newestEventAgeMs = newestEvent?.autonomous_event_created_at
    ? Date.now() - new Date(newestEvent.autonomous_event_created_at).getTime()
    : 0;
  const captionStale = newestEventAgeMs > STALE_CAPTION_AFTER_MS;
  let thinkingPhase: ThinkingPhase = (() => {
    if (isWaitingInput && pendingQuestion) {
      // Genuinely idle on the operator: static wait, not a pulsing "still working" animation.
      return {
        key: 'waiting_input',
        label: t('Waiting for your input'),
        color: theme.palette.warning.main,
        active: false,
      };
    }
    if (isWaitingInput && !newestIsActivity) {
      // The operator answered but the orchestrator has not emitted anything yet; the backend is
      // still WAITING_INPUT until the next poll flips it. Bridge that gap with a "resuming" caption
      // ONLY until real activity arrives - once it does, newestIsActivity is true and we fall through
      // to the switch below so the caption reflects what the AI is actually doing (running an
      // action, analyzing results, ...) instead of staying stuck on "Processing your answer".
      return {
        key: 'resuming',
        label: t('Processing your answer'),
        color: accent,
        active: true,
      };
    }
    if (engagedOnStatus && !newestIsActivity) {
      // Freshly engaged (start / restart / resume) but the first activity event has not landed yet.
      // The orchestrator is demonstrably working (a cycle is running); narrate that with a pulsing
      // caption instead of the static parked one, until the first DECISION / TOOL_ACTION arrives and
      // the switch below takes over. Fixes the "stuck on Awaiting the next event for minutes after I
      // clicked Redo plan, but the iterations had already begun" report.
      return {
        key: 'engaging',
        label: run.autonomous_run_plan_mode === true ? t('Building the scenario logic') : t('Getting to work'),
        color: accent,
        active: true,
      };
    }
    if (parkedOnStatus) {
      // Parked between cycles awaiting a human-timescale event: calm, static, no thought echo.
      return {
        key: 'parked',
        label: t('Awaiting the next event'),
        color: theme.palette.text.secondary,
        active: false,
      };
    }
    switch (lastActivityType) {
      case 'DECISION':
        return {
          key: 'deciding',
          label: t('Deciding the next move'),
          color: accent,
          active: true,
        };
      case 'TOOL_ACTION':
        return {
          key: 'acting',
          label: t('Running the next action'),
          color: accent,
          active: true,
        };
      case 'PROOF':
        return {
          key: 'proving',
          label: t('Capturing proof of exploitation'),
          color: theme.palette.success.main,
          active: true,
        };
      case 'GAP':
        return {
          key: 'gap',
          label: t('Noting a capability gap'),
          color: theme.palette.warning.main,
          active: true,
        };
      case 'HANDOVER':
        return {
          key: 'coordinating',
          label: t('Coordinating the attack'),
          color: accent,
          active: true,
        };
      case 'AGENT_DELEGATION': {
        const who = delegationInfo?.agentName;
        const delegationColor = theme.palette.ai?.main ?? accent;
        if (delegationInfo?.waiting) {
          // A specialist consult can run for minutes with no orchestrator-side event. While
          // heartbeats keep arriving the sub-agent is demonstrably working, so render a live,
          // pulsing "Consulting X" with the elapsed clock instead of a static "Waiting for X" that
          // reads as a stalled cockpit. Only once the heartbeats stop (a genuine stall) does it
          // settle back to the calm static wait.
          if (workingByHeartbeat) {
            return {
              key: 'delegating-working',
              label: who ? `${t('Consulting')} ${who}` : t('Consulting a specialist agent'),
              color: delegationColor,
              active: true,
            };
          }
          return {
            key: 'delegating-wait',
            label: who ? `${t('Waiting for')} ${who}` : t('Waiting for a specialist agent'),
            color: delegationColor,
            active: false,
          };
        }
        return {
          key: 'delegating-done',
          label: who ? `${t('Consulted')} ${who}` : t('Specialist agent consulted'),
          color: delegationColor,
          active: true,
        };
      }
      case 'NARRATION':
        return {
          key: 'analyzing',
          label: t('Analyzing the results'),
          color: accent,
          active: true,
        };
      default:
        return {
          key: 'thinking',
          label: t('Thinking through the next move'),
          color: accent,
          active: true,
        };
    }
  })();
  // How long the cockpit has been silent, in whole ELAPSED minutes, for the truthful stall
  // caption/notice. Floor (not round): a rounded value can overstate the silence (e.g. 3.6 min ->
  // 4), and the caption must never claim more elapsed time than has actually passed.
  const stalledMinutes = Math.max(1, Math.floor(newestEventAgeMs / 60000));
  // Capture whether the caption was pulsing BEFORE the staleness backstop settles it: a stale-yet-
  // active caption is the "frozen cockpit" signal (the timeline, heartbeats included, stopped while
  // the orchestrator was mid-work), as opposed to a park/wait that is already an idle caption.
  const captionWasActive = thinkingPhase.active;
  // Staleness backstop: never keep an active caption pulsing over a timeline that has not moved in
  // minutes. When the operator is being awaited, settle to the calm "Waiting for your input".
  // Otherwise the run is NOT parked (parks are already idle captions, left untouched above) and NOT
  // waiting - it has genuinely gone silent mid-work, so tell the truth ("No updates for N min",
  // static, no spinner) instead of the old calm "Awaiting the next event" that masked a stall.
  if (captionStale && thinkingPhase.active) {
    thinkingPhase = isWaitingInput
      ? {
          key: 'idle',
          label: t('Waiting for your input'),
          color: theme.palette.text.secondary,
          active: false,
        }
      : {
          key: 'stalled',
          label: `${t('No updates for')} ${stalledMinutes} min`,
          color: theme.palette.warning.main,
          active: false,
        };
  }
  // Terminal-failure + client-side stall detection and the single run-level notice they drive, so
  // the operator is never left staring at a frozen thought or a bare "No decisions" empty state.
  const statusIsTerminalFailure = status === 'FAILED' || status === 'CANCELED';
  // Nominally active but the caption went stale while pulsing: the orchestrator was working, then
  // the timeline (heartbeats included) stopped for minutes with no park/wait. This is the auth-
  // storm / crashed-cycle window BEFORE OpenAEV's own idle watchdog (WS8) settles the run.
  const runStalled = isActive && captionStale && captionWasActive && !isWaitingInput;
  // The last tool/auth error OpenAEV recorded on the run (cleared on (re)start/resume), surfaced
  // ONLY when the run is terminal or stalled - never on a healthy, actively-progressing run, where
  // a lingering earlier transient error would be stale noise.
  const lastErrorText = sanitizeEventText(run.autonomous_run_last_error);
  const runNotice: {
    severity: 'error' | 'warning';
    title: string;
    detail?: string;
  } | null = (() => {
    if (lastErrorText && (statusIsTerminalFailure || runStalled)) {
      let title = t('The AI hit an error');
      if (status === 'CANCELED') {
        title = t('The run was canceled');
      } else if (statusIsTerminalFailure) {
        title = t('The run stopped after an error');
      }
      return {
        severity: 'error',
        title,
        detail: lastErrorText,
      };
    }
    if (runStalled) {
      // Plan-mode builds are exempt from OpenAEV's idle watchdog (WS8), so only a live run can
      // truthfully promise auto-settlement; a stalled plan build is the operator's to restart.
      const planMode = run.autonomous_run_plan_mode === true;
      const settleHint = planMode
        ? t('You can restart the plan build if it stays stalled.')
        : t('OpenAEV will settle the run automatically if it stays silent.');
      return {
        severity: 'warning',
        title: planMode ? t('The scenario planner looks stalled') : t('The orchestrator looks stalled'),
        detail: `${t('No updates for')} ${stalledMinutes} min. ${settleHint}`,
      };
    }
    return null;
  })();

  return (
    <Box
      sx={{
        position: 'fixed',
        top: topOffset,
        right: chatbotMargin,
        bottom: 0,
        width,
        zIndex: 1100,
        display: 'flex',
        flexDirection: 'column',
        backgroundColor: theme.palette.background.paper,
        borderLeft: `1px solid ${theme.palette.divider}`,
        boxShadow: `-8px 0 24px -12px ${alpha(theme.palette.common.black, 0.4)}`,
        transition: isResizing || dragging
          ? 'none'
          : theme.transitions.create(['right'], {
              easing: theme.transitions.easing.easeInOut,
              duration: theme.transitions.duration.enteringScreen,
            }),
      }}
    >
      {/* Left-edge resize handle: drag to widen the panel up to a third of the viewport. */}
      <Box
        onMouseDown={handleResizeStart}
        sx={{
          'position': 'absolute',
          'top': 0,
          'left': 0,
          'bottom': 0,
          'width': 6,
          'cursor': 'col-resize',
          'zIndex': 1,
          '&:hover': { backgroundColor: alpha(accent, 0.25) },
          'backgroundColor': dragging ? alpha(accent, 0.35) : 'transparent',
        }}
      />

      {/* Header: identity only. The run status + lifecycle controls live in the scenario/simulation
          hero (single control surface), so they are intentionally not repeated here. */}
      <Box
        sx={{
          padding: theme.spacing(1.5, 2),
          borderBottom: `1px solid ${theme.palette.divider}`,
          background: `linear-gradient(180deg, ${alpha(accent, 0.12)} 0%, ${alpha(accent, 0)} 100%)`,
        }}
      >
        <Stack sx={{
          flexDirection: 'row',
          alignItems: 'center',
          gap: 1,
        }}
        >
          <AutoAwesome fontSize="small" sx={{ color: accent }} />
          <Typography
            variant="subtitle2"
            sx={{
              margin: 0,
              flex: 1,
              fontWeight: 600,
            }}
          >
            {run.autonomous_run_plan_mode === true
              ? t('Scenario planner')
              : t('Autonomous orchestrator')}
          </Typography>
          {isActive && (thinkingPhase.active
            ? <CircularProgress size={14} sx={{ color: accent }} />
            : (
                <HourglassEmpty
                  fontSize="small"
                  sx={{ color: runStalled ? theme.palette.warning.main : theme.palette.text.secondary }}
                />
              ))}
        </Stack>
      </Box>

      {/* Reasoning stream. */}
      <Box
        ref={scrollRef}
        onScroll={(event) => {
          const node = event.currentTarget;
          stickToBottomRef.current = node.scrollHeight - node.scrollTop - node.clientHeight < STICK_TO_BOTTOM_THRESHOLD_PX;
        }}
        sx={{
          flex: 1,
          overflowY: 'auto',
          padding: theme.spacing(1, 2),
        }}
      >
        {visibleEvents.length === 0 && !isActive && !runNotice
          ? (
              <Stack sx={{
                alignItems: 'center',
                justifyContent: 'center',
                height: '100%',
                gap: 1,
                textAlign: 'center',
              }}
              >
                <AutoAwesome sx={{
                  color: alpha(accent, 0.6),
                  fontSize: 40,
                }}
                />
                <Typography variant="body2" color="text.secondary">
                  {t('No decisions recorded for this run.')}
                </Typography>
              </Stack>
            )
          : (
              <Stack sx={{
                gap: 1.25,
                paddingBlock: 1,
              }}
              >
                {streamEvents.map((event) => {
                  const color = eventAccent(event, theme);
                  const eventTitle = sanitizeEventText(event.autonomous_event_title);
                  const eventContent = sanitizeEventText(event.autonomous_event_content);
                  return (
                    <Box
                      key={event.autonomous_event_id}
                      sx={{
                        'position': 'relative',
                        'paddingLeft': 2,
                        '&::before': {
                          content: '""',
                          position: 'absolute',
                          left: 0,
                          top: 4,
                          bottom: 4,
                          width: 2,
                          borderRadius: 1,
                          backgroundColor: color,
                        },
                      }}
                    >
                      <Stack sx={{
                        flexDirection: 'row',
                        alignItems: 'center',
                        gap: 0.75,
                        flexWrap: 'wrap',
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
                            fontWeight: 600,
                            letterSpacing: '0.04em',
                            borderRadius: 0.5,
                            color,
                            backgroundColor: alpha(color, 0.12),
                          }}
                        />
                        <Box sx={{ flex: 1 }} />
                        {event.autonomous_event_created_at && (
                          <Typography variant="caption" color="text.secondary">
                            {nsdt(event.autonomous_event_created_at)}
                          </Typography>
                        )}
                      </Stack>
                      {eventTitle && (
                        <Typography
                          variant="body2"
                          sx={{
                            margin: theme.spacing(0.25, 0, 0),
                            fontWeight: 600,
                            fontSize: '0.8125rem',
                          }}
                        >
                          {eventTitle}
                        </Typography>
                      )}
                      {eventContent && (
                        <Box sx={{ marginTop: 0.25 }}>
                          <EventMarkdown content={eventContent} />
                        </Box>
                      )}
                    </Box>
                  );
                })}
                {isActive && !pendingQuestion && (
                  <ThinkingBubble
                    phase={thinkingPhase}
                    theme={theme}
                    lines={thinkingLines}
                    activitySince={activitySince}
                  />
                )}
                {/* Run-level notice: a terminal-failure/cancel error, or a client-side stall. It
                    replaces a frozen thinking bubble (a stalled run stops pulsing above) and a bare
                    "No decisions" empty state, so the operator sees WHY the run stopped (the tool /
                    auth error OpenAEV recorded) or that it has gone silent, instead of a spinner. */}
                {runNotice && (
                  <Box
                    sx={{
                      padding: theme.spacing(1.25, 1.5),
                      borderRadius: 1.5,
                      border: `1px solid ${alpha(runNotice.severity === 'error' ? theme.palette.error.main : theme.palette.warning.main, 0.4)}`,
                      backgroundColor: alpha(runNotice.severity === 'error' ? theme.palette.error.main : theme.palette.warning.main, 0.08),
                    }}
                  >
                    <Stack sx={{
                      flexDirection: 'row',
                      alignItems: 'flex-start',
                      gap: 1,
                    }}
                    >
                      {runNotice.severity === 'error'
                        ? <ErrorOutline fontSize="small" color="error" sx={{ marginTop: '2px' }} />
                        : <WarningAmber fontSize="small" color="warning" sx={{ marginTop: '2px' }} />}
                      <Box sx={{ minWidth: 0 }}>
                        <Typography variant="subtitle2" sx={{ margin: 0 }}>
                          {runNotice.title}
                        </Typography>
                        {runNotice.detail && (
                          <Typography
                            variant="body2"
                            sx={{
                              margin: theme.spacing(0.25, 0, 0),
                              color: 'text.secondary',
                              fontSize: '0.8125rem',
                              whiteSpace: 'pre-wrap',
                              wordBreak: 'break-word',
                            }}
                          >
                            {runNotice.detail}
                          </Typography>
                        )}
                      </Box>
                    </Stack>
                  </Box>
                )}
                {/* When the run parks on the operator, its question + one-click choices render HERE,
                    inline at the tail of the scrollable stream (normal conversation flow) - not as a
                    fixed band above the composer. On a short viewport a long question (e.g. a scope
                    table) plus its choices would otherwise overflow a fixed region with no way to
                    scroll to them; in-flow they scroll with the rest of the feed while the compact
                    free-text composer stays pinned at the bottom as the "type your own answer" box. */}
                {pendingQuestion && (
                  <Box
                    sx={{
                      padding: theme.spacing(1.25, 1.5),
                      borderRadius: 1.5,
                      border: `1px solid ${alpha(theme.palette.warning.main, 0.4)}`,
                      backgroundColor: alpha(theme.palette.warning.main, 0.08),
                    }}
                  >
                    <Stack sx={{
                      flexDirection: 'row',
                      alignItems: 'flex-start',
                      gap: 1,
                    }}
                    >
                      <HelpOutline fontSize="small" color="warning" sx={{ marginTop: '2px' }} />
                      <Box sx={{ minWidth: 0 }}>
                        <Typography variant="subtitle2" sx={{ margin: 0 }}>
                          {sanitizeEventText(pendingQuestion.autonomous_event_title) || t('The AI needs your input to continue')}
                        </Typography>
                        {sanitizeEventText(pendingQuestion.autonomous_event_content) && (
                          <Box sx={{ marginTop: 0.25 }}>
                            <EventMarkdown content={sanitizeEventText(pendingQuestion.autonomous_event_content)} fontSize="0.8125rem" />
                          </Box>
                        )}
                      </Box>
                    </Stack>
                    {/* One-click choices, only where the operator can actually answer (steering is
                        hidden in observe-only mode - they answer from the parent scenario). */}
                    {hasChoices && !readOnly && (
                      <RadioGroup
                        value={selectedChoice ?? ''}
                        onChange={event => setSelectedChoice(event.target.value)}
                        sx={{
                          gap: 0.75,
                          marginTop: 1,
                        }}
                      >
                        {questionChoices.map((choice) => {
                          const isSelected = selectedChoice === choice.id;
                          return (
                            <FormControlLabel
                              key={choice.id}
                              value={choice.id}
                              control={(
                                <Radio
                                  size="small"
                                  sx={{
                                    'color': accent,
                                    '&.Mui-checked': { color: accent },
                                  }}
                                />
                              )}
                              label={choice.label}
                              sx={{
                                'margin': 0,
                                'alignItems': 'flex-start',
                                'borderRadius': 1.5,
                                'border': `1px solid ${isSelected ? accent : theme.palette.divider}`,
                                'backgroundColor': isSelected ? alpha(accent, 0.08) : theme.palette.background.paper,
                                'padding': theme.spacing(0.5, 1, 0.5, 0.5),
                                'transition': theme.transitions.create(['border-color', 'background-color']),
                                '&:hover': { borderColor: alpha(accent, 0.6) },
                                '& .MuiFormControlLabel-label': {
                                  fontSize: '0.8125rem',
                                  paddingTop: '5px',
                                },
                              }}
                            />
                          );
                        })}
                      </RadioGroup>
                    )}
                  </Box>
                )}
              </Stack>
            )}
      </Box>

      {/* Steering box - chatbot-style, doubles as the answer field when waiting on input. On the
          observe-only (simulation) view it is replaced by a hint pointing to the parent scenario,
          where all control (incl. steering) lives. */}
      {readOnly && (
        <Box sx={{
          padding: theme.spacing(1.5, 2),
          borderTop: `1px solid ${theme.palette.divider}`,
        }}
        >
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
            {t('Observe-only view. Steer and control this run from the parent scenario.')}
          </Typography>
        </Box>
      )}
      {!readOnly && isActive && (
        <Box sx={{
          padding: theme.spacing(1.5, 2),
          borderTop: `1px solid ${theme.palette.divider}`,
        }}
        >
          {/* Always-present composer: the free-text answer field, pinned at the bottom like a chat
              steering box. When the run parks on a question its one-click choices live in the
              scrollable stream above; a typed answer here wins over a selected choice, and the send
              button also submits the picked choice when the field is empty. */}
          <Box
            sx={{
              'marginTop': 0,
              'display': 'flex',
              'flexDirection': 'column',
              'borderRadius': 1,
              'border': `1px solid ${theme.palette.divider}`,
              'backgroundColor': 'transparent',
              'transition': theme.transitions.create('border-color'),
              '&:hover': { borderColor: theme.palette.text.primary },
              '&:focus-within': {
                borderColor: accent,
                boxShadow: `0 0 0 1px ${accent}`,
              },
            }}
          >
            <TextField
              value={directive}
              onChange={event => setDirective(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault();
                  handleComposerSubmit();
                }
              }}
              placeholder={composerPlaceholder}
              fullWidth
              multiline
              minRows={hasChoices ? 2 : 3}
              maxRows={8}
              variant="standard"
              InputProps={{ disableUnderline: true }}
              sx={{
                '& .MuiInputBase-root': {
                  alignItems: 'flex-start',
                  padding: theme.spacing(1.25, 1.5, 0.5),
                  fontSize: '0.875rem',
                },
              }}
            />
            <Stack sx={{
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: theme.spacing(0, 0.75, 0.75, 1.5),
            }}
            >
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{
                  fontSize: '0.6875rem',
                  opacity: 0.7,
                }}
              >
                {t('Enter to send - Shift+Enter for a new line')}
              </Typography>
              <IconButton
                size="small"
                onClick={handleComposerSubmit}
                disabled={!canSubmitAnswer}
                aria-label={t('Send')}
                sx={{
                  'backgroundColor': accent,
                  'color': theme.palette.ai?.contrastText ?? theme.palette.primary.contrastText,
                  '&:hover': { backgroundColor: theme.palette.ai?.dark ?? theme.palette.primary.dark },
                  '&.Mui-disabled': {
                    backgroundColor: alpha(accent, 0.3),
                    color: alpha('#ffffff', 0.5),
                  },
                }}
              >
                <SendOutlined fontSize="small" />
              </IconButton>
            </Stack>
          </Box>
        </Box>
      )}
    </Box>
  );
};

export default AutonomousReasoningPanel;
