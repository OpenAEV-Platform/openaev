import {
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogTitle,
  Spinner,
  Text,
} from '@filigran/design-system';
import { ArrowForwardOutlined } from '@mui/icons-material';
// fds:keep-mui the library has no Table yet (Figma DS Table 8582:34772 only); dressed to its spec by FDS_TABLE_SX
import { Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material';
import { type FunctionComponent, useEffect, useState } from 'react';
import { Link } from 'react-router';

import {
  changeThreatArsenalVersion,
  fetchThreatArsenalUsages,
  fetchThreatArsenalVersions,
} from '../../../actions/threat_arsenals/threatArsenal-version-actions';
import FdsAccordion from '../../../components/common/accordion/FdsAccordion';
import { FDS_SELECT_ALL_BANNER_STYLE, FDS_TABLE_SX } from '../../../components/common/fdsTableSpec';
import { useFormatter } from '../../../components/i18n';
import {
  type PayloadUsageOutput,
  type PayloadVersionChangeOutput,
  type PayloadVersionOutput,
} from '../../../utils/api-types';
import { MESSAGING$ } from '../../../utils/Environment';
import { USAGE_TYPE_LABELS } from './payloadUsageLabels';

interface Props {
  // Contract of the version the save replaced, and the version it created.
  previousActionId: string;
  newPayloadId: string;
  newVersion: string;
  usages: number;
  onClose: () => void;
}

// Above this many, a list of every inject no longer fits a dialog: one row per older version (all
// of it in one tick), its injects on demand, and the Versions tab for a finer choice.
const LARGE_LIST = 10;

interface Source {
  version: PayloadVersionOutput;
  usages: PayloadUsageOutput[];
}

// After a save that added a version: every inject, atomic test and chained step still on an older
// version (with its scenario and type, as in the Versions tab), all selected; unticking keeps one on
// its version. The selected ones move to the new version (F30), decided here, not version by version later.
// A few: one table of them all. Many: a summary per older version, details on demand (LARGE_LIST).
const PayloadNewVersionDialog: FunctionComponent<Props> = ({ previousActionId, newPayloadId, newVersion, onClose }) => {
  const { t } = useFormatter();
  const [versions, setVersions] = useState<PayloadVersionOutput[] | null>(null);
  const [sources, setSources] = useState<Source[] | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [moving, setMoving] = useState(false);

  useEffect(() => {
    fetchThreatArsenalVersions(previousActionId).then(async (response: { data: PayloadVersionOutput[] }) => {
      setVersions(response.data);
      // Every older installed version something still runs, newest first.
      const used = response.data.filter(v => v.payload_installed
        && v.payload_id !== newPayloadId
        && !!v.payload_action_id
        && (v.payload_usage_count ?? 0) > 0);
      const loaded = await Promise.all(used.map(version => fetchThreatArsenalUsages(version.payload_action_id as string)
        .then((usages: { data: PayloadUsageOutput[] }) => ({
          version,
          // A scenario's injects together, so whole scenarios can be (un)ticked at a glance.
          usages: usages.data.toSorted((a, b) => (a.usage_scenario_name ?? '').localeCompare(b.usage_scenario_name ?? '')
            || (a.usage_name ?? '').localeCompare(b.usage_name ?? '', undefined, { numeric: true })),
        }))));
      setSources(loaded);
      setSelected(new Set(loaded.flatMap(source => source.usages.map(usage => usage.usage_id))));
    });
  }, [previousActionId, newPayloadId]);

  // A version waiting for approval cannot run yet: what runs stays where it is until it is approved.
  const pendingApproval = versions?.find(v => v.payload_id === newPayloadId)?.payload_approval_status === 'PENDING';
  const allUsages = (sources ?? []).flatMap(source => source.usages);
  const allSelected = allUsages.length > 0 && selected.size === allUsages.length;
  const canMigrate = !pendingApproval;
  const large = allUsages.length > LARGE_LIST;
  const scenarioCount = (usages: PayloadUsageOutput[]) => new Set(usages.map(usage => usage.usage_scenario_id).filter(Boolean)).size;
  const newActionId = versions?.find(v => v.payload_id === newPayloadId)?.payload_action_id;

  const toggle = (id: string) => setSelected((previous) => {
    const next = new Set(previous);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    return next;
  });

  const setGroup = (ids: string[], on: boolean) => setSelected((previous) => {
    const next = new Set(previous);
    ids.forEach(id => (on ? next.add(id) : next.delete(id)));
    return next;
  });

  const usageRows = (usages: PayloadUsageOutput[]) => usages.map(usage => (
    <TableRow
      key={usage.usage_id}
      hover={canMigrate}
      selected={canMigrate && selected.has(usage.usage_id)}
      onClick={canMigrate ? () => toggle(usage.usage_id) : undefined}
      sx={{ cursor: canMigrate ? 'pointer' : 'default' }}
    >
      {canMigrate && (
        <TableCell padding="checkbox" onClick={event => event.stopPropagation()}>
          <Checkbox
            aria-label={usage.usage_name ?? usage.usage_id}
            checked={selected.has(usage.usage_id)}
            onCheckedChange={() => toggle(usage.usage_id)}
          />
        </TableCell>
      )}
      <TableCell>{usage.usage_name ?? usage.usage_id}</TableCell>
      <TableCell>{usage.usage_scenario_name ?? '-'}</TableCell>
      <TableCell>{t(USAGE_TYPE_LABELS[usage.usage_type] ?? usage.usage_type)}</TableCell>
    </TableRow>
  ));

  const migrate = () => {
    if (!sources) return;
    setMoving(true);
    Promise.all(sources
      .map(source => ({
        source,
        chosen: source.usages.filter(usage => selected.has(usage.usage_id)),
      }))
      .filter(({ chosen }) => chosen.length > 0)
      .map(({ source, chosen }) => changeThreatArsenalVersion(source.version.payload_action_id as string, {
        target_payload_id: newPayloadId,
        inject_ids: chosen.filter(u => u.usage_type !== 'step').map(u => u.usage_id),
        step_ids: chosen.filter(u => u.usage_type === 'step').map(u => u.usage_id),
      }).then((response: { data: PayloadVersionChangeOutput }) => response.data)))
      .then((results) => {
        const refused = results.reduce((sum, r) => sum + (r.refused?.length ?? 0), 0);
        const moved = results.reduce((sum, r) => sum + (r.moved_ids?.length ?? 0), 0);
        if (refused > 0) {
          MESSAGING$.notifyError(t('{count} usages could not move and stay on the previous version', { count: String(refused) }));
        } else {
          MESSAGING$.notifySuccess(t('{count} usages moved to v{version}', {
            count: String(moved),
            version: newVersion,
          }));
        }
      })
      .finally(() => {
        setMoving(false);
        onClose();
      });
  };

  // One DS accordion per older version, as in the Versions and History tabs: the version and its
  // count as the title, a tick for all of its injects on the right, its injects inside. A few injects:
  // every version open; many: all folded, with "Select all" across versions as in Gmail.
  const renderGroups = () => (
    <div style={{
      maxHeight: 420,
      overflowY: 'auto',
    }}
    >
      {canMigrate && large && (
        <div style={FDS_SELECT_ALL_BANNER_STYLE}>
          <Text variant="content-base">
            {t('{selected} of {count} selected', {
              selected: String(selected.size),
              count: String(allUsages.length),
            })}
          </Text>
          {allSelected
            ? <Button priority="tertiary" size="sm" onClick={() => setSelected(new Set())}>{t('Clear selection')}</Button>
            : (
                <Button priority="tertiary" size="sm" onClick={() => setSelected(new Set(allUsages.map(usage => usage.usage_id)))}>
                  {t('Select all {count}', { count: String(allUsages.length) })}
                </Button>
              )}
        </div>
      )}
      {(sources ?? []).map((source) => {
        const id = source.version.payload_id ?? source.version.payload_version ?? '';
        const ids = source.usages.map(usage => usage.usage_id);
        const chosen = ids.filter(usageId => selected.has(usageId)).length;
        return (
          <FdsAccordion
            key={id}
            density="list"
            defaultExpanded={!large}
            title={`v${source.version.payload_version}`}
            meta={(
              <>
                {source.version.payload_revoked && <Chip label={t('Revoked')} severity="critical" />}
                <Text variant="content-base" className="text-default-secondary">
                  {`(${t('{count, plural, one {# inject} other {# injects}} in {scenarios, plural, one {# scenario} other {# scenarios}}', {
                    count: String(ids.length),
                    scenarios: String(scenarioCount(source.usages)),
                  })})`}
                </Text>
              </>
            )}
            end={canMigrate && (
              <>
                {chosen > 0 && chosen < ids.length && (
                  <Text variant="content-compact" className="text-default-secondary">
                    {t('{count} selected', { count: String(chosen) })}
                  </Text>
                )}
                <Checkbox
                  aria-label={t('Every inject on v{version}', { version: source.version.payload_version ?? '' })}
                  checked={chosen === ids.length || (chosen > 0 ? 'indeterminate' : false)}
                  onCheckedChange={() => setGroup(ids, chosen !== ids.length)}
                />
              </>
            )}
            contentStyle={{ padding: '0 0 12px' }}
          >
            <Table size="small" sx={FDS_TABLE_SX} aria-label={t('Injects on v{version}', { version: source.version.payload_version ?? '' })}>
              <TableHead>
                <TableRow>
                  {canMigrate && <TableCell padding="checkbox" />}
                  <TableCell>{t('Inject')}</TableCell>
                  <TableCell>{t('Scenario')}</TableCell>
                  <TableCell>{t('Type')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>{usageRows(source.usages)}</TableBody>
            </Table>
          </FdsAccordion>
        );
      })}
    </div>
  );

  return (
    <Dialog open onOpenChange={open => !open && onClose()}>
      <DialogContent size="lg">
        <DialogTitle>{t('New version v{version}', { version: newVersion })}</DialogTitle>
        <DialogBody>
          {sources === null ? <Spinner /> : (
            <>
              {/* The sentence introduces the table: 8px apart (DS spacing token). */}
              <div style={{
                display: 'flex',
                flexDirection: 'column',
                gap: 'calc(var(--spacing) * 2)',
              }}
              >
                <Text variant="content-base">
                  {t('{count} scenario injects, atomic tests or chained steps use older versions:', { count: String(allUsages.length) })}
                </Text>
                {renderGroups()}
              </div>
              {pendingApproval && (
                <Text variant="content-compact" className="text-default-secondary">
                  {t('v{version} is waiting for approval: they stay on their versions until it is approved, then you can move them from the Versions tab.', { version: newVersion })}
                </Text>
              )}
            </>
          )}
        </DialogBody>
        <DialogFooter>
          {/* Large list (C): a finer choice, with filters and room, in the Versions tab. */}
          {canMigrate && sources !== null && large && newActionId && (
            <Button asChild priority="tertiary" style={{ marginRight: 'auto' }}>
              <Link to={`/admin/threat-arsenal?action=${newActionId}&tab=versions&focus=migrate`} onClick={onClose}>
                {t('Choose in the Versions tab')}
                <ArrowForwardOutlined fontSize="small" style={{ marginLeft: 6 }} />
              </Link>
            </Button>
          )}
          {canMigrate && sources !== null && !large && (
            <Text variant="content-compact" className="text-default-secondary" style={{ marginRight: 'auto' }}>
              {t('Unticked ones stay on their version. You can move them later from the Versions tab.')}
            </Text>
          )}
          <Button priority="secondary" onClick={onClose}>{canMigrate ? t('Later') : t('Close')}</Button>
          {canMigrate && (
            <Button onClick={migrate} disabled={moving || selected.size === 0 || sources === null}>
              {moving ? <Spinner size="sm" /> : t('Move {count} to v{version}', {
                count: String(selected.size),
                version: newVersion,
              })}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default PayloadNewVersionDialog;
