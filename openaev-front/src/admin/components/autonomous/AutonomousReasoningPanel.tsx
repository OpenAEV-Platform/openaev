import {
  AutoAwesome,
  HelpOutline,
  HourglassEmpty,
  SendOutlined,
} from '@mui/icons-material';
import { Box, Chip, CircularProgress, FormControlLabel, IconButton, Radio, RadioGroup, Stack, TextField, Typography } from '@mui/material';
import type { Theme } from '@mui/material/styles';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, useCallback, useEffect, useRef, useState } from 'react';

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
import { eventAccent, eventIcon, eventTypeLabel, sanitizeEventText } from './autonomousEventVisuals';
import { AUTONOMOUS_PANEL_WIDTH } from './useAutonomousPanelWidth';

const ACTIVE_STATUSES: AutonomousRunStatus[] = ['RUNNING', 'WAITING_INPUT'];
const POLL_INTERVAL_MS = 3000;

// Cap the proposed one-click choices so the callout stays scannable: at most this many radio options
// are ever shown, and the always-present free-text composer below is the escape hatch for anything
// the operator would rather type.
const MAX_QUESTION_CHOICES = 3;

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
const ThinkingBubble: FunctionComponent<{
  phase: ThinkingPhase;
  theme: Theme;
  lines: string[];
}> = ({
  phase,
  theme,
  lines,
}) => {
  const accent = phase.color;
  const active = phase.active;
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
  const [directive, setDirective] = useState('');
  // Which proposed one-click choice the operator picked (null = none; they can still type freely in
  // the always-visible composer, which takes precedence over a selected choice).
  const [selectedChoice, setSelectedChoice] = useState<string | null>(null);
  // The question the operator just answered - suppressed immediately (optimistic) so the callout
  // does not linger and hog space while we wait for the status poll to flip the run to RUNNING.
  const [answeredQuestionId, setAnsweredQuestionId] = useState<string | null>(null);
  const cursorRef = useRef(0);
  const scrollRef = useRef<HTMLDivElement | null>(null);
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
  useEffect(() => {
    setRun(initialRunRef.current);
  }, [initialRun.autonomous_run_id, initialRun.autonomous_run_simulation_id]);

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

  // Keep the stream pinned to the latest decision as it grows.
  useEffect(() => {
    const node = scrollRef.current;
    if (node) {
      node.scrollTop = node.scrollHeight;
    }
  }, [events.length]);

  const isWaitingInput = status === 'WAITING_INPUT';
  const latestQuestion = isWaitingInput
    ? [...events].reverse().find(e => e.autonomous_event_type === 'QUESTION')
    : undefined;
  // Hide the question the moment it is answered, without waiting for the 3s status poll.
  const pendingQuestion = latestQuestion && latestQuestion.autonomous_event_id !== answeredQuestionId
    ? latestQuestion
    : undefined;
  const questionChoices = (pendingQuestion ? parseQuestionChoices(pendingQuestion.autonomous_event_data) : [])
    .slice(0, MAX_QUESTION_CHOICES);
  const hasChoices = questionChoices.length > 0;

  let composerPlaceholder = t('Steer the AI live (e.g. focus on the finance subnet, avoid host X, try Kerberoasting)');
  if (hasChoices) {
    composerPlaceholder = t('Or type your own answer');
  } else if (isWaitingInput) {
    composerPlaceholder = t('Answer the AI (e.g. the web apps in scope are app-prod-01 and app-prod-02)');
  }

  // Reset the choice selection whenever a new question arrives.
  useEffect(() => {
    setSelectedChoice(null);
    setDirective('');
  }, [pendingQuestion?.autonomous_event_id]);

  const sendDirective = useCallback((content: string) => {
    const trimmed = content.trim();
    if (trimmed.length === 0 || !isActive) {
      return;
    }
    setDirective('');
    setSelectedChoice(null);
    // Optimistically dismiss the current question so its callout stops occupying space right away.
    if (pendingQuestion) {
      setAnsweredQuestionId(pendingQuestion.autonomous_event_id);
    }
    addAutonomousDirective(runId, trimmed).then(() => pollTimeline()).catch(() => {});
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
  const thinkingLines = events
    .filter(e => (['NARRATION', 'DECISION', 'TOOL_ACTION'] as const).includes(
      e.autonomous_event_type as 'NARRATION' | 'DECISION' | 'TOOL_ACTION',
    ))
    .map(e => sanitizeEventText(e.autonomous_event_content ?? e.autonomous_event_title))
    .filter((line): line is string => Boolean(line))
    .slice(-8);

  // Current orchestrator phase for the thinking window. Derived from status + the latest activity
  // event rather than the raw run status, so the caption narrates what the run is doing and animates
  // as it moves (deciding -> acting -> analyzing ...). Crucially, once the operator answers we flip
  // to "Processing your answer" immediately -- the backend status stays WAITING_INPUT until the next
  // 3s poll, so keying off status alone would freeze on "Waiting for your input".
  const lastActivityType = [...events].reverse().find(
    e => (['DECISION', 'TOOL_ACTION', 'PROOF', 'GAP', 'HANDOVER', 'NARRATION'] as const).includes(
      e.autonomous_event_type as 'DECISION' | 'TOOL_ACTION' | 'PROOF' | 'GAP' | 'HANDOVER' | 'NARRATION',
    ),
  )?.autonomous_event_type;
  // A STATUS event is the orchestrator's end-of-cycle "settled state" marker (e.g. "Phishing lure
  // in flight - awaiting human interaction"): the run stays RUNNING but is now PARKED, idle until a
  // human-timescale event or the next cycle. So when the newest event is a STATUS, the orchestrator
  // is NOT computing - the thinking window must stop pulsing and settle into a calm wait. As soon
  // as the next cycle emits an activity event, the newest event is no longer a STATUS and the
  // window animates again.
  const newestEvent = events.length > 0 ? events[events.length - 1] : undefined;
  const parkedOnStatus = newestEvent?.autonomous_event_type === 'STATUS';
  const thinkingPhase: ThinkingPhase = (() => {
    if (isWaitingInput && pendingQuestion) {
      // Genuinely idle on the operator: static wait, not a pulsing "still working" animation.
      return {
        key: 'waiting_input',
        label: t('Waiting for your input'),
        color: theme.palette.warning.main,
        active: false,
      };
    }
    if (isWaitingInput) {
      // The operator answered; the backend is still WAITING_INPUT until the next poll flips it, but
      // the run is genuinely resuming - so animate.
      return {
        key: 'resuming',
        label: t('Processing your answer'),
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
            {t('Autonomous orchestrator')}
          </Typography>
          {isActive && (thinkingPhase.active
            ? <CircularProgress size={14} sx={{ color: accent }} />
            : <HourglassEmpty fontSize="small" sx={{ color: theme.palette.text.secondary }} />)}
        </Stack>
      </Box>

      {/* Reasoning stream. */}
      <Box
        ref={scrollRef}
        sx={{
          flex: 1,
          overflowY: 'auto',
          padding: theme.spacing(1, 2),
        }}
      >
        {events.length === 0 && !isActive
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
                {events.map((event) => {
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
                        <Typography
                          variant="caption"
                          color="text.secondary"
                          sx={{
                            display: 'block',
                            whiteSpace: 'pre-wrap',
                          }}
                        >
                          {eventContent}
                        </Typography>
                      )}
                    </Box>
                  );
                })}
                {isActive && (
                  <ThinkingBubble
                    phase={thinkingPhase}
                    theme={theme}
                    lines={thinkingLines}
                  />
                )}
              </Stack>
            )}
      </Box>

      {/* Question callout when the run is parked on the operator. Disappears the moment the
          operator answers (optimistic), so it never lingers and hogs space below. */}
      {pendingQuestion && (
        <Box
          sx={{
            padding: theme.spacing(1.25, 2),
            borderTop: `1px solid ${alpha(theme.palette.warning.main, 0.4)}`,
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
            <Box>
              <Typography variant="subtitle2" sx={{ margin: 0 }}>
                {pendingQuestion.autonomous_event_title ?? t('The AI needs your input to continue')}
              </Typography>
              {pendingQuestion.autonomous_event_content && (
                <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-wrap' }}>
                  {pendingQuestion.autonomous_event_content}
                </Typography>
              )}
            </Box>
          </Stack>
        </Box>
      )}

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
          {hasChoices && (
            <RadioGroup
              value={selectedChoice ?? ''}
              onChange={event => setSelectedChoice(event.target.value)}
              sx={{ gap: 0.75 }}
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
                      'backgroundColor': isSelected ? alpha(accent, 0.08) : 'transparent',
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

          {/* Always-present composer: the free-text answer field sits directly next to the send
              button, so typing is a first-class action (not a hidden "custom answer" mode). A typed
              answer wins over a selected choice; the button also sends the picked choice when the
              field is empty. */}
          <Box
            sx={{
              'marginTop': hasChoices ? 1 : 0,
              'display': 'flex',
              'flexDirection': 'column',
              'borderRadius': 2,
              'border': `1px solid ${theme.palette.divider}`,
              'backgroundColor': alpha(theme.palette.action.hover, 0.4),
              'transition': theme.transitions.create(['border-color', 'background-color']),
              '&:focus-within': {
                borderColor: accent,
                backgroundColor: alpha(theme.palette.action.hover, 0.6),
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
