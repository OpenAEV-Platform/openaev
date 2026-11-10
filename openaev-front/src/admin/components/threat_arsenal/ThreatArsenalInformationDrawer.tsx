import { Button, Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
import { PlayArrowOutlined } from '@mui/icons-material';
import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchCatalogPayloadVersions, installCatalogPayload } from '../../../actions/catalog/payload-catalog-actions';
import { fetchThreatArsenalAction } from '../../../actions/threat_arsenals/threatArsenal-actions';
import { fetchThreatArsenalVersions } from '../../../actions/threat_arsenals/threatArsenal-version-actions';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import {
  type CatalogItemOutput,
  type Payload,
  type PayloadVersionOutput,
  type ThreatArsenalAction,
  type ThreatArsenalActionFullOutput,
  type ThreatArsenalApprovalDecisionOutput,
} from '../../../utils/api-types';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import CatalogPayloadVersionPreview from './catalog/CatalogPayloadVersionPreview';
import PayloadHistoryTab from './PayloadHistoryTab';
import PayloadNewVersionDialog from './PayloadNewVersionDialog';
import PayloadVersionSelect from './PayloadVersionSelect';
import PayloadVersionsTab from './PayloadVersionsTab';
import ThreatArsenalActionOverview from './ThreatArsenalActionOverview';

const toPayload = (action: ThreatArsenalActionFullOutput): Payload => {
  return {
    payload_id: action.action_id,
    payload_name: action.action_labels?.en ?? Object.values(action.action_labels ?? {})[0] ?? '',
    payload_created_at: action.action_created_at,
    payload_updated_at: action.action_updated_at,
    payload_description: action.action_description,
    payload_execution_arch: action.action_execution_arch,
    payload_platforms: action.action_platforms ?? [],
    payload_source: action.action_source,
    payload_status: action.action_status,
    // Always set for payload-based actions, the only ones converted here.
    payload_approval_status: action.action_approval_status ?? 'PENDING',
    payload_type: action.action_type,
    payload_external_id: action.action_external_id,
    payload_arguments: action.action_arguments,
    payload_cleanup_command: action.action_cleanup_command,
    payload_cleanup_executor: action.action_cleanup_executor,
    payload_collector_type: action.action_collector_type,
    payload_detection_remediations: action.action_detection_remediations,
    payload_expectations: action.action_expectations,
    payload_expected_security_platforms: action.action_expected_security_platforms,
    payload_output_parsers: action.action_output_parsers,
    payload_prerequisites: action.action_prerequisites,
    command_content: action.command_content,
    command_executor: action.command_executor,
    dns_resolution_hostname: action.dns_resolution_hostname,
    executable_file: action.executable_file,
    file_drop_file: action.file_drop_file,
  } as Payload;
};

type DrawerTab = 'overview' | 'versions' | 'history';

interface Props {
  open: boolean;
  onClose: () => void;
  threatArsenalAction: ThreatArsenalAction | null;
  onRunTest?: (action: ThreatArsenalAction) => void;
  /** Called after an approve / reject decision, so the list can refresh the row. */
  onApprovalChanged?: (actionId: string, status: ThreatArsenalActionFullOutput['action_approval_status']) => void;
  /** The tab shown first: the review queues open on Versions, where a pending version is decided. */
  initialTab?: DrawerTab;
  /** Versions tab ready to migrate the usages left on older versions (a card's Migrate link). */
  focusMigration?: boolean;
}

