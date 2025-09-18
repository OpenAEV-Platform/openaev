import type React from 'react';

import XtmHubDialogConnectivityLostAuthorizedRegister from './AuthorizedRegister';
import { DialogConnectivityLostStatus } from './DialogConnectivityLost.types';
import XtmHubDialogConnectivityLostUnauthorizedRegister from './UnauthorizedRegister';

interface Props {
  status: DialogConnectivityLostStatus;
  onCancel: () => void;
  onConfirm: () => void;
}

const XtmHubDialogConnectivityLost: React.FC<Props> = ({ status, onCancel, onConfirm }) => {
  const isAuthorizedDialogOpen = status === DialogConnectivityLostStatus.authorized;
  const isUnauthorizedDialogOpen = status === DialogConnectivityLostStatus.unauthorized;

  return (
    <>
      <XtmHubDialogConnectivityLostAuthorizedRegister
        open={isAuthorizedDialogOpen}
        onCancel={onCancel}
        onConfirm={onConfirm}
      />

      <XtmHubDialogConnectivityLostUnauthorizedRegister
        open={isUnauthorizedDialogOpen}
        onCancel={onCancel}
      />
    </>
  );
};

export default XtmHubDialogConnectivityLost;
