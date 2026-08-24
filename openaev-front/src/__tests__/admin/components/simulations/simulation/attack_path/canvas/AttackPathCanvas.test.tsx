import { createTheme, ThemeProvider } from '@mui/material/styles';
import { act, cleanup, render } from '@testing-library/react';
import { type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AP_FLOW_NODE_TYPE, type AttackPathFlowNode } from '../../../../../../../admin/components/simulations/simulation/attack_path/attack-path-flow-helpers';
import AttackPathCanvas from '../../../../../../../admin/components/simulations/simulation/attack_path/canvas/AttackPathCanvas';
import { computeCardRects, computeContentBounds } from '../../../../../../../admin/components/simulations/simulation/attack_path/canvas/canvas-geometry';

const mocks = vi.hoisted(() => ({ toBlob: vi.fn() }));

// html-to-image needs a real browser (foreignObject + canvas), so the DOM capture itself is stubbed:
// what this test pins is the contract the canvas offers to it — the frame of the WHOLE graph.
vi.mock('html-to-image', () => ({ toBlob: mocks.toBlob }));

vi.mock('../../../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (s: string) => s }) }));

const wrapper = ({ children }: { children: ReactNode }) => (
  <ThemeProvider theme={createTheme()}>{children}</ThemeProvider>
);

// Two cards far apart, so an export that only framed the visible camera would crop one out.
const nodes: AttackPathFlowNode[] = [{
  id: 'NODE_ASSET|host-x',
  type: AP_FLOW_NODE_TYPE.asset,
  position: {
    x: 0,
    y: 0,
  },
  data: { label: 'CORP-HOST' },
}, {
  id: 'NODE_FINDING|cred-1',
  type: AP_FLOW_NODE_TYPE.finding,
  position: {
    x: 1600,
    y: 900,
  },
  data: { label: 'admin:••••' },
}];

const settleExport = async (render: () => void) => {
  await act(async () => {
    render();
    await new Promise((resolve) => {
      requestAnimationFrame(() => setTimeout(resolve, 0));
    });
  });
};

describe('AttackPathCanvas PNG export', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('captures the whole graph, not the part the camera happens to show', async () => {
    const png = new Blob(['png'], { type: 'image/png' });
    mocks.toBlob.mockResolvedValue(png);
    const onExportDone = vi.fn();

    const { rerender } = render(
      <AttackPathCanvas nodes={nodes} edges={[]} exportRequest={0} onExportDone={onExportDone} />,
      { wrapper },
    );
    expect(mocks.toBlob).not.toHaveBeenCalled();

    // A new nonce asks for a capture: the canvas mounts the culled cards, then reads the DOM one
    // frame later.
    await settleExport(() => rerender(
      <AttackPathCanvas nodes={nodes} edges={[]} exportRequest={1} onExportDone={onExportDone} />,
    ));

    expect(mocks.toBlob).toHaveBeenCalledTimes(1);
    const [, options] = mocks.toBlob.mock.calls[0];
    // The frame is the whole laid-out world plus the export margin — the far card included, which a
    // capture of the visible camera could not contain.
    const world = computeContentBounds(computeCardRects(nodes));
    expect(options.width).toBe(world.width + 96);
    expect(options.height).toBe(world.height + 96);
    expect(options.width).toBeGreaterThan(400);
    expect(options.height).toBeGreaterThan(400);
    // The live camera transform is replaced by the framing one, so pan/zoom cannot crop the image.
    expect(options.style.transform).toBe('translate(48px, 48px)');
    expect(options.backgroundColor).toBeTruthy();

    expect(onExportDone).toHaveBeenCalledWith(png);
  });

  it('reports a failed capture rather than handing back a broken image', async () => {
    mocks.toBlob.mockRejectedValue(new Error('boom'));
    const onExportDone = vi.fn();

    const { rerender } = render(
      <AttackPathCanvas nodes={nodes} edges={[]} exportRequest={0} onExportDone={onExportDone} />,
      { wrapper },
    );
    await settleExport(() => rerender(
      <AttackPathCanvas nodes={nodes} edges={[]} exportRequest={1} onExportDone={onExportDone} />,
    ));

    expect(onExportDone).toHaveBeenCalledWith(null);
  });

  it('does not replay the last capture when the canvas is remounted', async () => {
    mocks.toBlob.mockResolvedValue(new Blob(['png'], { type: 'image/png' }));
    const onExportDone = vi.fn();

    // Mounting with a nonce already spent (leaving and coming back to the graph view) must not
    // download a second image behind the user's back.
    await settleExport(() => {
      render(
        <AttackPathCanvas nodes={nodes} edges={[]} exportRequest={7} onExportDone={onExportDone} />,
        { wrapper },
      );
    });

    expect(mocks.toBlob).not.toHaveBeenCalled();
    expect(onExportDone).not.toHaveBeenCalled();
  });
});
