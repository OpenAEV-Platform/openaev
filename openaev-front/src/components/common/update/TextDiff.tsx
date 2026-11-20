import { Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { useMemo } from 'react';

import { diffRuns } from './textDiffRuns';

interface Props {
  text: string;
  // The text it is compared to: the words of `text` missing there are highlighted.
  other: string;
}

// A text with the words that differ from another text highlighted.
const TextDiff = ({ text, other }: Props) => {
  const theme = useTheme();
  const runs = useMemo(() => diffRuns(text, other), [text, other]);
  const markStyle = {
    color: 'var(--text-default-primary)',
    background: 'var(--color-feedback-neutral-secondary-transparency-30)',
    borderRadius: 'var(--radius-sm)',
    padding: theme.spacing(0, 0.25),
  };
  return (
    <Text
      variant="content-code"
      style={{
        whiteSpace: 'pre-wrap',
        overflowWrap: 'anywhere',
      }}
    >
      {runs.map(({ token, changed }, index) => (changed ? <mark key={index} style={markStyle}>{token}</mark> : token))}
    </Text>
  );
};

export default TextDiff;
