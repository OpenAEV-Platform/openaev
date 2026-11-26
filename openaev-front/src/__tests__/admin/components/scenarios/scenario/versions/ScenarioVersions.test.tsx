import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { SCENARIO_URI } from '../../../../../../actions/scenarios/scenario-actions';
import ScenarioVersions from '../../../../../../admin/components/scenarios/scenario/versions/ScenarioVersions';
import { type ContentChangeOutput, type ScenarioChangeOutput, type ScenarioRevisionOutput, type ScenarioUpdatePreviewOutput, type ScenarioVersionsOutput } from '../../../../../../utils/api-types';
import { MESSAGING$ } from '../../../../../../utils/Environment';
import { setNotifyErrorHandler } from '../../../../../../utils/error/errorHandlerUtil';

const mocks = vi.hoisted(() => ({
  simpleCall: vi.fn(),
  simplePostCall: vi.fn(),
  realSimplePostCall: vi.fn(),
  post: vi.fn(),
  dispatch: vi.fn(),
  canManage: vi.fn(),
}));

vi.mock('../../../../../../network', async (importOriginal) => {
  const actual = await importOriginal() as Record<string, unknown>;
  return {
    ...actual,
    api: () => ({ post: mocks.post }),
  };
});

vi.mock('../../../../../../utils/Action', async (importOriginal) => {
  const actual = await importOriginal() as Record<string, unknown>;
  mocks.realSimplePostCall.mockImplementation(actual.simplePostCall as typeof mocks.realSimplePostCall);
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
      fsd: (date: string) => `day ${date}`,
      ft: (date: string) => `at ${date}`,
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
    scenario_revision_id: 'revision-3',
    scenario_revision_number: 3,
    scenario_revision_reason: 'UPDATE',
    scenario_revision_catalog_version: '1.0.0',
    scenario_revision_created_at: '2026-11-03',
    scenario_revision_user: 'Alice',
  },
  {
    scenario_revision_id: 'revision-2',
    scenario_revision_number: 2,
    scenario_revision_reason: 'ROLLBACK',
    scenario_revision_catalog_version: '1.1.0',
    scenario_revision_created_at: '2026-11-02',
  },
  {
    scenario_revision_id: 'revision-1',
    scenario_revision_number: 1,
    scenario_revision_reason: 'INSTALL',
    scenario_revision_catalog_version: '1.0.0',
    scenario_revision_created_at: '2026-11-01',
  },
];

const ELEMENT_KINDS: Record<string, ScenarioChangeOutput['scenario_change_element_kind']> = {
  scenario: 'SCENARIO',
  variables: 'VARIABLES',
};

// A change by the ID the backend gives it: "inject:a/title", "scenario/name", "inject:b".
const change = (id: string, status: ScenarioChangeOutput['scenario_change_status'], values: Partial<ScenarioChangeOutput> = {}): ScenarioChangeOutput => ({
  scenario_change_id: id,
  scenario_change_element_kind: ELEMENT_KINDS[id.split('/')[0]] ?? 'INJECT',
  scenario_change_field_kind: id.split('/')[1]?.toUpperCase() as ScenarioChangeOutput['scenario_change_field_kind'],
  scenario_change_status: status,
  scenario_change_editable: false,
  ...values,
});

const preview = (changes: ScenarioChangeOutput[]): ScenarioUpdatePreviewOutput => ({
  scenario_update_changelogs: [],
  scenario_update_changes: changes,
  scenario_update_payloads_to_install: [],
  scenario_update_revoked_payloads: [],
  scenario_update_lost_dependencies: [],
});

