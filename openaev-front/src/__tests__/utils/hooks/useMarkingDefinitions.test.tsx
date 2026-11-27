import '@testing-library/jest-dom/vitest';

import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { DATA_UPDATE_SUCCESS } from '../../../constants/ActionTypes';
import { type MarkingDefinitionOutput } from '../../../utils/api-types';
import useMarkingDefinitions from '../../../utils/hooks/useMarkingDefinitions';
import AppQueryProvider from '../../../utils/query/AppQueryProvider';
import { startStream, stopStream } from '../../../utils/stream/streamClient';
import FakeEventSource from '../stream/fakeEventSource';

const mocks = vi.hoisted(() => ({
  getAssignableMarkingDefinitions: vi.fn(),
  listMarkingDefinitions: vi.fn(),
}));

vi.mock('../../../actions/marking_definitions/marking-definition-actions', () => ({
  getAssignableMarkingDefinitions: mocks.getAssignableMarkingDefinitions,
  listMarkingDefinitions: mocks.listMarkingDefinitions,
}));
vi.mock('../../../utils/Action', () => ({
  buildUri: (path: string) => path,
  simpleCall: vi.fn(),
}));
vi.mock('../../../utils/url-helper', async (importOriginal) => {
  // eslint-disable-next-line @typescript-eslint/consistent-type-imports
  const original = await importOriginal<typeof import('../../../utils/url-helper')>();
  return {
    ...original,
    getCurrentTenantId: () => 'tenant-a',
  };
});
vi.mock('../../../utils/error/errorHandlerUtil', () => ({ notifyErrorHandler: vi.fn() }));
vi.mock('@tanstack/react-query-devtools', () => ({ ReactQueryDevtools: () => null }));

const definition = (id: string, name: string) => ({
  marking_definition_id: id,
  marking_definition_definition: name,
}) as MarkingDefinitionOutput;

const AMBER = definition('amber', 'TLP:AMBER');
const RED = definition('red', 'TLP:RED');

// Shows the name each id resolves to, as a marking chip does.
const Chips = ({ ids }: { ids: string[] }) => {
  const definitions = useMarkingDefinitions({ assignableOnly: true });
  return <>{ids.map(id => <span key={id}>{definitions[id]?.marking_definition_definition ?? '-'}</span>)}</>;
};

const SkippedChips = () => {
  useMarkingDefinitions({ skip: true });
  return null;
};

describe('useMarkingDefinitions', () => {
  // Rendered with the stream open, once the open's own invalidation has settled.
  const renderLive = async (ids: string[]) => {
    startStream();
    render(<AppQueryProvider><Chips ids={ids} /></AppQueryProvider>);
    FakeEventSource.last.emit('open');
    await act(() => vi.advanceTimersByTimeAsync(2000));
  };

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    mocks.getAssignableMarkingDefinitions.mockReset();
    mocks.listMarkingDefinitions.mockReset();
    FakeEventSource.reset();
    vi.stubGlobal('EventSource', FakeEventSource);
  });

  afterEach(() => {
    cleanup();
    stopStream();
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('given_theReaderClearedForAMarking_should_resolveItWithoutAReload', async () => {
    // Arrange: AMBER clearance, then the reader joins a group granting RED.
    mocks.getAssignableMarkingDefinitions.mockResolvedValue([AMBER]);
    await renderLive(['red']);
    expect(screen.getByText('-')).toBeInTheDocument();
    mocks.getAssignableMarkingDefinitions.mockResolvedValue([AMBER, RED]);

    // Act
    act(() => FakeEventSource.last.emit('clearance-changed', {}));

    // Assert
    await waitFor(() => expect(screen.getByText('TLP:RED')).toBeInTheDocument());
  });

  it('given_aShownDefinitionRenamed_should_showTheNewName', async () => {
    // Arrange
    mocks.getAssignableMarkingDefinitions.mockResolvedValue([AMBER]);
    await renderLive(['amber']);
    expect(screen.getByText('TLP:AMBER')).toBeInTheDocument();
    mocks.getAssignableMarkingDefinitions.mockResolvedValue([definition('amber', 'TLP:AMBER+STRICT')]);

    // Act
    act(() => FakeEventSource.last.emit('message', {
      event_type: DATA_UPDATE_SUCCESS,
      attribute_id: 'marking_definition_id',
      attribute_schema: 'markingdefinitions',
      instance: { marking_definition_id: 'amber' },
      listened: true,
    }));

    // Assert
    await waitFor(() => expect(screen.getByText('TLP:AMBER+STRICT')).toBeInTheDocument());
  });

  it('given_skip_should_requestNothing', () => {
    // Act
    render(<AppQueryProvider><SkippedChips /></AppQueryProvider>);

    // Assert
    expect(mocks.getAssignableMarkingDefinitions).not.toHaveBeenCalled();
    expect(mocks.listMarkingDefinitions).not.toHaveBeenCalled();
  });
});
