import { Checkbox, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo, useState } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type PayloadExecutableContent } from '../../../../utils/api-types';
import { lineDiff, versionFields } from './payloadVersionDiff';

interface Props {
  active: PayloadExecutableContent;
  pending: PayloadExecutableContent;
  activeNumber: number;
  pendingNumber: number;
}

/**
 * What a pending version changes in the executable content: the changed fields, multi-line ones
 * (command, arguments, prerequisites, cleanup) as a line diff. "Show unchanged fields" lists every
 * field of both versions, so either version can be read in full.
 */
const PayloadVersionComparison: FunctionComponent<Props> = ({ active, pending, activeNumber, pendingNumber }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [showAll, setShowAll] = useState(false);
  const fields = useMemo(() => versionFields(active, pending), [active, pending]);
  const shown = showAll ? fields : fields.filter(field => field.changed);

  const mono = {
    fontFamily: 'monospace',
    fontSize: 12,
    whiteSpace: 'pre-wrap' as const,
    wordBreak: 'break-word' as const,
    margin: 0,
  };
  const lineColor = (type: string) => {
    if (type === 'added') return theme.palette.success.main;
    if (type === 'removed') return theme.palette.error.main;
    return theme.palette.text.secondary;
  };
  const linePrefix = (type: string) => {
    if (type === 'added') return '+ ';
    if (type === 'removed') return '- ';
    return '  ';
  };

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(1.5),
    }}
    >
      {shown.length === 0 && (
        <Text variant="content-caption" className="text-default-secondary">
          {t('No change in what the action runs.')}
        </Text>
      )}
      {shown.map(field => (
        <div key={field.key}>
          <Text variant="content-caption" style={{ fontWeight: 600 }}>
            {t(field.label)}
            {!field.changed && ` · ${t('Unchanged')}`}
          </Text>
          {field.multiline && field.changed && (
            <pre aria-label={t(field.label)} style={mono}>
              {lineDiff(field.active, field.pending).map((line, index) => (
                <div key={`${field.key}-${index}`} style={{ color: lineColor(line.type) }}>
                  {`${linePrefix(line.type)}${line.text}`}
                </div>
              ))}
            </pre>
          )}
          {(!field.multiline || !field.changed) && (
            <div style={{
              display: 'grid',
              gridTemplateColumns: '1fr 1fr',
              gap: theme.spacing(1),
            }}
            >
              <div>
                <Text variant="content-caption" className="text-default-secondary">{`${t('Active')} (v${activeNumber})`}</Text>
                <pre style={mono}>{field.active || '-'}</pre>
              </div>
              <div>
                <Text variant="content-caption" className="text-default-secondary">{`${t('Pending')} (v${pendingNumber})`}</Text>
                <pre style={mono}>{field.pending || '-'}</pre>
              </div>
            </div>
          )}
        </div>
      ))}
      <Checkbox
        label={t('Show unchanged fields')}
        checked={showAll}
        onCheckedChange={checked => setShowAll(checked === true)}
      />
    </div>
  );
};

export default PayloadVersionComparison;
