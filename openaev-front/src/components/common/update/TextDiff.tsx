import { useMemo } from 'react';

import { diffRuns } from './textDiffRuns';

interface Props {
  text: string;
  // The text it is compared to: words of `text` missing there are highlighted.
  other: string;
  /**
   * Which side of a before / after this text is: the "before" text is grey, the "after" one normal.
   * On both sides the changed words get the same neutral highlight (DS tokens), never
   * strikethrough, so they stay readable; the Before / After labels say which side is which.
   */
  side?: 'before' | 'after';
}

// DS tokens as shipped, no custom strength: the neutral tertiary fill with the opposite text colour.
// The warning colour fails as a highlight in dark mode (1.15:1 against the box, 3:1 needed); this
// pair passes in both themes (fill 8.9:1 against the box, text 11:1 on the fill).
const MARK_STYLE = {
  color: 'var(--text-negative-primary)',
  background: 'var(--color-feedback-neutral-tertiary)',
  borderRadius: 2,
  padding: '0 2px',
};

// A text with the words that differ from another text highlighted.
const TextDiff = ({ text, other, side }: Props) => {
  const parts = useMemo(() => diffRuns(text, other), [text, other]);
  return (
    <div style={{
      fontFamily: 'monospace',
      fontSize: 12,
      whiteSpace: 'pre-wrap',
      overflowWrap: 'anywhere',
      color: side === 'before' ? 'var(--text-default-secondary)' : undefined,
    }}
    >
      {parts.map(({ token, changed }, index) => (changed
        ? (
            <mark
              key={index}
              style={MARK_STYLE}
            >
              {token}
            </mark>
          )
        : token))}
    </div>
  );
};

export default TextDiff;
