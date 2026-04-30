import { Add } from '@mui/icons-material';
import { Fab } from '@mui/material';
import { type FunctionComponent, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { addThreatArsenalAction } from '../../../actions/threat_arsenals/threatArsenal-actions';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import {
  type ThreatArsenalAction,
  type ThreatArsenalActionCreateInput as ApiThreatArsenalActionCreateInput,
} from '../../../utils/api-types';
import { type ThreatArsenalActionCreateCustomInput } from '../../../utils/api-types-custom';
import { type DetectionRemediationForm } from '../payloads/utils/payloadFormToPayloadInput';
import ThreatArsenalActionForm from './ThreatArsenalActionForm';

const useStyles = makeStyles()({
  createButton: {
    position: 'fixed',
    bottom: 30,
    right: 30,
  },
});

interface Props { onCreate?: (action: ThreatArsenalAction) => void }

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

const CreateThreatArsenalAction: FunctionComponent<Props> = ({ onCreate }) => {
  const [open, setOpen] = useState(false);
  const { t } = useFormatter();
  const { classes } = useStyles();

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
            detection_remediation_collector: key,
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
      setOpen(false);
    });
  };

  return (
    <>
      <Fab
        onClick={handleOpen}
        color="primary"
        aria-label="Add"
        className={classes.createButton}
      >
        <Add />
      </Fab>
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
