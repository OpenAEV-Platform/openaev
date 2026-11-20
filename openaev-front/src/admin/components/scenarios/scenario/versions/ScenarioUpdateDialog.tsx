import {
  Alert,
  Button,
  Chip,
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogTitle,
  Text,
  Textarea,
} from '@filigran/design-system';
import { ArrowForwardOutlined, CheckCircleOutlined, ExpandMoreOutlined } from '@mui/icons-material';
// fds:keep-mui the library has no Accordion yet (frontend.instructions.md lists it as still MUI)
import { Accordion, AccordionDetails, AccordionSummary } from '@mui/material';
import { Fragment, type FunctionComponent, type ReactNode, useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router';

import {
  applyScenarioUpdate,
  fetchCatalogScenarioVersions,
  previewScenarioUpdate,
  type ScenarioMergeChange,
  type ScenarioMergeChoice,
  type ScenarioUpdatePreview,
} from '../../../../../actions/catalog/scenario-catalog-actions';
import { ChangesTable } from '../../../../../components/common/content_versions/ContentVersionsTable';
import ConflictChoice from '../../../../../components/common/update/ConflictChoice';
import TextDiff from '../../../../../components/common/update/TextDiff';
import { useFormatter } from '../../../../../components/i18n';
import { MESSAGING$ } from '../../../../../utils/Environment';
import { compareVersions } from '../../../../../utils/versions';

interface Props {
  scenarioId: string;
  version?: string;
  revisionId?: string;
  revisionNumber?: number;
  /** When the revision was taken, to say which one it is without remembering its number. */
  revisionDate?: string;
  onClose: () => void;
  onApplied: () => void;
}

const BLOCK_LABELS: Record<string, string> = {
  'scenario:scenario': 'Name, description, severity and category',
  'scenario:materials': 'Articles, lessons and objectives',
  'contract': 'Payload version',
  'content': 'Content',
  'timing': 'Timing and dependencies',
  'text': 'Title and description',
};

// What happens to an inject when the update or rollback is applied.
const ELEMENT_LABELS: Record<string, string> = {
  ADDED: 'Will be added',
  REMOVED: 'Will be deleted',
  REMOVED_CONFLICT: 'Removed, changed locally',
  STAYS_DELETED: 'Stays deleted',
  KEPT_USER: 'Your inject, kept',
};

const summary = (value: unknown): string => {
  if (value === null || value === undefined) {
    return '-';
  }
  if (typeof value === 'string') {
    const payload = /^payload:[^|]*\|([^|]*)\|(.*)$/.exec(value);
    return payload ? `${payload[1]} v${payload[2]}` : value;
  }
  if (typeof value === 'object' && value !== null && 'title' in value) {
    return String((value as { title: unknown }).title ?? '-');
  }
  const text = JSON.stringify(value);
  return text.length > 140 ? `${text.slice(0, 140)}…` : text;
};

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value);

const fieldText = (value: unknown) => {
  if (value === null || value === undefined) return '';
  return typeof value === 'string' ? value : JSON.stringify(value);
};

// Command-type fields of an inject's content (commands, scripts, scanner templates): shown line by
// line with + and −, as in the Versions and History tabs of the Threat Arsenal.
const CODE_KEY = /command|script|argument|prerequisite|cleanup|template/i;

// A field name as people read it: main_focus becomes "Main focus".
const fieldLabel = (key: string) => {
  const words = key.replace(/^(scenario|inject)_/, '').replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
};

// A typed value starts from the client's own: plain text for a string, JSON otherwise.
const draftOf = (value: unknown) => (typeof value === 'string' ? value : JSON.stringify(value ?? null, null, 2));

