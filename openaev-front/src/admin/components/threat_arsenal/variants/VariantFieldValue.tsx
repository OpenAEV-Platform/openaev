import { Chip, Text } from '@filigran/design-system';

import TextDiff from '../../../../components/common/update/TextDiff';
import { useFormatter } from '../../../../components/i18n';

interface Props {
  value: unknown;
  // The value on the other side of a conflict: a text shows the words that differ.
  other?: unknown;
}

const line = (text: string, key?: number) => (
  <div
    key={key}
    style={{
      fontFamily: 'monospace',
      fontSize: 12,
      whiteSpace: 'pre-wrap',
      overflowWrap: 'anywhere',
    }}
  >
    {text}
  </div>
);

// A customizable value read as text: lists as chips or lines, never as JSON.
const VariantFieldValue = ({ value, other }: Props) => {
  const { t } = useFormatter();
  if (value === null || value === undefined || value === '' || (Array.isArray(value) && value.length === 0)) {
    return <Text variant="content-caption">{t('Empty')}</Text>;
  }
  if (Array.isArray(value)) {
    if (value.every(item => typeof item === 'string')) {
      return (
        <div style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 4,
        }}
        >
          {(value as string[]).map(item => <Chip key={item} label={item} />)}
        </div>
      );
    }
    return (
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 4,
      }}
      >
        {(value as Record<string, unknown>[]).map((item, index) => line(
          item.key !== undefined
            ? `${item.key} = ${item.default_value ?? ''}`
            : `${item.executor ?? ''}: ${item.get_command ?? ''}`,
          index,
        ))}
      </div>
    );
  }
  if (typeof value === 'string' && typeof other === 'string') {
    return <TextDiff text={value} other={other} />;
  }
  return line(String(value));
};

export default VariantFieldValue;
