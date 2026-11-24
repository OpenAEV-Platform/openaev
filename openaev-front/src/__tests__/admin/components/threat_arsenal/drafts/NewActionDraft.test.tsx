import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import CreateThreatArsenalAction from '../../../../../admin/components/threat_arsenal/CreateThreatArsenalAction';
import { type PayloadDraftOutput } from '../../../../../utils/api-types';

const { calls } = vi.hoisted(() => ({
  calls: {
    add: vi.fn(),
    createDraft: vi.fn(),
    saveDraft: vi.fn(),
    publishDraft: vi.fn(),
    discardDraft: vi.fn(),
    onDraftsChange: vi.fn(),
  },
}));

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));

vi.mock('../../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: () => true }) }));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ addThreatArsenalAction: (input: unknown) => calls.add(input) }));

vi.mock('../../../../../actions/threat_arsenals/threatArsenal-draft-actions', () => ({
  createNewActionDraft: (input: unknown) => calls.createDraft(input),
  saveNewActionDraft: (id: string, input: unknown) => calls.saveDraft(id, input),
  publishNewActionDraft: (id: string, input: unknown) => calls.publishDraft(id, input),
  discardNewActionDraft: (id: string) => calls.discardDraft(id),
}));

vi.mock('../../../../../admin/components/threat_arsenal/approval/notifySubmittedForApproval', () => ({ default: vi.fn() }));

// Fixture copy bound to constants: `i18next/no-literal-string` cannot tell them from UI copy.
const SAVE = 'save-draft';
const SUBMIT = 'submit';
const DISCARD = 'discard-draft';
const OPEN = 'open';
const CLOSE = 'close';

vi.mock('../../../../../components/common/Drawer', () => ({
  default: ({ open, handleClose, children }: {
    open: boolean;
    handleClose: () => void;
    children: ReactNode;
  }) => (open
    ? (
        <div data-testid="create-drawer">
          <button type="button" onClick={handleClose}>{CLOSE}</button>
          {children}
        </div>
      )
    : null),
}));

// The form is not under test: its buttons call the draft mode and the submit the drawer gives it.
vi.mock('../../../../../admin/components/threat_arsenal/ThreatArsenalActionForm', () => ({
  default: ({ onSubmit, initialValues, draft }: {
    onSubmit: (data: object) => Promise<void>;
    initialValues?: { action_name?: string };
    draft?: {
      publishLabel: string;
      saved: boolean;
      onSaveDraft: (values: object) => Promise<void>;
      onDiscard?: () => void;
    };
  }) => (
    <div>
      <span data-testid="initial-name">{initialValues?.action_name ?? ''}</span>
      <span data-testid="saved">{String(draft?.saved)}</span>
      <button type="button" onClick={() => draft?.onSaveDraft({ action_name: 'Dump credentials' })}>{SAVE}</button>
      <button type="button" onClick={() => onSubmit({ action_name: 'Dump credentials' })}>{SUBMIT}</button>
      {draft?.onDiscard && <button type="button" onClick={draft.onDiscard}>{DISCARD}</button>}
    </div>
  ),
}));

const savedDraft: PayloadDraftOutput = {
  draft_id: 'draft-1',
  draft_new_action: true,
  draft_content: { action_name: 'Saved new action' },
  draft_created_at: '2026-11-24T10:00:00Z',
  draft_updated_at: '2026-11-24T10:00:00Z',
};

const renderCreate = (draft?: PayloadDraftOutput) => render(
  <ThemeProvider theme={createTheme()}>
    <TooltipProvider>
      <CreateThreatArsenalAction
        draft={draft}
        onDraftsChange={calls.onDraftsChange}
        renderTrigger={open => <button type="button" onClick={open}>{OPEN}</button>}
      />
    </TooltipProvider>
  </ThemeProvider>,
);

describe('CreateThreatArsenalAction: a new action saved as a draft', () => {
  beforeEach(() => {
    calls.add.mockResolvedValue({ data: { injector_contract_id: 'action-1' } });
    calls.createDraft.mockResolvedValue({ data: savedDraft });
    calls.saveDraft.mockResolvedValue({ data: savedDraft });
    calls.publishDraft.mockResolvedValue({ data: { injector_contract_id: 'action-1' } });
    calls.discardDraft.mockResolvedValue({});
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given the Create form saved as a draft should create the draft, then publish it, not create the action directly', async () => {
    // Arrange
    renderCreate();
    fireEvent.click(screen.getByText(OPEN));

    // Act
    fireEvent.click(screen.getByText(SAVE));
    await waitFor(() => expect(screen.getByTestId('saved').textContent).toBe('true'));
    fireEvent.click(screen.getByText(SUBMIT));

    // Assert
    await waitFor(() => expect(calls.publishDraft).toHaveBeenCalledWith('draft-1', expect.objectContaining({ action_name: 'Dump credentials' })));
    expect(calls.createDraft).toHaveBeenCalledWith({ draft_content: { action_name: 'Dump credentials' } });
    expect(calls.add).not.toHaveBeenCalled();
    expect(calls.onDraftsChange).toHaveBeenCalled();
  });

  it('given no draft saved should create the action as before', async () => {
    // Arrange
    renderCreate();
    fireEvent.click(screen.getByText(OPEN));

    // Act
    fireEvent.click(screen.getByText(SUBMIT));

    // Assert
    await waitFor(() => expect(calls.add).toHaveBeenCalled());
    expect(calls.publishDraft).not.toHaveBeenCalled();
  });

  it('given a saved draft opened from My changes should start from its content and save it again', async () => {
    // Arrange
    renderCreate(savedDraft);
    fireEvent.click(screen.getByText(OPEN));

    // Act
    fireEvent.click(screen.getByText(SAVE));

    // Assert
    expect(screen.getByTestId('initial-name').textContent).toBe('Saved new action');
    await waitFor(() => expect(calls.saveDraft).toHaveBeenCalledWith('draft-1', { draft_content: { action_name: 'Dump credentials' } }));
    expect(calls.createDraft).not.toHaveBeenCalled();
  });

  it('given a draft saved from Create and the drawer closed should start a new action on the next Create', async () => {
    // Arrange
    renderCreate();
    fireEvent.click(screen.getByText(OPEN));
    fireEvent.click(screen.getByText(SAVE));
    await waitFor(() => expect(screen.getByTestId('saved').textContent).toBe('true'));
    fireEvent.click(screen.getByText(CLOSE));

    // Act
    fireEvent.click(screen.getByText(OPEN));
    fireEvent.click(screen.getByText(SAVE));

    // Assert
    await waitFor(() => expect(calls.createDraft).toHaveBeenCalledTimes(2));
    expect(calls.saveDraft).not.toHaveBeenCalled();
  });

  it('given a saved draft discarded should ask first, then discard it', async () => {
    // Arrange
    renderCreate(savedDraft);
    fireEvent.click(screen.getByText(OPEN));

    // Act
    fireEvent.click(screen.getByText(DISCARD));
    fireEvent.click(await screen.findByRole('button', { name: 'Discard' }));

    // Assert
    await waitFor(() => expect(calls.discardDraft).toHaveBeenCalledWith('draft-1'));
  });
});
