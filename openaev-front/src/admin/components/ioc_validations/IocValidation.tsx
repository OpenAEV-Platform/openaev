import { Alert, Button, Text, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { OpenInNewOutlined } from '@mui/icons-material';
import { useEffect } from 'react';
import { Link, useParams } from 'react-router';

import Breadcrumbs from '../../../components/Breadcrumbs';
import { Field, Section, SectionBlock } from '../../../components/common/detail/EntityDetailCommon';
import EllipsisTooltip from '../../../components/common/EllipsisTooltip';
import Empty from '../../../components/Empty';
import { useFormatter } from '../../../components/i18n';
import type { IocValidationIocOutput, IocValidationPairOutput } from '../../../utils/api-types';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { emptyFilled } from '../../../utils/String';
import IocValidationDate from './IocValidationDate';
import IocValidationDecisionActions from './IocValidationDecisionActions';
import IocValidationOutcomeChip from './IocValidationOutcomeChip';
import IocValidationSkeleton from './IocValidationSkeleton';
import IocValidationStatusChip from './IocValidationStatusChip';
import IocValidationTable, { type IocValidationTableColumn } from './IocValidationTable';
import {
  countIocValidationOutcomes,
  IOC_VALIDATION_BASE_URL,
  IOC_VALIDATION_FOCUS_RING_CLASS,
  IOC_VALIDATION_POLL_INTERVAL_MS,
  IOC_VALIDATION_SETTINGS_URL,
  iocValidationObservableTypeLabel,
  iocValidationTestKindLabel,
  isAwaitingApproval,
  isPollingStatus,
  isWebLink,
} from './iocValidationUtils';
import useIocValidation from './useIocValidation';

const IocValidation = () => {
  const { t } = useFormatter();
  const ability = useAbility();
  const canManageSettings = ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS);
  const canAccessSecurityPlatforms = ability.can(ACTIONS.ACCESS, SUBJECTS.SECURITY_PLATFORMS);
  const { iocValidationId } = useParams() as { iocValidationId: string };
  const { iocValidation, loadError, load, update } = useIocValidation(iocValidationId);

  useEffect(() => {
    load();
  }, [load]);

  // Results are computed by the background job: refresh while the request can still change.
  const status = iocValidation?.ioc_validation_status;
  useEffect(() => {
    if (!isPollingStatus(status)) {
      return undefined;
    }
    const interval = setInterval(load, IOC_VALIDATION_POLL_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [status, load]);

  if (loadError === 'failed' && !iocValidation) {
    return (
      <Alert
        severity="error"
        title={t('This IOC validation could not be loaded.')}
        action={(
          <Button priority="secondary" size="sm" onClick={() => load()}>
            {t('Retry')}
          </Button>
        )}
      />
    );
  }
  if (loadError === 'not_found') {
    return (
      <Alert
        severity="warning"
        title={t('This IOC validation does not exist or is not available in this tenant.')}
        action={(
          <Button asChild priority="secondary" size="sm">
            <Link to={IOC_VALIDATION_BASE_URL}>{t('Back to IOC validations')}</Link>
          </Button>
        )}
      />
    );
  }
  if (!iocValidation) {
    return <IocValidationSkeleton />;
  }

  const counts = countIocValidationOutcomes(iocValidation.ioc_validation_pairs);
  const awaitingApproval = isAwaitingApproval(iocValidation.ioc_validation_status);
  const rejected = iocValidation.ioc_validation_status === 'REJECTED';
  const runnable = iocValidation.ioc_validation_iocs.filter(ioc => ioc.ioc_test_kind).length;
  const skipped = iocValidation.ioc_validation_iocs.length - runnable;
  const awaitingMessage = skipped > 0
    ? t('Waiting for approval - IOCs to test: {runnable}, skipped by the safety settings: {skipped}', {
        runnable: String(runnable),
        skipped: String(skipped),
      })
    : t('Waiting for approval - IOCs to test: {runnable}', { runnable: String(runnable) });
  const statusMessage = awaitingApproval ? awaitingMessage : iocValidation.ioc_validation_status_message;
  const allowedTestKinds = iocValidation.ioc_validation_allowed_test_kinds ?? [];
  const skippedBySettings = (ioc: IocValidationIocOutput) => !ioc.ioc_test_kind
    && !!ioc.ioc_requested_test_kind && !allowedTestKinds.includes(ioc.ioc_requested_test_kind);
  const indicatorNames = new Map(iocValidation.ioc_validation_iocs.map(ioc => [ioc.ioc_indicator_ref, ioc.ioc_indicator_name]));
  const indicatorLabel = (ref: string) => indicatorNames.get(ref) || ref;

  const iocColumns: IocValidationTableColumn<IocValidationIocOutput>[] = [
    {
      key: 'indicator',
      label: t('Indicator'),
      width: '20%',
      render: ioc => ioc.ioc_indicator_name || ioc.ioc_indicator_ref,
    },
    {
      key: 'observable',
      label: t('Observable'),
      width: '24%',
      render: ioc => (
        <>
          <Text variant="content-compact" className="text-default-secondary">{t(iocValidationObservableTypeLabel(ioc.ioc_observable_type))}</Text>
          <Text variant="content-code" style={{ display: 'block' }}>{ioc.ioc_value}</Text>
        </>
      ),
    },
    {
      key: 'requested',
      label: t('Requested test'),
      width: '14%',
      render: ioc => t(iocValidationTestKindLabel(ioc.ioc_requested_test_kind)),
    },
    {
      key: 'planned',
      label: t('Test that runs'),
      width: '14%',
      render: (ioc) => {
        if (ioc.ioc_test_kind) {
          return t(iocValidationTestKindLabel(ioc.ioc_test_kind));
        }
        return ioc.ioc_refused ? t('Refused') : t('Skipped');
      },
    },
    {
      key: 'message',
      label: t('Details'),
      width: '28%',
      render: (ioc) => {
        if (!skippedBySettings(ioc)) {
          return <EllipsisTooltip>{emptyFilled(ioc.ioc_message)}</EllipsisTooltip>;
        }
        // The requested test has its own column; the planner's full sentence goes to the tooltip
        const reason = (
          <span
            tabIndex={ioc.ioc_message ? 0 : undefined}
            className={ioc.ioc_message ? IOC_VALIDATION_FOCUS_RING_CLASS : undefined}
            style={{
              minWidth: 0,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {t('Not allowed by the safety settings')}
          </span>
        );
        return (
          <span style={{
            display: 'flex',
            alignItems: 'baseline',
            gap: 8,
          }}
          >
            {ioc.ioc_message
              ? (
                  <Tooltip>
                    <TooltipTrigger asChild>{reason}</TooltipTrigger>
                    <TooltipContent>{ioc.ioc_message}</TooltipContent>
                  </Tooltip>
                )
              : reason}
            {canManageSettings && (
              <Link
                to={IOC_VALIDATION_SETTINGS_URL}
                className={IOC_VALIDATION_FOCUS_RING_CLASS}
                style={{
                  flexShrink: 0,
                  whiteSpace: 'nowrap',
                }}
              >
                {t('Open settings')}
              </Link>
            )}
          </span>
        );
      },
    },
  ];

  const pairColumns: IocValidationTableColumn<IocValidationPairOutput>[] = [
    {
      key: 'indicator',
      label: t('Indicator'),
      width: '24%',
      render: pair => indicatorLabel(pair.pair_indicator_ref),
    },
    {
      key: 'platform',
      label: t('Security platform'),
      width: '20%',
      render: pair => (pair.pair_security_platform_id && canAccessSecurityPlatforms
        ? <Link to={`/admin/security_platforms/${pair.pair_security_platform_id}`}>{pair.pair_platform_name || pair.pair_platform_ref}</Link>
        : (pair.pair_platform_name || pair.pair_platform_ref)),
    },
    {
      key: 'outcome',
      label: t('Outcome'),
      width: '14%',
      render: pair => <IocValidationOutcomeChip outcome={pair.pair_outcome} />,
    },
    {
      key: 'reason',
      label: t('Details'),
      width: '28%',
      render: pair => <EllipsisTooltip>{emptyFilled(pair.pair_outcome_reason)}</EllipsisTooltip>,
    },
    {
      key: 'evaluated',
      label: t('Evaluated'),
      width: '14%',
      render: pair => (pair.pair_evaluated_at ? <IocValidationDate date={pair.pair_evaluated_at} /> : '-'),
    },
  ];

  const openctiUrl = isWebLink(iocValidation.ioc_validation_opencti_url) ? iocValidation.ioc_validation_opencti_url : undefined;

  return (
    <section data-testid="ioc-validation-detail">
      <Breadcrumbs
        variant="object"
        elements={[
          {
            label: t('Atomic testings'),
            link: '/admin/atomic_testings',
          },
          {
            label: t('IOC validations'),
            link: IOC_VALIDATION_BASE_URL,
          },
          {
            label: iocValidation.ioc_validation_name,
            current: true,
          },
        ]}
      />
      <header style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 16,
        marginBottom: 16,
      }}
      >
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          minWidth: 0,
        }}
        >
          <Text variant="title-md" style={{ overflowWrap: 'anywhere' }}>{iocValidation.ioc_validation_name}</Text>
          <IocValidationStatusChip status={iocValidation.ioc_validation_status} />
        </div>
        <div style={{
          display: 'flex',
          gap: 8,
        }}
        >
          {openctiUrl && (
            <Button asChild priority="secondary">
              <a href={openctiUrl} target="_blank" rel="noopener noreferrer">
                <OpenInNewOutlined fontSize="small" />
                {t('Open in OpenCTI')}
              </a>
            </Button>
          )}
          <IocValidationDecisionActions iocValidation={iocValidation} onUpdate={update} onRefresh={load} />
        </div>
      </header>
      {statusMessage && (
        <Alert severity="info" title={statusMessage} style={{ marginBottom: 16 }} />
      )}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
        gap: 16,
        marginBottom: 16,
      }}
      >
        <Section title={t('Request')}>
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
            gap: 16,
          }}
          >
            <Field label={t('Requested by')}>{iocValidation.ioc_validation_requested_by || '-'}</Field>
            <Field label={t('Received')}><IocValidationDate date={iocValidation.ioc_validation_created_at} /></Field>
            {iocValidation.ioc_validation_decided_at && (
              <>
                <Field label={t('Decided by')}>{iocValidation.ioc_validation_decided_by_name || '-'}</Field>
                <Field label={t('Decided')}><IocValidationDate date={iocValidation.ioc_validation_decided_at} /></Field>
              </>
            )}
            <Field label={t('Requested tests')}>
              {iocValidation.ioc_validation_requested_test_kinds.map(kind => t(iocValidationTestKindLabel(kind))).join(', ') || '-'}
            </Field>
            <Field label={t('Allowed tests')}>
              {(iocValidation.ioc_validation_allowed_test_kinds ?? []).map(kind => t(iocValidationTestKindLabel(kind))).join(', ') || '-'}
            </Field>
          </div>
          {iocValidation.ioc_validation_description && (
            <div style={{ marginTop: 16 }}>
              <Field label={t('Description')}>{iocValidation.ioc_validation_description}</Field>
            </div>
          )}
        </Section>
        <SectionBlock title={t('Results')} centerContent={awaitingApproval || rejected}>
          {awaitingApproval || rejected
            ? <Empty message={awaitingApproval ? t('Results appear once the simulation runs') : t('No test ran: the request was rejected')} />
            : (
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(3, minmax(0, 1fr))',
                  gap: 16,
                }}
                >
                  <Field label={t('Prevented')}>{counts.prevented}</Field>
                  <Field label={t('Detected')}>{counts.detected}</Field>
                  <Field label={t('Missed')}>{counts.missed}</Field>
                  <Field label={t('Errors')}>{counts.error}</Field>
                  <Field label={t('Pending')}>{counts.pending}</Field>
                  {iocValidation.ioc_validation_completed_at && (
                    <Field label={t('Completed')}><IocValidationDate date={iocValidation.ioc_validation_completed_at} /></Field>
                  )}
                </div>
              )}
          {(iocValidation.ioc_validation_scenario_id || iocValidation.ioc_validation_simulation_id) && (
            <div style={{
              display: 'flex',
              gap: 8,
              marginTop: 16,
            }}
            >
              {iocValidation.ioc_validation_scenario_id && (
                <Button asChild priority="secondary" size="sm">
                  <Link to={`/admin/scenarios/${iocValidation.ioc_validation_scenario_id}`}>{t('Validation scenario')}</Link>
                </Button>
              )}
              {iocValidation.ioc_validation_simulation_id && (
                <Button asChild priority="secondary" size="sm">
                  <Link to={`/admin/simulations/${iocValidation.ioc_validation_simulation_id}`}>{t('Validation simulation')}</Link>
                </Button>
              )}
            </div>
          )}
        </SectionBlock>
      </div>
      <div style={{
        display: 'grid',
        gap: 16,
      }}
      >
        <Section title={t('Tested IOCs')}>
          <IocValidationTable
            caption={t('Tested IOCs')}
            columns={iocColumns}
            rows={iocValidation.ioc_validation_iocs}
            rowKey={(ioc, index) => `${ioc.ioc_indicator_ref}-${ioc.ioc_requested_test_kind ?? 'none'}-${index}`}
            emptyMessage={t('No IOC in this request.')}
          />
        </Section>
        <Section title={t('Security platforms')}>
          <IocValidationTable
            caption={t('Security platforms')}
            columns={pairColumns}
            rows={iocValidation.ioc_validation_pairs}
            rowKey={pair => pair.pair_deployed_on_ref}
            emptyMessage={t('No security platform in this request.')}
          />
        </Section>
      </div>
    </section>
  );
};

export default IocValidation;
