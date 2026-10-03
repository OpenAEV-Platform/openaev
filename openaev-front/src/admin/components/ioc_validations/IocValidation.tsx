import { Button, Text } from '@filigran/design-system';
import { OpenInNewOutlined } from '@mui/icons-material';
import { Alert } from '@mui/material';
import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';

import { fetchIocValidation } from '../../../actions/ioc_validations/ioc-validation-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { Field, Section } from '../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../components/i18n';
import Loader from '../../../components/Loader';
import type { IocValidationIocOutput, IocValidationOutput, IocValidationPairOutput } from '../../../utils/api-types';
import IocValidationDecisionActions from './IocValidationDecisionActions';
import IocValidationOutcomeChip from './IocValidationOutcomeChip';
import IocValidationStatusChip from './IocValidationStatusChip';
import IocValidationTable, { type IocValidationTableColumn } from './IocValidationTable';
import {
  countIocValidationOutcomes,
  IOC_VALIDATION_BASE_URL,
  IOC_VALIDATION_POLL_INTERVAL_MS,
  iocValidationTestKindLabel,
  isPollingStatus,
  isWebLink,
} from './iocValidationUtils';

const IocValidation = () => {
  const { t, fldt } = useFormatter();
  const { iocValidationId } = useParams() as { iocValidationId: string };
  const [iocValidation, setIocValidation] = useState<IocValidationOutput | null>(null);
  const [notFound, setNotFound] = useState(false);

  const load = useCallback(() => fetchIocValidation(iocValidationId)
    .then((result: { data: IocValidationOutput }) => {
      setIocValidation(result.data);
      setNotFound(false);
    })
    .catch(() => setNotFound(true)), [iocValidationId]);

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

  if (notFound) {
    return <Alert severity="warning" variant="outlined">{t('This IOC validation does not exist or is not available in this tenant.')}</Alert>;
  }
  if (!iocValidation) {
    return <Loader />;
  }

  const counts = countIocValidationOutcomes(iocValidation.ioc_validation_pairs);
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
          <Text variant="content-compact" className="text-default-secondary">{ioc.ioc_observable_type}</Text>
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
      render: ioc => (ioc.ioc_test_kind ? t(iocValidationTestKindLabel(ioc.ioc_test_kind)) : t('Skipped')),
    },
    {
      key: 'message',
      label: t('Details'),
      width: '28%',
      render: ioc => ioc.ioc_message || '-',
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
      render: pair => (pair.pair_security_platform_id
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
      render: pair => pair.pair_outcome_reason || '-',
    },
    {
      key: 'evaluated',
      label: t('Evaluated'),
      width: '14%',
      render: pair => (pair.pair_evaluated_at ? fldt(pair.pair_evaluated_at) : '-'),
    },
  ];

  const openctiUrl = isWebLink(iocValidation.ioc_validation_opencti_url) ? iocValidation.ioc_validation_opencti_url : undefined;

  return (
    <section data-testid="ioc-validation-detail">
      <Breadcrumbs
        variant="object"
        elements={[
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
          <IocValidationDecisionActions iocValidation={iocValidation} onUpdate={setIocValidation} onRefresh={load} />
        </div>
      </header>
      {iocValidation.ioc_validation_status_message && (
        <Alert severity="info" variant="outlined" style={{ marginBottom: 16 }}>
          {iocValidation.ioc_validation_status_message}
        </Alert>
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
            <Field label={t('Received')}>{fldt(iocValidation.ioc_validation_created_at)}</Field>
            <Field label={t('Decided by')}>{iocValidation.ioc_validation_decided_by_name || '-'}</Field>
            <Field label={t('Decided')}>{iocValidation.ioc_validation_decided_at ? fldt(iocValidation.ioc_validation_decided_at) : '-'}</Field>
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
        <Section title={t('Results')}>
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
            <Field label={t('Completed')}>{iocValidation.ioc_validation_completed_at ? fldt(iocValidation.ioc_validation_completed_at) : '-'}</Field>
          </div>
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
        </Section>
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
