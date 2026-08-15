import { describe, expect, it } from 'vitest';

import { type AutonomousEvent, type AutonomousEventType } from '../../../../actions/autonomous/autonomous-types';
import { isHeartbeatEvent, isLiveActivityEvent } from '../../../../admin/components/autonomous/autonomousEventVisuals';

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
