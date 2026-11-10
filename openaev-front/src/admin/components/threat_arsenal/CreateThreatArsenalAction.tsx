import { type FunctionComponent, type ReactNode, useState } from 'react';

import { addThreatArsenalAction } from '../../../actions/threat_arsenals/threatArsenal-actions';
import ButtonCreate from '../../../components/common/ButtonCreate';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import {
  type ThreatArsenalAction,
  type ThreatArsenalActionCreateInput as ApiThreatArsenalActionCreateInput,
} from '../../../utils/api-types';
import { type ThreatArsenalActionCreateCustomInput } from '../../../utils/api-types-custom';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { type DetectionRemediationForm } from '../payloads/utils/payloadFormToPayloadInput';
import notifySubmittedForApproval from './approval/notifySubmittedForApproval';
import ThreatArsenalActionForm from './ThreatArsenalActionForm';

interface Props {
  onCreate?: (action: ThreatArsenalAction) => void;
  /** Another way to open the form than the Create button, e.g. a "New action" row in a list. */
  renderTrigger?: (open: () => void) => ReactNode;
}

function handleCleanupCommandValue(cleanupCommand: string | null | undefined): string | null {
  return cleanupCommand === '' ? null : (cleanupCommand ?? null);
}

function handleCleanupExecutorValue(
  cleanupExecutor: string | null | undefined,
  cleanupCommand: string | null | undefined,
): string | null {
  if (cleanupExecutor !== '' && handleCleanupCommandValue(cleanupCommand) !== null) {
    return cleanupExecutor ?? null;
  }
  return null;
}

const CreateThreatArsenalAction: FunctionComponent<Props> = ({ onCreate, renderTrigger }) => {
  const [open, setOpen] = useState(false);
  const { t } = useFormatter();
  const ability = useAbility();

  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);

  const onSubmit = (data: ThreatArsenalActionCreateCustomInput) => {
    const inputValues: ApiThreatArsenalActionCreateInput = {
      ...data,
      action_source: 'MANUAL',
      action_status: 'VERIFIED',
      action_cleanup_executor: handleCleanupExecutorValue(data.action_cleanup_executor, data.action_cleanup_command),
      action_cleanup_command: handleCleanupCommandValue(data.action_cleanup_command),
      action_detection_remediations: Object.entries(data.remediations ?? {})
        .filter(([, value]) => value)
        .map(([key, value]) => {
          const remediation = value as unknown as DetectionRemediationForm;
          return {
            detection_remediation_security_platform: key,
            detection_remediation_values: remediation.content,
            detection_remediation_id: remediation.remediationId,
            author_rule: remediation.author_rule,
          };
        }),
    } as ApiThreatArsenalActionCreateInput;

    return addThreatArsenalAction(inputValues).then(({ data }: { data: ThreatArsenalAction }) => {
      if (data && onCreate) {
        onCreate(data);
      }
      if (!ability.can(ACTIONS.APPROVE, SUBJECTS.THREAT_ARSENALS)) {
        notifySubmittedForApproval(t, data);
      }
      setOpen(false);
    });
  };

  return (
    <>
      {renderTrigger ? renderTrigger(handleOpen) : <ButtonCreate onClick={handleOpen} />}
      <Drawer
        open={open}
        handleClose={handleClose}
        title={t('Create a new action')}
      >
        <ThreatArsenalActionForm
          editing={false}
          onSubmit={onSubmit}
          handleClose={handleClose}
        />
      </Drawer>
    </>
  );
};

export default CreateThreatArsenalAction;
