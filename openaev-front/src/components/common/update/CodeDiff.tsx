import { Text } from '@filigran/design-system';
import { UnfoldMoreOutlined } from '@mui/icons-material';
import { type CSSProperties, type FunctionComponent, useMemo, useState } from 'react';

import { tint } from '../../../utils/tint';
import { useFormatter } from '../../i18n';
import DiffLegend from './DiffLegend';
import { type DiffLine, diffRuns, lineDiff } from './textDiffRuns';

interface Props {
  /** Name of the field (e.g. "Command"). */
  label: string;
  before: string;
  after: string;
  /** e.g. "Before · v1.0.0" */
  beforeLabel: string;
  /** e.g. "After · v1.1.0" */
  afterLabel: string;
  /** Show the Before / After key in the header (default). Off when a key is shown once above several fields. */
  legend?: boolean;
  /** Wrap long lines (text such as a description); code keeps one line per row. */
  wrap?: boolean;
}

// Unchanged lines kept around each change; longer unchanged stretches fold.
const CONTEXT = 3;
// Past this height the diff scrolls inside its own frame, so a long command never pushes the rest away.
const MAX_HEIGHT = 320;

// DS tokens: the Alert fills for whole lines, the feedback colours for the signs and counts, and a
// stronger tint of the same feedback colour for the words that changed inside a line.
const KIND_STYLE: Record<DiffLine['kind'], CSSProperties> = {
  same: {},
  added: { background: 'var(--bg-alert-success)' },
  removed: { background: 'var(--bg-alert-error)' },
};
const SIGN_COLOR: Record<DiffLine['kind'], string> = {
  same: 'var(--text-default-secondary)',
  added: 'var(--icon-success)',
  removed: 'var(--icon-error)',
};
const WORD_STYLE: Record<'added' | 'removed', CSSProperties> = {
  added: {
    background: tint('var(--color-feedback-success-secondary)', 55),
    borderRadius: 2,
  },
  removed: {
    background: tint('var(--color-feedback-error-secondary)', 55),
    borderRadius: 2,
  },
};

type Row = {
  type: 'line';
  line: DiffLine;
} | {
  type: 'fold';
  start: number;
  lines: DiffLine[];
};

const rowsOf = (lines: DiffLine[], expanded: Set<number>): Row[] => {
  const near = lines.map((_, index) => lines
    .slice(Math.max(0, index - CONTEXT), index + CONTEXT + 1)
    .some(line => line.kind !== 'same'));
  const rows: Row[] = [];
  let index = 0;
  while (index < lines.length) {
    if (near[index] || lines[index].kind !== 'same') {
      rows.push({
        type: 'line',
        line: lines[index],
      });
      index += 1;
    } else {
      const start = index;
      while (index < lines.length && !near[index] && lines[index].kind === 'same') index += 1;
      const folded = lines.slice(start, index);
      // A single hidden line is shown rather than folded: unfolding it would cost the same space.
      if (expanded.has(start) || folded.length === 1) {
        folded.forEach(line => rows.push({
          type: 'line',
          line,
        }));
      } else {
        rows.push({
          type: 'fold',
          start,
          lines: folded,
        });
      }
    }
  }
  return rows;
};

/**
 * What changed in a piece of code (a command, its arguments, prerequisites…), as GitHub shows it:
 * one column, removed lines with "−" on the error fill, added lines with "+" on the success fill,
 * the words that changed inside a line highlighted, both line numbers in the gutter. Long unchanged
 * stretches fold ("⋯ 12 unchanged lines", click to show), and a long diff scrolls in its own frame.
 * The same block is used in the Versions tab, the History tab and rolled-back versions.
 */
