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

// DS tokens as shipped, no custom strength: the neutral Chip fill, a neutral-primary underline (5.4:1
// against the dark surface, so the highlight is seen) and the normal light text (11:1 on the fill).
// A light solid fill with dark text passes too, but glares in the dark theme.
const MARK_STYLE = {
  color: 'var(--text-default-primary)',
  background: 'var(--color-feedback-neutral-secondary-transparency-30)',
  borderBottom: '2px solid var(--color-feedback-neutral-primary)',
  borderRadius: '2px 2px 0 0',
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
