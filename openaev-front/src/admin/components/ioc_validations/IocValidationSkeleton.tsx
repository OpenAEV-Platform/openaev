// fds:keep-mui the library ships no loading placeholder (skeleton) component yet
import { Skeleton } from '@mui/material';
import { type FunctionComponent } from 'react';

import { Section } from '../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../components/i18n';

// A label over a value, as Field renders them.
const FieldSkeleton: FunctionComponent = () => (
  <div>
    <Skeleton variant="text" width="45%" sx={{ fontSize: 12 }} />
    <Skeleton variant="text" width="70%" sx={{ fontSize: 14 }} />
  </div>
);

const RowsSkeleton: FunctionComponent<{ rows: number }> = ({ rows }) => (
  <div style={{
    display: 'grid',
    gap: 8,
  }}
  >
    <Skeleton variant="text" width="100%" sx={{ fontSize: 13 }} />
    {Array.from({ length: rows }, (_, index) => (
      <Skeleton key={index} variant="rounded" height={32} />
    ))}
  </div>
);

/**
 * Loading state of an IOC validation request, in the shape of the loaded page (breadcrumb, header with its status
 * and decision, request and results sections, tested IOCs and security platforms), so nothing shifts on arrival.
 */
const IocValidationSkeleton: FunctionComponent = () => {
  const { t } = useFormatter();
  return (
    <section data-testid="ioc-validation-skeleton" aria-busy="true">
      <Skeleton
        variant="text"
        width={320}
        sx={{
          fontSize: 13,
          marginBottom: 1,
        }}
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
          flex: 1,
        }}
        >
          <Skeleton variant="text" width="35%" sx={{ fontSize: 24 }} />
          <Skeleton variant="rounded" width={96} height={24} />
        </div>
        <div style={{
          display: 'flex',
          gap: 8,
        }}
        >
          <Skeleton variant="rounded" width={96} height={32} />
          <Skeleton variant="rounded" width={112} height={32} />
        </div>
      </header>
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
            {Array.from({ length: 6 }, (_, index) => <FieldSkeleton key={index} />)}
          </div>
        </Section>
        <Section title={t('Results')}>
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(3, minmax(0, 1fr))',
            gap: 16,
          }}
          >
            {Array.from({ length: 6 }, (_, index) => <FieldSkeleton key={index} />)}
          </div>
        </Section>
      </div>
      <div style={{
        display: 'grid',
        gap: 16,
      }}
      >
        <Section title={t('Tested IOCs')}>
          <RowsSkeleton rows={3} />
        </Section>
        <Section title={t('Security platforms')}>
          <RowsSkeleton rows={2} />
        </Section>
      </div>
    </section>
  );
};

export default IocValidationSkeleton;