const ThreatArsenalInformationDrawer: FunctionComponent<Props> = ({
  open,
  onClose,
  threatArsenalAction,
  onRunTest,
  onApprovalChanged,
  initialTab,
  focusMigration,
}) => {
  const { t } = useFormatter();
  const canInstall = useAbility().can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
  const canRunTest = useAbility().can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);

  const [loading, setLoading] = useState(false);
  const [fullOutput, setFullOutput] = useState<ThreatArsenalActionFullOutput | null>(null);
  const [tab, setTab] = useState<DrawerTab>(initialTab ?? 'overview');
  // Every version of the payload, installed or only in the catalogue, and the one shown.
  const [versions, setVersions] = useState<PayloadVersionOutput[]>([]);
  const [catalogVersions, setCatalogVersions] = useState<CatalogItemOutput[]>([]);
  const [shownVersion, setShownVersion] = useState<string | null>(null);
  const [installing, setInstalling] = useState(false);
  const [versionsLoading, setVersionsLoading] = useState(false);
  // An approved version whose replaced version still runs injects: the approver is asked to move them.
  const [moveOffer, setMoveOffer] = useState<ThreatArsenalApprovalDecisionOutput | null>(null);

  const payload = threatArsenalAction?.action_payload;
  const shownRow = versions.find(v => v.payload_version === shownVersion);
  // Null when the shown version is not installed: only its catalogue metadata exists.
  let shownActionId = threatArsenalAction?.injector_contract_id ?? null;
  if (shownRow) {
    shownActionId = shownRow.payload_installed ? shownRow.payload_action_id ?? null : null;
  }

  const loadVersions = () => {
    // An action opened from a link (notification, review queue) has no version on its row yet: the
    // versions say which one it is.
    if (!threatArsenalAction || !payload) {
      setVersions([]);
      return Promise.resolve([] as PayloadVersionOutput[]);
    }
    setVersionsLoading(true);
    return fetchThreatArsenalVersions(threatArsenalAction.injector_contract_id).then((response) => {
      const loaded = response.data as PayloadVersionOutput[];
      setVersions(loaded);
      setShownVersion(shown => shown ?? loaded.find(v => v.payload_current)?.payload_version ?? null);
      return loaded;
    }).finally(() => setVersionsLoading(false));
  };
  // A decision taken in the Versions tab: refresh the action, then, after an approval, offer the
  // approver to move the injects still on the version it replaces.
  const handleDecided = (decision: ThreatArsenalApprovalDecisionOutput) => {
    setFullOutput(previous => (previous
      ? {
          ...previous,
          action_approval_status: decision.action_approval_status,
          action_approval_latest: decision.action_approval_latest,
        }
      : previous));
    onApprovalChanged?.(decision.action_id, decision.action_approval_status);
    loadVersions();
    if (decision.action_approval_status !== 'APPROVED' || !decision.action_previous_version_id) return;
    if ((decision.action_previous_version_usages ?? 0) > 0) {
      setMoveOffer(decision);
      return;
    }
    // The replaced version runs nothing, but an older one may: the dialog lists every older version
    // still in use, so offer it whenever there is one.
    fetchThreatArsenalVersions(decision.action_id)
      .then((response: { data: PayloadVersionOutput[] }) => {
        const olderInUse = response.data.some(version => version.payload_action_id !== decision.action_id
          && version.payload_installed
          && (version.payload_usage_count ?? 0) > 0);
        if (olderInUse) setMoveOffer(decision);
      })
      .catch(() => undefined);
  };

  useEffect(() => {
    if (!open || !threatArsenalAction) {
      setVersions([]);
      setCatalogVersions([]);
      setShownVersion(null);
      return;
    }
    setTab(initialTab ?? 'overview');
    setCatalogVersions([]);
    setShownVersion(payload?.payload_version ?? null);
    loadVersions();
  }, [open, threatArsenalAction]);

  useEffect(() => {
    // Reset state on every (re)entry so a previous in-flight fetch whose
    // `.finally()` was skipped by the cancellation guard can't leave the drawer
    // stuck on a spinner when the next opened action resolves quickly.
    if (!open || !shownActionId) {
      setLoading(false);
      setFullOutput(null);
      return undefined;
    }

    setFullOutput(null);
    setLoading(true);
    let cancelled = false;
    // Always fetch the full details: both payload-based contracts (execution,
    // arguments, cleanup) and injector-based ones (e.g. AI Red Team) carry
    // predefined expectations we surface in the drawer.
    fetchThreatArsenalAction(shownActionId)
      .then((result) => {
        if (cancelled) return;
        setFullOutput(result.data as ThreatArsenalActionFullOutput);
      })
      .catch(() => {
        if (cancelled) return;
        setFullOutput(null);
      })
      .finally(() => {
        // Keep the cancellation guard so a stale finally from a superseded
        // fetch can't clear the spinner of a newer in-flight fetch.
        if (cancelled) return;
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, shownActionId]);

  const externalId = versions.find(v => v.payload_external_id)?.payload_external_id;
  const shownNotInstalled = shownRow && !shownRow.payload_installed;
  useEffect(() => {
    if (shownNotInstalled && externalId && catalogVersions.length === 0) {
      fetchCatalogPayloadVersions(externalId).then(setCatalogVersions);
    }
  }, [shownNotInstalled, externalId]);

  const installShown = () => {
    if (!externalId || !shownVersion) return;
    setInstalling(true);
    installCatalogPayload(externalId, shownVersion)
      .then(() => loadVersions())
      .finally(() => setInstalling(false));
  };

  // Only payload-based contracts produce a payload object (execution/arguments/
  // cleanup sections). Expectations are passed separately so injector-based
  // contracts without a payload still display them.
  const selectedPayload: Payload | null = fullOutput && payload ? toPayload(fullOutput) : null;

  // The overview follows the version picked in the dropdown.
  const shownAction: ThreatArsenalAction | null = threatArsenalAction && shownRow && payload && shownActionId
    ? {
        ...threatArsenalAction,
        injector_contract_id: shownActionId,
        action_payload: {
          ...payload,
          payload_id: shownRow.payload_id,
          payload_version: shownRow.payload_version,
          payload_status: shownRow.payload_status,
          payload_revoked: shownRow.payload_revoked,
        },
      }
    : threatArsenalAction;

  const shownCatalogItem = catalogVersions.find(item => item.catalog_item_version === shownVersion);

  const renderOverview = () => {
    if (shownNotInstalled) {
      return shownCatalogItem
        ? <CatalogPayloadVersionPreview item={shownCatalogItem} canInstall={canInstall} installing={installing} onInstall={installShown} />
        : null;
    }
    return shownAction && (
      <ThreatArsenalActionOverview
        action={fullOutput?.action_labels
          ? {
              ...shownAction,
              action_labels: fullOutput.action_labels,
            }
          : shownAction}
        payload={selectedPayload}
        expectations={fullOutput?.action_expectations}
        expectationDetails={fullOutput?.action_expectation_details}
        expectedSecurityPlatforms={fullOutput?.action_expected_security_platforms}
        providing={fullOutput?.action_providing}
        lastModifiedByName={fullOutput ? (fullOutput.action_last_modified_by_name ?? null) : undefined}
        loading={loading}
      />
    );
  };

  return (
    <>
      <Drawer
        open={open}
        handleClose={onClose}
        title={t('Action information')}
        headerActions={onRunTest && canRunTest && shownActionId && shownAction && !shownRow?.payload_revoked && (
          <Button startIcon={<PlayArrowOutlined fontSize="small" />} onClick={() => onRunTest(shownAction)}>{t('Run a test')}</Button>
        )}
      >
        {threatArsenalAction == null ? <></> : (
          <>
            {payload && (
              <Tabs value={tab} onValueChange={value => setTab(value as DrawerTab)} panels="external">
                <TabsList actions={tab === 'overview' && shownVersion && (
                  <PayloadVersionSelect
                    value={shownVersion}
                    options={versions
                      .filter(v => !!v.payload_version)
                      .map(v => ({
                        version: v.payload_version as string,
                        latest: v.payload_is_latest,
                        installed: v.payload_installed,
                        revoked: v.payload_revoked,
                        approvalStatus: v.payload_approval_status,
                      }))}
                    onChange={setShownVersion}
                    loading={versionsLoading}
                  />
                )}
                >
                  <TabsTrigger value="overview">{t('Overview')}</TabsTrigger>
                  <TabsTrigger value="versions">{t('Versions')}</TabsTrigger>
                  <TabsTrigger value="history">{t('History')}</TabsTrigger>
                </TabsList>
              </Tabs>
            )}
            <div style={{ marginTop: payload ? 16 : 0 }}>
              {/* Approval lives in the Versions tab: each pending version is decided in its own row. */}
              {tab === 'versions' && payload && (
                <PayloadVersionsTab
                  actionId={threatArsenalAction.injector_contract_id}
                  onChanged={loadVersions}
                  onDecided={handleDecided}
                  focusMigration={focusMigration}
                />
              )}
              {tab === 'history' && payload && <PayloadHistoryTab actionId={threatArsenalAction.injector_contract_id} />}
              {(tab === 'overview' || !payload) && renderOverview()}
            </div>
          </>
        )}
      </Drawer>
      {moveOffer?.action_previous_version_id && (
        <PayloadNewVersionDialog
          previousActionId={moveOffer.action_previous_version_id}
          newPayloadId={moveOffer.action_payload_id ?? ''}
          newVersion={moveOffer.action_payload_version ?? ''}
          usages={moveOffer.action_previous_version_usages ?? 0}
          onClose={() => {
            setMoveOffer(null);
            loadVersions();
          }}
        />
      )}
    </>
  );
};

export default ThreatArsenalInformationDrawer;
