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

// A field name as people read it: main_focus becomes "Main focus".
const fieldLabel = (key: string) => {
  const words = key.replace(/^(scenario|inject)_/, '').replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
};

// A typed value starts from the client's own: plain text for a string, JSON otherwise.
const draftOf = (value: unknown) => (typeof value === 'string' ? value : JSON.stringify(value ?? null, null, 2));

// F20 in step 1: what an update or a rollback changes, and a side for each conflict.
const ScenarioUpdateDialog: FunctionComponent<Props> = ({ scenarioId, version, revisionId, revisionNumber, onClose, onApplied }) => {
  const { t } = useFormatter();
  const [preview, setPreview] = useState<ScenarioUpdatePreview | null>(null);
  const [choices, setChoices] = useState<Record<string, ScenarioMergeChoice>>({});
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [applying, setApplying] = useState(false);
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
        .map(key => ({
          field: `${titleOf(change.element, change.title)} · ${t(fieldLabel(key))}`,
          before: fieldText(before[key]),
          after: fieldText(after[key]),
        }));
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
        MESSAGING$.notifySuccess(version ? t('Scenario updated to v{version}', { version }) : t('Scenario rolled back'));
        onApplied();
      })
      .finally(() => setApplying(false));
  };

  const taken = preview?.changes.filter(change => change.status === 'TAKE_APPLIED') ?? [];
  const kept = preview?.changes.filter(change => change.status === 'KEEP_MINE') ?? [];
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
              <Alert severity="info" title={t('Your targets, teams, schedule and tags stay as they are.')} />
              {preview.revoked_payloads.length > 0 && (
                <Alert
                  severity={version ? 'error' : 'warning'}
                  title={`${version ? t('Runs a revoked payload') : t('Runs a revoked payload: launches will be refused')}: ${preview.revoked_payloads.join(', ')}`}
                />
              )}
              {preview.payloads_to_install.length > 0 && (
                <Alert severity="info" title={`${t('Payload versions installed with it')}: ${preview.payloads_to_install.join(', ')}`} />
              )}
              {conflicts.length > 0 && (
                <Text variant="content-base">
                  {version
                    ? t('Filigran changed what you customized. Keep one version for each conflict; the rest is combined.')
                    : t('You changed what the revision restores. Keep one version for each conflict; the rest is restored.')}
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
              {(notes.length > 0 || taken.length > 0 || preview.elements.length > 0) && (
                <div style={section}>
                  <Text variant="title-xs">
                    {version ? t('What\'s new in Filigran v{version}', { version }) : t('Restored from the revision')}
                  </Text>
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
                  {preview.elements.filter(element => element.change !== 'REMOVED_CONFLICT').map((element) => {
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
                    <ChangesTable changes={taken.flatMap(change => rowsOf(change, change.local ?? change.base, change.applied))} />
                  )}
                </div>
              )}
              {kept.length > 0 && (
                <div style={section}>
                  <Text variant="title-xs">{t('Your changes, kept')}</Text>
                  <ChangesTable changes={kept.flatMap(change => rowsOf(change, change.base, change.local))} />
                </div>
              )}
              {preview.warnings.length > 0 && (
                <Alert severity="warning" title={t('{count} points to check after applying', { count: preview.warnings.length })} />
              )}
              {conflicts.length === 0 && taken.length === 0 && preview.elements.length === 0 && (
                <Text variant="content-base">{t('Nothing changes in the content.')}</Text>
              )}
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
