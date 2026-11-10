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
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';
import { Link } from 'react-router';

import {
  changeThreatArsenalVersion,
  fetchThreatArsenalUsages,
  fetchThreatArsenalVersions,
} from '../../../actions/threat_arsenals/threatArsenal-version-actions';
import { FDS_GROUP_ROW_STYLE, FDS_SELECT_ALL_BANNER_STYLE } from '../../../components/common/fdsTableSpec';
import { useFormatter } from '../../../components/i18n';
import {
  type PayloadUsageOutput,
  type PayloadVersionChangeOutput,
  type PayloadVersionOutput,
} from '../../../utils/api-types';
import { MESSAGING$ } from '../../../utils/Environment';

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

// After a save that added a version: one row per older version something still runs, with how many
// injects and scenarios, all selected; unticking a version keeps its injects on it. The selected ones
// move to the new version (F30). The same design for 1 inject or hundreds; a finer choice, inject by
// inject, is in the Versions tab.
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
          // A scenario's injects together, so whole scenarios can be (un)ticked at a glance.
          usages: usages.data.toSorted((a, b) => (a.usage_scenario_name ?? '').localeCompare(b.usage_scenario_name ?? '')
            || (a.usage_name ?? '').localeCompare(b.usage_name ?? '', undefined, { numeric: true })),
        }))));
      setSources(loaded);
      setSelected(new Set(loaded.flatMap(source => source.usages.map(usage => usage.usage_id))));
    });
  }, [previousActionId, newPayloadId]);

  const allUsages = (sources ?? []).flatMap(source => source.usages);
  const allSelected = allUsages.length > 0 && selected.size === allUsages.length;
  const scenarioCount = (usages: PayloadUsageOutput[]) => new Set(usages.map(usage => usage.usage_scenario_id).filter(Boolean)).size;
  const newActionId = versions?.find(v => v.payload_id === newPayloadId)?.payload_action_id;

  const setGroup = (ids: string[], on: boolean) => setSelected((previous) => {
    const next = new Set(previous);
    ids.forEach(id => (on ? next.add(id) : next.delete(id)));
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

  // One row per older version with one tick for all of it, and "Select all" across versions, as in Gmail.
  const renderSummary = () => (
    <div

      style={{
        backgroundColor: 'var(--bg-elevation-default)',
        border: '1px solid var(--border-elevation-subtle-soft)',
        borderRadius: theme.shape.borderRadius,
        maxHeight: 420,
        overflowY: 'auto',
      }}
    >
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
      {(sources ?? []).map((source) => {
        const id = source.version.payload_id ?? source.version.payload_version ?? '';
        const ids = source.usages.map(usage => usage.usage_id);
        const chosen = ids.filter(usageId => selected.has(usageId)).length;
        return (
          <div key={id}>
            <div style={FDS_GROUP_ROW_STYLE}>
              <Checkbox
                aria-label={t('Every inject on v{version}', { version: source.version.payload_version ?? '' })}
                checked={chosen === ids.length || (chosen > 0 ? 'indeterminate' : false)}
                onCheckedChange={() => setGroup(ids, chosen !== ids.length)}
              />
              <Text variant="content-base-bold">{`v${source.version.payload_version}`}</Text>
              {source.version.payload_revoked && <Chip label={t('Revoked')} severity="critical" />}
              <Text variant="content-base" className="text-default-secondary">
                {`(${t('{count, plural, one {# inject} other {# injects}} in {scenarios, plural, one {# scenario} other {# scenarios}}', {
                  count: String(ids.length),
                  scenarios: String(scenarioCount(source.usages)),
                })})`}
              </Text>
              {chosen > 0 && chosen < ids.length && (
                <Text variant="content-compact" className="text-default-secondary" style={{ marginLeft: 'auto' }}>
                  {t('{count} selected', { count: String(chosen) })}
                </Text>
              )}
            </div>
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
          {/* The sentence introduces the list: 8px apart (DS spacing token). */}
          {sources === null ? <Spinner /> : (
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 'calc(var(--spacing) * 2)',
            }}
            >
              <Text variant="content-base">
                {t('{count} scenario injects, atomic tests or chained steps use older versions:', { count: String(allUsages.length) })}
              </Text>
              {renderSummary()}
            </div>
          )}
        </DialogBody>
        <DialogFooter>
          {/* A finer choice, inject by inject, with filters and room, in the Versions tab. */}
          {sources !== null && newActionId && (
            <Button asChild priority="tertiary" style={{ marginRight: 'auto' }}>
              <Link to={`/admin/threat-arsenal?action=${newActionId}&tab=versions&focus=migrate`} onClick={onClose}>
                {t('Choose in the Versions tab')}
                <ArrowForwardOutlined fontSize="small" style={{ marginLeft: 6 }} />
              </Link>
            </Button>
          )}
          <Button priority="secondary" onClick={onClose}>{t('Later')}</Button>
          <Button onClick={migrate} disabled={moving || selected.size === 0 || sources === null}>
            {moving ? <Spinner size="sm" /> : t('Move {count} to v{version}', {
              count: String(selected.size),
              version: newVersion,
            })}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default PayloadNewVersionDialog;