// A part of the dialog that opens and closes: the title, its count, and a one-line summary that says
// what is inside even when closed, so nothing is hidden without a trace.
const Section: FunctionComponent<{
  title: string;
  count?: number;
  summary?: string;
  defaultExpanded: boolean;
  testId: string;
  children: ReactNode;
}> = ({ title, count, summary, defaultExpanded, testId, children }) => (
  <Accordion
    defaultExpanded={defaultExpanded}
    disableGutters
    elevation={0}
    square
    data-testid={testId}
    sx={{
      'background': 'transparent',
      'borderTop': '1px solid var(--border-elevation-subtle-soft)',
      '&::before': { display: 'none' },
    }}
  >
    <AccordionSummary
      expandIcon={<ExpandMoreOutlined fontSize="small" />}
      sx={{
        'px': 0,
        'minHeight': 0,
        '& .MuiAccordionSummary-content': {
          my: 1.5,
          display: 'flex',
          alignItems: 'baseline',
          gap: 1,
          flexWrap: 'wrap',
          minWidth: 0,
        },
      }}
    >
      <Text variant="title-xs">{count === undefined ? title : `${title} (${count})`}</Text>
      {summary && <Text variant="content-caption" className="text-default-secondary">{summary}</Text>}
    </AccordionSummary>
    <AccordionDetails sx={{
      px: 0,
      pt: 0,
      pb: 2,
      display: 'flex',
      flexDirection: 'column',
      gap: 1,
    }}
    >
      {children}
    </AccordionDetails>
  </Accordion>
);

