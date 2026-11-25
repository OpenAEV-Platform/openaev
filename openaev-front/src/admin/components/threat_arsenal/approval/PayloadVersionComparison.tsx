import { Text } from '@filigran/design-system';
import { type FunctionComponent } from 'react';

import { PAYLOAD_CODE_FIELDS, type PayloadFieldChange, payloadFieldLabel, payloadFieldText } from '../../../../components/common/content_versions/payloadFieldChanges';
import BeforeAfter from '../../../../components/common/update/BeforeAfter';
import DiffLegend from '../../../../components/common/update/DiffLegend';
import { useFormatter } from '../../../../components/i18n';

interface Props {
  /** What changed, field by field, as the API gives it (version changes or draft changes). */
  changes: PayloadFieldChange[];
  /** No version to compare with (a new action): every field is added. */
  isNew?: boolean;
  activeNumber?: number | string;
  pendingNumber: number | string;
  /** Heading; "What was proposed" for a rejected version. */
  title?: string;
  /** Payload drafts: the After side is a draft (no number), compared with the version it started from. */
  draft?: boolean;
}

/**
 * What a version changes: every changed field, each as a labelled Before / After, the same fields
 * as a new version and the History tab. Unchanged fields are not repeated. A new action has nothing
 * to compare with, so every field it has is shown as added, line by line: the approver sees all
 * they approve.
 */
const PayloadVersionComparison: FunctionComponent<Props> = ({ changes, isNew = false, activeNumber, pendingNumber, title, draft = false }) => {
  const { t } = useFormatter();
  const beforeLabel = String(t('Before · v{version}', { version: String(activeNumber ?? '-') }));
  const afterLabel = draft ? String(t('After · draft')) : String(t('After · v{version}', { version: String(pendingNumber) }));
  const fields = changes.map(change => payloadFieldLabel(t, change.field)).join(', ');
  let summary: string | undefined;
  if (isNew) {
    summary = t('New action: nothing was approved before, so everything below is new in v{version}.', { version: String(pendingNumber) });
  } else if (changes.length > 0 && draft) {
    summary = t(changes.length === 1 ? '1 field changed: {fields} · compared with v{version}, the version this draft started from' : '{count} fields changed: {fields} · compared with v{version}, the version this draft started from', {
      count: String(changes.length),
      fields,
      version: String(activeNumber),
    });
  } else if (changes.length > 0) {
    summary = t(changes.length === 1 ? '1 field changed: {fields} · compared with v{version}, the version in use' : '{count} fields changed: {fields} · compared with v{version}, the version in use', {
      count: String(changes.length),
      fields,
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
        {summary && <Text variant="content-compact" className="text-default-secondary">{summary}</Text>}
      </div>
      {/* One key above all the changed fields, the same as in the History tab; a new action has no Before. */}
      {changes.length > 0 && !isNew && <DiffLegend beforeLabel={beforeLabel} afterLabel={afterLabel} />}
      {changes.map(change => (
        <BeforeAfter
          key={change.field}
          label={payloadFieldLabel(t, change.field)}
          before={payloadFieldText(t, change.field, change.before)}
          after={payloadFieldText(t, change.field, change.after)}
          beforeLabel={beforeLabel}
          afterLabel={afterLabel}
          // Every field of a new action is drawn as added lines (+, green), even the one-word ones.
          code={isNew || PAYLOAD_CODE_FIELDS.has(change.field)}
          legend={false}
        />
      ))}
    </div>
  );
};

export default PayloadVersionComparison;
