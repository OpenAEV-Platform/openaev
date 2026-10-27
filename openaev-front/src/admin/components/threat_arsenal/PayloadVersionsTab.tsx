import { Button, Checkbox, Chip, Spinner } from '@filigran/design-system';
import { ExpandLess, ExpandMore } from '@mui/icons-material';
import { Alert, Box, Collapse, Pagination, Skeleton, Table, TableBody, TableCell, TableHead, TablePagination, TableRow, Typography } from '@mui/material';
import { type FunctionComponent, type ReactNode, useEffect, useState } from 'react';

import { installCatalogPayload } from '../../../actions/catalog/payload-catalog-actions';
import {
  changeThreatArsenalVersion,
  fetchThreatArsenalUsages,
  fetchThreatArsenalVersions,
} from '../../../actions/threat_arsenals/threatArsenal-version-actions';
import { SkeletonRows } from '../../../components/common/content_versions/ContentVersionsTable';
import { useFormatter } from '../../../components/i18n';
import {
  type PayloadUsageOutput,
  type PayloadVersionChangeOutput,
  type PayloadVersionOutput,
} from '../../../utils/api-types';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import PayloadVersionSelect from './PayloadVersionSelect';

const VERSIONS_PER_PAGE = 5;
const USAGES_PER_PAGE_OPTIONS = [10, 25, 50];

interface Props {
  actionId: string;
  onChanged?: () => void;
}

interface UsagesProps {
  source: PayloadVersionOutput;
  usages: PayloadUsageOutput[];
  targets: PayloadVersionOutput[];
  canMigrate: boolean;
  loading: boolean;
  installAction?: ReactNode;
  onMigrate: (source: PayloadVersionOutput, target: PayloadVersionOutput, usages: PayloadUsageOutput[]) => Promise<PayloadVersionChangeOutput | null>;
}

const USAGE_TYPE_LABELS: Record<string, string> = {
  inject: 'Inject',
  atomic_testing: 'Atomic testing',
  step: 'Chained step',
};