const CodeDiff: FunctionComponent<Props> = ({ label, before, after, beforeLabel, afterLabel, legend = true, wrap = false }) => {
  const { t } = useFormatter();
  const lines = useMemo(() => lineDiff(before, after), [before, after]);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const rows = rowsOf(lines, expanded);
  const added = lines.filter(line => line.kind === 'added').length;
  const removed = lines.filter(line => line.kind === 'removed').length;
  const summary = String(t('{added} lines added, {removed} lines removed', {
    added: String(added),
    removed: String(removed),
  }));

  const gutter: CSSProperties = {
    width: 36,
    flexShrink: 0,
    padding: '0 6px',
    textAlign: 'right',
    color: 'var(--text-default-secondary)',
    userSelect: 'none',
  };

  const code = (line: DiffLine) => {
    if (line.kind === 'same' || line.counterpart === undefined) return line.text || ' ';
    return diffRuns(line.text, line.counterpart).map(({ token, changed }, index) => (changed
      ? (
          <mark
            key={index}
            style={{
              ...WORD_STYLE[line.kind as 'added' | 'removed'],
              color: 'inherit',
            }}
          >
            {token}
          </mark>
        )
      : token));
  };

  return (
    <div
      role="group"
      aria-label={`${label} · ${summary}`}
      style={{
        borderRadius: 4,
        border: '1px solid var(--border-elevation-subtle-soft)',
        overflow: 'hidden',
      }}
    >
      <div style={{
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: 12,
        padding: '8px 12px',
        borderBottom: '1px solid var(--border-elevation-subtle-soft)',
      }}
      >
        <Text variant="content-base-bold">{label}</Text>
        <Text variant="content-compact-bold" aria-hidden>
          <span style={{ color: 'var(--color-feedback-success-primary)' }}>{`+${added}`}</span>
          {' '}
          <span style={{ color: 'var(--color-feedback-error-primary)' }}>{`−${removed}`}</span>
        </Text>
        {/* The key, when this diff stands alone; a list of changed fields shows it once above them all. */}
        {legend && (
          <div style={{ marginLeft: 'auto' }}>
            <DiffLegend beforeLabel={beforeLabel} afterLabel={afterLabel} />
          </div>
        )}
      </div>
      <div
        tabIndex={0}
        aria-label={summary}
        style={{
          maxHeight: MAX_HEIGHT,
          overflow: 'auto',
          fontFamily: 'var(--font-content-code), ui-monospace, monospace',
          // DS code size (12px), with generous line height for reading long scripts.
          fontSize: 'var(--text-content-code)',
          lineHeight: '22px',
        }}
      >
        {/* Code scrolls sideways (one line per row); wrapped text stays the width of the frame. */}
        <div style={{
          minWidth: '100%',
          width: wrap ? '100%' : 'max-content',
        }}
        >
          {rows.map(row => (row.type === 'fold'
            ? (
                <button
                  key={`fold-${row.start}`}
                  type="button"
                  onClick={() => setExpanded(new Set([...expanded, row.start]))}
                  style={{
                    all: 'unset',
                    boxSizing: 'border-box',
                    cursor: 'pointer',
                    width: '100%',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 8,
                    padding: '2px 8px',
                    color: 'var(--text-default-secondary)',
                    background: tint('var(--text-default-secondary)', 8),
                  }}
                >
                  <UnfoldMoreOutlined style={{ fontSize: 16 }} />
                  {t(row.lines.length === 1 ? '1 unchanged line' : '{count} unchanged lines', { count: String(row.lines.length) })}
                </button>
              )
            : (
                <div
                  key={`${row.line.kind}-${row.line.beforeNo ?? ''}-${row.line.afterNo ?? ''}`}
                  style={{
                    display: 'flex',
                    ...KIND_STYLE[row.line.kind],
                  }}
                >
                  <span aria-hidden style={gutter}>{row.line.beforeNo ?? ''}</span>
                  <span aria-hidden style={gutter}>{row.line.afterNo ?? ''}</span>
                  <span
                    style={{
                      width: 24,
                      flexShrink: 0,
                      textAlign: 'center',
                      color: SIGN_COLOR[row.line.kind],
                      fontWeight: 600,
                      userSelect: 'none',
                    }}
                  >
                    {{
                      same: ' ',
                      added: '+',
                      removed: '−',
                    }[row.line.kind]}
                  </span>
                  <span style={{
                    whiteSpace: wrap ? 'pre-wrap' : 'pre',
                    overflowWrap: wrap ? 'anywhere' : undefined,
                    flex: wrap ? 1 : undefined,
                    minWidth: wrap ? 0 : undefined,
                    paddingRight: 12,
                    color: 'var(--text-default-primary)',
                  }}
                  >
                    {code(row.line)}
                  </span>
                </div>
              )))}
        </div>
      </div>
    </div>
  );
};

export default CodeDiff;
