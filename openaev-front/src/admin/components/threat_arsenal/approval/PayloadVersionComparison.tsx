import { Text } from '@filigran/design-system';
import { type FunctionComponent, useMemo } from 'react';

import BeforeAfter from '../../../../components/common/update/BeforeAfter';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadExecutableContent } from '../../../../utils/api-types';
import { versionFields } from './payloadVersionDiff';

interface Props {
  active: PayloadExecutableContent;
  pending: PayloadExecutableContent;
  activeNumber: number | string;
  pendingNumber: number | string;
  /** Heading; "What was proposed" for a rejected version. */
  title?: string;
}

/**
 * What a version changes in what the action runs: only the changed fields, each as a labelled
 * Before / After. Unchanged fields are not repeated.
 */
const PayloadVersionComparison: FunctionComponent<Props> = ({ active, pending, activeNumber, pendingNumber, title }) => {
  const { t } = useFormatter();
  const fields = useMemo(() => versionFields(active, pending), [active, pending]);
  const changed = fields.filter(field => field.changed);
  const beforeLabel = String(t('Before · v{version}', { version: String(activeNumber) }));
  const afterLabel = String(t('After · v{version}', { version: String(pendingNumber) }));

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 10,
    }}
    >
      <div>
        <Text variant="content-compact-bold">
          {`${title ?? t('What changed')} · v${activeNumber} → v${pendingNumber}`}
        </Text>
        <Text variant="content-caption" className="text-default-secondary">
          {changed.length === 0
            ? t('No change in what the action runs.')
            : t(changed.length === 1 ? '1 field changed: {fields}' : '{count} fields changed: {fields}', {
                count: String(changed.length),
                fields: changed.map(field => t(field.label)).join(', '),
              })}
        </Text>
      </div>
      {changed.map(field => (
        <BeforeAfter
          key={field.key}
          label={t(field.label)}
          before={field.active}
          after={field.pending}
          beforeLabel={beforeLabel}
          afterLabel={afterLabel}
        />
      ))}
    </div>
  );
};

export default PayloadVersionComparison;
