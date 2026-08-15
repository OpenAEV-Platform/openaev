import { describe, expect, it } from 'vitest';

import { type AutonomousEvent, type AutonomousEventType } from '../../../../actions/autonomous/autonomous-types';
import {
  collapseThinkingSteps,
  isHeartbeatEvent,
  isLiveActivityEvent,
  resolveLiveCaption,
  resolveLiveLabel,
  type ThinkingStep,
} from '../../../../admin/components/autonomous/autonomousEventVisuals';

// Minimal AutonomousEvent factory: the classification predicates only read `type` + `data`, but the
// interface requires id/run_id/sequence, so default those to keep the fixtures readable.
let sequence = 0;
const makeEvent = (
  type: AutonomousEventType,
  data?: string | null,
): AutonomousEvent => ({
  autonomous_event_id: `evt-${(sequence += 1)}`,
  autonomous_event_run_id: 'run-1',
  autonomous_event_sequence: sequence,
  autonomous_event_type: type,
  autonomous_event_data: data ?? null,
});

// Richer factory for the thinking-window collapse tests: those read the event content + created_at
// (the caption text and its timestamp), which the predicate factory above does not set.
const makeStreamEvent = (
  type: AutonomousEventType,
  content: string,
  createdAt?: string,
): AutonomousEvent => ({
  autonomous_event_id: `evt-${(sequence += 1)}`,
  autonomous_event_run_id: 'run-1',
  autonomous_event_sequence: sequence,
  autonomous_event_type: type,
  autonomous_event_content: content,
  autonomous_event_created_at: createdAt,
});

describe('isLiveActivityEvent', () => {
  it('is true for a NARRATION whose data JSON has live === true (compact serialization)', () => {
    expect(isLiveActivityEvent(makeEvent('NARRATION', '{"live":true,"iteration":3}'))).toBe(true);
  });

  it('is true regardless of key spacing (Python json.dumps style)', () => {
    expect(isLiveActivityEvent(makeEvent('NARRATION', '{"live": true, "iteration": 3}'))).toBe(true);
  });

  it('is false when live is present but not the boolean true', () => {
    expect(isLiveActivityEvent(makeEvent('NARRATION', '{"live":false}'))).toBe(false);
    expect(isLiveActivityEvent(makeEvent('NARRATION', '{"live":"yes"}'))).toBe(false);
  });

  it('is false for a genuine NARRATION with no live flag (never hides real narration)', () => {
    expect(isLiveActivityEvent(makeEvent('NARRATION', '{"iteration":3}'))).toBe(false);
    expect(isLiveActivityEvent(makeEvent('NARRATION', 'Compromised the domain controller.'))).toBe(false);
  });

  it('is false when the substring "live" is not present as a JSON key', () => {
    // Guards the cheap pre-check: a value that merely contains the letters "live" must not match.
    expect(isLiveActivityEvent(makeEvent('NARRATION', '{"liveness":"high"}'))).toBe(false);
    expect(isLiveActivityEvent(makeEvent('NARRATION', '{"note":"went live"}'))).toBe(false);
  });

  it('is false for the live flag on a non-NARRATION event type', () => {
    expect(isLiveActivityEvent(makeEvent('DECISION', '{"live":true}'))).toBe(false);
    expect(isLiveActivityEvent(makeEvent('STATUS', '{"live":true}'))).toBe(false);
  });

  it('is false when data is missing or malformed JSON', () => {
    expect(isLiveActivityEvent(makeEvent('NARRATION', null))).toBe(false);
    expect(isLiveActivityEvent(makeEvent('NARRATION', '{"live":true'))).toBe(false);
  });

  it('is false for an undefined event', () => {
    expect(isLiveActivityEvent(undefined)).toBe(false);
  });

  it('returns a stable result across repeated calls on the same event (cached parse)', () => {
    const event = makeEvent('NARRATION', '{"live":true}');
    expect(isLiveActivityEvent(event)).toBe(true);
    expect(isLiveActivityEvent(event)).toBe(true);
  });
});

describe('isHeartbeatEvent', () => {
  it('is true for a STATUS whose data JSON has heartbeat === true', () => {
    expect(isHeartbeatEvent(makeEvent('STATUS', '{"heartbeat":true}'))).toBe(true);
  });

  it('is false when heartbeat is not the boolean true', () => {
    expect(isHeartbeatEvent(makeEvent('STATUS', '{"heartbeat":false}'))).toBe(false);
    expect(isHeartbeatEvent(makeEvent('STATUS', '{"phase":"engaged"}'))).toBe(false);
  });

  it('is false for the heartbeat flag on a non-STATUS event type', () => {
    expect(isHeartbeatEvent(makeEvent('NARRATION', '{"heartbeat":true}'))).toBe(false);
  });

  it('is false when data is missing or malformed JSON', () => {
    expect(isHeartbeatEvent(makeEvent('STATUS', null))).toBe(false);
    expect(isHeartbeatEvent(makeEvent('STATUS', '{"heartbeat":true'))).toBe(false);
  });

  it('is false for an undefined event', () => {
    expect(isHeartbeatEvent(undefined)).toBe(false);
  });
});

