import { Text } from '@filigran/design-system';
import { type FunctionComponent, useMemo } from 'react';

import BeforeAfter from '../../../../components/common/update/BeforeAfter';
import DiffLegend from '../../../../components/common/update/DiffLegend';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadExecutableContent, versionFields } from './payloadVersionDiff';

interface Props {
  /** The version compared with; none for a new action (no version approved yet): everything is added. */
  active?: PayloadExecutableContent;
  pending: PayloadExecutableContent;
  activeNumber?: number | string;
  pendingNumber: number | string;
  /** Heading; "What was proposed" for a rejected version. */
  title?: string;
}

// What the action runs, compared line by line like code; the other fields as Before / After.
const CODE_FIELDS = new Set(['content', 'cleanup_command', 'arguments', 'prerequisites', 'multi_turn', 'success_detector']);

/**
 * What a version changes in what the action runs: only the changed fields, each as a labelled
 * Before / After. Unchanged fields are not repeated. A new action has nothing to compare with, so
 * every field it runs is shown as added, line by line: the approver sees all they approve.
 */
const PayloadVersionComparison: FunctionComponent<Props> = ({ active, pending, activeNumber, pendingNumber, title }) => {
  const { t } = useFormatter();
  const isNew = !active;
  const fields = useMemo(() => versionFields(active ?? {}, pending), [active, pending]);
  const changed = fields.filter(field => field.changed);
  const beforeLabel = String(t('Before · v{version}', { version: String(activeNumber ?? '-') }));
  const afterLabel = String(t('After · v{version}', { version: String(pendingNumber) }));
  let summary: string;
  if (isNew) {
    summary = t('New action: nothing was approved before, so everything below is new in v{version}.', { version: String(pendingNumber) });
  } else if (changed.length === 0) {
    summary = t('No change in what the action runs.');
  } else {
    summary = t(changed.length === 1 ? '1 field changed: {fields} · compared with v{version}, the version in use' : '{count} fields changed: {fields} · compared with v{version}, the version in use', {
      count: String(changed.length),
      fields: changed.map(field => t(field.label)).join(', '),
      version: String(activeNumber),
    });
  }

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 12,
    }}
    >
      <div>
        <Text as="h3" variant="title-xs" style={{ margin: 0 }}>
          {title ?? t(isNew ? 'What this version runs' : 'What changed')}
        </Text>
        <Text variant="content-compact" className="text-default-secondary">{summary}</Text>
      </div>
      {/* One key above all the changed fields, the same as in the History tab; a new action has no Before. */}
      {changed.length > 0 && !isNew && <DiffLegend beforeLabel={beforeLabel} afterLabel={afterLabel} />}
      {changed.map(field => (
        <BeforeAfter
          key={field.key}
          label={t(field.label)}
          before={field.active}
          after={field.pending}
          beforeLabel={beforeLabel}
          afterLabel={afterLabel}
          // Every field of a new action is drawn as added lines (+, green), even the one-word ones.
          code={isNew || CODE_FIELDS.has(field.key)}
          legend={false}
        />
      ))}
    </div>
  );
};

export default PayloadVersionComparison;
