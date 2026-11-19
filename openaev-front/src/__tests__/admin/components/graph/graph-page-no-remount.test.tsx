import { act, cleanup, render, screen } from '@testing-library/react';
import { useEffect } from 'react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type GraphOutput } from '../../../../admin/components/graph/graph-model';

const { neighbourhoodMock, explorerMountSpy } = vi.hoisted(() => ({
  neighbourhoodMock: vi.fn(),
  explorerMountSpy: vi.fn(),
}));

const slice = (focus: string): GraphOutput => ({
  graph_focus: focus,
  graph_nodes: [],
  graph_edges: [],
  graph_truncated: false,
  graph_path: [],
});

vi.mock('../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));
vi.mock('../../../../components/Breadcrumbs', () => ({ default: () => null }));
vi.mock('../../../../components/Loader', () => ({ default: () => <div data-testid="loader" /> }));
vi.mock('../../../../admin/components/graph/GraphStartingPoints', () => ({ default: () => null }));
vi.mock('../../../../admin/components/graph/GraphOverview', () => ({ default: () => null }));
vi.mock('../../../../actions/graph/graph-actions', () => ({
  fetchGraphNeighbourhood: neighbourhoodMock,
  fetchGraphOverview: vi.fn(() => new Promise(() => {})),
  fetchGraphStartingPoints: vi.fn(() => Promise.resolve({ starting_points: [] })),
  fetchGraphStory: vi.fn(() => new Promise(() => {})),
}));

// Counts MOUNTS of the explorer: an expand in place writes the new focus in the URL, and the page must
// keep the same explorer on screen (Sam, 19 Nov 2026: pivoting is expanding, never a full re-render).
const ExplorerProbe = ({ onFocusChange }: { onFocusChange?: (focus: string | null) => void }) => {
  useEffect(() => {
    explorerMountSpy();
  }, []);
  return <button type="button" data-testid="expand" onClick={() => onFocusChange?.('ASSET:b2')} />;
};
vi.mock('../../../../admin/components/graph/GraphExplorer', () => ({ default: (props: { onFocusChange?: (focus: string | null) => void }) => <ExplorerProbe {...props} /> }));

import Graph from '../../../../admin/components/graph/Graph';

describe('Graph page: expand in place never remounts the explorer', () => {
  beforeEach(() => {
    neighbourhoodMock.mockReset();
    explorerMountSpy.mockReset();
    neighbourhoodMock.mockImplementation(() => Promise.resolve(slice('ASSET:a1')));
  });
  afterEach(cleanup);

  it('keeps the explorer mounted and loads nothing again when the explorer moves the focus', async () => {
    render(
      <MemoryRouter initialEntries={['/admin/graph?focus=asset:a1']}>
        <Graph />
      </MemoryRouter>,
    );
    const button = await screen.findByTestId('expand');
    expect(explorerMountSpy).toHaveBeenCalledTimes(1);
    expect(neighbourhoodMock).toHaveBeenCalledTimes(1);

    await act(async () => {
      button.click();
    });

    expect(screen.getByTestId('expand')).toBe(button);
    expect(explorerMountSpy).toHaveBeenCalledTimes(1);
    expect(neighbourhoodMock).toHaveBeenCalledTimes(1);
    expect(screen.queryByTestId('loader')).toBeNull();
  });
});
