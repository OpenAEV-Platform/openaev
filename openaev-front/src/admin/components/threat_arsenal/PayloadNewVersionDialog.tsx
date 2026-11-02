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
import { Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { Fragment, type FunctionComponent, useEffect, useState } from 'react';

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
import { USAGE_TYPE_LABELS } from './payloadUsageLabels';

interface Props {
  // Contract of the version the save replaced, and the version it created.
  previousActionId: string;
  newPayloadId: string;
  newVersion: string;
  usages: number;
  onClose: () => void;
}

interface Source {
  version: PayloadVersionOutput;
  usages: PayloadUsageOutput[];
}

// After a save that added a version: every inject, atomic test and chained step still on an older
// version (with its scenario and type, as in the Versions tab), all selected; unticking keeps one on
// its version. The selected ones move to the new version (F30), decided here, not version by version later.
const PayloadNewVersionDialog: FunctionComponent<Props> = ({ previousActionId, newPayloadId, newVersion, onClose }) => {
  const { t } = useFormatter();
  const theme = useTheme();
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
          usages: usages.data,
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

  const toggle = (id: string) => setSelected((previous) => {
    const next = new Set(previous);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    return next;
  });

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
      border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
      borderRadius: theme.shape.borderRadius,
    }}
    >
      <Table size="small" stickyHeader aria-label={t('Injects on older versions')}>
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
                <TableCell colSpan={canMigrate ? 4 : 3}>
                  <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                  }}
                  >
                    <Text variant="content-base-bold">{`v${source.version.payload_version}`}</Text>
                    {source.version.payload_revoked && <Chip label={t('Revoked')} severity="critical" />}
                    <Text variant="content-compact" className="text-default-secondary">
                      {t('{count} injects', { count: String(source.usages.length) })}
                    </Text>
                  </div>
                </TableCell>
              </TableRow>
              {source.usages.map(usage => (
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
              ))}
            </Fragment>
          ))}
        </TableBody>
      </Table>
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
              {renderTable()}
              {pendingApproval && (
                <Text variant="content-compact" className="text-default-secondary">
                  {t('v{version} is waiting for approval: they stay on their versions until it is approved, then you can move them from the Versions tab.', { version: newVersion })}
                </Text>
              )}
            </>
          )}
        </DialogBody>
        <DialogFooter>
          {canMigrate && sources !== null && (
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
