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
import { ArrowForwardOutlined, CheckCircleOutlined, ExpandMoreOutlined } from '@mui/icons-material';
// fds:keep-mui the library has no Accordion yet
import { Accordion, AccordionDetails, AccordionSummary } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useState } from 'react';
import { Link } from 'react-router';

import ChangesTable from '../../../../../components/common/update/ChangesTable';
import { useFormatter } from '../../../../../components/i18n';
import { type ScenarioChangeOutput, type ScenarioPayloadVersionOutput } from '../../../../../utils/api-types';
import { MESSAGING$ } from '../../../../../utils/Environment';
import useCountLabel from '../../../../../utils/hooks/useCountLabel';
import { changeLabel, changesTableRow, elementLabel, valueText } from './scenarioChanges';
import ScenarioConflicts from './ScenarioConflicts';
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

interface SectionProps {
  title: string;
  count?: number;
  summary?: string;
  defaultExpanded: boolean;
  children: ReactNode;
}

const Section: FunctionComponent<SectionProps> = ({ title, count, summary, defaultExpanded, children }) => (
  <Accordion
    defaultExpanded={defaultExpanded}
    disableGutters
    elevation={0}
    square
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
      gap: 1.5,
    }}
    >
      {children}
    </AccordionDetails>
  </Accordion>
);

