import { useEffect, useReducer, useState } from 'react';

import { installCatalogPayload } from '../../../../actions/catalog/payload-catalog-actions';
import { useFormatter } from '../../../../components/i18n';
import { type ThreatArsenalAction } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { isFeatureEnabled } from '../../../../utils/utils';
import useCatalogPayloadVersions from '../catalog/useCatalogPayloadVersions';
import useCustomization from '../customization/useCustomization';
import { olderVersionsInUse, totalUsageCount } from '../versions/usages/usageCounts';
import useUsagesByVersion from '../versions/usages/useUsagesByVersion';
import useNewVersionNotice from '../versions/useNewVersionNotice';
import usePayloadVersions from '../versions/usePayloadVersions';
import useShownVersion from '../versions/useShownVersion';
import { notInstalledVersions } from '../versions/versionRows';
import useActionDetails from './useActionDetails';
import { type OpenedTab } from './useOpenedAction';

const increment = (key: number) => key + 1;

/**
 * What the drawer of an action shows: its versions and the one picked, the details of the shown one, the
 * catalogue versions not installed, the usages left on older versions and the customization of a copy.
 */
const useActionDrawer = (
  open: boolean,
  action: ThreatArsenalAction | null,
  initialTab: OpenedTab | undefined,
  onUpdate?: (action: ThreatArsenalAction, replacedId?: string) => void,
) => {
  const { t, tPick } = useFormatter();
  const [tab, setTab] = useState<string>(initialTab ? 'versions' : 'overview');
  useEffect(() => setTab(initialTab ? 'versions' : 'overview'), [open, action, initialTab]);

  const showVersions = isFeatureEnabled('CATALOG') && !!action?.action_payload;
  const [versionsKey, reloadVersions] = useReducer(increment, 0);
  const versions = usePayloadVersions(action?.injector_contract_id ?? '', open && showVersions, versionsKey);
  // A move changes the usages of two versions: they are all loaded again.
  const [usagesKey, reloadUsages] = useReducer(increment, 0);
  // The Overview only counts what is left on older versions.
  const usages = useUsagesByVersion(tab === 'versions' ? versions : versions?.filter(version => !version.payload_is_latest) ?? null, usagesKey);
  const customization = useCustomization(open ? action : null);
  const { shownId, show, installed, shownAction, notInstalledVersion } = useShownVersion(action, versions);

  // Always fetch the full details: both payload-based contracts (execution, arguments, cleanup) and
  // injector-based ones (e.g. AI Red Team) carry predefined expectations we surface in the drawer.
  const opened = useActionDetails(open ? action?.injector_contract_id : undefined);
  const otherVersion = shownAction !== action;
  const other = useActionDetails(otherVersion ? shownAction?.injector_contract_id : undefined);
  const { details, loading } = otherVersion ? other : opened;

  const externalId = opened.details?.action_external_id;
  const catalogue = useCatalogPayloadVersions(showVersions && action?.action_payload_managed ? externalId : undefined);
  const notInstalled = versions ? notInstalledVersions(catalogue, versions) : [];
  const shownCatalogueVersion = catalogue.find(item => item.catalog_payload_version === notInstalledVersion);

  const latest = versions?.find(version => version.payload_is_latest);
  const toMove = versions && usages && latest ? totalUsageCount(olderVersionsInUse(versions, usages, latest.payload_action_id)) : 0;

  const [installing, setInstalling] = useState<string | null>(null);
  const install = (version: string) => {
    if (!externalId || !action) return;
    setInstalling(version);
    installCatalogPayload(externalId, version)
      .then(({ installed_id }) => {
        MESSAGING$.notifySuccess(t('{name} v{version} installed', {
          name: tPick(action.action_labels),
          version,
        }));
        reloadVersions();
        show(installed_id);
      })
      .catch(() => undefined)
      .finally(() => setInstalling(null));
  };

  const { notice, afterSave, closeNotice } = useNewVersionNotice(action?.injector_contract_id ?? '', onUpdate);

  return {
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
  };
};

export default useActionDrawer;
