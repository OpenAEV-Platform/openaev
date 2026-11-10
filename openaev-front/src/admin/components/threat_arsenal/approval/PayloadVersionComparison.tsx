import { Text } from '@filigran/design-system';
import { type FunctionComponent, useMemo } from 'react';

import BeforeAfter from '../../../../components/common/update/BeforeAfter';
import DiffLegend from '../../../../components/common/update/DiffLegend';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadExecutableContent, versionFields } from './payloadVersionDiff';

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
// What the action runs, compared line by line like code; the other fields as Before / After.
const CODE_FIELDS = new Set(['content', 'cleanup_command', 'arguments', 'prerequisites', 'multi_turn', 'success_detector']);

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
      gap: 12,
    }}
    >
      <div>
        <Text as="h3" variant="title-xs" style={{ margin: 0 }}>
          {title ?? t('What changed')}
        </Text>
        <Text variant="content-compact" className="text-default-secondary">
          {changed.length === 0
            ? t('No change in what the action runs.')
            : t(changed.length === 1 ? '1 field changed: {fields} · compared with v{version}, the version in use' : '{count} fields changed: {fields} · compared with v{version}, the version in use', {
                count: String(changed.length),
                fields: changed.map(field => t(field.label)).join(', '),
                version: String(activeNumber),
              })}
        </Text>
      </div>
      {/* One key above all the changed fields, the same as in the History tab. */}
      {changed.length > 0 && <DiffLegend beforeLabel={beforeLabel} afterLabel={afterLabel} />}
      {changed.map(field => (
        <BeforeAfter
          key={field.key}
          label={t(field.label)}
          before={field.active}
          after={field.pending}
          beforeLabel={beforeLabel}
          afterLabel={afterLabel}
          code={CODE_FIELDS.has(field.key)}
          legend={false}
        />
      ))}
    </div>
  );
};

export default PayloadVersionComparison;
