import * as R from 'ramda';
import { type ReactNode, useMemo } from 'react';
import { useLocation } from 'react-router';
import { Subject, timer } from 'rxjs';
import { debounce } from 'rxjs/operators';

export interface Message {
  type: 'error' | 'message';
  text: ReactNode;
  sticky?: boolean;
}

// Service bus
const MESSENGER$ = new Subject<Message[]>().pipe(debounce(() => timer(500)));
export const MESSAGING$ = {
  messages: MESSENGER$,
  notifyError: (text: ReactNode, sticky = false) => (MESSENGER$ as Subject<Message[]>).next([{
    type: 'error',
    text,
    sticky,
  }]),
  notifySuccess: (text: ReactNode) => (MESSENGER$ as Subject<Message[]>).next([{
    type: 'message',
    text,
  }]),
  toggleNav: new Subject<void>(),
  redirect: new Subject<string>(),
};

// Default application exception.
export class ApplicationError extends Error {
  data: unknown;

  constructor(errors: unknown) {
    super();
    this.data = errors;
  }
}

export const useQueryParameter = (parameters: string[]): (string | null)[] => {
  const { search } = useLocation();
  const query = useMemo(() => new URLSearchParams(search), [search]);
  return parameters.map(p => query.get(p));
};

// Network
const isEmptyPath = R.isNil(window.BASE_PATH) || R.isEmpty(window.BASE_PATH);
const contextPath = isEmptyPath || window.BASE_PATH === '/' ? '' : window.BASE_PATH;
export const APP_BASE_PATH = isEmptyPath || contextPath?.startsWith('/') ? contextPath : `/${contextPath}`;

export const fileUri = (fileImport: string): string => `${APP_BASE_PATH}${fileImport}`; // No slash here, will be replaced by the builder

// Export
const escape = (value: unknown): string | undefined => value?.toString()
  .replaceAll('"', '""')
  .replaceAll('\n', '\\n');

type TagsMap = Record<string, { tag_name?: string } | undefined>;
type OrganizationsMap = Record<string, { organization_name?: string } | undefined>;
type ExercisesMap = Record<string, { exercise_name?: string } | undefined>;
type ScenariosMap = Record<string, { scenario_name?: string } | undefined>;

export const exportData = <T extends object>(
  type: string,
  keys: string[],
  data: T[],
  tagsMap: TagsMap = {},
  organizationsMap: OrganizationsMap = {},
  exercisesMap: ExercisesMap = {},
  scenariosMap: ScenariosMap = {},
): Record<string, string | undefined>[] => {
  return data
    .map(d => R.pick(keys, d as Record<string, unknown>) as Record<string, unknown>)
    .map((d) => {
      let entry = d;

      if (entry[`${type}_type`] === null) {
        entry[`${type}_type`] = 'deleted';
      }

      const tagsKey = `${type}_tags`;
      if (entry[tagsKey]) {
        entry = R.assoc(
          tagsKey,
          (entry[tagsKey] as string[]).map(t => tagsMap[t]?.tag_name).filter(x => !!x),
          entry,
        );
      }

      const exercisesKey = `${type}_exercises`;
      if (entry[exercisesKey]) {
        entry = R.assoc(
          exercisesKey,
          (entry[exercisesKey] as string[]).map(e => exercisesMap[e]?.exercise_name).filter(x => !!x),
          entry,
        );
      }

      const scenariosKey = `${type}_scenarios`;
      if (entry[scenariosKey]) {
        entry = R.assoc(
          scenariosKey,
          (entry[scenariosKey] as string[]).map(e => scenariosMap[e]?.scenario_name).filter(x => !!x),
          entry,
        );
      }

      const orgKey = `${type}_organization`;
      if (entry[orgKey]) {
        entry = R.assoc(
          orgKey,
          organizationsMap[entry[orgKey] as string]?.organization_name || '',
          entry,
        );
      }

      if (entry.inject_content) {
        entry = R.assoc(
          'inject_content',
          JSON.stringify(entry.inject_content),
          entry,
        );
      }
      return R.mapObjIndexed(escape, entry) as Record<string, string | undefined>;
    });
};
