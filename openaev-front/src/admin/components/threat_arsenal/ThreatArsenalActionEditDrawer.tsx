import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { updateThreatArsenalAction } from '../../../actions/threat_arsenals/threatArsenal-actions';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import { type ThreatArsenalAction } from '../../../utils/api-types';
import { type ThreatArsenalActionCreateCustomInput } from '../../../utils/api-types-custom';
import InjectorContractForm, { type InjectorContractFormValues } from '../integrations/injectors/injector_contracts/InjectorContractForm';
import PayloadUpdateBanner from './customization/PayloadUpdateBanner';
import useActionEdit from './hook/useActionEdit';
import PayloadRevocationBanner from './revocation/PayloadRevocationBanner';
import ThreatArsenalActionForm from './ThreatArsenalActionForm';
import { buildInitialValues, toActionInput } from './utils/actionFormValues';
import SnapshotRemediationProvider from './utils/SnapshotRemediationProvider';
import { isShownRevoked } from './utils/threatArsenalStatusUtils';
import usePayloadVersions from './versions/usePayloadVersions';

interface Props {
  // The action edited; closed without one.
  action: ThreatArsenalAction | null;
  onClose: () => void;
  onSaved: (saved: ThreatArsenalAction) => void;
}

const ThreatArsenalActionEditDrawer: FunctionComponent<Props> = ({ action, onClose, onSaved }) => {
  const { t, tPick } = useFormatter();
  const theme = useTheme();
  const edit = useActionEdit(action);
  const actionId = action?.injector_contract_id ?? '';
  const revoked = usePayloadVersions(actionId, !!action && isShownRevoked(action))
    ?.find(version => version.payload_action_id === actionId && version.payload_revoked);
  const payloadId = action?.action_payload?.payload_id ?? '';
  const name = action ? tPick(action.action_labels) : '';
  const customized = !!edit?.customization;

  const saved = (result: ThreatArsenalAction) => {
    onClose();
    onSaved(result);
  };

  const onSubmitEdit = async (data: ThreatArsenalActionCreateCustomInput) => {
    const response = await updateThreatArsenalAction(actionId, toActionInput(data));
    if (response.data) {
      saved(response.data as ThreatArsenalAction);
    } else {
      onClose();
    }
  };

  const onSubmitInjectorContractEdit = (data: InjectorContractFormValues) => {
    onSubmitEdit({
      ...edit?.details,
      action_name: edit?.details.action_labels ? tPick(edit.details.action_labels) : '',
      action_tags: data.injector_contract_tags,
      action_attack_patterns: data.injector_contract_attack_patterns,
      action_domains: data.injector_contract_domains,
    } as ThreatArsenalActionCreateCustomInput);
  };

  return (
    <Drawer
      open={!!action}
      handleClose={onClose}
      title={customized ? `${t('Customize')} · ${name}` : `${t('Update the action :')} ${name}`}
    >
      <>
        <PayloadUpdateBanner actionId={actionId} customization={edit?.customization ?? null} canUpdate onUpdated={saved} />
        {revoked && (
          <div style={{ marginBottom: theme.spacing(2) }}>
            <PayloadRevocationBanner version={revoked} />
          </div>
        )}
        {edit && !!payloadId && (
          <SnapshotRemediationProvider>
            <ThreatArsenalActionForm
              onSubmit={onSubmitEdit}
              handleClose={onClose}
              editing
              filigranValues={edit.filigranValues}
              initialValues={{
                ...buildInitialValues(edit.details, tPick(edit.details.action_labels)),
                payload_id: payloadId,
              }}
            />
          </SnapshotRemediationProvider>
        )}
        {edit && !payloadId && (
          <InjectorContractForm
            initialValues={{
              injector_contract_attack_patterns: edit.details.action_attack_patterns,
              injector_contract_domains: edit.details.action_domains,
              injector_contract_tags: edit.details.action_tags,
            }}
            editing
            onSubmit={onSubmitInjectorContractEdit}
            handleClose={onClose}
          />
        )}
      </>
    </Drawer>
  );
};

export default ThreatArsenalActionEditDrawer;