const updatePreview: ScenarioUpdatePreviewOutput = {
  ...preview([
    change('inject:a/title', 'CONFLICT', {
      scenario_change_title: 'Scan',
      scenario_change_editable: true,
      scenario_change_applied: 'nmap -sV',
      scenario_change_local: 'nmap -A',
    }),
    change('scenario/name', 'CONFLICT', {
      scenario_change_editable: true,
      scenario_change_applied: 'Recon',
      scenario_change_local: 'My recon',
    }),
    change('inject:b', 'CONFLICT', { scenario_change_title: 'Warn' }),
    change('scenario/description', 'TAKE_APPLIED', {
      scenario_change_local: 'Old',
      scenario_change_applied: 'New',
    }),
    change('scenario/category', 'KEEP_MINE', {
      scenario_change_base: 'recon',
      scenario_change_local: 'mine',
    }),
    change('inject:c', 'ADDED', { scenario_change_title: 'Mail' }),
  ]),
  scenario_update_payloads_to_install: [{
    payload_name: 'Nmap',
    payload_version: '1.1.0',
  }],
  scenario_update_lost_dependencies: ['Scan'],
  scenario_update_changelogs: [{
    scenario_changelog_version: '1.1.0',
    scenario_changelog: 'New scan',
  }],
};

const history: ContentChangeOutput[] = [{
  content_change_id: 'history-1',
  content_change_action: 'UPDATE',
  content_change_mode: 'MANUAL',
  content_change_version: '1.1.0',
  content_change_user: 'Alice',
  content_change_created_at: '2026-11-04',
}];

