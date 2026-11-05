import { Button, Dialog, DialogBody, DialogContent, DialogTitle, Text } from '@filigran/design-system';
// fds:keep-mui the library has no Table yet (Figma DS Table 8582:34772 only); dressed to its spec by FDS_TABLE_SX
import { Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material';
import { type FunctionComponent, useMemo, useState } from 'react';

import { FDS_TABLE_FRAME_STYLE, FDS_TABLE_SX } from '../../../../components/common/fdsTableSpec';
import BeforeAfter from '../../../../components/common/update/BeforeAfter';
import TextDiff from '../../../../components/common/update/TextDiff';
import { useFormatter } from '../../../../components/i18n';
import { type PayloadExecutableContent } from '../../../../utils/api-types';
import { tint } from '../../../../utils/tint';
import { versionFields } from './payloadVersionDiff';

interface Props {
  active: PayloadExecutableContent;
  pending: PayloadExecutableContent;
  activeNumber: number | string;
  pendingNumber: number | string;
  /** Heading; "What was proposed" for a rejected version. */
  title?: string;
}

// The drawer shows only the highlights; the full comparison is one click away.
const MAX_FIELDS = 3;

/**
 * What a version changes in the executable content, as highlights: each changed field as a labelled
 * Before / After (at most 3), then "See full comparison" with every field of both versions side by side.
 */
const PayloadVersionComparison: FunctionComponent<Props> = ({ active, pending, activeNumber, pendingNumber, title }) => {
  const { t } = useFormatter();
  const [full, setFull] = useState(false);
  const [showUnchanged, setShowUnchanged] = useState(false);
  const fields = useMemo(() => versionFields(active, pending), [active, pending]);
  const changed = fields.filter(field => field.changed);
  const unchanged = fields.filter(field => !field.changed);
  const beforeLabel = String(t('Before · v{version}', { version: String(activeNumber) }));
  const afterLabel = String(t('After · v{version}', { version: String(pendingNumber) }));
  const mono = {
    fontFamily: 'monospace',
    fontSize: 12,
    whiteSpace: 'pre-wrap' as const,
    overflowWrap: 'anywhere' as const,
  };

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
      {changed.slice(0, MAX_FIELDS).map(field => (
        <BeforeAfter
          key={field.key}
          label={t(field.label)}
          before={field.active}
          after={field.pending}
          beforeLabel={beforeLabel}
          afterLabel={afterLabel}
        />
      ))}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
      }}
      >
        {changed.length > MAX_FIELDS && (
          <Text variant="content-caption" className="text-default-secondary">
            {t('+ {count} more changes', { count: String(changed.length - MAX_FIELDS) })}
          </Text>
        )}
        <Button size="sm" priority="tertiary" onClick={() => setFull(true)}>{t('See full comparison')}</Button>
      </div>
      {full && (
        <Dialog open onOpenChange={open => !open && setFull(false)}>
          <DialogContent size="lg">
            <DialogTitle>{`v${activeNumber} → v${pendingNumber}`}</DialogTitle>
            <DialogBody>
              <div style={{
                display: 'flex',
                flexDirection: 'column',
                gap: 12,
              }}
              >
                <Text variant="content-caption" className="text-default-secondary">{t('Changed fields first, then the rest. Read-only.')}</Text>
                <div style={FDS_TABLE_FRAME_STYLE}>
                  <Table
                    size="small"
                    aria-label={t('Full comparison')}
                    sx={{
                      ...FDS_TABLE_SX,
                      tableLayout: 'fixed',
                    }}
                  >
                    <TableHead>
                      <TableRow>
                        <TableCell sx={{ width: '20%' }}>{t('Field')}</TableCell>
                        <TableCell sx={{ width: '40%' }}>{beforeLabel}</TableCell>
                        <TableCell sx={{ width: '40%' }}>{afterLabel}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {changed.map(field => (
                        <TableRow key={field.key} sx={{ backgroundColor: tint('var(--color-filigran-brand-primary)', 6) }}>
                          <TableCell sx={{ verticalAlign: 'top' }}>{t(field.label)}</TableCell>
                          <TableCell sx={{ verticalAlign: 'top' }}>
                            {field.active ? <TextDiff text={field.active} other={field.pending} side="before" /> : '-'}
                          </TableCell>
                          <TableCell sx={{ verticalAlign: 'top' }}>
                            {field.pending ? <TextDiff text={field.pending} other={field.active} side="after" /> : '-'}
                          </TableCell>
                        </TableRow>
                      ))}
                      {showUnchanged && unchanged.map(field => (
                        <TableRow key={field.key}>
                          <TableCell sx={{ verticalAlign: 'top' }}>{t(field.label)}</TableCell>
                          <TableCell sx={{
                            ...mono,
                            verticalAlign: 'top',
                            color: 'var(--text-default-secondary)',
                          }}
                          >
                            {field.active || '-'}
                          </TableCell>
                          <TableCell sx={{
                            ...mono,
                            verticalAlign: 'top',
                          }}
                          >
                            {field.pending || '-'}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
                {unchanged.length > 0 && (
                  <div>
                    <Button size="sm" priority="tertiary" onClick={() => setShowUnchanged(!showUnchanged)}>
                      {showUnchanged
                        ? t('Hide unchanged fields')
                        : t('Show {count} unchanged fields', { count: String(unchanged.length) })}
                    </Button>
                  </div>
                )}
              </div>
            </DialogBody>
          </DialogContent>
        </Dialog>
      )}
    </div>
  );
};

export default PayloadVersionComparison;