// F20 in step 1: what an update or a rollback changes, and a side for each conflict.
const ScenarioUpdateDialog: FunctionComponent<Props> = ({ scenarioId, version, revisionId, revisionNumber, revisionDate, onClose, onApplied }) => {
  const { t, fsd } = useFormatter();
  const [preview, setPreview] = useState<ScenarioUpdatePreview | null>(null);
  const [choices, setChoices] = useState<Record<string, ScenarioMergeChoice>>({});
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [applying, setApplying] = useState(false);
  // When applying fails, the dialog shows only that, small: the preview is no longer what matters.
  const [applyError, setApplyError] = useState<'changed' | 'failed' | null>(null);
  // Kept edits whose content is open ("View").
  const [viewing, setViewing] = useState<Set<string>>(new Set());
  // Release notes of the versions this update brings, newest first.
  const [notes, setNotes] = useState<{
    version: string;
    changelog: string;
  }[]>([]);
  const target = version
    ? { version }
    : { revision_id: revisionId };
  const theirs = version ? t('Filigran version v{version}', { version }) : t('Revision #{number}', { number: revisionNumber ?? '' });

  const loadPreview = () => {
    setPreview(null);
    previewScenarioUpdate(scenarioId, target).then(({ data }) => setPreview(data));
  };
  useEffect(loadPreview, [scenarioId, version, revisionId]);

  useEffect(() => {
    if (!version || !preview) return;
    fetchCatalogScenarioVersions(scenarioId).then(({ data }) => setNotes(data.versions
      .filter(release => release.changelog
        && compareVersions(release.version, preview.current_version) > 0
        && compareVersions(release.version, version) <= 0)
      .sort((a, b) => compareVersions(b.version, a.version))
      .map(release => ({
        version: release.version,
        changelog: release.changelog ?? '',
      }))));
  }, [scenarioId, version, preview]);

  const titleOf = (element: string, title?: string) => {
    if (element === 'scenario') {
      return t('Scenario');
    }
    if (element === 'variables') {
      return t('Variables');
    }
    const name = title ?? element.split(':').slice(1).join(':');
    return element.startsWith('inject') ? t('Inject “{title}”', { title: name }) : name;
  };
  const blockOf = (change: ScenarioMergeChange) => {
    if (change.key) {
      return change.block === 'variables' ? t('Variable {key}', { key: change.key }) : t('Argument {key}', { key: change.key });
    }
    return t(BLOCK_LABELS[`${change.element}:${change.block}`] ?? BLOCK_LABELS[change.block] ?? change.block);
  };
  // Field, before, after; a block of fields reads one changed field per row.
  const rowsOf = (change: ScenarioMergeChange, before: unknown, after: unknown) => {
    if (isRecord(before) && isRecord(after)) {
      return [...new Set([...Object.keys(before), ...Object.keys(after)])]
        .filter(key => fieldText(before[key]) !== fieldText(after[key]))
        .map((key) => {
          const code = CODE_KEY.test(key);
          // A structured command (arguments, prerequisites) reads line by line when indented.
          const text = (value: unknown) => (code && typeof value !== 'string' && value != null ? JSON.stringify(value, null, 2) : fieldText(value));
          return {
            field: `${titleOf(change.element, change.title)} · ${t(fieldLabel(key))}`,
            before: text(before[key]),
            after: text(after[key]),
            code,
          };
        });
    }
    return [{
      field: `${titleOf(change.element, change.title)} · ${blockOf(change)}`,
      before: summary(before),
      after: summary(after),
    }];
  };

  const conflicts = useMemo(() => {
    if (!preview) {
      return [];
    }
    const removed = preview.elements
      .filter(element => element.change === 'REMOVED_CONFLICT')
      .map(element => ({
        id: element.id,
        title: element.title ?? element.id,
        block: t('Removed by this version, changed locally'),
        mine: t('Keep it as my own inject'),
        applied: t('Delete it'),
        editable: false,
        local: undefined as unknown,
      }));
    return [
      ...preview.changes
        .filter(change => change.status === 'CONFLICT')
        .map(change => ({
          id: change.id,
          title: titleOf(change.element, change.title),
          block: blockOf(change),
          mine: summary(change.local),
          applied: summary(change.applied),
          editable: change.editable,
          local: change.local,
        })),
      ...removed,
    ];
  }, [preview]);
  const ready = conflicts.every(conflict => choices[conflict.id]);
  const chooseAll = (choice: ScenarioMergeChoice) => setChoices(Object.fromEntries(conflicts.map(conflict => [conflict.id, choice])));

  const apply = () => {
    if (!preview) {
      return;
    }
    const edits: Record<string, unknown> = {};
    try {
      conflicts.filter(conflict => choices[conflict.id] === 'EDIT').forEach((conflict) => {
        const draft = drafts[conflict.id] ?? draftOf(conflict.local);
        edits[conflict.id] = typeof conflict.local === 'string' ? draft : JSON.parse(draft);
      });
    } catch {
      MESSAGING$.notifyError(t('A value is not valid JSON'));
      return;
    }
    setApplying(true);
    setApplyError(null);
    applyScenarioUpdate(scenarioId, {
      ...target,
      choices,
      edits,
      local_hash: preview.local_hash,
    })
      .then(() => {
        // The way back, right when it matters: a link to the Versions tab, where the revision saved just
        // before is listed with "Roll back to this revision" (and its preview, before anything changes).
        MESSAGING$.notifySuccess(
          <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 8,
            flexWrap: 'wrap',
          }}
          >
            {version ? t('Scenario updated to v{version}.', { version }) : t('Scenario rolled back.')}
            <Link
              to={`/admin/scenarios/${encodeURIComponent(scenarioId)}/versions`}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 4,
                fontWeight: 600,
              }}
            >
              {t('Roll back')}
              <ArrowForwardOutlined fontSize="inherit" />
            </Link>
          </span>,
        );
        onApplied();
      })
      // The server applies all or nothing: on failure nothing changed. 409: the scenario changed since
      // this preview, so the preview must be opened again.
      .catch((error: {
        status?: number;
        response?: { status?: number };
      }) => {
        setApplyError((error.response?.status ?? error.status) === 409 ? 'changed' : 'failed');
      })
      .finally(() => setApplying(false));
  };

  let applyLabel = String(version ? t('Update') : t('Roll back'));
  if (applyError) applyLabel = String(t('Try again'));
  let applyErrorTitle = String(version ? t('The update didn\'t apply. Nothing was changed.') : t('The rollback didn\'t apply. Nothing was changed.'));
  if (applyError === 'changed') applyErrorTitle = String(t('The scenario changed since this preview. Nothing was changed.'));
  const taken = preview?.changes.filter(change => change.status === 'TAKE_APPLIED') ?? [];
  const kept = preview?.changes.filter(change => change.status === 'KEEP_MINE') ?? [];
  // One row per kept field, with the user's value (what stays after applying).
  const keptRows = kept.flatMap(change => rowsOf(change, change.base, change.local).map((item, index) => ({
    key: `${change.id}-${index}`,
    element: change.element,
    name: change.key ?? '',
    field: item.field,
    value: item.after,
    code: 'code' in item && !!item.code,
  })));
  // Inject-level changes: what applying adds or deletes, and what stays as it is.
  const changedElements = preview?.elements.filter(element => element.change === 'ADDED' || element.change === 'REMOVED') ?? [];
  const keptElements = preview?.elements.filter(element => element.change === 'STAYS_DELETED' || element.change === 'KEPT_USER') ?? [];
  const needTargets = changedElements.filter(element => element.change === 'ADDED').length;
  const nothingChanges = !!preview && taken.length === 0 && changedElements.length === 0 && notes.length === 0
    && preview.payloads_to_install.length === 0 && conflicts.length === 0;
  // What stays, grouped: the variables in one row (a scenario has many), the other edits one by one.
  const keptVariables = keptRows.filter(item => item.element === 'variables');
  const keptOthers = keptRows.filter(item => item.element !== 'variables');
  const keptInjectEdits = keptOthers.filter(item => item.element.startsWith('inject')).length;
  const keptScenarioEdits = keptOthers.length - keptInjectEdits;
  const staysSummary = [
    keptVariables.length > 0 ? t('{count, plural, one {# variable} other {# variables}}', { count: keptVariables.length }) : null,
    keptInjectEdits > 0 ? t('{count, plural, one {# inject edit} other {# inject edits}}', { count: keptInjectEdits }) : null,
    keptScenarioEdits > 0 ? t('{count, plural, one {# scenario edit} other {# scenario edits}}', { count: keptScenarioEdits }) : null,
    keptElements.length > 0 ? t('{count, plural, one {# inject of yours} other {# injects of yours}}', { count: keptElements.length }) : null,
    t('targets, teams, schedule and tags'),
  ].filter(Boolean).join(', ');
  // Every row of the section, the targets, teams, schedule and tags row included.
  const staysCount = keptVariables.length + keptOthers.length + keptElements.length + 1;
  const changesCount = taken.length + changedElements.length;
  const todoCount = conflicts.length + (needTargets > 0 ? 1 : 0) + (preview?.warnings.length ? 1 : 0);
  // "(v2.0.0, 9 Nov 2026)": the revision's version and date, so the user knows which one it is.
  const revisionFacts = [preview?.target_version && `v${preview.target_version}`, revisionDate && fsd(revisionDate)].filter(Boolean);
  const revisionDetails = revisionFacts.length > 0 ? ` (${revisionFacts.join(', ')})` : '';
  // The diff key in plain words, a version on each side as in the Threat Arsenal: "Now · v2.0.0" (with
  // "with your edits" when the current state is not exactly that version) and "After the update · v2.3.0".
  const currentVersion = preview?.current_version;
  const hasEdits = kept.length > 0 || conflicts.length > 0 || (preview?.elements.some(element => element.change === 'KEPT_USER') ?? false);
  let beforeLabel = String(t('Now'));
  if (currentVersion) {
    beforeLabel = String(hasEdits
      ? t('Now · v{version} with your edits', { version: currentVersion })
      : t('Now · v{version}', { version: currentVersion }));
  }
  let afterLabel = String(t('After the update · v{version}', { version: version ?? '' }));
  if (!version) {
    afterLabel = String(preview?.target_version
      ? t('After the rollback · revision #{number} (v{version})', {
          number: revisionNumber ?? '',
          version: preview.target_version,
        })
      : t('After the rollback · revision #{number}', { number: revisionNumber ?? '' }));
  }
  // Above a kept edit's content: it is the same now and after applying.
  const unchangedLabel = String(currentVersion
    ? t(version ? 'Now (v{version}) and after the update: unchanged' : 'Now (v{version}) and after the rollback: unchanged', { version: currentVersion })
    : t(version ? 'Now and after the update: unchanged' : 'Now and after the rollback: unchanged'));
  const section = {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 8,
  };
  const row = {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    flexWrap: 'wrap' as const,
  };

  return (
    <Dialog open onOpenChange={open => !open && onClose()}>
      <DialogContent size={applyError ? 'sm' : 'lg'}>
        <DialogTitle>
          {version
            ? t('Update to Filigran v{version}', { version })
            : t('Roll back to revision #{number}', { number: revisionNumber ?? '' })}
        </DialogTitle>
        <DialogBody>
          {applyError && (
            <Alert
              severity="error"
              title={applyErrorTitle}
              description={applyError === 'changed'
                ? t('See the latest preview before applying.')
                : t('Try again, or contact your administrator if it keeps failing.')}
            />
          )}
          {!applyError && !preview && <Text variant="content-base">{t('Loading')}</Text>}
          {!applyError && preview && (
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 20,
            }}
            >
              {/* What applying does, in one sentence. The details follow in three parts that open and close:
                  what changes (open: the decision), what stays (closed, its summary says what is inside), and
                  what the user needs to do (open when there is something, one line otherwise). */}
              <div style={section} data-testid="scenario-update-summary">
                {/* Undo is said here, where everyone reads, and again in the message after applying. */}
                <Text variant="content-base">
                  {version
                    ? t('The content moves to Filigran v{version}.', { version })
                    : t('The content goes back to revision #{number}{details}.', {
                        number: revisionNumber ?? '',
                        details: revisionDetails,
                      })}
                  {` ${t('You can roll back later from Versions.')}`}
                </Text>
              </div>
              <div>
                <Section
                  title={t('What changes')}
                  count={changesCount}
                  defaultExpanded
                  testId="scenario-update-changes"
                >
                  {preview.current_version && preview.target_version && preview.current_version !== preview.target_version && (
                    <Text variant="content-base">
                      {t('Version: v{from} → v{to}', {
                        from: preview.current_version,
                        to: preview.target_version,
                      })}
                    </Text>
                  )}
                  {preview.revoked_payloads.length > 0 && (
                    <Alert
                      severity={version ? 'error' : 'warning'}
                      title={`${version ? t('Uses a revoked version') : t('Uses a revoked version: these injects will fail')}: ${preview.revoked_payloads.join(', ')}`}
                    />
                  )}
                  {preview.payloads_to_install.length > 0 && (
                    <Alert severity="info" title={`${t('Payload versions installed with it')}: ${preview.payloads_to_install.join(', ')}`} />
                  )}
                  {notes.map(note => (
                    <div
                      key={note.version}
                      style={{
                        display: 'grid',
                        gridTemplateColumns: '64px minmax(0, 1fr)',
                        gap: 12,
                      }}
                    >
                      <Text variant="content-caption">{`v${note.version}`}</Text>
                      <Text variant="content-base">{note.changelog}</Text>
                    </div>
                  ))}
                  {changedElements.map((element) => {
                    let reason: ReactNode = null;
                    if (element.change === 'ADDED') {
                      reason = version ? t('New in v{version}', { version }) : t('In revision #{number}', { number: revisionNumber ?? '' });
                    } else if (element.change === 'REMOVED') {
                      reason = version ? t('Removed in v{version}', { version }) : t('Not in revision #{number}', { number: revisionNumber ?? '' });
                    }
                    let severity: 'low' | 'medium' | undefined;
                    if (element.change === 'ADDED') severity = 'low';
                    if (element.change === 'REMOVED') severity = 'medium';
                    return (
                      <div key={element.id} style={row}>
                        <Chip label={t(ELEMENT_LABELS[element.change])} severity={severity} />
                        <Text variant="content-base">{t('Inject “{title}”', { title: element.title ?? element.id })}</Text>
                        {reason && <Text variant="content-caption">{reason}</Text>}
                        {element.change === 'ADDED' && <Text variant="content-caption">{`· ${t('Needs targets')}`}</Text>}
                      </div>
                    );
                  })}
                  {taken.length > 0 && (
                    <ChangesTable
                      changes={taken.flatMap(change => rowsOf(change, change.local ?? change.base, change.applied))}
                      beforeLabel={String(beforeLabel)}
                      afterLabel={String(afterLabel)}
                    />
                  )}
                  {nothingChanges && <Text variant="content-base">{t('Nothing in the content changes.')}</Text>}
                </Section>
                {/* A kept edit does not change, so it is one quiet row, and its content opens as plain text. */}
                <Section
                  title={t('What stays')}
                  count={staysCount}
                  summary={staysSummary}
                  defaultExpanded={false}
                  testId="scenario-update-kept"
                >
                  {keptVariables.length > 0 && (
                    <div style={section}>
                      <div style={row}>
                        <Chip label={t('Kept')} />
                        <Text variant="content-base">{t('Variables ({count})', { count: keptVariables.length })}</Text>
                        {!viewing.has('variables') && (
                          <Text
                            variant="content-caption"
                            className="text-default-secondary"
                            style={{
                              minWidth: 0,
                              flex: 1,
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                              whiteSpace: 'nowrap',
                            }}
                          >
                            {keptVariables.map(item => item.name).join(', ')}
                          </Text>
                        )}
                        <Button
                          size="sm"
                          priority="tertiary"
                          style={{ marginLeft: 'auto' }}
                          aria-expanded={viewing.has('variables')}
                          onClick={() => setViewing((previous) => {
                            const next = new Set(previous);
                            if (next.has('variables')) next.delete('variables');
                            else next.add('variables');
                            return next;
                          })}
                        >
                          {viewing.has('variables') ? t('Hide') : t('View')}
                        </Button>
                      </div>
                      {viewing.has('variables') && (
                        <div style={{
                          display: 'grid',
                          gridTemplateColumns: 'max-content minmax(0, 1fr)',
                          alignItems: 'baseline',
                          columnGap: 16,
                          rowGap: 4,
                          paddingLeft: 12,
                        }}
                        >
                          {keptVariables.map(item => (
                            <Fragment key={item.key}>
                              <Text variant="content-base">{item.name}</Text>
                              <Text variant="content-caption" className="text-default-secondary">{item.value || t('(empty)')}</Text>
                            </Fragment>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                  {keptElements.map(element => (
                    <div key={element.id} style={row}>
                      <Chip label={t(ELEMENT_LABELS[element.change])} />
                      <Text variant="content-base">{t('Inject “{title}”', { title: element.title ?? element.id })}</Text>
                    </div>
                  ))}
                  {keptOthers.map((item) => {
                    const open = viewing.has(item.key);
                    return (
                      <div key={item.key} style={section}>
                        <div style={row}>
                          <Chip label={t('Kept')} />
                          <Text variant="content-base">{item.field}</Text>
                          {item.code
                            ? (
                                <Button
                                  size="sm"
                                  priority="tertiary"
                                  style={{ marginLeft: 'auto' }}
                                  aria-expanded={open}
                                  onClick={() => setViewing((previous) => {
                                    const next = new Set(previous);
                                    if (open) next.delete(item.key);
                                    else next.add(item.key);
                                    return next;
                                  })}
                                >
                                  {open ? t('Hide') : t('View')}
                                </Button>
                              )
                            : <Text variant="content-caption">{item.value || t('(empty)')}</Text>}
                        </div>
                        {item.code && open && (
                          <>
                            {/* Which version this is, as every block of content in the dialog says: here, the same on both sides. */}
                            <Text variant="content-caption">{unchangedLabel}</Text>
                            <pre
                              tabIndex={0}
                              aria-label={item.field}
                              style={{
                                margin: 0,
                                maxHeight: 320,
                                overflow: 'auto',
                                padding: '8px 12px',
                                borderRadius: 4,
                                border: '1px solid var(--border-elevation-subtle-soft)',
                                fontFamily: 'var(--font-content-code), ui-monospace, monospace',
                                fontSize: 'var(--text-content-code)',
                                lineHeight: '22px',
                                color: 'var(--text-default-primary)',
                              }}
                            >
                              {item.value || t('(empty)')}
                            </pre>
                          </>
                        )}
                      </div>
                    );
                  })}
                  <div style={row}>
                    <Chip label={t('Kept')} />
                    <Text variant="content-base">{t('Targets, teams, schedule and tags')}</Text>
                  </div>
                </Section>
                {/* Nothing to do is one line; something to do opens by default, so it is never missed. */}
                {todoCount === 0 && (
                  <div
                    style={{
                      ...row,
                      paddingTop: 12,
                      borderTop: '1px solid var(--border-elevation-subtle-soft)',
                    }}
                    data-testid="scenario-update-todo"
                  >
                    <CheckCircleOutlined fontSize="small" color="success" />
                    <Text variant="content-base">{t('Nothing to decide before applying.')}</Text>
                  </div>
                )}
                {todoCount > 0 && (
                  <Section
                    title={t('What you need to do')}
                    count={todoCount}
                    defaultExpanded
                    testId="scenario-update-todo"
                  >
                    {conflicts.length > 0 && (
                      <Text variant="content-base">
                        {version
                          ? t('Filigran changed things you edited. Pick which version to keep for each.')
                          : t('You edited things this rollback restores. Pick which version to keep for each.')}
                      </Text>
                    )}
                    {conflicts.length > 0 && (
                      <div style={section}>
                        <div style={{
                          ...row,
                          justifyContent: 'space-between',
                        }}
                        >
                          <Text variant="title-xs">{t('Conflicts ({count})', { count: conflicts.length })}</Text>
                          <div style={row}>
                            <Button size="sm" priority="secondary" onClick={() => chooseAll('KEEP_MINE')}>{t('Keep all mine')}</Button>
                            <Button size="sm" priority="secondary" onClick={() => chooseAll('TAKE_APPLIED')}>{t('Take all')}</Button>
                          </div>
                        </div>
                        {conflicts.map((conflict) => {
                          const choice = choices[conflict.id];
                          const choose = (value: ScenarioMergeChoice) => setChoices(previous => ({
                            ...previous,
                            [conflict.id]: value,
                          }));
                          let selected: 'theirs' | 'mine' | undefined;
                          if (choice === 'TAKE_APPLIED') selected = 'theirs';
                          if (choice === 'KEEP_MINE') selected = 'mine';
                          return (
                            <ConflictChoice
                              key={conflict.id}
                              title={`${conflict.title} · ${conflict.block}`}
                              theirsLabel={theirs}
                              mineLabel={t('Yours')}
                              theirs={<TextDiff text={conflict.applied} other={conflict.mine} />}
                              mine={<TextDiff text={conflict.mine} other={conflict.applied} />}
                              selected={selected}
                              onSelect={side => choose(side === 'theirs' ? 'TAKE_APPLIED' : 'KEEP_MINE')}
                              footer={conflict.editable && (
                                <div style={section}>
                                  <div>
                                    <Button size="sm" priority={choice === 'EDIT' ? 'primary' : 'tertiary'} onClick={() => choose('EDIT')}>
                                      {t('Edit')}
                                    </Button>
                                  </div>
                                  {choice === 'EDIT' && (
                                    <Textarea
                                      label={t('Value')}
                                      value={drafts[conflict.id] ?? draftOf(conflict.local)}
                                      onChange={event => setDrafts(previous => ({
                                        ...previous,
                                        [conflict.id]: event.target.value,
                                      }))}
                                      rows={4}
                                    />
                                  )}
                                </div>
                              )}
                            />
                          );
                        })}
                      </div>
                    )}
                    {needTargets > 0 && (
                      <Text variant="content-base">
                        {t('{count, plural, one {After: add targets to # new inject.} other {After: add targets to # new injects.}}', { count: needTargets })}
                      </Text>
                    )}
                    {preview.warnings.length > 0 && (
                      <Alert severity="warning" title={t('{count} things to check after applying', { count: preview.warnings.length })} />
                    )}
                  </Section>
                )}
              </div>
            </div>
          )}
        </DialogBody>
        <DialogFooter>
          <Button priority="secondary" onClick={onClose}>{applyError ? t('Close') : t('Cancel')}</Button>
          {applyError === 'changed' && (
            <Button onClick={() => {
              setApplyError(null);
              loadPreview();
            }}
            >
              {t('Show the latest preview')}
            </Button>
          )}
          {applyError !== 'changed' && (
            <Button onClick={apply} disabled={!preview || !ready || applying}>
              {applyLabel}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default ScenarioUpdateDialog;
