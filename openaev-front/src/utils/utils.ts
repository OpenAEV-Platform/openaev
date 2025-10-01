import * as R from 'ramda';

import { type LoggedHelper } from '../actions/helper';
import { useHelper } from '../store';
import { MESSAGING$ } from './Environment';

export const export_max_size = 50000;

export const isNotEmptyField = <T>(field: T | null | undefined): field is T => !R.isEmpty(field) && !R.isNil(field);
export const isEmptyField = <T>(
  field: T | null | undefined,
): field is null | undefined => !isNotEmptyField(field);

export const recordKeys = <K extends PropertyKey, T>(object: Record<K, T>) => {
  return Object.keys(object) as (K)[];
};

export function recordEntries<K extends PropertyKey, T>(object: Record<K, T>) {
  return Object.entries(object) as ([K, T])[];
}

export function arrayToRecord<T, K extends keyof T>(
  list: T[],
  key: K,
): Record<string, T> | null {
  if (!list || !list.length)
    return null;

  return list.reduce((acc, item) => {
    const recordKey = String(item[key]);
    acc[recordKey] = item;
    return acc;
  }, {} as Record<string, T>);
}

export const copyToClipboard = (t: (text: string) => string, text: string) => {
  if ('clipboard' in navigator) {
    navigator.clipboard.writeText(text);
  } else {
    document.execCommand('copy', true, text);
  }
  MESSAGING$.notifySuccess(t('Copied to clipboard'));
};

export const download = (content: string | Blob, filename: string, contentType: string | undefined) => {
  let finalContentType = contentType;
  if (!contentType) {
    finalContentType = 'application/octet-stream';
  }
  const blob = content instanceof Blob ? content : new Blob([content], { type: finalContentType });
  const a = document.createElement('a');
  const url = URL.createObjectURL(blob);
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);

  URL.revokeObjectURL(url);
};

export const removeEmptyFields = (
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  obj: Record<string, any | undefined>,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
): Record<string, any> => {
  const clone = { ...obj };
  Object.keys(clone).forEach((key) => {
    if (typeof clone[key] !== 'string' && isEmptyField(clone[key])) {
      delete clone[key];
    }
  });
  return clone;
};

export const deleteElementByValue = (obj: Record<string, string>, val: string) => {
  for (const key in obj) {
    if (obj[key] === val) {
      delete obj[key];
    }
  }
  return obj;
};

export const readFileContent = (file: File): Promise<unknown> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();

    reader.onload = (event) => {
      try {
        const jsonContent = JSON.parse(event.target?.result as string);
        resolve(jsonContent);
      } catch (error) {
        reject(error);
      }
    };

    reader.onerror = error => reject(error);
    reader.readAsText(file);
  });
};

export const randomElements = (elements: never[], number: number) => {
  const shuffled = elements.sort(() => 0.5 - Math.random());
  return shuffled.slice(0, number);
};

export const debounce = <T>(func: (...param: T[]) => void, timeout = 500) => {
  let timer: number;

  return (...args: T[]) => {
    window.clearTimeout(timer);
    timer = window.setTimeout(func, timeout, ...args);
  };
};

// the argument type here is an exported enum type from Java; it's supposed to be a union of enum strings
// see api-types.d.ts
// currently we copy/paste the generated enum types here since they don't exist as a standalone type in TS
export const isFeatureEnabled = (feature: '_RESERVED' | 'STIX_SECURITY_COVERAGE_FOR_VULNERABILITIES') => {
  const { settings } = useHelper((helper: LoggedHelper) => {
    return { settings: helper.getPlatformSettings() };
  });

  return (settings.enabled_dev_features ?? []).includes(feature);
};

export const getUrl = (url: string, base: string): string => {
  const urlToReturn = new URL(url, base);
  return urlToReturn.toString();
};
