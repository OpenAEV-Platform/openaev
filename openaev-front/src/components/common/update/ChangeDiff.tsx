import { Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, useMemo } from 'react';

import { tint } from '../../../utils/tint';
import { useFormatter } from '../../i18n';
import { type DiffLine, diffRuns, lineDiff } from './textDiffRuns';

export interface ChangeDiffRow {
  id: string;
  field: string;
  before: string;
  after: string;
}

interface Props {
  rows: ChangeDiffRow[];
  beforeLabel: string;
  afterLabel: string;
}

const KINDS: Record<DiffLine['kind'], {
  sign: string;
  color: string;
  line?: string;
  word?: string;
}> = {
  same: {
    sign: ' ',
    color: 'var(--text-default-secondary)',
  },
  added: {
    sign: '+',
    color: 'var(--icon-success)',
    line: 'var(--bg-alert-success)',
    word: tint('var(--color-feedback-success-secondary)', 55),
  },
  removed: {
    sign: '−',
    color: 'var(--icon-error)',
    line: 'var(--bg-alert-error)',
    word: tint('var(--color-feedback-error-secondary)', 55),
  },
};

const BORDER = '1px solid var(--border-elevation-subtle-soft)';

interface ShownLine extends DiffLine {
  // The words of the line, those that changed marked; only on a line paired with another.
  runs?: ReturnType<typeof diffRuns>;
}

const shownLines = (before: string, after: string): ShownLine[] => lineDiff(before, after).map(line => ({
  ...line,
  runs: line.counterpart === undefined ? undefined : diffRuns(line.text, line.counterpart),
}));

const lineText = (line: ShownLine) => {
  if (!line.runs) {
    return line.text || ' ';
  }
  return line.runs.map(({ token, changed }, index) => (changed
    ? (
        <mark
          key={index}
          style={{
            background: KINDS[line.kind].word,
            color: 'inherit',
            borderRadius: 'var(--radius-sm)',
          }}
        >
          {token}
        </mark>
      )
    : token));
};

interface FieldDiffProps { row: ChangeDiffRow }

// One changed field read line by line: removed lines in red, added ones in green, both numbered.
const FieldDiff = ({ row }: FieldDiffProps) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const lines = useMemo(() => shownLines(row.before, row.after), [row.before, row.after]);
  const added = lines.filter(line => line.kind === 'added').length;
  const removed = lines.filter(line => line.kind === 'removed').length;
  const gutter: CSSProperties = {
    width: theme.spacing(4.5),
    flexShrink: 0,
    paddingRight: theme.spacing(0.75),
    textAlign: 'right',
    color: 'var(--text-default-secondary)',
    userSelect: 'none',
  };
  return (
    <div
      role="group"
      aria-label={`${row.field} · ${t('{added} lines added, {removed} lines removed', {
        added: String(added),
        removed: String(removed),
      })}`}
      style={{
        border: BORDER,
        borderRadius: 'var(--radius-sm)',
        overflow: 'hidden',
      }}
    >
      <div style={{
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: theme.spacing(1.5),
        padding: theme.spacing(1, 1.5),
        borderBottom: BORDER,
      }}
      >
        <Text variant="content-base-bold">{row.field}</Text>
        <Text variant="content-compact-bold" aria-hidden>
          <span style={{ color: KINDS.added.color }}>{`+${added}`}</span>
          {' '}
          <span style={{ color: KINDS.removed.color }}>{`−${removed}`}</span>
        </Text>
      </div>
      <div style={{
        maxHeight: theme.spacing(40),
        overflow: 'auto',
        fontFamily: 'var(--font-content-code), monospace',
        fontSize: 'var(--text-content-code)',
        lineHeight: theme.spacing(2.75),
      }}
      >
        {lines.map(line => (
          <div
            key={`${line.kind}-${line.beforeNo ?? ''}-${line.afterNo ?? ''}`}
            data-kind={line.kind}
            style={{
              display: 'flex',
              background: KINDS[line.kind].line,
            }}
          >
            <span aria-hidden style={gutter}>{line.beforeNo ?? ''}</span>
            <span aria-hidden style={gutter}>{line.afterNo ?? ''}</span>
            <span
              aria-hidden
              style={{
                width: theme.spacing(3),
                flexShrink: 0,
                textAlign: 'center',
                color: KINDS[line.kind].color,
                userSelect: 'none',
              }}
            >
              {KINDS[line.kind].sign}
            </span>
            <span style={{
              flex: 1,
              minWidth: 0,
              paddingRight: theme.spacing(1.5),
              whiteSpace: 'pre-wrap',
              overflowWrap: 'anywhere',
              color: 'var(--text-default-primary)',
            }}
            >
              {lineText(line)}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
};

// What changes field by field, as a code diff under one key of the two sides compared.
const ChangeDiff = ({ rows, beforeLabel, afterLabel }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const key = (kind: 'removed' | 'added', label: string) => (
    <span style={{
      display: 'inline-flex',
      alignItems: 'center',
      gap: theme.spacing(0.75),
    }}
    >
      <span aria-hidden style={{ color: KINDS[kind].color }}>{KINDS[kind].sign}</span>
      <Text variant="content-compact" className="text-default-secondary">{label}</Text>
    </span>
  );
  return (
    <div
      role="group"
      aria-label={t('Changes')}
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(1.5),
      }}
    >
      <div style={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: theme.spacing(2),
      }}
      >
        {key('removed', beforeLabel)}
        {key('added', afterLabel)}
      </div>
      {rows.map(row => <FieldDiff key={row.id} row={row} />)}
    </div>
  );
};

export default ChangeDiff;
