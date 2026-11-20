import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { SCENARIO_URI } from '../../../../../../actions/scenarios/scenario-actions';
import ScenarioVersions from '../../../../../../admin/components/scenarios/scenario/versions/ScenarioVersions';
import { type ScenarioChangeOutput, type ScenarioRevisionOutput, type ScenarioVersionsOutput } from '../../../../../../utils/api-types';
import { MESSAGING$ } from '../../../../../../utils/Environment';

const mocks = vi.hoisted(() => ({
  simpleCall: vi.fn(),
  simplePostCall: vi.fn(),
  dispatch: vi.fn(),
  canManage: vi.fn(),
}));

vi.mock('../../../../../../utils/Action', async (importOriginal) => {
  const actual = await importOriginal() as Record<string, unknown>;
  return {
    ...actual,
    simpleCall: mocks.simpleCall,
    simplePostCall: mocks.simplePostCall,
  };
});

vi.mock('../../../../../../components/i18n', async (importOriginal) => {
  const actual = await importOriginal() as Record<string, unknown>;
  return {
    ...actual,
    useFormatter: () => ({
      t: (message: string, values?: Record<string, string>) => message.replace(/\{(\w+)\}/g, (_, key: string) => values?.[key] ?? key),
      nsdt: (date: string) => `time ${date}`,
    }),
  };
});

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal() as Record<string, unknown>;
  return {
    ...actual,
    useParams: () => ({ scenarioId: 'scenario-1' }),
  };
});

vi.mock('../../../../../../utils/hooks', () => ({ useAppDispatch: () => mocks.dispatch }));
vi.mock('../../../../../../utils/permissions/useScenarioPermissions', () => ({ default: () => ({ canManage: mocks.canManage() }) }));

const SCENARIO = `${SCENARIO_URI}/scenario-1`;

const versions: ScenarioVersionsOutput = {
  scenario_simulations_count: 3,
  scenario_versions: [
    {
      scenario_version: '1.3.0',
      scenario_version_status: 'REVOKED',
      scenario_version_current: false,
      scenario_version_latest: false,
    },
    {
      scenario_version: '1.2.0',
      scenario_version_status: 'DEPRECATED',
      scenario_version_current: false,
      scenario_version_latest: false,
    },
    {
      scenario_version: '1.1.0',
      scenario_version_status: 'VERIFIED',
      scenario_version_published_at: '2026-11-01',
      scenario_version_changelog: 'New scan',
      scenario_version_current: false,
      scenario_version_latest: true,
    },
    {
      scenario_version: '1.0.0',
      scenario_version_status: 'VERIFIED',
      scenario_version_current: true,
      scenario_version_latest: false,
    },
  ],
};

const revisions: ScenarioRevisionOutput[] = [
  {
    scenario_revision_id: 'revision-2',
    scenario_revision_number: 2,
    scenario_revision_reason: 'UPDATE',
    scenario_revision_catalog_version: '1.0.0',
    scenario_revision_created_at: '2026-11-03',
    scenario_revision_user: 'Alice',
  },
  {
    scenario_revision_id: 'revision-1',
    scenario_revision_number: 1,
    scenario_revision_reason: 'ROLLBACK',
    scenario_revision_catalog_version: '1.1.0',
    scenario_revision_created_at: '2026-11-02',
  },
];

const changes: ScenarioChangeOutput[] = [
  {
    scenario_change_id: 'inject:a/title',
    scenario_change_element: 'inject:a',
    scenario_change_field: 'title',
    scenario_change_title: 'Scan',
    scenario_change_status: 'CONFLICT',
    scenario_change_applied: 'nmap -sV',
    scenario_change_local: 'nmap -A',
  },
  {
    scenario_change_id: 'scenario/name',
    scenario_change_element: 'scenario',
    scenario_change_field: 'name',
    scenario_change_status: 'CONFLICT',
    scenario_change_applied: 'Recon',
    scenario_change_local: 'My recon',
  },
  {
    scenario_change_id: 'scenario/description',
    scenario_change_element: 'scenario',
    scenario_change_field: 'description',
    scenario_change_status: 'TAKE_APPLIED',
  },
];

const serve = (responses: Record<string, unknown>) => mocks.simpleCall.mockImplementation((uri: string) => Promise.resolve({ data: responses[uri] ?? [] }));

const renderVersions = () => render(<ScenarioVersions />);

const open = async (action: string) => {
  fireEvent.click((await screen.findAllByRole('button', { name: action }))[0]);
  return screen.findByRole('dialog');
};

