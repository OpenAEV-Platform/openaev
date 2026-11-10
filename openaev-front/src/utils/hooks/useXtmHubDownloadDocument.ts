import { useContext, useEffect, useRef, useState } from 'react';

import type { LoggedHelper } from '../../actions/helper';
import { DialogConnectivityLostStatus } from '../../admin/components/xtm_hub/dialog/connectivity-lost/DialogConnectivityLost.types';
import { useHelper } from '../../store';
import { useAbility } from '../permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../permissions/types';
import XtmHubClient from '../xtm-hub-client';
import { UserContext } from './useAuth';
import useXtmHubUserPlatformToken from './useXtmHubUserPlatformToken';

interface Props {
  serviceInstanceId?: string;
  fileId?: string;
  onSuccess: (file: File) => void;
  onError: (error: unknown) => void;
}

interface Return { dialogConnectivityLostStatus: DialogConnectivityLostStatus }

const useXtmHubDownloadDocument = ({ serviceInstanceId, fileId, onSuccess, onError }: Props): Return => {
  const ability = useAbility();
  const { settings } = useContext(UserContext);
  const { userPlatformToken } = useXtmHubUserPlatformToken(settings?.xtm_hub_url);
  const registration = useHelper((helper: LoggedHelper) => helper.getXtmHubRegistration());
  const [dialogConnectivityLostStatus, setDialogConnectivityLostStatus] = useState<DialogConnectivityLostStatus>(DialogConnectivityLostStatus.unknown);
  // The document is handed over once per mount: the caller imports it on success.
  const deliveredRef = useRef(false);

  useEffect(() => {
    const isTryingToDownloadDocument = !!fileId && !!serviceInstanceId;
    const isPlatformRegistered = registration?.tenant_xtmhub_registration_status === 'REGISTERED';
    if (isTryingToDownloadDocument && !isPlatformRegistered) {
      if (ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS)) {
        setDialogConnectivityLostStatus(DialogConnectivityLostStatus.authorized);
      } else {
        setDialogConnectivityLostStatus(DialogConnectivityLostStatus.unauthorized);
      }
      return undefined;
    }

    if (deliveredRef.current || !settings || !userPlatformToken || !serviceInstanceId || !fileId) {
      return undefined;
    }

    // A re-run (StrictMode remount, or a dependency changing mid-download) supersedes
    // the running download, so only the latest one hands the document over.
    let cancelled = false;
    const fetchData = async () => {
      try {
        const file = await XtmHubClient.fetchDocument({
          settings,
          serviceInstanceId,
          fileId,
          userPlatformToken,
        });

        if (!cancelled) {
          deliveredRef.current = true;
          onSuccess(file);
        }
      } catch (e) {
        if (!cancelled) {
          deliveredRef.current = true;
          onError(e);
        }
      }
    };

    fetchData();
    return () => {
      cancelled = true;
    };
  }, [settings, registration, serviceInstanceId, fileId, userPlatformToken]);

  return { dialogConnectivityLostStatus };
};

export default useXtmHubDownloadDocument;
