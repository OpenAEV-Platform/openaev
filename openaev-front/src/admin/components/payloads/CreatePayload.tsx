import { Add } from '@mui/icons-material';
import { Fab } from '@mui/material';
import { type FunctionComponent, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { addPayload } from '../../../actions/payloads/payload-actions';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import {
  type Domain, InjectorContractActionOutput,
  type PayloadCreateInput as ApiPayloadCreateInput,
} from '../../../utils/api-types';
import { type DetectionRemediationForm } from './utils/payloadFormToPayloadInput';
import PayloadForm from './PayloadForm';
import {useAppDispatch} from "../../../utils/hooks";
import {PayloadCreateInput} from "../../../utils/api-types-custom";

const useStyles = makeStyles()({
  createButton: {
    position: 'fixed',
    bottom: 30,
    right: 30,
  },
});

interface Props {
  onCreate?: (payloads: InjectorContractActionOutput) => void;
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

const CreatePayload: FunctionComponent<Props> = ({ onCreate }) => {
  const [open, setOpen] = useState(false);
  const { t } = useFormatter();
  const { classes } = useStyles();
  const dispatch = useAppDispatch();

  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);

  const onSubmit = (data: PayloadCreateInput) => {
    const inputValues: ApiPayloadCreateInput = {
      ...data,
      payload_source: 'MANUAL',
      payload_status: 'VERIFIED',
      payload_domains: data.payload_domains.map((domain: Domain) => domain.domain_id),
      payload_cleanup_executor: handleCleanupExecutorValue(data.payload_cleanup_executor, data.payload_cleanup_command),
      payload_cleanup_command: handleCleanupCommandValue(data.payload_cleanup_command),
      payload_detection_remediations: Object.entries(data.remediations ?? {})
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
    } as ApiPayloadCreateInput;

    return dispatch(addPayload(inputValues).then(({data }: {data: InjectorContractActionOutput}) => {
      if (data && onCreate) {
        onCreate(data);
      }
      setOpen(false);
    }));
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
        title={t('Create a new payload')}
      >
        <PayloadForm
          editing={false}
          onSubmit={onSubmit}
          handleClose={handleClose}
        />
      </Drawer>
    </>
  );
};

export default CreatePayload;

