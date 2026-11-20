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
import { type FunctionComponent, type ReactNode, useEffect, useMemo, useState } from 'react';

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

// F20 in step 1: what an update or a rollback changes, and a side for each conflict.
const ScenarioUpdateDialog: FunctionComponent<Props> = ({ scenarioId, version, revisionId, revisionNumber, revisionDate, onClose, onApplied }) => {
  const { t, fsd } = useFormatter();
  const [preview, setPreview] = useState<ScenarioUpdatePreview | null>(null);
  const [choices, setChoices] = useState<Record<string, ScenarioMergeChoice>>({});
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [applying, setApplying] = useState(false);
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

  useEffect(() => {
    previewScenarioUpdate(scenarioId, target).then(({ data }) => setPreview(data));
  }, [scenarioId, version, revisionId]);

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
    applyScenarioUpdate(scenarioId, {
      ...target,
      choices,
      edits,
      local_hash: preview.local_hash,
    })
      .then(() => {
        // Applying saves the current state as a revision first: said right after, when undo matters.
        MESSAGING$.notifySuccess(version
          ? t('Scenario updated to v{version}. To undo, roll back from Versions.', { version })
          : t('Scenario rolled back. To undo, roll back again from Versions.'));
        onApplied();
      })
      .finally(() => setApplying(false));
  };

  const taken = preview?.changes.filter(change => change.status === 'TAKE_APPLIED') ?? [];
  const kept = preview?.changes.filter(change => change.status === 'KEEP_MINE') ?? [];
  // One row per kept field, with the user's value (what stays after applying).
  const keptRows = kept.flatMap(change => rowsOf(change, change.base, change.local).map((item, index) => ({
    key: `${change.id}-${index}`,
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
      <DialogContent size="lg">
        <DialogTitle>
          {version
            ? t('Update to Filigran v{version}', { version })
            : t('Roll back to revision #{number}', { number: revisionNumber ?? '' })}
        </DialogTitle>
        <DialogBody>
          {!preview ? <Text variant="content-base">{t('Loading')}</Text> : (
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 20,
            }}
            >
              {/* What applying does, in one sentence, then its size (idea B): only what happens is counted,
                  a chip at 0 says nothing. The details follow in three parts: what changes, what stays, and
                  what the user needs to do. */}
              <div style={section} data-testid="scenario-update-summary">
                {/* Undo is said here, where everyone reads, and again in the message after applying. */}
                <Text variant="content-base">
                  {version
                    ? t('The content moves to Filigran v{version}.', { version })
                    : t('The content goes back to revision #{number}{details}.', {
                        number: revisionNumber ?? '',
                        details: revisionDetails,
                      })}
                  {` ${t('You can undo it later from Versions.')}`}
                </Text>
                {(() => {
                  const added = preview.elements.filter(element => element.change === 'ADDED').length;
                  const removed = preview.elements.filter(element => element.change === 'REMOVED').length;
                  const chips: {
                    key: string;
                    count: number;
                    label: string;
                    severity?: 'info' | 'medium';
                  }[] = [
                    {
                      key: 'taken',
                      count: taken.length,
                      label: version
                        ? t('Changes from Filigran: {count}', { count: String(taken.length) })
                        : t('Changes restored: {count}', { count: String(taken.length) }),
                      severity: 'info',
                    },
                    {
                      key: 'kept',
                      count: kept.length,
                      label: t('Your edits kept: {count}', { count: String(kept.length) }),
                    },
                    {
                      key: 'conflicts',
                      count: conflicts.length,
                      label: t('Conflicts to decide: {count}', { count: String(conflicts.length) }),
                      severity: 'medium',
                    },
                    {
                      key: 'added',
                      count: added,
                      label: t('Injects added: {count}', { count: String(added) }),
                    },
                    {
                      key: 'removed',
                      count: removed,
                      label: t('Injects removed: {count}', { count: String(removed) }),
                    },
                  ];
                  const shown = chips.filter(chip => chip.count > 0);
                  return shown.length > 0 && (
                    <div style={row}>
                      {shown.map(chip => <Chip key={chip.key} label={chip.label} severity={chip.severity} />)}
                    </div>
                  );
                })()}
              </div>
              {/* What changes. A diff here only shows what applying changes (idea A). */}
              <div style={section} data-testid="scenario-update-changes">
                <Text variant="title-xs">{t('What changes')}</Text>
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
              </div>
              {/* What stays: a kept edit does not change, so it is one quiet row, and its content opens as
                  plain text (no + / −). */}
              <div style={section} data-testid="scenario-update-kept">
                <Text variant="title-xs">{t('What stays')}</Text>
                {keptElements.map(element => (
                  <div key={element.id} style={row}>
                    <Chip label={t(ELEMENT_LABELS[element.change])} />
                    <Text variant="content-base">{t('Inject “{title}”', { title: element.title ?? element.id })}</Text>
                  </div>
                ))}
                {keptRows.map((item) => {
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
              </div>
              {/* What the user needs to do: before applying (conflicts), then after (targets, checks). */}
              <div style={section} data-testid="scenario-update-todo">
                <Text variant="title-xs">{t('What you need to do')}</Text>
                {conflicts.length === 0 && <Text variant="content-base">{t('Nothing to decide before applying.')}</Text>}
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
              </div>
            </div>
          )}
        </DialogBody>
        <DialogFooter>
          <Button priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
          <Button onClick={apply} disabled={!preview || !ready || applying}>
            {version ? t('Update') : t('Roll back')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default ScenarioUpdateDialog;
