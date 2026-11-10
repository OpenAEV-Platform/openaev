import { type PayloadArgument, type PayloadPrerequisite, type ThreatArsenalActionFullOutput } from '../../../../utils/api-types';

/**
 * What a payload version runs, compared field by field between two versions. Versions are immutable
 * (each one its own action), so this is read from each version's action output.
 */
export interface PayloadExecutableContent {
  arguments?: PayloadArgument[];
  cleanup_command?: string;
  cleanup_executor?: string;
  category?: string;
  content?: string;
  converters?: string[];
  elevation_required?: boolean;
  engine?: string;
  execution_arch?: string;
  executor?: string;
  file_id?: string;
  file_name?: string;
  hostname?: string;
  ip_dst?: string;
  ip_src?: string;
  multi_turn?: Record<string, unknown>;
  platforms?: string[];
  port_dst?: number;
  port_src?: number;
  prerequisites?: PayloadPrerequisite[];
  protocol?: string;
  success_detector?: Record<string, unknown>;
}

/** The content of one version, from its action. */
export const toExecutableContent = (action: ThreatArsenalActionFullOutput): PayloadExecutableContent => ({
  arguments: action.action_arguments,
  cleanup_command: action.action_cleanup_command,
  cleanup_executor: action.action_cleanup_executor,
  content: action.command_content,
  execution_arch: action.action_execution_arch,
  executor: action.command_executor,
  file_name: action.executable_file ?? action.file_drop_file,
  hostname: action.dns_resolution_hostname,
  platforms: action.action_platforms as string[] | undefined,
  prerequisites: action.action_prerequisites,
});

export type DiffLineType = 'same' | 'added' | 'removed';

export interface DiffLine {
  type: DiffLineType;
  text: string;
}

/**
 * Line diff of two texts (longest common subsequence): removed lines of the active text, added
 * lines of the pending one, and the lines they share, in order.
 */
export const lineDiff = (before: string, after: string): DiffLine[] => {
  const a = before === '' ? [] : before.split('\n');
  const b = after === '' ? [] : after.split('\n');
  const lcs: number[][] = Array.from({ length: a.length + 1 }, () => new Array<number>(b.length + 1).fill(0));
  for (let i = a.length - 1; i >= 0; i -= 1) {
    for (let j = b.length - 1; j >= 0; j -= 1) {
      lcs[i][j] = a[i] === b[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
    }
  }
  const lines: DiffLine[] = [];
  let i = 0;
  let j = 0;
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) {
      lines.push({
        type: 'same',
        text: a[i],
      });
      i += 1;
      j += 1;
    } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
      lines.push({
        type: 'removed',
        text: a[i],
      });
      i += 1;
    } else {
      lines.push({
        type: 'added',
        text: b[j],
      });
      j += 1;
    }
  }
  a.slice(i).forEach(text => lines.push({
    type: 'removed',
    text,
  }));
  b.slice(j).forEach(text => lines.push({
    type: 'added',
    text,
  }));
  return lines;
};

export interface VersionField {
  key: string;
  label: string;
  active: string;
  pending: string;
  changed: boolean;
  /** Compared line by line (commands, arguments, prerequisites). */
  multiline: boolean;
}

const text = (value: unknown): string => {
  if (value === undefined || value === null || value === '') return '';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
};

const sortedList = (values?: string[]) => [...(values ?? [])].sort().join(', ');

const argumentLines = (args?: PayloadArgument[]) => (args ?? [])
  .map(arg => `${arg.key} (${arg.type}) = ${arg.default_value ?? ''}${arg.separator ? ` [${arg.separator}]` : ''}`)
  .join('\n');

const prerequisiteLines = (prerequisites?: PayloadPrerequisite[]) => (prerequisites ?? [])
  .map(prerequisite => [
    `${prerequisite.executor}: ${prerequisite.get_command}`,
    prerequisite.check_command ? `  check: ${prerequisite.check_command}` : '',
  ].filter(Boolean).join('\n'))
  .join('\n');

const FIELDS: {
  key: string;
  label: string;
  read: (content: PayloadExecutableContent) => string;
  multiline?: boolean;
}[] = [
  {
    key: 'content',
    label: 'Command',
    read: c => text(c.content),
    multiline: true,
  },
  {
    key: 'executor',
    label: 'Executor',
    read: c => text(c.executor),
  },
  {
    key: 'platforms',
    label: 'Platforms',
    read: c => sortedList(c.platforms),
  },
  {
    key: 'execution_arch',
    label: 'Architecture',
    read: c => text(c.execution_arch),
  },
  {
    key: 'elevation_required',
    label: 'Elevation required',
    read: c => text(c.elevation_required ?? false),
  },
  {
    key: 'arguments',
    label: 'Arguments',
    read: c => argumentLines(c.arguments),
    multiline: true,
  },
  {
    key: 'prerequisites',
    label: 'Prerequisites',
    read: c => prerequisiteLines(c.prerequisites),
    multiline: true,
  },
  {
    key: 'cleanup_executor',
    label: 'Cleanup executor',
    read: c => text(c.cleanup_executor),
  },
  {
    key: 'cleanup_command',
    label: 'Cleanup command',
    read: c => text(c.cleanup_command),
    multiline: true,
  },
  {
    key: 'file',
    label: 'File',
    read: c => text(c.file_name ?? c.file_id),
  },
  {
    key: 'hostname',
    label: 'Hostname',
    read: c => text(c.hostname),
  },
  {
    key: 'network',
    label: 'Network traffic',
    read: c => (c.ip_dst || c.ip_src || c.protocol
      ? `${text(c.protocol)} ${text(c.ip_src)}:${text(c.port_src)} -> ${text(c.ip_dst)}:${text(c.port_dst)}`
      : ''),
  },
  {
    key: 'engine',
    label: 'Engine',
    read: c => text(c.engine),
  },
  {
    key: 'category',
    label: 'Category',
    read: c => text(c.category),
  },
  {
    key: 'converters',
    label: 'Converters',
    read: c => (c.converters ?? []).join(', '),
  },
  {
    key: 'multi_turn',
    label: 'Multi-turn',
    read: c => text(c.multi_turn),
  },
  {
    key: 'success_detector',
    label: 'Success detector',
    read: c => text(c.success_detector),
  },
];

/**
 * The executable fields of the active and pending contents, side by side. Fields empty in both are
 * left out (they do not apply to this payload type).
 */
export const versionFields = (active: PayloadExecutableContent, pending: PayloadExecutableContent): VersionField[] => FIELDS
  .map((field) => {
    const activeValue = field.read(active);
    const pendingValue = field.read(pending);
    return {
      key: field.key,
      label: field.label,
      active: activeValue,
      pending: pendingValue,
      changed: activeValue !== pendingValue,
      multiline: field.multiline ?? false,
    };
  })
  .filter(field => field.active !== '' || field.pending !== '');
