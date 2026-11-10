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

// ---- Line diff (GitHub-style unified view of code: commands, arguments, prerequisites).

export type DiffLineKind = 'same' | 'added' | 'removed';

export interface DiffLine {
  kind: DiffLineKind;
  text: string;
  /** Line number in the before text (removed and unchanged lines). */
  beforeNo?: number;
  /** Line number in the after text (added and unchanged lines). */
  afterNo?: number;
  /** The line it replaced or was replaced by, for the word highlight inside a changed line. */
  counterpart?: string;
}

// Beyond this many lines on a side, the comparison is lines removed then lines added (no alignment).
const MAX_LINES = 1500;

const splitLines = (value: string) => (value === '' ? [] : value.replace(/\r\n/g, '\n').split('\n'));

/**
 * The after text compared line by line with the before text (longest common subsequence): removed,
 * added and unchanged lines in order, numbered on each side. A run of removed lines followed by a run
 * of added lines pairs them one to one, so a changed line can show which words changed.
 */
export const lineDiff = (before: string, after: string): DiffLine[] => {
  const a = splitLines(before);
  const b = splitLines(after);
  const raw: {
    kind: DiffLineKind;
    text: string;
  }[] = [];
  if (a.length > MAX_LINES || b.length > MAX_LINES) {
    a.forEach(text => raw.push({
      kind: 'removed',
      text,
    }));
    b.forEach(text => raw.push({
      kind: 'added',
      text,
    }));
  } else {
    const lengths = Array.from({ length: a.length + 1 }, () => new Array<number>(b.length + 1).fill(0));
    for (let i = a.length - 1; i >= 0; i -= 1) {
      for (let j = b.length - 1; j >= 0; j -= 1) {
        lengths[i][j] = a[i] === b[j] ? lengths[i + 1][j + 1] + 1 : Math.max(lengths[i + 1][j], lengths[i][j + 1]);
      }
    }
    let i = 0;
    let j = 0;
    while (i < a.length || j < b.length) {
      if (i < a.length && j < b.length && a[i] === b[j]) {
        raw.push({
          kind: 'same',
          text: a[i],
        });
        i += 1;
        j += 1;
      } else if (j >= b.length || (i < a.length && lengths[i + 1][j] >= lengths[i][j + 1])) {
        raw.push({
          kind: 'removed',
          text: a[i],
        });
        i += 1;
      } else {
        raw.push({
          kind: 'added',
          text: b[j],
        });
        j += 1;
      }
    }
  }

  let beforeNo = 0;
  let afterNo = 0;
  const lines: DiffLine[] = raw.map((line) => {
    if (line.kind !== 'added') beforeNo += 1;
    if (line.kind !== 'removed') afterNo += 1;
    return {
      ...line,
      beforeNo: line.kind === 'added' ? undefined : beforeNo,
      afterNo: line.kind === 'removed' ? undefined : afterNo,
    };
  });

  // Pair each run of removed lines with the run of added lines right after it.
  let index = 0;
  while (index < lines.length) {
    if (lines[index].kind !== 'removed') {
      index += 1;
    } else {
      const removedStart = index;
      while (index < lines.length && lines[index].kind === 'removed') index += 1;
      const addedStart = index;
      while (index < lines.length && lines[index].kind === 'added') index += 1;
      const pairs = Math.min(addedStart - removedStart, index - addedStart);
      for (let k = 0; k < pairs; k += 1) {
        lines[removedStart + k].counterpart = lines[addedStart + k].text;
        lines[addedStart + k].counterpart = lines[removedStart + k].text;
      }
    }
  }
  return lines;
};