describe('ScenarioVersions', () => {
  const notifySuccess = vi.spyOn(MESSAGING$, 'notifySuccess');

  beforeEach(() => {
    mocks.canManage.mockReturnValue(true);
    mocks.simplePostCall.mockResolvedValue({ data: {} });
    notifySuccess.mockImplementation(() => undefined);
    serve({
      [`${SCENARIO}/versions`]: versions,
      [`${SCENARIO}/revisions`]: revisions,
      [`${SCENARIO}/versions/1.1.0/update-preview`]: changes,
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given_versionsAndRevisions_should_listThemWithTheirActions', async () => {
    // Act
    renderVersions();

    // Assert
    const versionsTable = await screen.findByRole('table', { name: 'Versions' });
    const latest = within(versionsTable).getByText('v1.1.0').closest('tr') as HTMLElement;
    const current = within(versionsTable).getByText('v1.0.0').closest('tr') as HTMLElement;
    expect(within(latest).getByText('Latest version')).toBeInTheDocument();
    expect(within(latest).getByText('New scan')).toBeInTheDocument();
    expect(within(latest).getByText('time 2026-11-01')).toBeInTheDocument();
    expect(within(current).getByText('Current version')).toBeInTheDocument();
    expect(within(current).getByText('3')).toBeInTheDocument();
    expect(within(versionsTable).getByText('Revoked')).toBeInTheDocument();
    expect(within(versionsTable).getByText('Deprecated')).toBeInTheDocument();
    expect(within(versionsTable).getAllByRole('button', { name: 'Update' })).toHaveLength(1);
    expect(within(latest).getByRole('button', { name: 'Update' })).toBeEnabled();
    const revisionsTable = await screen.findByRole('table', { name: 'Revisions' });
    await within(revisionsTable).findByText('#2');
    const newest = within(revisionsTable).getByText('#2').closest('tr') as HTMLElement;
    expect(within(newest).getByText('Before an update')).toBeInTheDocument();
    expect(within(newest).getByText('v1.0.0')).toBeInTheDocument();
    expect(within(newest).getByText('Alice')).toBeInTheDocument();
    expect(within(newest).getByText('time 2026-11-03')).toBeInTheDocument();
    expect(within(revisionsTable).getByText('Before a rollback')).toBeInTheDocument();
    expect(within(revisionsTable).getAllByRole('button', { name: 'Roll back to this revision' })).toHaveLength(2);
  });

  it('given_noRevision_should_saySo', async () => {
    // Arrange
    serve({ [`${SCENARIO}/versions`]: versions });

    // Act
    renderVersions();

    // Assert
    expect(await screen.findByText('No revision yet')).toBeInTheDocument();
  });

  it('given_noManageRight_should_disableUpdateAndRollBack', async () => {
    // Arrange
    mocks.canManage.mockReturnValue(false);

    // Act
    renderVersions();

    // Assert
    expect(await screen.findByRole('button', { name: 'Update' })).toBeDisabled();
    expect(await screen.findAllByRole('button', { name: 'Roll back to this revision' })).toHaveLength(2);
    screen.getAllByRole('button', { name: 'Roll back to this revision' }).forEach(button => expect(button).toBeDisabled());
  });

  it('given_conflicts_should_updateWithTheSideChosenForEach', async () => {
    // Arrange
    renderVersions();
    const dialog = await open('Update');
    const titleConflict = await within(dialog).findByRole('radiogroup', { name: 'Scan · Title' });
    const nameConflict = within(dialog).getByRole('radiogroup', { name: 'Scenario · Name' });
    const apply = within(dialog).getByRole('button', { name: 'Update' });
    expect(dialog).toHaveAccessibleName('Update to v1.1.0');
    expect(within(dialog).getByText('Taken')).toBeInTheDocument();
    expect(within(dialog).getByText('Scenario · Description')).toBeInTheDocument();
    expect(apply).toBeDisabled();

    // Act
    fireEvent.click(within(titleConflict).getByRole('radio', { name: 'Version v1.1.0' }));
    fireEvent.click(within(nameConflict).getByRole('radio', { name: 'Yours' }));
    expect(within(titleConflict).getByRole('radio', { name: 'Version v1.1.0' })).toBeChecked();
    fireEvent.click(apply);

    // Assert
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(mocks.simplePostCall).toHaveBeenCalledWith(`${SCENARIO}/versions/1.1.0/update`, {
      scenario_update_choices: {
        'inject:a/title': 'TAKE_APPLIED',
        'scenario/name': 'KEEP_MINE',
      },
    });
    expect(notifySuccess).toHaveBeenCalledWith('Scenario updated to v1.1.0');
    expect(mocks.dispatch).toHaveBeenCalledTimes(1);
    expect(mocks.simpleCall.mock.calls.filter(([uri]) => uri === `${SCENARIO}/versions`)).toHaveLength(2);
  });

  it('given_aRevisionWithNothingToChoose_should_rollBackToIt', async () => {
    // Arrange
    renderVersions();
    const dialog = await open('Roll back to this revision');
    expect(dialog).toHaveAccessibleName('Roll back to revision #2');
    expect(await within(dialog).findByText('Nothing changes in the content.')).toBeInTheDocument();

    // Act
    fireEvent.click(within(dialog).getByRole('button', { name: 'Roll back' }));

    // Assert
    await waitFor(() => expect(notifySuccess).toHaveBeenCalledWith('Scenario rolled back'));
    expect(mocks.simplePostCall).toHaveBeenCalledWith(`${SCENARIO}/revisions/revision-2/rollback`, { scenario_update_choices: {} });
  });

  it('given_aPreviewStillLoading_should_closeOnCancelWithoutApplying', async () => {
    // Arrange
    renderVersions();
    await screen.findByRole('button', { name: 'Update' });
    mocks.simpleCall.mockReturnValueOnce(new Promise(() => {}));
    const dialog = await open('Update');
    expect(within(dialog).getByRole('button', { name: 'Update' })).toBeDisabled();

    // Act
    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));

    // Assert
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(mocks.simplePostCall).not.toHaveBeenCalled();
  });
});