describe('isHeartbeatEvent and isLiveActivityEvent are mutually exclusive', () => {
  it('a live NARRATION is not a heartbeat, and a heartbeat STATUS is not live-activity', () => {
    const liveNarration = makeEvent('NARRATION', '{"live":true,"iteration":7}');
    const heartbeat = makeEvent('STATUS', '{"heartbeat":true}');
    expect(isLiveActivityEvent(liveNarration)).toBe(true);
    expect(isHeartbeatEvent(liveNarration)).toBe(false);
    expect(isHeartbeatEvent(heartbeat)).toBe(true);
    expect(isLiveActivityEvent(heartbeat)).toBe(false);
  });
});

describe('collapseThinkingSteps', () => {
  it('collapses a consecutive same-caption burst into ONE step (no wall of identical lines)', () => {
    // The confirmed bug: a same-caption burst (e.g. the arsenal build) rendered N identical glowing
    // lines. Collapse must fold them into a single step carrying the repeat count.
    const burst = Array.from({ length: 12 }, () => makeStreamEvent('NARRATION', 'Searching arsenal for contracts'));
    const steps = collapseThinkingSteps(burst);
    expect(steps).toHaveLength(1);
    expect(steps[0].text).toBe('Searching arsenal for contracts');
    expect(steps[0].count).toBe(12);
  });

  it('keeps the FIRST timestamp of the run as `since` so the per-caption clock never resets mid-burst', () => {
    const steps = collapseThinkingSteps([
      makeStreamEvent('NARRATION', 'Searching arsenal for contracts', '2026-01-01T00:00:00.000Z'),
      makeStreamEvent('NARRATION', 'Searching arsenal for contracts', '2026-01-01T00:00:05.000Z'),
      makeStreamEvent('NARRATION', 'Searching arsenal for contracts', '2026-01-01T00:00:10.000Z'),
    ]);
    expect(steps).toHaveLength(1);
    expect(steps[0].count).toBe(3);
    // First event's timestamp - NOT the latest - so `now - since` measures the whole burst monotonically.
    expect(steps[0].since).toBe(new Date('2026-01-01T00:00:00.000Z').getTime());
  });

  it('starts a fresh step when the caption changes, and the last step is the live one', () => {
    const steps = collapseThinkingSteps([
      makeStreamEvent('NARRATION', 'Searching arsenal for contracts'),
      makeStreamEvent('NARRATION', 'Searching arsenal for contracts'),
      makeStreamEvent('DECISION', 'Authoring the attack path'),
    ]);
    expect(steps.map(s => s.text)).toEqual([
      'Searching arsenal for contracts',
      'Authoring the attack path',
    ]);
    expect(steps[0].count).toBe(2);
    expect(steps[1].count).toBe(1);
    // The last step is the live one (the caption the orchestrator is narrating right now).
    expect(steps[steps.length - 1].text).toBe('Authoring the attack path');
  });

  it('treats a caption that recurs after a different one as a NEW step (only adjacent dupes merge)', () => {
    const steps = collapseThinkingSteps([
      makeStreamEvent('NARRATION', 'Searching arsenal for contracts'),
      makeStreamEvent('NARRATION', 'Searching arsenal for contracts'),
      makeStreamEvent('NARRATION', 'Resolving contract capabilities'),
      makeStreamEvent('NARRATION', 'Searching arsenal for contracts'),
    ]);
    expect(steps.map(s => ({
      text: s.text,
      count: s.count,
    }))).toEqual([
      {
        text: 'Searching arsenal for contracts',
        count: 2,
      },
      {
        text: 'Resolving contract capabilities',
        count: 1,
      },
      {
        text: 'Searching arsenal for contracts',
        count: 1,
      },
    ]);
  });

  it('dedupes on the RENDERED caption so markdown-only differences still collapse', () => {
    // stripMarkdown('**Searching arsenal for contracts**') === 'Searching arsenal for contracts'.
    const steps = collapseThinkingSteps([
      makeStreamEvent('NARRATION', '**Searching arsenal for contracts**'),
      makeStreamEvent('NARRATION', 'Searching arsenal for contracts'),
    ]);
    expect(steps).toHaveLength(1);
    expect(steps[0].count).toBe(2);
  });

  it('ignores non-stream event types and empty captions', () => {
    const steps = collapseThinkingSteps([
      makeStreamEvent('STATUS', 'Run engaged'),
      makeStreamEvent('QUESTION', 'Which subnet is in scope?'),
      makeStreamEvent('NARRATION', '   '),
      makeStreamEvent('TOOL_ACTION', 'Launching the payload'),
    ]);
    expect(steps.map(s => s.text)).toEqual(['Launching the payload']);
  });

  it('falls back to the title when there is no content', () => {
    const titleOnly: AutonomousEvent = {
      autonomous_event_id: `evt-${(sequence += 1)}`,
      autonomous_event_run_id: 'run-1',
      autonomous_event_sequence: sequence,
      autonomous_event_type: 'DECISION',
      autonomous_event_title: 'Deciding the next move',
    };
    const steps = collapseThinkingSteps([titleOnly]);
    expect(steps).toEqual([{
      text: 'Deciding the next move',
      count: 1,
      since: null,
      sequence: titleOnly.autonomous_event_sequence,
    }]);
  });

  it('advances `sequence` to the NEWEST folded event so staleness is measured from the latest occurrence', () => {
    // The per-step `sequence` drives resolveLiveCaption: it must track the LAST folded event, not
    // the first, so a caption that is still recurring stays newer than an older cycle boundary.
    const first = makeStreamEvent('NARRATION', 'Searching arsenal for contracts');
    const second = makeStreamEvent('NARRATION', 'Searching arsenal for contracts');
    const third = makeStreamEvent('NARRATION', 'Searching arsenal for contracts');
    const steps = collapseThinkingSteps([first, second, third]);
    expect(steps).toHaveLength(1);
    // Newest folded event's sequence (not the first), while `since` stays anchored to the first.
    expect(steps[0].sequence).toBe(third.autonomous_event_sequence);
    expect(steps[0].since).toBe(null);
  });

  it('keeps at most `limit` steps, always retaining the live (last) one', () => {
    const events = Array.from({ length: 10 }, (_, i) => makeStreamEvent('NARRATION', `caption ${i}`));
    const steps = collapseThinkingSteps(events, 8);
    expect(steps).toHaveLength(8);
    // Oldest distinct captions drop off the top; the newest is retained as the live step.
    expect(steps[0].text).toBe('caption 2');
    expect(steps[steps.length - 1].text).toBe('caption 9');
  });
});

