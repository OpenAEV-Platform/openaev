import { Button, Text, Textarea, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { CheckCircleOutlined, DoNotDisturbOnOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactElement, useContext, useState } from 'react';

import { approveIocValidation, rejectIocValidation } from '../../../actions/ioc_validations/ioc-validation-actions';
import DialogConfirmation from '../../../components/common/DialogConfirmation';
import { useFormatter } from '../../../components/i18n';
import { type IocValidationOutput } from '../../../utils/api-types';
import { MESSAGING$ } from '../../../utils/Environment';
import { fdsLayerClass, layerInputVars, SURFACE_LAYER } from '../../../utils/fdsLayer';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, PERMISSION_REQUIRED, SUBJECTS } from '../../../utils/permissions/types';
import { IOC_VALIDATION_REJECT_REASON_MAX_LENGTH, isAwaitingApproval } from './iocValidationUtils';

interface Props {
  iocValidation: IocValidationOutput;
  onUpdate: (iocValidation: IocValidationOutput) => void;
  onRefresh: () => void;
}

const IocValidationDecisionActions: FunctionComponent<Props> = ({ iocValidation, onUpdate, onRefresh }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const ability = useContext(AbilityContext);
  const canLaunch = ability.can(ACTIONS.LAUNCH, SUBJECTS.ASSESSMENT);

  const [approveOpen, setApproveOpen] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [reason, setReason] = useState('');

  if (!isAwaitingApproval(iocValidation.ioc_validation_status)) {
    return null;
  }

  // The request may have been decided elsewhere meanwhile: reload instead of leaving a stale page.
  const handleDecisionFailure = () => {
    setApproveOpen(false);
    setRejectOpen(false);
    onRefresh();
  };

  const handleApprove = () => approveIocValidation(iocValidation.ioc_validation_id)
    .then((result: { data: IocValidationOutput }) => {
      setApproveOpen(false);
      onUpdate(result.data);
      MESSAGING$.notifySuccess(t('The IOC validation has been approved. The validation simulation is starting.'));
    })
    .catch(handleDecisionFailure);

  const handleReject = () => {
    const trimmedReason = reason.trim();
    return rejectIocValidation(iocValidation.ioc_validation_id, { ioc_validation_reason: trimmedReason || undefined })
      .then((result: { data: IocValidationOutput }) => {
        setRejectOpen(false);
        setReason('');
        onUpdate(result.data);
        MESSAGING$.notifySuccess(t('The IOC validation has been rejected.'));
      })
      .catch(handleDecisionFailure);
  };

  // A disabled button fires no pointer event, so the tooltip hangs on an enabled wrapper.
  const withPermissionTooltip = (button: ReactElement) => (canLaunch
    ? button
    : (
        <Tooltip>
          <TooltipTrigger asChild>
            <span style={{ display: 'inline-flex' }}>{button}</span>
          </TooltipTrigger>
          <TooltipContent>{t(PERMISSION_REQUIRED)}</TooltipContent>
        </Tooltip>
      ));

  return (
    <>
      {withPermissionTooltip(
        <Button
          type="button"
          priority="secondary"
          variant="destructive"
          startIcon={<DoNotDisturbOnOutlined fontSize="small" />}
          disabled={!canLaunch}
          onClick={() => setRejectOpen(true)}
        >
          {t('Reject')}
        </Button>,
      )}
      {withPermissionTooltip(
        <Button
          type="button"
          startIcon={<CheckCircleOutlined fontSize="small" />}
          disabled={!canLaunch}
          onClick={() => setApproveOpen(true)}
        >
          {t('Approve and start the simulation')}
        </Button>,
      )}
      <DialogConfirmation
        open={approveOpen}
        handleClose={() => setApproveOpen(false)}
        handleSubmit={handleApprove}
        text={t('Approve this IOC validation? A simulation starts at once and runs benign tests on the target assets of the validation scenario. Nothing is downloaded or executed from the indicators.')}
        submitLabel={t('Approve and start the simulation')}
      />
      <DialogConfirmation
        open={rejectOpen}
        handleClose={() => setRejectOpen(false)}
        handleSubmit={handleReject}
        text={t('Reject this IOC validation? No test runs and OpenCTI is notified with the reason below.')}
        submitLabel={t('Reject')}
        submitColor="error"
        extraContent={(
          <div
            className={fdsLayerClass(SURFACE_LAYER)}
            style={{
              ...layerInputVars,
              marginTop: theme.spacing(2),
            }}
          >
            <Textarea
              label={t('Reason')}
              placeholder={t('Optional reason reported to OpenCTI')}
              value={reason}
              onChange={event => setReason(event.target.value)}
              maxLength={IOC_VALIDATION_REJECT_REASON_MAX_LENGTH}
              rows={4}
              resize="vertical"
            />
            <Text
              variant="content-caption"
              className="text-default-secondary"
              style={{
                display: 'block',
                marginTop: theme.spacing(0.5),
                textAlign: 'right',
              }}
            >
              {`${reason.length} / ${IOC_VALIDATION_REJECT_REASON_MAX_LENGTH}`}
            </Text>
          </div>
        )}
      />
    </>
  );
};

export default IocValidationDecisionActions;
