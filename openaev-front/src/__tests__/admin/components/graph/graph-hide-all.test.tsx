import { TooltipProvider } from '@filigran/design-system';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

import { type GraphNodeOutput, type GraphOutput } from '../../../../admin/components/graph/graph-model';

vi.mock('../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (value: string) => value,
    fd: (value: string) => value,
  }),
}));
vi.mock('../../../../actions/graph/graph-actions', () => ({
  fetchGraphNeighbourhood: vi.fn(() => new Promise(() => {})),
  fetchGraphPath: vi.fn(() => new Promise(() => {})),
}));
vi.mock('../../../../components/xtm/useCounterpartMatches', () => ({
  default: () => new Set<string>(),
  counterpartRefId: () => '',
}));
vi.mock('../../../../components/xtm/counterpartStore', () => ({ resolveCounterparts: vi.fn(() => Promise.resolve([])) }));
vi.mock('../../../../components/xtm/XtmPivot', () => ({
  default: () => null,
  ProductMark: () => null,
}));
vi.mock('../../../../components/xtm/XtmOpenCrq', () => ({
  default: () => null,
  OpenCrqMark: () => null,
  OPENCRQ_PRODUCT: 'opencrq',
}));

import GraphExplorer from '../../../../admin/components/graph/GraphExplorer';

const node = (id: string): GraphNodeOutput => ({
  node_id: id,
  node_entity_id: id,
  node_type: 'ASSET',
  node_label: `label-${id}`,
  node_failed_count: 0,
  node_finding_count: 0,
});

// One node, the focus: its side panel opens with the graph, and its Hide hides every node drawn.
const slice: GraphOutput = {
  graph_focus: 'a',
  graph_nodes: [node('a')],
  graph_edges: [],
  graph_truncated: false,
  graph_path: [],
};

const renderExplorer = () => render(
  <TooltipProvider>
    <MemoryRouter>
      <div style={{ height: 600 }}>
        <GraphExplorer initial={slice} />
      </div>
    </MemoryRouter>
  </TooltipProvider>,
);

const hideTheFocus = () => {
  // The side panel of the focus carries Hide; the focus is the only node, so nothing stays drawn.
  fireEvent.click(screen.getByRole('button', { name: 'Hide' }));
};

describe('Graph explorer: hiding every node never leaves a dead end (D1 of #9448)', () => {
  beforeAll(() => {
    if (!('ResizeObserver' in globalThis)) {
      vi.stubGlobal('ResizeObserver', class {
        observe() {}

        unobserve() {}

        disconnect() {}
      });
    }
  });
  afterEach(cleanup);

  it('keeps the toolbar, closes the side panel and lets Undo restore the view', async () => {
    renderExplorer();
    expect(screen.queryByTestId('graph-empty')).toBeNull();
    await act(async () => {
      hideTheFocus();
    });

    expect(screen.getByText('Every node of the graph is hidden')).toBeTruthy();
    expect(screen.getByTestId('graph-canvas-toolbar')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Hide' })).toBeNull();

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Undo' }));
    });
    expect(screen.queryByTestId('graph-empty')).toBeNull();
  });

  it('restores every hidden node from the empty canvas action', async () => {
    renderExplorer();
    await act(async () => {
      hideTheFocus();
    });
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'Show hidden nodes (1)' }));
    });
    expect(screen.queryByTestId('graph-empty')).toBeNull();
  });

  it('undoes the hide with Ctrl+Z on the empty canvas, which holds the focus', async () => {
    renderExplorer();
    await act(async () => {
      hideTheFocus();
    });
    const pane = screen.getByTestId('graph-empty');
    expect(document.activeElement).toBe(pane);
    await act(async () => {
      fireEvent.keyDown(pane, {
        key: 'z',
        ctrlKey: true,
      });
    });
    expect(screen.queryByTestId('graph-empty')).toBeNull();
  });
});
