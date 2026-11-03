import {
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogBody,
  DialogContent,
  DialogFooter,
  DialogTitle,
  IconButton,
  Spinner,
  Text,
} from '@filigran/design-system';
import { ArrowForwardOutlined, ExpandLess, ExpandMore } from '@mui/icons-material';
// fds:keep-mui the library has no Table yet (Figma DS Table 8582:34772 only); dressed to its spec by FDS_TABLE_SX
import { Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Fragment, type FunctionComponent, useEffect, useState } from 'react';
import { Link } from 'react-router';

import {
  changeThreatArsenalVersion,
  fetchThreatArsenalUsages,
  fetchThreatArsenalVersions,
} from '../../../actions/threat_arsenals/threatArsenal-version-actions';
import { useFormatter } from '../../../components/i18n';
import {
  type PayloadUsageOutput,
  type PayloadVersionChangeOutput,
  type PayloadVersionOutput,
} from '../../../utils/api-types';
import { MESSAGING$ } from '../../../utils/Environment';
import { FDS_GROUP_ROW_STYLE, FDS_SELECT_ALL_BANNER_STYLE, FDS_TABLE_SX } from './fdsTableSpec';
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
const PAGE_SIZE = 10;

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
  const theme = useTheme();
  const [versions, setVersions] = useState<PayloadVersionOutput[] | null>(null);
  const [sources, setSources] = useState<Source[] | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [moving, setMoving] = useState(false);
  // Large list: how many injects of each older version are shown (0 = folded).
  const [shown, setShown] = useState<Record<string, number>>({});

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

  const renderTable = () => (
    <div style={{
      maxHeight: 360,
      overflowY: 'auto',
      border: '1px solid var(--border-elevation-subtle-soft)',
      borderRadius: theme.shape.borderRadius,
    }}
    >
      <Table size="small" stickyHeader sx={FDS_TABLE_SX} aria-label={t('Injects on older versions')}>
        <TableHead>
          <TableRow>
            {canMigrate && (
              <TableCell padding="checkbox">
                <Checkbox
                  aria-label={t('Select all')}
                  checked={allSelected || (selected.size > 0 ? 'indeterminate' : false)}
                  onCheckedChange={() => setSelected(allSelected ? new Set() : new Set(allUsages.map(usage => usage.usage_id)))}
                />
              </TableCell>
            )}
            <TableCell>{t('Inject')}</TableCell>
            <TableCell>{t('Scenario')}</TableCell>
            <TableCell>{t('Type')}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {(sources ?? []).map(source => (
            <Fragment key={source.version.payload_id}>
              <TableRow>
                <TableCell colSpan={canMigrate ? 4 : 3} sx={{ padding: '0 !important' }}>
                  <div style={FDS_GROUP_ROW_STYLE}>
                    <Text variant="content-base-bold">{`v${source.version.payload_version}`}</Text>
                    {source.version.payload_revoked && <Chip label={t('Revoked')} severity="critical" />}
                    <Text variant="content-base" className="text-default-secondary">
                      {`(${t('{count} injects', { count: String(source.usages.length) })})`}
                    </Text>
                  </div>
                </TableCell>
              </TableRow>
              {usageRows(source.usages)}
            </Fragment>
          ))}
        </TableBody>
      </Table>
    </div>
  );

  // Large list (A): one row per older version with one tick for all of it, its injects on demand,
  // and "Select all" across versions, as in Gmail.
  const renderSummary = () => (
    <div style={{
      border: '1px solid var(--border-elevation-subtle-soft)',
      borderRadius: theme.shape.borderRadius,
      maxHeight: 420,
      overflowY: 'auto',
    }}
    >
      {canMigrate && (
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
        const visible = shown[id] ?? 0;
        return (
          <div key={id}>
            <div style={FDS_GROUP_ROW_STYLE}>
              <IconButton
                size="sm"
                priority="tertiary"
                aria-expanded={visible > 0}
                aria-label={visible > 0
                  ? t('Hide the injects on v{version}', { version: source.version.payload_version ?? '' })
                  : t('Show the injects on v{version}', { version: source.version.payload_version ?? '' })}
                icon={visible > 0 ? <ExpandLess fontSize="small" /> : <ExpandMore fontSize="small" />}
                onClick={() => setShown({
                  ...shown,
                  [id]: visible > 0 ? 0 : PAGE_SIZE,
                })}
              />
              {canMigrate && (
                <Checkbox
                  aria-label={t('Every inject on v{version}', { version: source.version.payload_version ?? '' })}
                  checked={chosen === ids.length || (chosen > 0 ? 'indeterminate' : false)}
                  onCheckedChange={() => setGroup(ids, chosen !== ids.length)}
                />
              )}
              <Text variant="content-base-bold">{`v${source.version.payload_version}`}</Text>
              {source.version.payload_revoked && <Chip label={t('Revoked')} severity="critical" />}
              <Text variant="content-base" className="text-default-secondary">
                {`(${t('{count, plural, one {# inject} other {# injects}} in {scenarios, plural, one {# scenario} other {# scenarios}}', {
                  count: String(ids.length),
                  scenarios: String(scenarioCount(source.usages)),
                })})`}
              </Text>
              {canMigrate && chosen > 0 && chosen < ids.length && (
                <Text variant="content-compact" className="text-default-secondary" style={{ marginLeft: 'auto' }}>
                  {t('{count} selected', { count: String(chosen) })}
                </Text>
              )}
            </div>
            {visible > 0 && (
              <>
                <Table size="small" sx={FDS_TABLE_SX} aria-label={t('Injects on v{version}', { version: source.version.payload_version ?? '' })}>
                  <TableBody>{usageRows(source.usages.slice(0, visible))}</TableBody>
                </Table>
                {visible < ids.length && (
                  <div style={{ padding: '4px 12px 8px' }}>
                    <Button
                      priority="tertiary"
                      size="sm"
                      onClick={() => setShown({
                        ...shown,
                        [id]: visible + PAGE_SIZE,
                      })}
                    >
                      {t('Show {count} more', { count: String(Math.min(PAGE_SIZE, ids.length - visible)) })}
                    </Button>
                  </div>
                )}
              </>
            )}
          </div>
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
              <Text variant="content-base">
                {t('{count} scenario injects, atomic tests or chained steps use older versions:', { count: String(allUsages.length) })}
              </Text>
              {large ? renderSummary() : renderTable()}
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
