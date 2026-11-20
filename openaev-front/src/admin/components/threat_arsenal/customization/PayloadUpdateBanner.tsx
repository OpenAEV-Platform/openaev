import { Alert, Button } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type PayloadCustomizationOutput, type ThreatArsenalAction } from '../../../../utils/api-types';
import useCanUpdateAction from '../hook/useCanUpdateAction';
import PayloadNewVersionDialog from '../versions/PayloadNewVersionDialog';
import useNewVersionNotice from '../versions/useNewVersionNotice';
import PayloadUpdateDialog from './PayloadUpdateDialog';

interface Props {
  action: ThreatArsenalAction;
  customization: PayloadCustomizationOutput | null;
  onUpdate?: (action: ThreatArsenalAction, replacedId?: string) => void;
}

// On a customized copy: the newer Filigran version it can take, reviewed in a dialog.
const PayloadUpdateBanner: FunctionComponent<Props> = ({ action, customization, onUpdate }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const canUpdate = useCanUpdateAction(action);
  const [reviewing, setReviewing] = useState(false);
  const { notice, afterSave, closeNotice } = useNewVersionNotice(action.injector_contract_id, onUpdate);
  const update = customization?.customization_update;

  const updated = (saved: ThreatArsenalAction) => {
    setReviewing(false);
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
          onUpdated={updated}
        />
      )}
      {notice && <PayloadNewVersionDialog action={notice.action} olderVersions={notice.olderVersions} onClose={closeNotice} />}
    </>
  );
};

export default PayloadUpdateBanner;
