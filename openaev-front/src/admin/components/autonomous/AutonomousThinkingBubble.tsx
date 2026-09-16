import { HourglassEmpty } from '@mui/icons-material';
import { Box, Stack, Typography } from '@mui/material';
import type { Theme } from '@mui/material/styles';
import { alpha } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useRef, useState } from 'react';

import { type ThinkingPhase, type ThinkingStep } from './autonomousEventVisuals';

// Turn a millisecond gap into a compact "still working" clock: "12s", "1m 20s". Kept short so it
// sits inline next to the phase caption / on the live step without wrapping.
const formatElapsed = (ms: number): string => {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  if (totalSeconds < 60) return `${totalSeconds}s`;
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}m ${seconds.toString().padStart(2, '0')}s`;
};

// Tail-of-stream status window. While the orchestrator is actively working (active phase) it shows
// three pulsing dots plus its most recent reasoning, faintly shimmering, so the panel feels alive
// between activity events (mirrors the XTM One scrolling thinking window). When the run is idle -
// parked on a status awaiting a human-timescale event, or waiting on the operator - it settles into
// a STATIC hourglass + calm caption with no pulsing and no thought echo, so a parked run stops
// looking like it is still thinking. The label + colour reflect the CURRENT phase and animate on
// every phase change.
//
// The body is a list of COLLAPSED steps (see collapseThinkingSteps): consecutive identical captions
// are folded into a single line so a same-caption burst is not a wall of identical glowing lines.
// The live (last) step keeps visibly moving even when its caption does not change, via a monotonic
// per-caption elapsed clock (ticked once a second) and a repeat count - so the operator always sees
// motion, never a frozen-looking window.
const ThinkingBubble: FunctionComponent<{
  phase: ThinkingPhase;
  theme: Theme;
  steps: ThinkingStep[];
  /** Timestamp of the most recent NON-PULSE activity, so the header can tick a live "working for Ns"
   *  clock anchored to the current move (not the per-iteration pulse cadence). This is what turns a
   *  silent stretch (e.g. the orchestrator grinding through tool retries with no new narration) from
   *  a frozen caption into a visibly advancing counter. */
  activitySince?: string | number | null;
  /** Whether the LAST step is genuinely the live caption (default true). The panel sets this false
   *  when a newer non-pulse boundary (e.g. an "engaged" STATUS the backend appends to the retained
   *  timeline on a resume) has superseded the last thinking step: it is then stale history, so it
   *  must render dimmed with NO shimmer and NO per-caption timer, otherwise its clock would span the
   *  whole paused interval. When false no step is treated as live. */
  lastStepLive?: boolean;
}> = ({
  phase,
  theme,
  steps,
  activitySince,
  lastStepLive = true,
}) => {
  const accent = phase.color;
  const active = phase.active;
  // Tick a single 1s clock ONLY while actively working, so every elapsed counter (the header
  // "working for Ns" and the live step's per-caption timer) advances live off one interval, and the
  // interval is torn down the moment the run parks/waits. Keyed on `active` alone: the anchors are
  // stable timestamps, so `now - anchor` is monotonic and never needs a reset on a phase/caption
  // change (which would make a counter jump backwards - the reported "clock resets every few
  // seconds" symptom).
  const [now, setNow] = useState<number>(() => Date.now());
  useEffect(() => {
    if (!active) return undefined;
    setNow(Date.now());
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, [active]);
  const sinceMs = activitySince != null ? new Date(activitySince).getTime() : Number.NaN;
  const elapsedLabel = active && Number.isFinite(sinceMs) ? formatElapsed(now - sinceMs) : null;
  // A parked/waiting phase never streams the live thought echo (there is no live thought - the run
  // is idle), and its dots do not pulse.
  const showLatest = active && steps.length > 0;
  // Which step (if any) is the LIVE one - the only line that shimmers and carries the per-caption
  // timer. Normally the last step, but the panel demotes it via lastStepLive=false when a newer
  // boundary has superseded it (a resume leaves the last thinking step as stale history); then no
  // step is live and every line renders as calm, dimmed history.
  const liveIndex = lastStepLive ? steps.length - 1 : -1;
  // Keep the window pinned to the bottom as the reasoning grows, so it visibly scrolls like the
  // XTM One thinking window: each new step is appended at the bottom and older steps scroll up out
  // of view under the fade mask. Keyed on the step SIGNATURE (text + count), not on `now`, so a
  // per-second timer tick never forces a scroll - only a genuinely new/updated step does.
  const textRef = useRef<HTMLDivElement | null>(null);
  const stepsSignature = steps.map(step => `${step.text}#${step.count}`).join('|');
  useEffect(() => {
    const node = textRef.current;
    if (node) {
      node.scrollTop = node.scrollHeight;
    }
  }, [stepsSignature]);

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
            {`\u00b7 ${elapsedLabel}`}
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
          {steps.map((step, index) => {
            const isLive = index === liveIndex;
            // The live step keeps moving even on a static caption: a per-caption elapsed clock that
            // ticks up from the caption-run start, plus the repeat count. Older (and stale) steps are
            // finalized history - dimmed, static, no timer.
            const liveElapsed = isLive && active && step.since != null ? formatElapsed(now - step.since) : null;
            return (
              // Stable key from the step identity (first-event timestamp + caption), NOT the array
              // index: the 8-step window drops the oldest entry as the stream grows, which shifts
              // every index and would remount all rows (resetting timers/animations). since + text are
              // fixed for a step's lifetime (they do not change as duplicates fold in), so only a
              // genuinely new step mounts.
              <Typography
                key={`${step.since ?? 'na'}-${step.text.slice(0, 24)}`}
                data-testid="autonomous-thinking-step"
                variant="caption"
                sx={{
                  display: 'block',
                  fontStyle: 'italic',
                  lineHeight: 1.5,
                  color: alpha(theme.palette.text.secondary, isLive ? 0.95 : 0.5),
                  whiteSpace: 'pre-wrap',
                  // Only the live line shimmers - the "live" thought; older lines settle, dimmed.
                  animation: isLive ? 'aevThinkingShimmer 2.4s ease-in-out infinite' : undefined,
                }}
              >
                {step.text}
                {step.count > 1 && (
                  <Box
                    component="span"
                    sx={{
                      marginLeft: 0.5,
                      fontStyle: 'normal',
                      opacity: 0.6,
                      fontVariantNumeric: 'tabular-nums',
                    }}
                  >
                    {`\u00d7${step.count}`}
                  </Box>
                )}
                {liveElapsed && (
                  <Box
                    component="span"
                    sx={{
                      marginLeft: 0.5,
                      fontStyle: 'normal',
                      opacity: 0.7,
                      fontVariantNumeric: 'tabular-nums',
                    }}
                  >
                    {`\u00b7 ${liveElapsed}`}
                  </Box>
                )}
              </Typography>
            );
          })}
        </Box>
      )}
    </Box>
  );
};

export default ThinkingBubble;
