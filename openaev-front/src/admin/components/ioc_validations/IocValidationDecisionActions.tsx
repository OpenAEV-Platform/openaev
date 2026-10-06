import { Alert, Button, Text, Textarea, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { CheckCircleOutlined, DoNotDisturbOnOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactElement, useContext, useRef, useState } from 'react';

import { approveIocValidation, fetchIocValidationApprovalPreview, rejectIocValidation } from '../../../actions/ioc_validations/ioc-validation-actions';
import { Field } from '../../../components/common/detail/EntityDetailCommon';
import DialogConfirmation from '../../../components/common/DialogConfirmation';
import { useFormatter } from '../../../components/i18n';
import { type IocValidationApprovalPreviewOutput, type IocValidationIocOutput, type IocValidationOutput } from '../../../utils/api-types';
import { MESSAGING$ } from '../../../utils/Environment';
import { fdsLayerClass, layerInputVars, SURFACE_LAYER } from '../../../utils/fdsLayer';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, PERMISSION_REQUIRED, SUBJECTS } from '../../../utils/permissions/types';
import { emptyFilled } from '../../../utils/String';
import { TextSkeleton } from './IocValidationSkeleton';
import IocValidationTable, { type IocValidationTableColumn } from './IocValidationTable';
import { IOC_VALIDATION_REJECT_REASON_MAX_LENGTH, iocValidationTestKindLabel, isAwaitingApproval } from './iocValidationUtils';

