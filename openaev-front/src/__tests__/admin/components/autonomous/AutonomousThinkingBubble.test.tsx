import { createTheme, ThemeProvider } from '@mui/material/styles';
import { act, cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type ThinkingPhase, type ThinkingStep } from '../../../../admin/components/autonomous/autonomousEventVisuals';
import ThinkingBubble from '../../../../admin/components/autonomous/AutonomousThinkingBubble';

// The thinking window renders the orchestrator's live reasoning off `theme` PROPS (not context), but
// MUI components under it still resolve styling from context - so wrap AND pass the same theme.
const theme = createTheme();

const renderBubble = (
  phase: ThinkingPhase,
  steps: ThinkingStep[],
  activitySince?: string | number | null,
  lastStepLive = true,
): void => {
  render(
    <ThemeProvider theme={theme}>
      <ThinkingBubble
        phase={phase}
        theme={theme}
        steps={steps}
        activitySince={activitySince}
        lastStepLive={lastStepLive}
      />
    </ThemeProvider>,
  );
};

const workingPhase = (label: string, key = 'engaging'): ThinkingPhase => ({
  key,
  label,
  color: '#00bcd4',
  active: true,
});

// Middot / multiplication sign the renderer uses inline (kept as escapes so the test source stays
// ASCII and matches the component's `\u00b7` / `\u00d7` output exactly).
const REPEAT = (n: number) => `\u00d7${n}`;

describe('ThinkingBubble', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-01-01T00:00:00.000Z'));
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('collapses a same-caption burst to ONE line that advances a monotonic timer (never a frozen wall)', () => {
    const base = Date.now();
    // A 12-iteration same-caption burst arrives pre-collapsed as a single live step that started 5s ago.
    const steps: ThinkingStep[] = [
      {
        text: 'Searching arsenal for contracts',
        count: 12,
        since: base - 5000,
        sequence: 12,
      },
    ];
    renderBubble(workingPhase('Searching arsenal for contracts'), steps, base);

    // Exactly one rendered line despite 12 collapsed events - not a wall of identical glowing lines.
    expect(screen.getAllByTestId('autonomous-thinking-step')).toHaveLength(1);
    const line = () => screen.getByTestId('autonomous-thinking-step');
    expect(line().textContent).toContain('Searching arsenal for contracts');
    // The repeat count and the per-caption elapsed clock are both shown (visible motion sources).
    expect(line().textContent).toContain(REPEAT(12));
    expect(line().textContent).toContain('5s');

    // Advancing the clock ticks the timer UP monotonically, in place - still one line, no reset.
    act(() => {
      vi.advanceTimersByTime(4000);
    });
    expect(screen.getAllByTestId('autonomous-thinking-step')).toHaveLength(1);
    expect(line().textContent).toContain('9s');
    expect(line().textContent).not.toContain('5s');

    act(() => {
      vi.advanceTimersByTime(60000);
    });
    // Crosses the minute boundary and keeps climbing (69s -> "1m 09s"): strictly monotonic.
    expect(line().textContent).toContain('1m 09s');
  });

  it('anchors the bold label to the caption the panel passes in (label follows the live caption)', () => {
    const base = Date.now();
    renderBubble(
      workingPhase('Searching arsenal for contracts'),
      [{
        text: 'Searching arsenal for contracts',
        count: 1,
        since: base,
        sequence: 1,
      }],
      base,
    );
    // The header renders the caption as the bold phase label - not a generic "Analyzing the results".
    expect(screen.getAllByText('Searching arsenal for contracts').length).toBeGreaterThanOrEqual(1);
    expect(screen.queryByText('Analyzing the results')).toBeNull();
  });

  it('starts a fresh live line on a caption change; only the live (last) line carries the timer', () => {
    const base = Date.now();
    const steps: ThinkingStep[] = [
      {
        text: 'Searching arsenal for contracts',
        count: 4,
        since: base - 20000,
        sequence: 4,
      },
      {
        text: 'Authoring the attack path',
        count: 1,
        since: base - 3000,
        sequence: 5,
      },
    ];
    renderBubble(workingPhase('Authoring the attack path'), steps, base);

    const lines = screen.getAllByTestId('autonomous-thinking-step');
    expect(lines).toHaveLength(2);
    // The finalized (older) step keeps its repeat count but shows NO ticking timer.
    expect(lines[0].textContent).toContain('Searching arsenal for contracts');
    expect(lines[0].textContent).toContain(REPEAT(4));
    expect(lines[0].textContent).not.toMatch(/\d+s/);
    // The live (last) step shows the per-caption timer and no count (count === 1).
    expect(lines[1].textContent).toContain('Authoring the attack path');
    expect(lines[1].textContent).toContain('3s');
    expect(lines[1].textContent).not.toContain(REPEAT(1));

    act(() => {
      vi.advanceTimersByTime(2000);
    });
    const advanced = screen.getAllByTestId('autonomous-thinking-step');
    // Only the live line's clock moves; the finalized line stays static.
    expect(advanced[1].textContent).toContain('5s');
    expect(advanced[0].textContent).not.toMatch(/\d+s/);
  });

  it('shows no thought echo when the phase is idle/parked (calm wait, not a working stream)', () => {
    const base = Date.now();
    renderBubble(
      {
        key: 'parked',
        label: 'Awaiting the next event',
        color: '#888888',
        active: false,
      },
      [{
        text: 'Searching arsenal for contracts',
        count: 3,
        since: base - 5000,
        sequence: 3,
      }],
      base,
    );
    // An idle phase renders the calm caption but streams no step lines and no ticking timer.
    expect(screen.queryAllByTestId('autonomous-thinking-step')).toHaveLength(0);
    expect(screen.getByText('Awaiting the next event')).toBeDefined();
  });

  it('demotes the last step to dimmed history (no timer, no motion) when it is not live (resume boundary)', () => {
    // The resume regression: the panel passes lastStepLive=false when a newer non-pulse boundary (a
    // fresh "engaged" STATUS on the retained timeline) has superseded the last thinking step. Even
    // though the phase is active, the stale step must NOT tick a per-caption timer that would span
    // the paused interval - it renders as calm history with only its repeat count.
    const base = Date.now();
    const steps: ThinkingStep[] = [
      {
        text: 'Searching arsenal for contracts',
        count: 6,
        since: base - 600000,
        sequence: 6,
      },
    ];
    renderBubble(workingPhase('Getting to work'), steps, base, false);

    const line = screen.getByTestId('autonomous-thinking-step');
    expect(line.textContent).toContain('Searching arsenal for contracts');
    // Repeat count is still shown (it is real history), but NO per-caption timer (would be "10m 00s").
    expect(line.textContent).toContain(REPEAT(6));
    expect(line.textContent).not.toMatch(/\d+m\s|\d+s/);

    // Ticking the clock must not start a timer on the stale line - it stays frozen history.
    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(screen.getByTestId('autonomous-thinking-step').textContent).not.toMatch(/\d+m\s|\d+s/);
  });
});
