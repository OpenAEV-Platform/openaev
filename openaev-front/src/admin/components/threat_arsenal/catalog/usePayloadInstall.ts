import { useState } from 'react';

import { installCatalogPayload } from '../../../../actions/catalog/payload-catalog-actions';
import useNotifyInstalled from '../../../../components/common/marketplace/install/useNotifyInstalled';
import { type AvailablePayloadActionOutput } from '../../../../utils/api-types';
import { useActionName } from './availableActions';

// The catalogue payloads installed from this page, and the ones installing.
const usePayloadInstall = () => {
  const nameOf = useActionName();
  const notifyInstalled = useNotifyInstalled();
  const [installedIds, setInstalledIds] = useState<Set<string>>(new Set());
  const [installingIds, setInstallingIds] = useState<Set<string>>(new Set());

  const markInstalled = (action: AvailablePayloadActionOutput, version: string) => {
    setInstalledIds(previous => new Set([...previous, action.action_id]));
    notifyInstalled(nameOf(action), version);
  };
  // The latest version unless another one is picked.
  const install = (action: AvailablePayloadActionOutput, version = action.action_payload_version) => {
    const id = action.action_id;
    setInstallingIds(previous => new Set([...previous, id]));
    // True once installed, false when the install failed.
    return installCatalogPayload(id, version)
      .then(() => {
        markInstalled(action, version);
        return true;
      })
      .catch(() => false)
      .finally(() => setInstallingIds(previous => new Set([...previous].filter(other => other !== id))));
  };

  return {
    isInstalled: (action: AvailablePayloadActionOutput) => installedIds.has(action.action_id),
    isInstalling: (action: AvailablePayloadActionOutput) => installingIds.has(action.action_id),
    install,
    // A reload lists only what is still to install.
    forgetInstalled: () => setInstalledIds(new Set()),
  };
};

export default usePayloadInstall;
