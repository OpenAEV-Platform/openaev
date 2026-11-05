import { useTheme } from '@mui/material/styles';
import { useMemo } from 'react';

import { tint } from '../../../utils/tint';

interface Props {
  text: string;
  // The text it is compared to: words of `text` missing there are highlighted.
  other: string;
  /**
   * Which side of a before / after this text is. "before": grey text, removed words struck through on a
   * grey tint; "after": changed words on a brand tint. No red or green, which read as error / success.
   * Without it, changed words get the neutral highlight of the variant comparisons.
   */
  side?: 'before' | 'after';
}

const markStyle = (side: Props['side']) => {
  if (side === 'after') {
    return {
      color: 'inherit',
      background: tint('var(--color-filigran-brand-primary)', 25),
      fontWeight: 600,
      borderRadius: 2,
    };
  }
  if (side === 'before') {
    return {
      color: 'inherit',
      background: tint('var(--text-default-primary)', 12),
      textDecoration: 'line-through',
      borderRadius: 2,
    };
  }
  return null;
};

const MAX_TOKENS = 400;

// Words, spaces and each punctuation sign apart: "a;" and "a |" share "a".
const tokens = (value: string) => value.match(/[\w-]+|\s+|[^\w\s]/g) ?? [];

// Which tokens of `left` are part of a longest common subsequence with `right`.
const kept = (left: string[], right: string[]) => {
  const lengths = Array.from({ length: left.length + 1 }, () => new Array<number>(right.length + 1).fill(0));
  for (let i = left.length - 1; i >= 0; i -= 1) {
    for (let j = right.length - 1; j >= 0; j -= 1) {
      lengths[i][j] = left[i] === right[j] ? lengths[i + 1][j + 1] + 1 : Math.max(lengths[i + 1][j], lengths[i][j + 1]);
    }
  }
  const common = new Array<boolean>(left.length).fill(false);
  let i = 0;
  let j = 0;
  while (i < left.length && j < right.length) {
    if (left[i] === right[j]) {
      common[i] = true;
      i += 1;
      j += 1;
    } else if (lengths[i + 1][j] >= lengths[i][j + 1]) {
      i += 1;
    } else {
      j += 1;
    }
  }
  return common;
};

// A text with the words that differ from another text highlighted.
const TextDiff = ({ text, other, side }: Props) => {
  const theme = useTheme();
  const parts = useMemo(() => {
    const left = tokens(text);
    const right = tokens(other);
    if (left.length > MAX_TOKENS || right.length > MAX_TOKENS) {
      return left.map(token => ({
        token,
        changed: false,
      }));
    }
    const common = kept(left, right);
    const flags = left.map((token, index) => !common[index] && token.trim() !== '');
    // A space between two changed words belongs to the change: one highlight per run, not per word.
    left.forEach((token, index) => {
      if (token.trim() === '' && flags[index - 1] && flags[index + 1]) flags[index] = true;
    });
    return left.reduce<{
      token: string;
      changed: boolean;
    }[]>((runs, token, index) => {
      const last = runs[runs.length - 1];
      if (last && last.changed === flags[index]) {
        last.token += token;
      } else {
        runs.push({
          token,
          changed: flags[index],
        });
      }
      return runs;
    }, []);
  }, [text, other]);
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
              style={markStyle(side) ?? {
                color: 'inherit',
                background: tint(theme.palette.warning.main, 30),
                borderRadius: 2,
              }}
            >
              {token}
            </mark>
          )
        : token))}
    </div>
  );
};

export default TextDiff;
