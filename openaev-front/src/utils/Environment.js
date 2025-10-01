import * as R from 'ramda';
import { useMemo } from 'react';
import { useLocation } from 'react-router';
import { Subject, timer } from 'rxjs';
import { debounce } from 'rxjs/operators';

// Service bus
const MESSENGER$ = new Subject().pipe(debounce(() => timer(500)));
export const MESSAGING$ = {
  messages: MESSENGER$,
  notifyError: (text, sticky = false) => MESSENGER$.next([{
    type: 'error',
    text,
    sticky,
  }]),
  notifySuccess: text => MESSENGER$.next([{
    type: 'message',
    text,
  }]),
  toggleNav: new Subject(),
  redirect: new Subject(),
};

// Default application exception.
export class ApplicationError extends Error {
  constructor(errors) {
    super();
    this.data = errors;
  }
}

export const useQueryParameter = (parameters) => {
  const { search } = useLocation();
  const query = useMemo(() => new URLSearchParams(search), [search]);
  return parameters.map(p => query.get(p));
};

// Network
const isEmptyPath = R.isNil(window.BASE_PATH) || R.isEmpty(window.BASE_PATH);
const contextPath = isEmptyPath || window.BASE_PATH === '/' ? '' : window.BASE_PATH;
export const APP_BASE_PATH = isEmptyPath || contextPath.startsWith('/') ? contextPath : `/${contextPath}`;

export const fileUri = fileImport => `${APP_BASE_PATH}${fileImport}`; // No slash here, will be replaced by the builder

// Export
const escape = value => value?.toString().replaceAll('"', '""');
export const exportData = (
  type,
  keys,
  data,
  tagsMap,
  organizationsMap,
  exercisesMap,
  scenariosMap,
) => {
  return data
    .map(d => R.pick(keys, d))
    .map((d) => {
      let entry = d;

      if (entry[`${type}_type`] === null) {
        entry[`${type}_type`] = 'deleted';
      }

      if (entry[`${type}_tags`]) {
        entry = R.assoc(
          `${type}_tags`,
          entry[`${type}_tags`].map(t => tagsMap[t]?.tag_name).filter(x => !!x),
          entry,
        );
      }
      if (entry[`${type}_exercises`]) {
        entry = R.assoc(
          `${type}_exercises`,
          entry[`${type}_exercises`].map(e => exercisesMap[e]?.exercise_name).filter(x => !!x),
          entry,
        );
      }
      if (entry[`${type}_scenarios`]) {
        entry = R.assoc(
          `${type}_scenarios`,
          entry[`${type}_scenarios`].map(e => scenariosMap[e]?.scenario_name).filter(x => !!x),
          entry,
        );
      }
      if (entry[`${type}_organization`]) {
        entry = R.assoc(
          `${type}_organization`,
          organizationsMap[entry[`${type}_organization`]]?.organization_name || '',
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
      return R.mapObjIndexed(escape, entry);
    });
};