// The injects, atomic tests and chained steps on one version, moved together to another version (F30).
const VersionUsages: FunctionComponent<UsagesProps> = ({ source, usages, targets, canMigrate, loading, installAction, onMigrate }) => {
  const { t } = useFormatter();
  const usageCount = source.payload_usage_count ?? 0;
  const [expanded, setExpanded] = useState(usageCount > 0 || !!source.payload_revoked);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(USAGES_PER_PAGE_OPTIONS[0]);
  // The latest version is the usual target, so it is picked by default.
  const defaultTarget = (targets.find(v => v.payload_is_latest) ?? targets[0])?.payload_version ?? '';
  const [target, setTarget] = useState(defaultTarget);
  const [migrating, setMigrating] = useState(false);
  const [result, setResult] = useState<PayloadVersionChangeOutput | null>(null);
  const [names, setNames] = useState<Record<string, string>>({});
  useEffect(() => {
    setSelected(new Set());
    setPage(0);
  }, [usages]);
  useEffect(() => setTarget(defaultTarget), [defaultTarget]);

  // The header checkbox selects every usage of the version, on every page.
  const allSelected = usages.length > 0 && selected.size === usages.length;
  const pageUsages = usages.slice(page * rowsPerPage, (page + 1) * rowsPerPage);
  const toggle = (id: string) => setSelected((previous) => {
    const next = new Set(previous);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    return next;
  });

  const migrate = () => {
    const targetVersion = targets.find(v => v.payload_version === target);
    if (!targetVersion) return;
    const chosen = usages.filter(usage => selected.has(usage.usage_id));
    // The moved usages leave this list: keep their names for the refusal messages.
    setNames(Object.fromEntries(chosen.map(usage => [usage.usage_id, usage.usage_name ?? usage.usage_id])));
    setMigrating(true);
    onMigrate(source, targetVersion, chosen)
      .then(setResult)
      .finally(() => setMigrating(false));
  };

  const sectionSx = {
    paddingInline: 2,
    paddingBlock: 1.5,
    borderTop: 1,
    borderColor: 'divider',
  };

  return (
    <Box sx={{
      border: 1,
      borderColor: 'divider',
      borderRadius: 1,
      overflow: 'hidden',
    }}
    >
      <Box
        role="button"
        tabIndex={0}
        aria-expanded={expanded}
        onClick={() => setExpanded(!expanded)}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            setExpanded(!expanded);
          }
        }}
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          paddingInline: 2,
          paddingBlock: 1.5,
          backgroundColor: 'action.hover',
          cursor: 'pointer',
        }}
      >
        {expanded ? <ExpandLess fontSize="small" /> : <ExpandMore fontSize="small" />}
        <Typography variant="body1" sx={{ fontWeight: 600 }}>{`v${source.payload_version ?? '-'}`}</Typography>
        {!source.payload_installed && <Chip label={t('Not installed')} />}
        {source.payload_revoked && <Chip label={t('Revoked')} severity="critical" />}
        <Box sx={{ flex: 1 }} />
        {source.payload_installed
          ? (
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                {t('{count} injects', { count: String(loading ? usageCount : usages.length) })}
              </Typography>
            )
          : <span onClick={event => event.stopPropagation()}>{installAction}</span>}
      </Box>
      <Collapse in={expanded} unmountOnExit>
        {source.payload_changelog && (
          <Typography
            variant="body2"
            sx={{
              ...sectionSx,
              color: 'text.secondary',
            }}
          >
            {source.payload_changelog}
          </Typography>
        )}
        {source.payload_revoked && (
          <Alert severity="warning" sx={{ borderRadius: 0 }}>{t('These injects are skipped at execution: migrate them to another version.')}</Alert>
        )}
        {result && (
          <Alert severity={(result.refused ?? []).length > 0 ? 'warning' : 'success'} sx={{ borderRadius: 0 }} onClose={() => setResult(null)}>
            {`${t('Moved')}: ${result.moved_ids?.length ?? 0}`}
            {(result.refused ?? []).map(refusal => (
              <div key={refusal.usage_id}>{`${t('Not moved')}: ${names[refusal.usage_id] ?? refusal.usage_id} (${refusal.reason})`}</div>
            ))}
          </Alert>
        )}
        {source.payload_installed && !loading && usages.length === 0 && (
          <Typography
            variant="body2"
            sx={{
              ...sectionSx,
              color: 'text.secondary',
            }}
          >
            {t('No inject uses this version')}
          </Typography>
        )}
        {source.payload_installed && ((loading && usageCount > 0) || usages.length > 0) && (
          <Table size="small" aria-label={t('Injects on v{version}', { version: source.payload_version ?? '-' })}>
            <TableHead>
              <TableRow>
                {canMigrate && (
                  <TableCell padding="checkbox">
                    <Checkbox
                      aria-label={t('Select all')}
                      checked={allSelected}
                      onCheckedChange={() => setSelected(allSelected ? new Set() : new Set(usages.map(usage => usage.usage_id)))}
                    />
                  </TableCell>
                )}
                <TableCell>{t('Inject')}</TableCell>
                <TableCell>{t('Scenario')}</TableCell>
                <TableCell>{t('Type')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading && usages.length === 0 && (
                <SkeletonRows rows={Math.min(usageCount, rowsPerPage)} columns={canMigrate ? 4 : 3} />
              )}
              {pageUsages.map(usage => (
                <TableRow
                  key={usage.usage_id}
                  hover={canMigrate}
                  selected={selected.has(usage.usage_id)}
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
            </TableBody>
          </Table>
        )}
        {usages.length > USAGES_PER_PAGE_OPTIONS[0] && (
          <TablePagination
            component="div"
            count={usages.length}
            page={page}
            onPageChange={(_, newPage) => setPage(newPage)}
            rowsPerPage={rowsPerPage}
            rowsPerPageOptions={USAGES_PER_PAGE_OPTIONS}
            onRowsPerPageChange={(event) => {
              setRowsPerPage(parseInt(event.target.value, 10));
              setPage(0);
            }}
          />
        )}
        {canMigrate && usages.length > 0 && (
          <Box sx={{
            ...sectionSx,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            gap: 1,
          }}
          >
            {targets.length === 0
              ? <Typography variant="body2" sx={{ color: 'text.secondary' }}>{t('No other version to migrate to')}</Typography>
              : (
                  <>
                    <Typography
                      variant="body2"
                      sx={{
                        color: 'text.secondary',
                        marginRight: 'auto',
                      }}
                    >
                      {t('{count} selected', { count: String(selected.size) })}
                    </Typography>
                    <Typography variant="body2">{t('Migrate to')}</Typography>
                    <PayloadVersionSelect
                      value={target}
                      options={targets.map(v => ({
                        version: v.payload_version as string,
                        latest: v.payload_is_latest,
                        installed: v.payload_installed,
                      }))}
                      onChange={setTarget}
                    />
                    <Button disabled={selected.size === 0 || target === '' || migrating} onClick={migrate}>
                      {migrating ? <Spinner size="sm" /> : t('Migrate')}
                    </Button>
                  </>
                )}
          </Box>
        )}
      </Collapse>
    </Box>
  );
};

// F7: every version of the payload, installed or only in the catalogue, with the injects on each one.
const PayloadVersionsTab: FunctionComponent<Props> = ({ actionId, onChanged }) => {
  const { t } = useFormatter();
  // Installing and moving usages both need to manage the arsenal, as the API requires.
  const canManage = useAbility().can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
  const [versions, setVersions] = useState<PayloadVersionOutput[]>([]);
  // Usages per version action, fetched only for the versions of the page shown.
  const [usages, setUsages] = useState<Record<string, PayloadUsageOutput[]>>({});
  const [loadingUsages, setLoadingUsages] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [installing, setInstalling] = useState<string | null>(null);

  const listed = versions.filter(v => !!v.payload_version);
  const pageCount = Math.max(1, Math.ceil(listed.length / VERSIONS_PER_PAGE));
  const pageVersions = listed.slice((page - 1) * VERSIONS_PER_PAGE, page * VERSIONS_PER_PAGE);

  const fetchUsages = (wanted: PayloadVersionOutput[]) => {
    const ids = wanted
      .filter(v => v.payload_installed && v.payload_action_id && (v.payload_usage_count ?? 0) > 0)
      .map(v => v.payload_action_id as string);
    if (ids.length === 0) return Promise.resolve();
    setLoadingUsages(previous => new Set([...previous, ...ids]));
    return Promise.all(ids.map(id => fetchThreatArsenalUsages(id)
      .then(response => setUsages(previous => ({
        ...previous,
        [id]: response.data as PayloadUsageOutput[],
      })))
      .finally(() => setLoadingUsages((previous) => {
        const next = new Set(previous);
        next.delete(id);
        return next;
      })))).then(() => undefined);
  };

  const load = () => fetchThreatArsenalVersions(actionId)
    .then((response) => {
      const loaded = response.data as PayloadVersionOutput[];
      setUsages({});
      setVersions(loaded);
      return loaded;
    })
    .finally(() => setLoading(false));
  useEffect(() => {
    setLoading(true);
    setPage(1);
    load();
  }, [actionId]);

  // Every reload resets the usages, so the page shown fetches them again.
  useEffect(() => {
    fetchUsages(pageVersions.filter(v => v.payload_action_id && !usages[v.payload_action_id]));
  }, [versions, page]);

  const reload = () => load().then(() => onChanged?.());

  const install = (version: PayloadVersionOutput) => {
    if (version.payload_external_id && version.payload_version) {
      setInstalling(version.payload_version);
      installCatalogPayload(version.payload_external_id, version.payload_version)
        .then(reload)
        .finally(() => setInstalling(null));
    }
  };

  // A target that is not installed yet is installed first, then the usages move to it.
  const migrate = async (source: PayloadVersionOutput, target: PayloadVersionOutput, chosen: PayloadUsageOutput[]) => {
    let targetId = target.payload_id;
    if (!target.payload_installed && target.payload_external_id && target.payload_version) {
      await installCatalogPayload(target.payload_external_id, target.payload_version);
      const refreshed = (await fetchThreatArsenalVersions(actionId)).data as PayloadVersionOutput[];
      targetId = refreshed.find(v => v.payload_version === target.payload_version)?.payload_id;
    }
    if (!targetId || !source.payload_action_id) return null;
    const response = await changeThreatArsenalVersion(source.payload_action_id, {
      target_payload_id: targetId,
      inject_ids: chosen.filter(usage => usage.usage_type !== 'step').map(usage => usage.usage_id),
      step_ids: chosen.filter(usage => usage.usage_type === 'step').map(usage => usage.usage_id),
    });
    await reload();
    return response.data as PayloadVersionChangeOutput;
  };

  const installButton = (version: PayloadVersionOutput) => !version.payload_revoked && (
    <Button size="sm" priority="secondary" disabled={!canManage || installing !== null} onClick={() => install(version)}>
      {installing === version.payload_version ? <Spinner size="sm" /> : t('Install')}
    </Button>
  );

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 16,
    }}
    >
      {loading && versions.length === 0 && [0, 1].map(key => <Skeleton key={key} variant="rounded" height={96} />)}
      {pageVersions.map(source => (
        <VersionUsages
          key={source.payload_version}
          source={source}
          usages={source.payload_action_id ? usages[source.payload_action_id] ?? [] : []}
          targets={versions.filter(v => v.payload_version && v.payload_version !== source.payload_version && !v.payload_revoked)}
          canMigrate={canManage}
          loading={!!source.payload_action_id && (loadingUsages.has(source.payload_action_id) || (!usages[source.payload_action_id] && (source.payload_usage_count ?? 0) > 0))}
          installAction={installButton(source)}
          onMigrate={migrate}
        />
      ))}
      {pageCount > 1 && (
        <Pagination
          count={pageCount}
          page={page}
          onChange={(_, newPage) => setPage(newPage)}
          size="small"
          sx={{
            display: 'flex',
            justifyContent: 'center',
          }}
        />
      )}
    </div>
  );
};

export default PayloadVersionsTab;
