// Word-level comparison of two texts, shared by TextDiff and the Before / After block.

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

/** The text as runs of unchanged and changed words, compared with another text. */
export const diffRuns = (text: string, other: string): {
  token: string;
  changed: boolean;
}[] => {
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
};