describe('resolveLiveLabel', () => {
  it('shows the live caption for a generic working phase (label follows the live stream)', () => {
    expect(resolveLiveLabel('engaging', true, 'Getting to work', 'Searching arsenal for contracts'))
      .toBe('Searching arsenal for contracts');
    expect(resolveLiveLabel('analyzing', true, 'Analyzing the results', 'Correlating the findings'))
      .toBe('Correlating the findings');
    expect(resolveLiveLabel('thinking', true, 'Thinking through the next move', 'Mapping the perimeter'))
      .toBe('Mapping the perimeter');
  });

  it('keeps the specific label for a meaningful phase even when a live caption exists', () => {
    expect(resolveLiveLabel('deciding', true, 'Deciding the next move', 'Searching arsenal for contracts'))
      .toBe('Deciding the next move');
    expect(resolveLiveLabel('delegating-working', true, 'Consulting the payload specialist', 'noise'))
      .toBe('Consulting the payload specialist');
    expect(resolveLiveLabel('waiting_input', false, 'Waiting for your input', 'noise'))
      .toBe('Waiting for your input');
  });

  it('keeps the fallback label when inactive or when there is no live caption', () => {
    expect(resolveLiveLabel('engaging', false, 'Getting to work', 'Searching arsenal for contracts'))
      .toBe('Getting to work');
    expect(resolveLiveLabel('engaging', true, 'Getting to work', null)).toBe('Getting to work');
    expect(resolveLiveLabel('engaging', true, 'Getting to work', '')).toBe('Getting to work');
  });
});

describe('resolveLiveCaption', () => {
  const step = (text: string, sequence: number): ThinkingStep => ({
    text,
    count: 1,
    since: null,
    sequence,
  });

  it('returns the last step caption while it is at least as new as the newest non-pulse event', () => {
    const steps = [step('Searching arsenal for contracts', 10), step('Authoring the attack path', 14)];
    // The live step (seq 14) is the newest non-pulse event (14): current -> live caption.
    expect(resolveLiveCaption(steps, 14)).toBe('Authoring the attack path');
    // A fresh live pulse (step seq 15) newer than the newest non-pulse boundary (14) is still live.
    expect(resolveLiveCaption([step('Mapping the perimeter', 15)], 14)).toBe('Mapping the perimeter');
  });

  it('returns null when a newer non-pulse boundary has superseded the last step (resume regression guard)', () => {
    // The exact resume case: the last thinking step is pre-pause (seq 13); on resume the backend
    // appends a fresh "engaged" STATUS (a non-pulse event, seq 14) that never becomes a step. The
    // last step is now stale history, so it must NOT be exposed as the live caption - otherwise the
    // bold label would resurrect the pre-pause caption and the per-caption timer would span the
    // paused interval, regressing the resume-safe behavior #7449 established.
    const steps = [step('Searching arsenal for contracts', 13)];
    expect(resolveLiveCaption(steps, 14)).toBeNull();
  });

  it('treats the last step as live when there is no non-pulse boundary at all', () => {
    // All-pulse stream (only live-activity narrations / heartbeats): nothing can supersede the step.
    const steps = [step('Searching arsenal for contracts', 5)];
    expect(resolveLiveCaption(steps, null)).toBe('Searching arsenal for contracts');
    expect(resolveLiveCaption(steps, undefined)).toBe('Searching arsenal for contracts');
  });

  it('returns null when there are no steps', () => {
    expect(resolveLiveCaption([], 10)).toBeNull();
    expect(resolveLiveCaption([], null)).toBeNull();
  });
});