// Tests listed in the approval dialog; the request page lists them all.
const APPROVAL_SUMMARY_MAX_ROWS = 10;
// What the approval starts, as the server plans it now: the tests that run on each indicator and the security
// platforms expected to see them. While the plan loads, its place is kept with the tests the request shows as
// planned, so the dialog and its actions do not move when it lands. A request shown without planned test has
// nothing to approve whatever the plan says, so its warning shows at once.
const IocValidationApprovalSummary: FunctionComponent<{
  preview: IocValidationApprovalPreviewOutput | null;
  shownPlanned: number;
  // Shown in place of the confirmation question, when there is nothing to approve.
  standalone: boolean;
}> = ({ preview, shownPlanned, standalone }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const summaryStyle = {
    display: 'grid',
    gap: theme.spacing(1.5),
    marginTop: standalone ? 0 : theme.spacing(2),
  };
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
  const moreIndicators = (count: number) => count > APPROVAL_SUMMARY_MAX_ROWS && (
    <Text variant="content-caption" className="text-default-secondary">
      {t('{count} more indicators', { count: String(count - APPROVAL_SUMMARY_MAX_ROWS) })}
    </Text>
  );

  if (shownPlanned === 0) {
    return (
      <div data-testid="ioc-validation-approval-summary" aria-busy={!preview} style={summaryStyle}>
        <Alert
          severity="warning"
          title={t('Nothing can run: every IOC of this request was skipped when it was received. Reject the request and ask for a new validation from OpenCTI.')}
        />
      </div>
    );
  }

  if (!preview) {
    return (
      <div data-testid="ioc-validation-approval-summary" aria-busy="true" style={summaryStyle}>
        <Text variant="content-compact" className="text-default-secondary" role="status">
          {t('Checking the tests with the current settings...')}
        </Text>
        <IocValidationTable
          caption={t('Checking the tests with the current settings...')}
          columns={columns.map((column): IocValidationTableColumn<null> => ({
            key: column.key,
            label: column.label,
            width: column.width,
            render: () => (column.key === 'value'
              ? <Text variant="content-code"><TextSkeleton width="10em" inline /></Text>
              : <TextSkeleton />),
          }))}
          rows={Array.from({ length: Math.min(shownPlanned, APPROVAL_SUMMARY_MAX_ROWS) }, () => null)}
          rowKey={(_, index) => `loading-${index}`}
          emptyMessage=""
        />
        {moreIndicators(shownPlanned)}
        <Field label={t('Security platforms')}><TextSkeleton width="40%" /></Field>
      </div>
    );
  }

  const blocker = preview.ioc_validation_preview_blocker;
  const planned = preview.ioc_validation_preview_iocs.filter(ioc => ioc.ioc_test_kind);
  const platforms = [...new Set(preview.ioc_validation_preview_pairs
    .map(pair => pair.pair_platform_name || pair.pair_platform_ref))];
  // Nothing planned (the preview pairs only the planned tests): the warning says it all. Planned tests the targets
  // cannot run stay listed under the warning.
  if (blocker && planned.length === 0) {
    return (
      <div data-testid="ioc-validation-approval-summary" style={summaryStyle}>
        <Alert severity="warning" title={blocker} />
      </div>
    );
  }
  return (
    <div data-testid="ioc-validation-approval-summary" style={summaryStyle}>
      {blocker && <Alert severity="warning" title={blocker} />}
      <Text variant="content-compact" className="text-default-secondary">{t('Tests that run once approved')}</Text>
      <IocValidationTable
        caption={t('Tests that run once approved')}
        columns={columns}
        rows={planned.slice(0, APPROVAL_SUMMARY_MAX_ROWS)}
        rowKey={(ioc, index) => `${ioc.ioc_indicator_ref}-${ioc.ioc_test_kind ?? 'none'}-${index}`}
        emptyMessage={t('No test would run now.')}
      />
      {moreIndicators(planned.length)}
      <Field label={t('Security platforms')}>{emptyFilled(platforms.join(', '))}</Field>
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
  const [preview, setPreview] = useState<IocValidationApprovalPreviewOutput | null>(null);
  // The preview request in flight: an answer arriving after the dialog closed or reopened is ignored.
  const previewRequest = useRef(0);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [reason, setReason] = useState('');

  if (!isAwaitingApproval(iocValidation.ioc_validation_status)) {
    return null;
  }

  const shownPlanned = iocValidation.ioc_validation_iocs.filter(ioc => ioc.ioc_test_kind).length;
  // An approval never runs a test the request was shown without, so a request without planned test has nothing to
  // approve from the start, and a preview planning none has nothing to approve once it lands: no question then.
  const nothingToApprove = preview
    ? !!preview.ioc_validation_preview_blocker && preview.ioc_validation_preview_iocs.every(ioc => !ioc.ioc_test_kind)
    : shownPlanned === 0;

  const closeApproval = () => {
    previewRequest.current += 1;
    setApproveOpen(false);
  };

  // The request may have been decided elsewhere meanwhile: reload instead of leaving a stale page.
  const handleDecisionFailure = () => {
    closeApproval();
    setRejectOpen(false);
    onRefresh();
  };

  // The approval runs only if it plans what this preview shows, so the dialog never confirms a stale plan.
  const openApproval = () => {
    previewRequest.current += 1;
    const request = previewRequest.current;
    setPreview(null);
    setApproveOpen(true);
    fetchIocValidationApprovalPreview(iocValidation.ioc_validation_id)
      .then((result: { data: IocValidationApprovalPreviewOutput }) => {
        if (request === previewRequest.current) {
          setPreview(result.data);
        }
      })
      .catch(handleDecisionFailure);
  };

  const handleApprove = () => {
    if (!preview) {
      return undefined;
    }
    return approveIocValidation(iocValidation.ioc_validation_id, { ioc_validation_preview_fingerprint: preview.ioc_validation_preview_fingerprint })
      .then((result: { data: IocValidationOutput }) => {
        closeApproval();
        onUpdate(result.data);
        MESSAGING$.notifySuccess(t('The IOC validation has been approved. The validation simulation is starting.'));
      })
      .catch(handleDecisionFailure);
  };

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
          onClick={openApproval}
        >
          {t('Approve and start the simulation')}
        </Button>,
      )}
      <DialogConfirmation
        open={approveOpen}
        handleClose={closeApproval}
        handleSubmit={handleApprove}
        text={nothingToApprove
          ? undefined
          : t('Approve this IOC validation? A simulation starts at once and runs benign tests on the target assets of the validation scenario. Nothing is downloaded or executed from the indicators.')}
        submitLabel={t('Approve and start the simulation')}
        submitDisabled={!preview || !!preview.ioc_validation_preview_blocker}
        fullWidth
        extraContent={(
          <IocValidationApprovalSummary preview={preview} shownPlanned={shownPlanned} standalone={nothingToApprove} />
        )}
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
