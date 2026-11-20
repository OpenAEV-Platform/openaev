import { Alert, Button } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type PayloadCustomizationOutput, type ThreatArsenalAction } from '../../../../utils/api-types';
import PayloadUpdateDialog from './PayloadUpdateDialog';

interface Props {
  actionId: string;
  customization: PayloadCustomizationOutput | null;
  canUpdate: boolean;
  onUpdated: (action: ThreatArsenalAction) => void;
}

// On a customized copy: the Filigran version it is built from and the newer one it can take, reviewed in a dialog.
const PayloadUpdateBanner: FunctionComponent<Props> = ({ actionId, customization, canUpdate, onUpdated }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [reviewing, setReviewing] = useState(false);
  const update = customization?.customization_update;
  if (!customization || !update) {
    return null;
  }

  return (
    <>
      <Alert
        style={{ marginBottom: theme.spacing(2) }}
        severity="info"
        title={t('Filigran v{version} is available', { version: update.update_version })}
        description={t('Based on v{version}', { version: customization.customization_base_version })}
        action={canUpdate && (
          <Button priority="secondary" size="sm" onClick={() => setReviewing(true)}>{t('Update')}</Button>
        )}
      />
      {reviewing && (
        <PayloadUpdateDialog
          actionId={actionId}
          baseVersion={customization.customization_base_version}
          update={update}
          onClose={() => setReviewing(false)}
          onUpdated={(saved) => {
            setReviewing(false);
            onUpdated(saved);
          }}
        />
      )}
    </>
  );
};

export default PayloadUpdateBanner;
