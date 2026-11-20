import { Alert, Button } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type ThreatArsenalAction } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import useCanUpdateAction from '../hook/useCanUpdateAction';
import PayloadNewVersionDialog from '../versions/PayloadNewVersionDialog';
import useNewVersionNotice from '../versions/useNewVersionNotice';
import PayloadUpdateDialog from './PayloadUpdateDialog';
import useCustomization from './useCustomization';

interface Props {
  action: ThreatArsenalAction;
  onUpdate?: (action: ThreatArsenalAction, replacedId?: string) => void;
}

// On a customized copy: the newer Filigran version it can take, reviewed in a dialog.
const PayloadUpdateBanner: FunctionComponent<Props> = ({ action, onUpdate }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const customization = useCustomization(action);
  const canUpdate = useCanUpdateAction(action);
  const [reviewing, setReviewing] = useState(false);
  const { notice, afterSave, closeNotice } = useNewVersionNotice(action.injector_contract_id, onUpdate);
  const update = customization?.customization_update;

  const updated = (saved: ThreatArsenalAction, version: string) => {
    setReviewing(false);
    MESSAGING$.notifySuccess(t('Updated to Filigran v{version}', { version }));
    afterSave(saved);
  };

  return (
    <>
      {customization && update && (
        <Alert
          style={{ marginBottom: theme.spacing(2) }}
          severity="info"
          title={t('Filigran v{version} is available', { version: update.update_version })}
          description={t('Your copy is based on v{version}.', { version: customization.customization_base_version })}
          action={canUpdate && (
            <Button priority="secondary" size="sm" onClick={() => setReviewing(true)}>{t('Review the update')}</Button>
          )}
        />
      )}
      {update && reviewing && (
        <PayloadUpdateDialog
          actionId={action.injector_contract_id}
          update={update}
          onClose={() => setReviewing(false)}
          onUpdated={saved => updated(saved, update.update_version)}
        />
      )}
      {notice && <PayloadNewVersionDialog action={notice.action} olderVersions={notice.olderVersions} onClose={closeNotice} />}
    </>
  );
};

export default PayloadUpdateBanner;
