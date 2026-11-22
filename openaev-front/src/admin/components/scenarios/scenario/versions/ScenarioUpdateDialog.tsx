import {
  Alert,
  Button,
  Chip,
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogTitle,
  Spinner,
  Text,
} from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import ChangesTable from '../../../../../components/common/update/ChangesTable';
import { useFormatter } from '../../../../../components/i18n';
import { type ScenarioChangeOutput, type ScenarioPayloadVersionOutput } from '../../../../../utils/api-types';
import { changesTableRow, elementLabel } from './scenarioChanges';
import ScenarioConflicts from './ScenarioConflicts';
import ScenarioUpdateSummary from './ScenarioUpdateSummary';
import { type ScenarioUpdateTarget } from './scenarioUpdateTargets';
import useScenarioUpdate from './useScenarioUpdate';

interface Props {
  target: ScenarioUpdateTarget;
  onClose: () => void;
  onApplied: () => void;
}

const payloadNames = (payloads: ScenarioPayloadVersionOutput[]) => payloads
  .map(payload => `${payload.payload_name ?? ''} v${payload.payload_version}`.trim())
  .join(', ');

// What an update or a rollback changes, the side to keep for each conflict, then applying it.
const ScenarioUpdateDialog: FunctionComponent<Props> = ({ target, onClose, onApplied }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const column = {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: theme.spacing(1.5),
  };
  const update = useScenarioUpdate(target, onApplied);
  const { preview, conflicts, taken, kept, added, removed } = update;
  const notes = preview?.scenario_update_changelogs ?? [];

  const injectLine = (change: ScenarioChangeOutput, label: string, reason: string) => (
    <div
      key={change.scenario_change_id}
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        alignItems: 'center',
        gap: theme.spacing(1),
      }}
    >
      <Chip label={label} severity={change.scenario_change_status === 'ADDED' ? 'low' : 'medium'} />
      <Text variant="content-base">{elementLabel(change, t)}</Text>
      <Text variant="content-caption">{change.scenario_change_status === 'ADDED' ? `${reason} · ${t('Needs targets')}` : reason}</Text>
    </div>
  );

  const body = () => {
    if (preview === null) {
      return <Spinner />;
    }
    const nothing = conflicts.length + taken.length + kept.length + added.length + removed.length === 0;
    return (
      <div style={{
        ...column,
        gap: theme.spacing(2.5),
      }}
      >
        <ScenarioUpdateSummary
          title={target.summaryTitle}
          conflicts={conflicts.length}
          counts={[
            {
              label: target.takenLabel(String(taken.length)),
              count: taken.length,
              severity: 'info',
            },
            {
              label: t('Your edits kept: {count}', { count: String(kept.length) }),
              count: kept.length,
            },
            {
              label: t('Conflicts to decide: {count}', { count: String(conflicts.length) }),
              count: conflicts.length,
              severity: 'medium',
            },
            {
              label: t('Injects added: {count}', { count: String(added.length) }),
              count: added.length,
            },
            {
              label: t('Injects removed: {count}', { count: String(removed.length) }),
              count: removed.length,
            },
          ]}
        />
        <Alert severity="info" title={t('Your targets, teams, schedule and tags stay as they are.')} />
        {preview.scenario_update_revoked_payloads.length > 0 && (
          <Alert
            severity={target.revokedSeverity}
            title={t('Runs a revoked payload version: its injects will fail')}
            description={payloadNames(preview.scenario_update_revoked_payloads)}
          />
        )}
        {preview.scenario_update_payloads_to_install.length > 0 && (
          <Alert
            severity="info"
            title={t('Payload versions installed with it')}
            description={payloadNames(preview.scenario_update_payloads_to_install)}
          />
        )}
        {conflicts.length > 0 && (
          <ScenarioConflicts
            target={target}
            conflicts={conflicts}
            choices={update.choices}
            edits={update.edits}
            choose={update.choose}
            chooseAll={update.chooseAll}
            startEdit={update.startEdit}
            edit={update.edit}
          />
        )}
        {(notes.length > 0 || added.length > 0 || removed.length > 0 || taken.length > 0) && (
          <div style={column}>
            <Text variant="title-xs">{target.newsTitle}</Text>
            {notes.map(note => (
              <div
                key={note.scenario_changelog_version}
                style={{
                  display: 'grid',
                  gridTemplateColumns: `${theme.spacing(8)} minmax(0, 1fr)`,
                  gap: theme.spacing(1.5),
                }}
              >
                <Text variant="content-caption">{`v${note.scenario_changelog_version}`}</Text>
                <Text variant="content-base">{note.scenario_changelog}</Text>
              </div>
            ))}
            {added.map(change => injectLine(change, t('Will be added'), target.addedReason))}
            {removed.map(change => injectLine(change, t('Will be deleted'), target.removedReason))}
            {taken.length > 0 && (
              <ChangesTable
                rows={taken.map(change => changesTableRow(change, change.scenario_change_local, change.scenario_change_applied, t))}
                beforeLabel={t('Before · now')}
                afterLabel={target.afterLabel}
              />
            )}
          </div>
        )}
        {kept.length > 0 && (
          <div style={column}>
            <Text variant="title-xs">{target.keptTitle}</Text>
            <ChangesTable
              rows={kept.map(change => changesTableRow(change, change.scenario_change_base, change.scenario_change_local, t))}
              beforeLabel={t('Before · Filigran v{version}', { version: target.installedVersion })}
              afterLabel={t('After · your edit')}
            />
          </div>
        )}
        {preview.scenario_update_lost_dependencies.length > 0 && (
          <Alert
            severity="warning"
            title={t('Some injects lose a dependency: check them after applying.')}
            description={preview.scenario_update_lost_dependencies.join(', ')}
          />
        )}
        {nothing && <Text variant="content-base">{t('Nothing changes in the content.')}</Text>}
      </div>
    );
  };

  return (
    <Dialog open onOpenChange={open => !open && onClose()}>
      <DialogContent size="lg">
        <DialogTitle>{target.title}</DialogTitle>
        <DialogBody>{body()}</DialogBody>
        <DialogFooter>
          <Button priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
          <Button onClick={update.apply} disabled={!update.ready || update.applying}>{target.applyLabel}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default ScenarioUpdateDialog;
