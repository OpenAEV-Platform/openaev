// Word-level and line-level comparison of two texts.

const MAX_TOKENS = 400;
const MAX_LINES = 1500;

// Words, spaces and each punctuation sign apart: "a;" and "a |" share "a". A dotted or slashed value (IP, version, path) stays one word.
const tokens = (value: string) => value.match(/[\w-]+(?:[./][\w-]+)*|\s+|[^\w\s]/g) ?? [];

// The length of the longest common subsequence of every pair of suffixes.
const commonLengths = (left: string[], right: string[]) => {
  const lengths = Array.from({ length: left.length + 1 }, () => new Array<number>(right.length + 1).fill(0));
  for (let i = left.length - 1; i >= 0; i -= 1) {
    for (let j = right.length - 1; j >= 0; j -= 1) {
      lengths[i][j] = left[i] === right[j] ? lengths[i + 1][j + 1] + 1 : Math.max(lengths[i + 1][j], lengths[i][j + 1]);
    }
  }
  return lengths;
};

// Which tokens of `left` are part of a longest common subsequence with `right`.
const kept = (left: string[], right: string[]) => {
  const lengths = commonLengths(left, right);
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

export interface DiffLine {
  kind: 'same' | 'added' | 'removed';
  text: string;
  beforeNo?: number;
  afterNo?: number;
  // The line it replaces or is replaced by, to mark the words that changed.
  counterpart?: string;
}

const splitLines = (value: string) => (value === '' ? [] : value.replace(/\r\n/g, '\n').split('\n'));

// A run of removed lines followed by a run of added lines pairs them one to one.
const pairChangedLines = (lines: DiffLine[]) => {
  let index = 0;
  while (index < lines.length) {
    if (lines[index].kind === 'removed') {
      const removedStart = index;
      while (index < lines.length && lines[index].kind === 'removed') index += 1;
      const addedStart = index;
      while (index < lines.length && lines[index].kind === 'added') index += 1;
      const pairs = Math.min(addedStart - removedStart, index - addedStart);
      for (let k = 0; k < pairs; k += 1) {
        lines[removedStart + k].counterpart = lines[addedStart + k].text;
        lines[addedStart + k].counterpart = lines[removedStart + k].text;
      }
    } else {
      index += 1;
    }
  }
  return lines;
};

/** The after text compared line by line with the before text: unchanged, removed and added lines, numbered on each side. */
export const lineDiff = (before: string, after: string): DiffLine[] => {
  const left = splitLines(before);
  const right = splitLines(after);
  const lengths = left.length > MAX_LINES || right.length > MAX_LINES ? null : commonLengths(left, right);
  const lines: DiffLine[] = [];
  let i = 0;
  let j = 0;
  while (i < left.length || j < right.length) {
    if (lengths && i < left.length && j < right.length && left[i] === right[j]) {
      lines.push({
        kind: 'same',
        text: left[i],
        beforeNo: i + 1,
        afterNo: j + 1,
      });
      i += 1;
      j += 1;
    } else if (j >= right.length || (i < left.length && (!lengths || lengths[i + 1][j] >= lengths[i][j + 1]))) {
      lines.push({
        kind: 'removed',
        text: left[i],
        beforeNo: i + 1,
      });
      i += 1;
    } else {
      lines.push({
        kind: 'added',
        text: right[j],
        afterNo: j + 1,
      });
      j += 1;
    }
  }
  return pairChangedLines(lines);
};
