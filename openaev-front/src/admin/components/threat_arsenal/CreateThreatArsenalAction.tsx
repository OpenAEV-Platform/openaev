import { Button, Dialog, DialogBody, DialogContent, DialogFooter, DialogTitle, Text } from '@filigran/design-system';
import { type FunctionComponent, type ReactNode, useState } from 'react';

import { addThreatArsenalAction } from '../../../actions/threat_arsenals/threatArsenal-actions';
import {
  createNewActionDraft,
  discardNewActionDraft,
  publishNewActionDraft,
  saveNewActionDraft,
} from '../../../actions/threat_arsenals/threatArsenal-draft-actions';
import ButtonCreate from '../../../components/common/ButtonCreate';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import {
  type PayloadDraftOutput,
  type ThreatArsenalAction,
  type ThreatArsenalActionCreateInput as ApiThreatArsenalActionCreateInput,
} from '../../../utils/api-types';
import { type ThreatArsenalActionCreateCustomInput } from '../../../utils/api-types-custom';
import { MESSAGING$ } from '../../../utils/Environment';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { type DetectionRemediationForm } from '../payloads/utils/payloadFormToPayloadInput';
import { type ThreatArsenalActionFormValues } from './actionFormValues';
import notifySubmittedForApproval from './approval/notifySubmittedForApproval';
import ThreatArsenalActionForm from './ThreatArsenalActionForm';

interface Props {
  onCreate?: (action: ThreatArsenalAction) => void;
  /** Another way to open the form than the Create button, e.g. a "New action" row in a list. */
  renderTrigger?: (open: () => void) => ReactNode;
  /** Payload drafts: opens on the saved draft of a new action (from Drafts of new actions). */
  draft?: PayloadDraftOutput;
  /** A draft of a new action was saved, discarded or published. */
  onDraftsChange?: () => void;
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

const CreateThreatArsenalAction: FunctionComponent<Props> = ({ onCreate, renderTrigger, draft, onDraftsChange }) => {
  const [open, setOpen] = useState(false);
  const { t } = useFormatter();
  const ability = useAbility();
  const canApprove = ability.can(ACTIONS.APPROVE, SUBJECTS.THREAT_ARSENALS);
  // Payload drafts: the draft of this new action, once saved; it has no version until published.
  const [draftId, setDraftId] = useState<string | undefined>(draft?.draft_id);
  const [discarding, setDiscarding] = useState(false);

  const handleOpen = () => setOpen(true);
  // Opened from Create (no draft given): a draft saved meanwhile is kept, but the next Create starts
  // a new action instead of overwriting it. It is found again in the Active list (Drafts of new
  // actions), shared with everyone.
  const handleClose = () => {
    setOpen(false);
    if (!draft) {
      setDraftId(undefined);
    }
  };

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

    const created = draftId ? publishNewActionDraft(draftId, inputValues) : addThreatArsenalAction(inputValues);
    return created.then(({ data }: { data: ThreatArsenalAction }) => {
      if (data && onCreate) {
        onCreate(data);
      }
      if (!canApprove) {
        notifySubmittedForApproval(t, data);
      }
      if (draftId) {
        setDraftId(undefined);
        onDraftsChange?.();
      }
      setOpen(false);
    });
  };

  // Saves the form as the draft of the new action, unchecked: a draft may be incomplete.
  const saveDraft = async (values: ThreatArsenalActionCreateCustomInput) => {
    const input = { draft_content: values as unknown as Record<string, unknown> };
    if (draftId) {
      await saveNewActionDraft(draftId, input);
    } else {
      const created = (await createNewActionDraft(input)).data as PayloadDraftOutput;
      setDraftId(created.draft_id);
    }
    MESSAGING$.notifySuccess(t('Draft saved'));
    onDraftsChange?.();
  };

  const discard = async () => {
    if (!draftId) return;
    await discardNewActionDraft(draftId);
    setDraftId(undefined);
    setDiscarding(false);
    setOpen(false);
    MESSAGING$.notifySuccess(t('Draft discarded'));
    onDraftsChange?.();
  };

  return (
    <>
      {renderTrigger ? renderTrigger(handleOpen) : <ButtonCreate onClick={handleOpen} />}
      <Drawer
        open={open}
        handleClose={handleClose}
        title={draft ? t('Edit draft') : t('Create a new action')}
      >
        <ThreatArsenalActionForm
          editing={false}
          onSubmit={onSubmit}
          handleClose={handleClose}
          initialValues={draft ? draft.draft_content as ThreatArsenalActionFormValues : undefined}
          draft={{
            publishLabel: canApprove ? t('Publish version') : t('Send for approval'),
            saved: draftId !== undefined,
            onSaveDraft: saveDraft,
            onDiscard: draftId ? () => setDiscarding(true) : undefined,
          }}
        />
      </Drawer>
      <Dialog open={discarding} onOpenChange={next => !next && setDiscarding(false)}>
        <DialogContent size="sm">
          <DialogTitle>{t('Discard draft')}</DialogTitle>
          <DialogBody>
            <Text variant="content-base">{t('Discard the draft of this new action?')}</Text>
          </DialogBody>
          <DialogFooter>
            <Button priority="secondary" onClick={() => setDiscarding(false)}>{t('Cancel')}</Button>
            <Button variant="destructive" onClick={discard}>{t('Discard')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
};

export default CreateThreatArsenalAction;
