import {
  Alert,
  Button,
  Chip,
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogTitle,
  Radio,
  RadioGroup,
  Text,
  Textarea,
} from '@filigran/design-system';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import {
  applyScenarioUpdate,
  previewScenarioUpdate,
  type ScenarioMergeChange,
  type ScenarioMergeChoice,
  type ScenarioUpdatePreview,
} from '../../../../../actions/catalog/scenario-catalog-actions';
import { useFormatter } from '../../../../../components/i18n';
import { MESSAGING$ } from '../../../../../utils/Environment';

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

const ELEMENT_LABELS: Record<string, string> = {
  ADDED: 'Added',
  REMOVED: 'Removed',
  REMOVED_CONFLICT: 'Removed, changed locally',
  STAYS_DELETED: 'Stays deleted',
  KEPT_USER: 'Kept',
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

// A typed value starts from the client's own: plain text for a string, JSON otherwise.
const draftOf = (value: unknown) => (typeof value === 'string' ? value : JSON.stringify(value ?? null, null, 2));

// F20 in step 1: what an update or a rollback changes, and a side for each conflict.
const ScenarioUpdateDialog: FunctionComponent<Props> = ({ scenarioId, version, revisionId, revisionNumber, onClose, onApplied }) => {
  const { t } = useFormatter();
  const [preview, setPreview] = useState<ScenarioUpdatePreview | null>(null);
  const [choices, setChoices] = useState<Record<string, ScenarioMergeChoice>>({});
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [applying, setApplying] = useState(false);
  const target = version
    ? { version }
    : { revision_id: revisionId };
  const theirs = version ? t('Filigran version v{version}', { version }) : t('Revision #{number}', { number: revisionNumber ?? '' });

  useEffect(() => {
    previewScenarioUpdate(scenarioId, target).then(({ data }) => setPreview(data));
  }, [scenarioId, version, revisionId]);

  const titleOf = (element: string, title?: string) => {
    if (element === 'scenario') {
      return t('Scenario');
    }
    if (element === 'variables') {
      return t('Variables');
    }
    return title ?? element.split(':').slice(1).join(':');
  };
  const blockOf = (change: ScenarioMergeChange) => {
    if (change.key) {
      return change.block === 'variables' ? t('Variable {key}', { key: change.key }) : t('Argument {key}', { key: change.key });
    }
    return t(BLOCK_LABELS[`${change.element}:${change.block}`] ?? BLOCK_LABELS[change.block] ?? change.block);
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
            ? t('Update to v{version}', { version })
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
              <Text variant="content-caption">
                {t('Your targets, teams, schedule and tags stay as they are.')}
              </Text>
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
                  {conflicts.map(conflict => (
                    <div
                      key={conflict.id}
                      style={{
                        ...section,
                        paddingBottom: 8,
                      }}
                    >
                      <div style={row}>
                        <Text variant="content-base">{conflict.title}</Text>
                        <Chip label={conflict.block} />
                      </div>
                      <div style={{
                        display: 'grid',
                        gridTemplateColumns: '1fr 1fr',
                        gap: 12,
                      }}
                      >
                        <Text variant="content-caption">{`${t('Mine')}: ${conflict.mine}`}</Text>
                        <Text variant="content-caption">{`${theirs}: ${conflict.applied}`}</Text>
                      </div>
                      <RadioGroup
                        orientation="horizontal"
                        value={choices[conflict.id] ?? ''}
                        onValueChange={value => setChoices(previous => ({
                          ...previous,
                          [conflict.id]: value as ScenarioMergeChoice,
                        }))}
                      >
                        <Radio value="KEEP_MINE" label={t('Keep mine')} />
                        <Radio value="TAKE_APPLIED" label={version ? t('Take Filigran\'s') : t('Take the revision')} />
                        {conflict.editable && <Radio value="EDIT" label={t('Edit')} />}
                      </RadioGroup>
                      {choices[conflict.id] === 'EDIT' && (
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
                  ))}
                </div>
              )}
              {preview.elements.length > 0 && (
                <div style={section}>
                  <Text variant="title-xs">{t('Injects')}</Text>
                  {preview.elements.filter(element => element.change !== 'REMOVED_CONFLICT').map(element => (
                    <div key={element.id} style={row}>
                      <Chip label={t(ELEMENT_LABELS[element.change])} severity={element.change === 'ADDED' ? 'low' : undefined} />
                      <Text variant="content-base">{element.title ?? element.id}</Text>
                      {element.change === 'ADDED' && <Text variant="content-caption">{t('Needs targets')}</Text>}
                    </div>
                  ))}
                </div>
              )}
              {taken.length > 0 && (
                <div style={section}>
                  <Text variant="title-xs">{version ? t('Changed by Filigran') : t('Restored from the revision')}</Text>
                  {taken.map(change => (
                    <Text key={change.id} variant="content-base">{`${titleOf(change.element, change.title)} · ${blockOf(change)}`}</Text>
                  ))}
                </div>
              )}
              {kept.length > 0 && (
                <div style={section}>
                  <Text variant="title-xs">{t('Your changes, kept')}</Text>
                  {kept.map(change => (
                    <Text key={change.id} variant="content-base">{`${titleOf(change.element, change.title)} · ${blockOf(change)}`}</Text>
                  ))}
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
