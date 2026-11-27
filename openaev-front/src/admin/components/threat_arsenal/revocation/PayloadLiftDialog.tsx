import { Alert, Button, Spinner } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { liftPayloadRevocation } from '../../../../actions/payloads/payload-revocation-actions';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadVersionOutput } from '../../../../utils/api-types';
import { usageCount } from '../versions/usages/usageCounts';
import useUsageImpact from '../versions/usages/useUsageImpact';
import PayloadVersionDialog from './PayloadVersionDialog';
import UsagesLink from './UsagesLink';
import useVersionAction from './useVersionAction';

interface Props {
  open: boolean;
  name: string;
  // The revoked versions the tenant can lift.
  versions: PayloadVersionOutput[];
  currentPayloadId: string;
  // The action of the card: the list shows the latest version of each payload only.
  actionId: string;
  onClose: () => void;
  onDone: (version: PayloadVersionOutput) => void;
}

// Lift the revocation of one version: what runs again.
const PayloadLiftDialog: FunctionComponent<Props> = ({ open, name, versions, currentPayloadId, actionId, onClose, onDone }) => {
  const { t } = useFormatter();
  const { selected, setSelected, version, usages, submitting, submit } = useVersionAction({
    open,
    versions,
    currentPayloadId,
    onDone,
  });
  const impact = useUsageImpact(usages);
  const count = usages ? usageCount(usages) : 0;

  const renderImpact = () => {
    if (!version) return null;
    if (!usages) return <Spinner size="sm" />;
    return (
      <Alert
        elevation={1}
        severity="info"
        title={t('v{version} runs again in this tenant.', { version: version.payload_version })}
        description={count > 0 ? impact : undefined}
        action={count > 0 ? <UsagesLink actionId={actionId} /> : undefined}
      />
    );
  };

  return (
    <PayloadVersionDialog
      open={open}
      title={t('Lift a revocation')}
      name={name}
      versions={versions}
      selected={selected}
      onSelect={setSelected}
      onClose={onClose}
      submit={(
        <Button type="button" loading={submitting} disabled={!version || usages === null} onClick={() => submit(chosen => liftPayloadRevocation(chosen.payload_id))}>
          {t('Lift revocation')}
        </Button>
      )}
    >
      {renderImpact()}
    </PayloadVersionDialog>
  );
};

export default PayloadLiftDialog;
