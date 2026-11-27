import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { type ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import NewActionDrafts from '../../../../../admin/components/threat_arsenal/drafts/NewActionDrafts';
import { type PayloadDraftOutput } from '../../../../../utils/api-types';

const { mockCan, mockOpened } = vi.hoisted(() => ({
  mockCan: vi.fn(),
  mockOpened: vi.fn(),
}));

vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (value: string, values?: Record<string, string>) => Object.entries(values ?? {})
      .reduce((text, [key, val]) => text.replace(`{${key}}`, val), value),
    nsdt: (date: string) => date,
  }),
}));

vi.mock('../../../../../utils/permissions/permissionsContext', () => ({ useAbility: () => ({ can: mockCan }) }));

vi.mock('../../../../../components/common/accordion/FdsAccordion', () => ({
  default: ({ title, children }: {
    title: string;
    children: ReactNode;
  }) => <section aria-label={title}>{children}</section>,
}));

vi.mock('../../../../../components/common/marketplace/MarketplaceGrid', () => ({ default: ({ children }: { children: ReactNode }) => <div>{children}</div> }));

// The Create form opened on the draft: the trigger it renders, and which draft it opens.
vi.mock('../../../../../admin/components/threat_arsenal/CreateThreatArsenalAction', () => ({
  default: ({ draft, renderTrigger }: {
    draft: PayloadDraftOutput;
    renderTrigger: (open: () => void) => ReactNode;
  }) => <>{renderTrigger(() => mockOpened(draft.draft_id))}</>,
}));

const draft: PayloadDraftOutput = {
  draft_id: 'd1',
  draft_new_action: true,
  draft_content: { action_name: 'Dump credentials' },
  draft_updated_by: 'Sam',
  draft_created_at: '2026-11-27T10:00:00Z',
  draft_updated_at: '2026-11-27T11:00:00Z',
};

describe('NewActionDrafts', () => {
  beforeEach(() => {
    mockCan.mockReturnValue(true);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given a draft of a new action should list it as a draft, with who saved it', () => {
    // Act
    render(<NewActionDrafts drafts={[draft]} onCreate={vi.fn()} onDraftsChange={vi.fn()} />);

    // Assert
    expect(screen.getByRole('region', { name: 'Drafts of new actions' })).toBeTruthy();
    expect(screen.getByText('Dump credentials')).toBeTruthy();
    expect(screen.getByText('Draft')).toBeTruthy();
    expect(screen.getByText('New action')).toBeTruthy();
    expect(screen.getByText('Saved by Sam, 2026-11-27T11:00:00Z')).toBeTruthy();
  });

  it('given a user who can create actions should open the Create form on the draft', () => {
    // Arrange
    render(<NewActionDrafts drafts={[draft]} onCreate={vi.fn()} onDraftsChange={vi.fn()} />);

    // Act
    fireEvent.click(screen.getByRole('button', { name: /Dump credentials/ }));

    // Assert
    expect(mockOpened).toHaveBeenCalledWith('d1');
  });

  it('given a reader should show the draft without opening it', () => {
    // Arrange
    mockCan.mockReturnValue(false);

    // Act
    render(
      <NewActionDrafts
        drafts={[{
          ...draft,
          draft_content: {},
        }]}
        onCreate={vi.fn()}
        onDraftsChange={vi.fn()}
      />,
    );

    // Assert
    expect(screen.getByText('Untitled')).toBeTruthy();
    expect(screen.queryByRole('button')).toBeNull();
  });

  it('given no draft should show nothing', () => {
    // Act
    const { container } = render(<NewActionDrafts drafts={[]} onCreate={vi.fn()} onDraftsChange={vi.fn()} />);

    // Assert
    expect(container.innerHTML).toBe('');
  });
});
