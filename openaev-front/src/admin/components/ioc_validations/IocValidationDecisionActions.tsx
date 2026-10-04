import { Button, Text, Textarea, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { CheckCircleOutlined, DoNotDisturbOnOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactElement, useContext, useState } from 'react';

import { approveIocValidation, rejectIocValidation } from '../../../actions/ioc_validations/ioc-validation-actions';
import { Field } from '../../../components/common/detail/EntityDetailCommon';
import DialogConfirmation from '../../../components/common/DialogConfirmation';
import { useFormatter } from '../../../components/i18n';
import { type IocValidationIocOutput, type IocValidationOutput } from '../../../utils/api-types';
import { MESSAGING$ } from '../../../utils/Environment';
import { fdsLayerClass, layerInputVars, SURFACE_LAYER } from '../../../utils/fdsLayer';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, PERMISSION_REQUIRED, SUBJECTS } from '../../../utils/permissions/types';
import IocValidationTable, { type IocValidationTableColumn } from './IocValidationTable';
import { IOC_VALIDATION_REJECT_REASON_MAX_LENGTH, iocValidationTestKindLabel, isAwaitingApproval } from './iocValidationUtils';

// Tests listed in the approval dialog; the request page lists them all.
const APPROVAL_SUMMARY_MAX_ROWS = 10;

// What the approval starts: the tests that run on each indicator and the security platforms expected to see them.
const IocValidationApprovalSummary: FunctionComponent<{ iocValidation: IocValidationOutput }> = ({ iocValidation }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const planned = iocValidation.ioc_validation_iocs.filter(ioc => ioc.ioc_test_kind);
  const plannedIndicators = new Set(planned.map(ioc => ioc.ioc_indicator_ref));
  const platforms = [...new Set(iocValidation.ioc_validation_pairs
    .filter(pair => plannedIndicators.has(pair.pair_indicator_ref))
    .map(pair => pair.pair_platform_name || pair.pair_platform_ref))];
  const columns: IocValidationTableColumn<IocValidationIocOutput>[] = [
    {
      key: 'indicator',
      label: t('Indicator'),
      width: '34%',
      render: ioc => ioc.ioc_indicator_name || ioc.ioc_indicator_ref,
    },
    {
      key: 'value',
      label: t('Observable'),
      width: '38%',
      render: ioc => <Text variant="content-code">{ioc.ioc_value}</Text>,
    },
    {
      key: 'test',
      label: t('Test that runs'),
      width: '28%',
      render: ioc => t(iocValidationTestKindLabel(ioc.ioc_test_kind)),
    },
  ];
  return (
    <div
      data-testid="ioc-validation-approval-summary"
      style={{
        display: 'grid',
        gap: theme.spacing(1.5),
        marginTop: theme.spacing(2),
      }}
    >
      <Text variant="content-compact" className="text-default-secondary">{t('Tests that run once approved')}</Text>
      <IocValidationTable
        caption={t('Tests that run once approved')}
        columns={columns}
        rows={planned.slice(0, APPROVAL_SUMMARY_MAX_ROWS)}
        rowKey={(ioc, index) => `${ioc.ioc_indicator_ref}-${ioc.ioc_test_kind ?? 'none'}-${index}`}
        emptyMessage={t('No IOC in this request.')}
      />
      {planned.length > APPROVAL_SUMMARY_MAX_ROWS && (
        <Text variant="content-caption" className="text-default-secondary">
          {t('{count} more indicators', { count: String(planned.length - APPROVAL_SUMMARY_MAX_ROWS) })}
        </Text>
      )}
      <Field label={t('Security platforms')}>{platforms.join(', ') || t('None named in the request')}</Field>
    </div>
  );
};

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
        extraContent={<IocValidationApprovalSummary iocValidation={iocValidation} />}
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
