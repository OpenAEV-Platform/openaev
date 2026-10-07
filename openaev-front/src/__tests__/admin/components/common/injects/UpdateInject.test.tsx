import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { createContext, type ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import UpdateInject from '../../../../../admin/components/common/injects/UpdateInject';

const { mockDispatch, mockCannot } = vi.hoisted(() => ({
  mockDispatch: vi.fn(() => Promise.resolve()),
  mockCannot: vi.fn(() => false),
}));

let currentInject: Record<string, unknown> | undefined;

vi.mock('../../../../../actions/Inject', () => ({ fetchInject: vi.fn(() => ({ type: 'FETCH_INJECT' })) }));

vi.mock('../../../../../actions/injects/inject-action', () => ({ fetchDocumentsPayloadByInject: vi.fn(() => Promise.resolve([])) }));

vi.mock('../../../../../store', () => ({ useHelper: vi.fn((selector: (helper: { getInject: () => unknown }) => unknown) => selector({ getInject: () => currentInject })) }));

vi.mock('../../../../../utils/hooks', () => ({ useAppDispatch: () => mockDispatch }));

vi.mock('../../../../../utils/hooks/useDataLoader', async () => {
  const React = await import('react');
  return {
    default: (loader: () => Promise<void> | void) => {
      React.useEffect(() => {
        void loader();
      }, []);
    },
  };
});

vi.mock('../../../../../components/i18n', async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...(original as Record<string, unknown>),
    useFormatter: () => ({ t: (value: string) => value }),
  };
});

vi.mock('../../../../../components/common/Drawer', () => ({
  default: ({ open, children, title }: {
    open: boolean;
    children: ReactNode;
    title: string;
  }) => (open
    ? (
        <div data-testid="drawer">
          <h1>{title}</h1>
          {children}
        </div>
      )
    : null),
}));

vi.mock('../../../../../admin/components/common/injects/form/InjectForm', () => ({ default: ({ disabled }: { disabled?: boolean }) => <div data-testid="inject-form" data-disabled={String(disabled ?? false)} /> }));

vi.mock('../../../../../admin/components/common/injects/InjectContractOverview', () => ({ default: () => <div data-testid="inject-contract-overview" /> }));

vi.mock('../../../../../admin/components/common/injects/UpdateInjectLogicalChains', () => ({ default: () => <div data-testid="logical-chains" /> }));

vi.mock('../../../../../admin/components/common/injects/InjectCardComponent', () => ({ default: ({ title }: { title: string }) => <div data-testid="inject-card">{title}</div> }));

vi.mock('../../../../../admin/components/common/injects/InjectIcon', () => ({ default: () => <div data-testid="inject-icon" /> }));

vi.mock('../../../../../components/PlatformIcon', () => ({ default: () => <div data-testid="platform-icon" /> }));

vi.mock('../../../../../admin/components/common/Context', () => {
  const PermissionsContext = createContext({
    permissions: {
      canAccess: true,
      canManage: true,
      canLaunch: true,
      canDelete: true,
      readOnly: false,
      isRunning: false,
    },
    inherited_context: 'NONE',
  });

  return { PermissionsContext };
});

vi.mock('../../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ cannot: mockCannot }) }));

const theme = createTheme();

const renderUpdateInject = () => render(
  <ThemeProvider theme={theme}>
    <UpdateInject
      open
      handleClose={() => {}}
      onUpdateInject={() => Promise.resolve()}
      massUpdateInject={() => Promise.resolve()}
      injectId="inject-1"
      isAtomic
      injects={[]}
      articlesFromExerciseOrScenario={[]}
      uriVariable=""
      variablesFromExerciseOrScenario={[]}
    />
  </ThemeProvider>,
);

describe('UpdateInject', () => {
  beforeEach(() => {
    currentInject = undefined;
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('does not render guarded panels when loading ends but inject is undefined', async () => {
    // Arrange
    currentInject = undefined;

    // Act
    renderUpdateInject();

    // Assert
    await waitFor(() => expect(mockDispatch).toHaveBeenCalled());
    expect(screen.getByTestId('drawer')).toBeDefined();
    expect(screen.queryByTestId('inject-form')).toBeNull();
    expect(screen.queryByTestId('inject-contract-overview')).toBeNull();
    expect(screen.queryByTestId('logical-chains')).toBeNull();
  });

  it('renders InjectForm when inject is available after loading', async () => {
    // Arrange
    currentInject = {
      inject_id: 'inject-1',
      inject_enabled: true,
      inject_title: 'Inject title',
      inject_injector: 'injector-1',
      inject_attack_patterns: [],
      inject_kill_chain_phases: [],
      inject_injector_contract: {
        convertedContent: {
          contract_id: 'contract-1',
          fields: [],
          variables: [],
        },
        injector_contract_injector_names: { 'injector-1': 'Injector One' },
        injector_contract_platforms: [],
        injector_contract_needs_executor: false,
      },
    };

    // Act
    renderUpdateInject();

    // Assert
    await waitFor(() => expect(screen.getByTestId('inject-form')).toBeDefined());
  });

  it('keeps the form editable when the inject is disabled', async () => {
    // Arrange: a disabled inject only means "excluded from execution", never read-only
    currentInject = {
      inject_id: 'inject-1',
      inject_enabled: false,
      inject_title: 'Disabled inject',
      inject_injector: 'injector-1',
      inject_attack_patterns: [],
      inject_kill_chain_phases: [],
      inject_injector_contract: {
        convertedContent: {
          contract_id: 'contract-1',
          fields: [],
          variables: [],
        },
        injector_contract_injector_names: { 'injector-1': 'Injector One' },
        injector_contract_platforms: [],
        injector_contract_needs_executor: false,
      },
    };

    // Act
    renderUpdateInject();

    // Assert
    await waitFor(() => expect(screen.getByTestId('inject-form')).toBeDefined());
    expect(screen.getByTestId('inject-form').getAttribute('data-disabled')).toBe('false');
  });
});