const ScenarioUpdateDialog: FunctionComponent<Props> = ({ target, onClose, onApplied }) => {
  const { t } = useFormatter();
  const counted = useCountLabel();
  const theme = useTheme();
  const row = {
    display: 'flex',
    flexWrap: 'wrap' as const,
    alignItems: 'baseline',
    gap: theme.spacing(1),
  };
  const applied = () => {
    MESSAGING$.notifySuccess(
      <span style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: theme.spacing(1),
      }}
      >
        {target.appliedMessage}
        <Link
          to={`/admin/scenarios/${encodeURIComponent(target.scenarioId)}/versions`}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: theme.spacing(0.5),
          }}
        >
          {t('Roll back')}
          <ArrowForwardOutlined fontSize="inherit" />
        </Link>
      </span>,
    );
    onApplied();
  };
  const update = useScenarioUpdate(target, applied);
  const { preview, refusal, conflicts, taken, kept, added, removed, applyError } = update;
  const notes = preview?.scenario_update_changelogs ?? [];
  // A kept edit shows its value, not a diff: a red / green diff reads like a change.
  const [shownKept, setShownKept] = useState<string | null>(null);

  const failure = refusal ?? applyError;
  if (failure !== null) {
    let title = refusal !== null ? String(t('The preview didn\'t load. Nothing was changed.')) : target.failedTitle;
    let description = String(t('Try again, or contact your administrator if it keeps failing.'));
    let next: ReactNode = refusal !== null
      ? <Button onClick={update.retryPreview}>{t('Try again')}</Button>
      : <Button onClick={update.apply} loading={update.applying}>{t('Try again')}</Button>;
    // A 400 gives its reason and no retry: the same request would be refused again.
    if (failure.status === 400) {
      description = failure.message || String(t('This change cannot be made. Contact your administrator if you need it.'));
      next = null;
    } else if (failure.status === 409) {
      title = String(t('The scenario changed since this preview. Nothing was changed.'));
      description = String(t('See the latest preview before applying.'));
      next = <Button onClick={update.showLatestPreview}>{t('Show the latest preview')}</Button>;
    }
    return (
      <Dialog open onOpenChange={open => !open && onClose()}>
        <DialogContent size="sm">
          <DialogTitle>{target.title}</DialogTitle>
          <DialogBody>
            {/* The dialog is a layer-2 surface: its Alerts paint layer 1. */}
            <Alert severity="warning" title={title} description={description} elevation={1} />
          </DialogBody>
          <DialogFooter>
            <Button priority="secondary" onClick={onClose}>{t('Close')}</Button>
            {next}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    );
  }

  const injectLine = (change: ScenarioChangeOutput, label: string, reason: string) => (
    <div key={change.scenario_change_id} style={row}>
      <Chip label={label} severity={change.scenario_change_status === 'ADDED' ? 'low' : 'medium'} />
      <Text variant="content-base">{elementLabel(change, t)}</Text>
      <Text variant="content-caption" className="text-default-secondary">{reason}</Text>
    </div>
  );

  const keptRow = (id: string, label: string, value?: string) => (
    <div key={id}>
      <div style={row}>
        <Chip label={t('Kept')} />
        <Text variant="content-base" style={{ flex: 1 }}>{label}</Text>
        {value !== undefined && (
          <Button priority="tertiary" size="sm" onClick={() => setShownKept(shownKept === id ? null : id)}>
            {shownKept === id ? t('Hide') : t('View')}
          </Button>
        )}
      </div>
      {shownKept === id && (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(0.5),
          paddingTop: theme.spacing(1),
        }}
        >
          <Text variant="content-caption" className="text-default-secondary">{target.keptCaption}</Text>
          <pre style={{
            margin: 0,
            padding: theme.spacing(1, 1.5),
            border: '1px solid var(--border-elevation-subtle-soft)',
            borderRadius: 'var(--radius-sm)',
            whiteSpace: 'pre-wrap',
            fontFamily: 'var(--font-content-code), monospace',
            fontSize: 'var(--text-content-code)',
          }}
          >
            {value || t('(empty)')}
          </pre>
        </div>
      )}
    </div>
  );

  const body = () => {
    if (preview === null) {
      return <Spinner />;
    }
    const changing = taken.length + added.length + removed.length;
    const lostDependencies = preview.scenario_update_lost_dependencies;
    const todo = conflicts.length + (added.length > 0 ? 1 : 0) + (lostDependencies.length > 0 ? 1 : 0);
    const beforeLabel = kept.length > 0
      ? t('Now · v{version} with your edits', { version: target.installedVersion })
      : t('Now · v{version}', { version: target.installedVersion });
    const staysSummary = kept.length > 0
      ? `${counted(kept.length, '1 edit of yours', '{count} edits of yours')}, ${t('targets, teams, schedule and tags')}`
      : t('Targets, teams, schedule and tags');
    return (
      <div style={{
        display: 'flex',
        flexDirection: 'column',
      }}
      >
        <Text variant="content-base" style={{ paddingBottom: theme.spacing(1.5) }}>
          {`${target.intro} ${t('You can roll back later from Versions.')}`}
        </Text>
        <Section title={t('What changes')} count={changing} defaultExpanded>
          <Text variant="content-compact" className="text-default-secondary">
            {target.installedVersion === target.version
              ? t('Version: v{version}, unchanged', { version: target.version })
              : t('Version: v{from} → v{to}', {
                  from: target.installedVersion,
                  to: target.version,
                })}
          </Text>
          {preview.scenario_update_revoked_payloads.length > 0 && (
            <Alert
              severity={target.revokedSeverity}
              title={t('Runs a revoked payload version: its injects will fail')}
              description={payloadNames(preview.scenario_update_revoked_payloads)}
              elevation={1}
            />
          )}
          {preview.scenario_update_payloads_to_install.length > 0 && (
            <Alert
              severity="info"
              title={t('Payload versions installed with it')}
              description={payloadNames(preview.scenario_update_payloads_to_install)}
              elevation={1}
            />
          )}
          {notes.map(note => (
            <div
              key={note.scenario_changelog_version}
              style={{
                display: 'grid',
                gridTemplateColumns: `${theme.spacing(8)} minmax(0, 1fr)`,
                gap: theme.spacing(1.5),
              }}
            >
              <Text variant="content-caption" className="text-default-secondary">{`v${note.scenario_changelog_version}`}</Text>
              <Text variant="content-base">{note.scenario_changelog}</Text>
            </div>
          ))}
          {added.map(change => injectLine(change, t('Will be added'), target.addedReason))}
          {removed.map(change => injectLine(change, t('Will be deleted'), target.removedReason))}
          {taken.length > 0 && (
            <ChangesTable
              rows={taken.map(change => changesTableRow(change, change.scenario_change_local, change.scenario_change_applied, t))}
              beforeLabel={beforeLabel}
              afterLabel={target.afterLabel}
            />
          )}
          {changing === 0 && <Text variant="content-base">{t('Nothing in the content changes.')}</Text>}
        </Section>
        <Section title={t('What stays')} count={kept.length + 1} summary={staysSummary} defaultExpanded={false}>
          {kept.map(change => keptRow(change.scenario_change_id, changeLabel(change, t), valueText(change, change.scenario_change_local)))}
          {keptRow('targets', t('Targets, teams, schedule and tags'))}
        </Section>
        {todo === 0
          ? (
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: theme.spacing(1),
                paddingTop: theme.spacing(1.5),
                borderTop: '1px solid var(--border-elevation-subtle-soft)',
              }}
              >
                <CheckCircleOutlined fontSize="small" color="success" />
                <Text variant="content-base">{t('Nothing to decide before applying.')}</Text>
              </div>
            )
          : (
              <Section title={t('What you need to do')} count={todo} defaultExpanded>
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
                {added.length > 0 && (
                  <Text variant="content-base">{counted(added.length, 'After: add targets to 1 new inject.', 'After: add targets to {count} new injects.')}</Text>
                )}
                {lostDependencies.length > 0 && (
                  <Alert
                    severity="warning"
                    title={t('Some injects lose a dependency: check them after applying.')}
                    description={lostDependencies.join(', ')}
                    elevation={1}
                  />
                )}
              </Section>
            )}
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
          <Button onClick={update.apply} loading={update.applying} disabled={!update.ready}>{target.applyLabel}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default ScenarioUpdateDialog;