const HISTORY_SEARCH = `${SCENARIO}/history/search`;
const servedHistory = () => Promise.resolve({
  data: {
    content: history,
    totalElements: history.length,
  },
});
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
    mocks.simplePostCall.mockImplementation((uri: string) => (uri === HISTORY_SEARCH ? servedHistory() : Promise.resolve({ data: {} })));
    notifySuccess.mockImplementation(() => undefined);
    serve({
      [`${SCENARIO}/versions`]: versions,
      [`${SCENARIO}/revisions`]: revisions,
      [`${SCENARIO}/versions/1.1.0/preview`]: updatePreview,
      [`${SCENARIO}/revisions/revision-3/preview`]: preview([]),
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
    expect(within(latest).getByText('Not installed')).toBeInTheDocument();
    expect(within(current).queryByText('Not installed')).not.toBeInTheDocument();
    expect(within(latest).getByText('New scan')).toBeInTheDocument();
    expect(within(latest).getByText('time 2026-11-01')).toBeInTheDocument();
    expect(within(current).getByText('Current version')).toBeInTheDocument();
    expect(within(current).getByText('3')).toBeInTheDocument();
    expect(within(versionsTable).getByText('Revoked')).toBeInTheDocument();
    expect(within(versionsTable).getByText('Deprecated')).toBeInTheDocument();
    expect(within(versionsTable).getAllByRole('button', { name: 'Update' })).toHaveLength(1);
    expect(within(latest).getByRole('button', { name: 'Update' })).toBeEnabled();
    const revisionsTable = await screen.findByRole('table', { name: 'Revisions' });
    await within(revisionsTable).findByText('#3');
    const newest = within(revisionsTable).getByText('#3').closest('tr') as HTMLElement;
    expect(within(newest).getByText('Before update')).toBeInTheDocument();
    expect(within(newest).getByText('v1.0.0')).toBeInTheDocument();
    expect(within(newest).getByText('Alice')).toBeInTheDocument();
    expect(within(newest).getByText('time 2026-11-03')).toBeInTheDocument();
    expect(within(revisionsTable).getByText('Before rollback')).toBeInTheDocument();
    const installed = within(revisionsTable).getByText('#1').closest('tr') as HTMLElement;
    expect(within(installed).getByText('Installed')).toBeInTheDocument();
    expect(within(revisionsTable).getAllByRole('button', { name: 'Roll back to this revision' })).toHaveLength(3);
    const historyTable = await screen.findByRole('table', { name: 'History' });
    const updated = within(historyTable).getByText('Updated').closest('tr') as HTMLElement;
    expect(within(updated).getByText('v1.1.0')).toBeInTheDocument();
    expect(within(updated).getByText('Alice')).toBeInTheDocument();
  });

  it('given_dataStillLoading_should_showPlaceholders', async () => {
    // Arrange
    mocks.simpleCall.mockImplementation(() => new Promise(() => {}));
    mocks.simplePostCall.mockImplementation(() => new Promise(() => {}));

    // Act
    renderVersions();

    // Assert
    expect(await screen.findByText('Versions')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.queryByText('No revision yet')).not.toBeInTheDocument();
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
    expect(await screen.findAllByRole('button', { name: 'Roll back to this revision' })).toHaveLength(3);
    screen.getAllByRole('button', { name: 'Roll back to this revision' }).forEach(button => expect(button).toBeDisabled());
  });

  it('given_conflicts_should_updateWithTheSideChosenOrTheValueTypedForEach', async () => {
    // Arrange
    renderVersions();
    const dialog = await open('Update');
    const titleConflict = await within(dialog).findByRole('radiogroup', { name: 'Inject “Scan” · Title' });
    const nameConflict = within(dialog).getByRole('radiogroup', { name: 'Scenario · Name' });
    const removedConflict = within(dialog).getByRole('radiogroup', { name: 'Inject “Warn” · Removed in v1.1.0, changed by you' });
    const apply = within(dialog).getByRole('button', { name: 'Update' });
    expect(dialog).toHaveAccessibleName('Update to Filigran v1.1.0');
    expect(within(dialog).getByText('The content moves to Filigran v1.1.0. You can roll back later from Versions.')).toBeInTheDocument();
    // What changes, open: the version, the notes, the injects added and the diff named on both sides.
    expect(within(dialog).getByText('What changes (2)')).toBeInTheDocument();
    expect(within(dialog).getByText('Version: v1.0.0 → v1.1.0')).toBeInTheDocument();
    expect(within(dialog).getByText('Nmap v1.1.0')).toBeInTheDocument();
    expect(within(dialog).getByText('New scan')).toBeInTheDocument();
    expect(within(dialog).getByText('Will be added')).toBeInTheDocument();
    expect(within(dialog).getByText('Inject “Mail”')).toBeInTheDocument();
    expect(within(dialog).getByText('New in v1.1.0')).toBeInTheDocument();
    expect(within(dialog).getAllByRole('group', { name: 'Changes' })).toHaveLength(1);
    expect(within(dialog).getByRole('group', { name: 'Scenario · Description · 1 lines added, 1 lines removed' })).toBeInTheDocument();
    expect(within(dialog).getByText('Now · v1.0.0 with your edits')).toBeInTheDocument();
    expect(within(dialog).getByText('After the update · v1.1.0')).toBeInTheDocument();
    // What stays, closed: the user's own edits and the rest, summed up.
    expect(within(dialog).getByText('What stays (2)')).toBeInTheDocument();
    expect(within(dialog).getByText('1 edit of yours, targets, teams, schedule and tags')).toBeInTheDocument();
    // What the user has to do: the conflicts, the targets to add, what to check.
    expect(within(dialog).getByText('What you need to do (5)')).toBeInTheDocument();
    expect(within(dialog).getByText('Filigran changed things you edited. Pick which version to keep for each.')).toBeInTheDocument();
    expect(within(dialog).getByText('Conflicts (3)')).toBeInTheDocument();
    expect(within(dialog).getByText('After: add targets to 1 new inject.')).toBeInTheDocument();
    expect(within(dialog).getByText('Some injects lose a dependency: check them after applying.')).toBeInTheDocument();
    expect(apply).toBeDisabled();

    // Act
    fireEvent.click(within(dialog).getAllByRole('button', { name: 'Edit' })[0]);
    const value = within(dialog).getByRole('textbox', { name: 'Value' });
    expect(value).toHaveValue('nmap -A');
    fireEvent.change(value, { target: { value: 'nmap -Pn' } });
    fireEvent.click(within(nameConflict).getByRole('radio', { name: 'Yours' }));
    fireEvent.click(within(removedConflict).getByRole('radio', { name: 'Delete it' }));
    expect(within(titleConflict).getByRole('radio', { name: 'Filigran version v1.1.0' })).not.toBeChecked();
    expect(within(removedConflict).getByRole('radio', { name: 'Keep it as my own inject' })).not.toHaveAttribute('aria-describedby');
    fireEvent.click(apply);

    // Assert
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(mocks.simplePostCall).toHaveBeenCalledWith(`${SCENARIO}/versions/1.1.0/updates`, {
      scenario_update_choices: {
        'inject:a/title': 'EDIT',
        'scenario/name': 'KEEP_MINE',
        'inject:b': 'TAKE_APPLIED',
      },
      scenario_update_edits: { 'inject:a/title': 'nmap -Pn' },
    }, undefined, false);
    // The message after it carries the way back (a Roll back link).
    expect(notifySuccess).toHaveBeenCalledTimes(1);
    expect(mocks.dispatch).toHaveBeenCalledTimes(1);
    expect(mocks.simpleCall.mock.calls.filter(([uri]) => uri === `${SCENARIO}/versions`)).toHaveLength(2);
    expect(mocks.simplePostCall.mock.calls.filter(([uri]) => uri === HISTORY_SEARCH)).toHaveLength(2);
  });

  it.each([
    [500, 'The update didn\'t apply. Nothing was changed.', 'Try again, or contact your administrator if it keeps failing.', 'Try again'],
    [400, 'The update didn\'t apply. Nothing was changed.', 'Refused', undefined],
    [409, 'The scenario changed since this preview. Nothing was changed.', 'See the latest preview before applying.', 'Show the latest preview'],
  ])('given_anUpdateRefused_should_showOnlyTheErrorAndWhatToDo (%s)', async (status, title, description, next) => {
    // Arrange
    const notifyError = vi.fn();
    setNotifyErrorHandler(notifyError);
    mocks.post.mockRejectedValueOnce({
      status,
      message: 'Refused',
    });
    mocks.simplePostCall.mockImplementation((uri: string, ...rest: unknown[]) => (uri === HISTORY_SEARCH ? servedHistory() : mocks.realSimplePostCall(uri, ...rest)));
    renderVersions();
    const dialog = await open('Update');
    await within(dialog).findByRole('radiogroup', { name: 'Inject “Scan” · Title' });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Take all' }));

    // Act
    fireEvent.click(within(dialog).getByRole('button', { name: 'Update' }));

    // Assert
    const failed = await screen.findByText(title);
    expect(failed).toBeInTheDocument();
    expect(screen.getByText(description)).toBeInTheDocument();
    expect(screen.queryByText('What changes (2)')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^(Try again|Show the latest preview)$/ })?.textContent).toBe(next);
    // The footer's Close, beside the dialog's own close cross.
    expect(screen.getAllByRole('button', { name: 'Close' })).toHaveLength(2);
    expect(notifyError).not.toHaveBeenCalled();
    expect(notifySuccess).not.toHaveBeenCalled();
    expect(mocks.dispatch).not.toHaveBeenCalled();
  });

  it.each([
    ['Update', 'Update'],
    ['Roll back to this revision', 'Roll back'],
  ])('given_aPreviewRefused_should_showWhyInsteadOfLoading (%s)', async (action) => {
    // Arrange
    const refusal = 'This scenario was installed before updates were supported: it cannot be updated or rolled back';
    renderVersions();
    await screen.findAllByRole('button', { name: action });
    mocks.simpleCall.mockRejectedValueOnce({
      status: 400,
      message: refusal,
    });

    // Act
    await open(action);

    // Assert: a warning, the server's reason under it, and only Close: the same request would be refused again.
    const failed = await screen.findByRole('dialog');
    expect(await within(failed).findByText('The preview didn\'t load. Nothing was changed.')).toBeInTheDocument();
    expect(within(failed).getByText(refusal)).toBeInTheDocument();
    expect(failed.querySelector('[class*="animate-spin"]')).not.toBeInTheDocument();
    expect(within(failed).queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
    fireEvent.click(within(failed).getAllByRole('button', { name: 'Close' })[0]);
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('given_aPreviewThatFailedToLoad_should_offerToTryAgain', async () => {
    // Arrange
    renderVersions();
    await screen.findAllByRole('button', { name: 'Update' });
    mocks.simpleCall.mockRejectedValueOnce({ status: 500 });

    // Act
    await open('Update');

    // Assert
    const failed = await screen.findByRole('dialog');
    expect(await within(failed).findByText('Try again, or contact your administrator if it keeps failing.')).toBeInTheDocument();
    expect(within(failed).getByRole('button', { name: 'Try again' })).toBeEnabled();
  });

  it('given_anEditStarted_should_markTheEditButtonPressed', async () => {
    // Arrange
    renderVersions();
    const dialog = await open('Update');
    await within(dialog).findByRole('radiogroup', { name: 'Inject “Scan” · Title' });
    const edit = within(dialog).getAllByRole('button', { name: 'Edit' })[0];
    expect(edit).toHaveAttribute('aria-pressed', 'false');

    // Act
    fireEvent.click(edit);

    // Assert
    expect(edit).toHaveAttribute('aria-pressed', 'true');
  });

  it('given_aRevokedPayload_should_nameItInTheWarning', async () => {
    // Arrange
    serve({
      [`${SCENARIO}/versions`]: versions,
      [`${SCENARIO}/revisions`]: revisions,
      [`${SCENARIO}/versions/1.1.0/preview`]: {
        ...preview([]),
        scenario_update_revoked_payloads: [{
          payload_name: 'Nmap',
          payload_version: '1.0.0',
        }],
      },
    });
    renderVersions();

    // Act
    const dialog = await open('Update');

    // Assert
    expect(await within(dialog).findByText('Runs a revoked payload version: its injects will fail')).toBeInTheDocument();
    expect(within(dialog).getByText('Nmap v1.0.0')).toBeInTheDocument();
  });

  it('given_conflicts_should_takeAllInOneClick', async () => {
    // Arrange
    renderVersions();
    const dialog = await open('Update');
    await within(dialog).findByRole('radiogroup', { name: 'Inject “Scan” · Title' });

    // Act
    fireEvent.click(within(dialog).getByRole('button', { name: 'Take all' }));
    fireEvent.click(within(dialog).getByRole('button', { name: 'Update' }));

    // Assert
    await waitFor(() => expect(mocks.simplePostCall).toHaveBeenCalledWith(`${SCENARIO}/versions/1.1.0/updates`, {
      scenario_update_choices: {
        'inject:a/title': 'TAKE_APPLIED',
        'scenario/name': 'TAKE_APPLIED',
        'inject:b': 'TAKE_APPLIED',
      },
      scenario_update_edits: {},
    }, undefined, false));
  });

  it('given_aRevisionWithNothingToChoose_should_rollBackToIt', async () => {
    // Arrange
    renderVersions();
    const dialog = await open('Roll back to this revision');
    expect(dialog).toHaveAccessibleName('Roll back to revision #3');
    expect(await within(dialog).findByText('The content goes back to revision #3 (v1.0.0, day 2026-11-03). You can roll back later from Versions.')).toBeInTheDocument();
    expect(within(dialog).getByText('Nothing in the content changes.')).toBeInTheDocument();
    expect(within(dialog).getByText('Nothing to decide before applying.')).toBeInTheDocument();

    // Act
    fireEvent.click(within(dialog).getByRole('button', { name: 'Roll back' }));

    // Assert
    await waitFor(() => expect(notifySuccess).toHaveBeenCalledTimes(1));
    expect(mocks.simplePostCall).toHaveBeenCalledWith(`${SCENARIO}/revisions/revision-3/rollbacks`, {
      scenario_update_choices: {},
      scenario_update_edits: {},
    }, undefined, false);
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
    expect(mocks.simplePostCall.mock.calls.filter(([uri]) => uri !== HISTORY_SEARCH)).toHaveLength(0);
  });
});
