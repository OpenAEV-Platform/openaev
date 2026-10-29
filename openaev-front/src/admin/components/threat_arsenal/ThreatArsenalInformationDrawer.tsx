import { Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
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
} from '../../../utils/api-types';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import CatalogPayloadVersionPreview from './catalog/CatalogPayloadVersionPreview';
import PayloadHistoryTab from './PayloadHistoryTab';
import PayloadVersionSelect from './PayloadVersionSelect';
import PayloadVersionsTab from './PayloadVersionsTab';
import ThreatArsenalActionOverview from './ThreatArsenalActionOverview';
import ThreatArsenalUseActions from './ThreatArsenalUseActions';

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

interface Props {
  open: boolean;
  onClose: () => void;
  threatArsenalAction: ThreatArsenalAction | null;
}

const ThreatArsenalInformationDrawer: FunctionComponent<Props> = ({
  open,
  onClose,
  threatArsenalAction,
}) => {
  const { t } = useFormatter();
  const canInstall = useAbility().can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);

  const [loading, setLoading] = useState(false);
  const [fullOutput, setFullOutput] = useState<ThreatArsenalActionFullOutput | null>(null);
  const [tab, setTab] = useState<'overview' | 'versions' | 'history'>('overview');
  // Every version of the payload, installed or only in the catalogue, and the one shown.
  const [versions, setVersions] = useState<PayloadVersionOutput[]>([]);
  const [catalogVersions, setCatalogVersions] = useState<CatalogItemOutput[]>([]);
  const [shownVersion, setShownVersion] = useState<string | null>(null);
  const [installing, setInstalling] = useState(false);
  const [versionsLoading, setVersionsLoading] = useState(false);

  const payload = threatArsenalAction?.action_payload;
  const shownRow = versions.find(v => v.payload_version === shownVersion);
  // Null when the shown version is not installed: only its catalogue metadata exists.
  let shownActionId = threatArsenalAction?.injector_contract_id ?? null;
  if (shownRow) {
    shownActionId = shownRow.payload_installed ? shownRow.payload_action_id ?? null : null;
  }

  const loadVersions = () => {
    if (!threatArsenalAction || !payload?.payload_version) {
      setVersions([]);
      return Promise.resolve([] as PayloadVersionOutput[]);
    }
    setVersionsLoading(true);
    return fetchThreatArsenalVersions(threatArsenalAction.injector_contract_id).then((response) => {
      const loaded = response.data as PayloadVersionOutput[];
      setVersions(loaded);
      return loaded;
    }).finally(() => setVersionsLoading(false));
  };

  useEffect(() => {
    if (!open || !threatArsenalAction) {
      setVersions([]);
      setCatalogVersions([]);
      setShownVersion(null);
      return;
    }
    setTab('overview');
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
        loading={loading}
      />
    );
  };

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={t('Action information')}
    >
      {threatArsenalAction == null ? <></> : (
        <>
          {shownActionId && !shownRow?.payload_revoked && <ThreatArsenalUseActions actionId={shownActionId} />}
          {payload && (
            <Tabs value={tab} onValueChange={value => setTab(value as 'overview' | 'versions' | 'history')} panels="external">
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
            {tab === 'versions' && payload && <PayloadVersionsTab actionId={threatArsenalAction.injector_contract_id} onChanged={loadVersions} />}
            {tab === 'history' && payload && <PayloadHistoryTab actionId={threatArsenalAction.injector_contract_id} />}
            {(tab === 'overview' || !payload) && renderOverview()}
          </div>
        </>
      )}
    </Drawer>
  );
};

export default ThreatArsenalInformationDrawer;
