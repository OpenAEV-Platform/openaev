import { useMemo } from 'react';

import { diffRuns } from './textDiffRuns';

interface Props {
  text: string;
  // The text it is compared to: the words of `text` missing there are highlighted.
  other: string;
}

const MARK_STYLE = {
  color: 'var(--text-default-primary)',
  background: 'var(--color-feedback-neutral-secondary-transparency-30)',
  borderRadius: 2,
  padding: '0 2px',
};

// A text with the words that differ from another text highlighted.
const TextDiff = ({ text, other }: Props) => {
  const runs = useMemo(() => diffRuns(text, other), [text, other]);
  return (
    <span style={{
      fontFamily: 'var(--font-content-code), ui-monospace, monospace',
      fontSize: 12,
      whiteSpace: 'pre-wrap',
      overflowWrap: 'anywhere',
    }}
    >
      {runs.map(({ token, changed }, index) => (changed ? <mark key={index} style={MARK_STYLE}>{token}</mark> : token))}
    </span>
  );
};

export default TextDiff;
