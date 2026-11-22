import { Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useEffect, useState } from 'react';

import { fetchThreatArsenalAction } from '../../../actions/threat_arsenals/threatArsenal-actions';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import {
  type Payload,
  type ThreatArsenalAction,
  type ThreatArsenalActionFullOutput,
} from '../../../utils/api-types';
import { isFeatureEnabled } from '../../../utils/utils';
import PayloadHistoryTab from './history/PayloadHistoryTab';
import { type OpenedTab } from './hook/useOpenedAction';
import PayloadRevocationBanner from './revocation/PayloadRevocationBanner';
import ThreatArsenalActionOverview from './ThreatArsenalActionOverview';
import PayloadVersionsTab from './versions/PayloadVersionsTab';
import usePayloadVersions from './versions/usePayloadVersions';

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
  initialTab?: OpenedTab;
}

const ThreatArsenalInformationDrawer: FunctionComponent<Props> = ({
  open,
  onClose,
  threatArsenalAction,
  initialTab,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const [loading, setLoading] = useState(false);
  const [fullOutput, setFullOutput] = useState<ThreatArsenalActionFullOutput | null>(null);
  const [tab, setTab] = useState<string>(initialTab ? 'versions' : 'overview');
  const showVersions = isFeatureEnabled('CATALOG') && !!threatArsenalAction?.action_payload;
  const versions = usePayloadVersions(threatArsenalAction?.injector_contract_id ?? '', open && showVersions);
  const shownVersion = versions?.find(v => v.payload_id === threatArsenalAction?.action_payload?.payload_id);
  const payloadTabs: Record<string, () => ReactNode> = {
    versions: () => <PayloadVersionsTab versions={versions} focusMove={initialTab === 'move'} />,
    history: () => <PayloadHistoryTab actionId={threatArsenalAction?.injector_contract_id ?? ''} />,
  };
  const payloadTab = showVersions ? payloadTabs[tab] : undefined;

  useEffect(() => setTab(initialTab ? 'versions' : 'overview'), [open, threatArsenalAction, initialTab]);

  useEffect(() => {
    // Reset state on every (re)entry so a previous in-flight fetch whose
    // `.finally()` was skipped by the cancellation guard can't leave the drawer
    // stuck on a spinner when the next opened action resolves quickly.
    if (!open || !threatArsenalAction) {
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
    fetchThreatArsenalAction(threatArsenalAction.injector_contract_id)
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
  }, [open, threatArsenalAction]);

  // Only payload-based contracts produce a payload object (execution/arguments/
  // cleanup sections). Expectations are passed separately so injector-based
  // contracts without a payload still display them.
  const selectedPayload: Payload | null
    = fullOutput && threatArsenalAction?.action_payload ? toPayload(fullOutput) : null;

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={t('Action information')}
    >
      {threatArsenalAction == null ? <></> : (
        <>
          {showVersions && (
            <Tabs value={tab} onValueChange={setTab} panels="external">
              <TabsList>
                <TabsTrigger value="overview">{t('Overview')}</TabsTrigger>
                <TabsTrigger value="versions">{t('Versions')}</TabsTrigger>
                <TabsTrigger value="history">{t('History')}</TabsTrigger>
              </TabsList>
            </Tabs>
          )}
          <div style={{ marginTop: showVersions ? theme.spacing(2) : 0 }}>
            {payloadTab
              ? payloadTab()
              : (
                  <>
                    {shownVersion?.payload_revoked && (
                      <div style={{ marginBottom: theme.spacing(2) }}>
                        <PayloadRevocationBanner version={shownVersion} />
                      </div>
                    )}
                    <ThreatArsenalActionOverview
                      action={threatArsenalAction}
                      payload={selectedPayload}
                      expectations={fullOutput?.action_expectations}
                      expectationDetails={fullOutput?.action_expectation_details}
                      expectedSecurityPlatforms={fullOutput?.action_expected_security_platforms}
                      providing={fullOutput?.action_providing}
                      loading={loading}
                    />
                  </>
                )}
          </div>
        </>
      )}
    </Drawer>
  );
};

export default ThreatArsenalInformationDrawer;
