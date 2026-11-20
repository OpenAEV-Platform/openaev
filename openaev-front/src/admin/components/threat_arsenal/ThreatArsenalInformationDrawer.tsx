import { Button, Tabs, TabsList, TabsTrigger } from '@filigran/design-system';
import { PlayArrowOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useEffect, useReducer, useState } from 'react';

import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import {
  type Payload,
  type ThreatArsenalAction,
  type ThreatArsenalActionFullOutput,
} from '../../../utils/api-types';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../utils/utils';
import CatalogPayloadVersionPreview from './catalog/CatalogPayloadVersionPreview';
import useCatalogPayloadVersions from './catalog/useCatalogPayloadVersions';
import PayloadUpdateBanner from './customization/PayloadUpdateBanner';
import useCustomization from './customization/useCustomization';
import PayloadHistoryTab from './history/PayloadHistoryTab';
import useActionDetails from './hook/useActionDetails';
import { type OpenedTab } from './hook/useOpenedAction';
import PayloadRevocationBanner from './revocation/PayloadRevocationBanner';
import ThreatArsenalActionOverview from './ThreatArsenalActionOverview';
import { isShownRevoked } from './utils/threatArsenalStatusUtils';
import PayloadVersionSelect from './versions/PayloadVersionSelect';
import PayloadVersionsTab from './versions/PayloadVersionsTab';
import { olderVersionsInUse, totalUsageCount } from './versions/usages/usageCounts';
import useUsagesByVersion from './versions/usages/useUsagesByVersion';
import usePayloadVersions from './versions/usePayloadVersions';
import useShownVersion from './versions/useShownVersion';
import { notInstalledVersions } from './versions/versionRows';

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
  const canRunTest = useAbility().can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);

  const [tab, setTab] = useState<string>(initialTab ? 'versions' : 'overview');
  const showVersions = isFeatureEnabled('CATALOG') && !!threatArsenalAction?.action_payload;
  const versions = usePayloadVersions(threatArsenalAction?.injector_contract_id ?? '', open && showVersions);
  // A move changes the usages of two versions: they are all loaded again.
  const [reloadKey, reload] = useReducer((key: number) => key + 1, 0);
  const usages = useUsagesByVersion(versions, reloadKey);
  const customization = useCustomization(open ? threatArsenalAction : null);
  const { shownId, show, installed, shownAction, notInstalledVersion } = useShownVersion(threatArsenalAction, versions);

  // Always fetch the full details: both payload-based contracts (execution, arguments, cleanup) and
  // injector-based ones (e.g. AI Red Team) carry predefined expectations we surface in the drawer.
  const opened = useActionDetails(open ? threatArsenalAction?.injector_contract_id : undefined);
  const otherVersion = shownAction !== threatArsenalAction;
  const other = useActionDetails(otherVersion ? shownAction?.injector_contract_id : undefined);
  const { details, loading } = otherVersion ? other : opened;

  const catalogue = useCatalogPayloadVersions(showVersions && threatArsenalAction?.action_payload_managed ? opened.details?.action_external_id : undefined);
  const notInstalled = versions ? notInstalledVersions(catalogue, versions) : [];
  const shownCatalogueVersion = catalogue.find(item => item.catalog_payload_version === notInstalledVersion);

  const latest = versions?.find(version => version.payload_is_latest);
  const toMove = versions && usages && latest ? totalUsageCount(olderVersionsInUse(versions, usages, latest.payload_action_id)) : 0;
  const payloadTabs: Record<string, () => ReactNode> = {
    versions: () => <PayloadVersionsTab versions={versions} usages={usages} onMoved={reload} notInstalled={notInstalled} focusMove={initialTab === 'move'} />,
    history: () => <PayloadHistoryTab actionId={threatArsenalAction?.injector_contract_id ?? ''} />,
  };
  const payloadTab = showVersions ? payloadTabs[tab] : undefined;

  useEffect(() => setTab(initialTab ? 'versions' : 'overview'), [open, threatArsenalAction, initialTab]);

  // Only payload-based contracts produce a payload object (execution/arguments/
  // cleanup sections). Expectations are passed separately so injector-based
  // contracts without a payload still display them.
  const selectedPayload: Payload | null
    = details && threatArsenalAction?.action_payload ? toPayload(details) : null;

  const renderOverview = () => {
    if (notInstalledVersion) {
      return shownCatalogueVersion && <CatalogPayloadVersionPreview version={shownCatalogueVersion} />;
    }
    return shownAction && (
      <>
        {!otherVersion && <PayloadUpdateBanner action={shownAction} customization={customization} onUpdate={onUpdate} />}
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
      headerActions={canRunTest && onRunTest && shownAction && !isShownRevoked(shownAction) && (
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
        </>
      )}
    </Drawer>
  );
};

export default ThreatArsenalInformationDrawer;
