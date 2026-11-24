import { Alert, Button, Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
import { PlayArrowOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';

import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import {
  type Payload,
  type ThreatArsenalAction,
  type ThreatArsenalActionFullOutput,
} from '../../../utils/api-types';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import CatalogPayloadVersionPreview from './catalog/CatalogPayloadVersionPreview';
import PayloadUpdateBanner from './customization/PayloadUpdateBanner';
import PayloadHistoryTab from './history/PayloadHistoryTab';
import useActionDrawer from './hook/useActionDrawer';
import useCanUpdateAction from './hook/useCanUpdateAction';
import { type OpenedTab } from './hook/useOpenedAction';
import PayloadRevocationBanner from './revocation/PayloadRevocationBanner';
import ThreatArsenalActionOverview from './ThreatArsenalActionOverview';
import { isShownRevoked } from './utils/threatArsenalStatusUtils';
import PayloadNewVersionDialog from './versions/PayloadNewVersionDialog';
import PayloadVersionSelect from './versions/PayloadVersionSelect';
import PayloadVersionsTab from './versions/PayloadVersionsTab';

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
  onUpdate?: (action: ThreatArsenalAction, replacedId?: string) => void;
  onRunTest?: (action: ThreatArsenalAction) => void;
}

const ThreatArsenalInformationDrawer: FunctionComponent<Props> = ({
  open,
  onClose,
  threatArsenalAction,
  initialTab,
  onUpdate,
  onRunTest,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const ability = useAbility();
  const canRunTest = ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
  const canInstall = ability.can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
  const canUpdate = useCanUpdateAction({ action_grant_resource_id: threatArsenalAction?.action_grant_resource_id ?? '' });
  const {
    tab,
    setTab,
    showVersions,
    versions,
    usages,
    reloadUsages,
    customization,
    shownId,
    show,
    installed,
    shownAction,
    otherVersion,
    details,
    loading,
    notInstalled,
    notInstalledVersion,
    shownCatalogueVersion,
    toMove,
    installing,
    install,
    notice,
    afterSave,
    closeNotice,
  } = useActionDrawer(open, threatArsenalAction, initialTab, onUpdate);
  const shownRevoked = isShownRevoked(shownAction);
  const payloadTabs: Record<string, () => ReactNode> = {
    versions: () => (
      <PayloadVersionsTab
        versions={versions}
        usages={usages}
        onMoved={reloadUsages}
        notInstalled={notInstalled}
        focusMove={initialTab === 'move'}
        installing={installing}
        onInstall={canInstall ? install : undefined}
      />
    ),
    history: () => <PayloadHistoryTab actionId={threatArsenalAction?.injector_contract_id ?? ''} />,
  };
  const payloadTab = showVersions ? payloadTabs[tab] : undefined;

  // Only payload-based contracts produce a payload object (execution/arguments/
  // cleanup sections). Expectations are passed separately so injector-based
  // contracts without a payload still display them.
  const selectedPayload: Payload | null
    = details && threatArsenalAction?.action_payload ? toPayload(details) : null;

  const renderOverview = () => {
    if (notInstalledVersion) {
      return shownCatalogueVersion && (
        <>
          <Alert
            style={{ marginBottom: theme.spacing(2) }}
            severity="info"
            title={t('This version is not installed')}
            action={canInstall && (
              <Button priority="secondary" size="sm" loading={installing === notInstalledVersion} onClick={() => install(notInstalledVersion)}>{t('Install')}</Button>
            )}
          />
          <CatalogPayloadVersionPreview version={shownCatalogueVersion} />
        </>
      );
    }
    return shownAction && (
      <>
        {!otherVersion && (
          <PayloadUpdateBanner actionId={shownAction.injector_contract_id} customization={customization} canUpdate={canUpdate} onUpdated={afterSave} />
        )}
        {installed?.payload_revoked && (
          <div style={{ marginBottom: theme.spacing(2) }}>
            <PayloadRevocationBanner version={installed} />
          </div>
        )}
        <ThreatArsenalActionOverview
          action={details?.action_labels
            ? {
                ...shownAction,
                action_labels: details.action_labels,
              }
            : shownAction}
          payload={selectedPayload}
          expectations={details?.action_expectations}
          expectationDetails={details?.action_expectation_details}
          expectedSecurityPlatforms={details?.action_expected_security_platforms}
          providing={details?.action_providing}
          loading={loading}
          toMove={toMove}
          newBase={otherVersion ? undefined : customization?.customization_update?.update_version}
        />
      </>
    );
  };

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={t('Action information')}
      headerActions={canRunTest && onRunTest && shownAction && !shownRevoked && (
        <Button startIcon={<PlayArrowOutlined fontSize="small" />} onClick={() => onRunTest(shownAction)}>{t('Run a test')}</Button>
      )}
    >
      {threatArsenalAction == null ? <></> : (
        <>
          {showVersions && (
            <Tabs value={tab} onValueChange={setTab} panels="external">
              <TabsList actions={tab === 'overview' && !!versions?.length && (
                <PayloadVersionSelect value={shownId} versions={versions} notInstalled={notInstalled} onChange={show} />
              )}
              >
                <TabsTrigger value="overview">{t('Overview')}</TabsTrigger>
                <TabsTrigger value="versions">{t('Versions')}</TabsTrigger>
                <TabsTrigger value="history">{t('History')}</TabsTrigger>
              </TabsList>
            </Tabs>
          )}
          <div style={{ marginTop: showVersions ? theme.spacing(2) : 0 }}>
            {payloadTab ? payloadTab() : renderOverview()}
          </div>
          {notice && <PayloadNewVersionDialog action={notice.action} olderVersions={notice.olderVersions} onClose={closeNotice} />}
        </>
      )}
    </Drawer>
  );
};

export default ThreatArsenalInformationDrawer;
