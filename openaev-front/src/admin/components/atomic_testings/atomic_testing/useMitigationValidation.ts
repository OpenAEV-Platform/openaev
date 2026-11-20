import { useState } from 'react';

// PROTOTYPE - MOCK ONLY. Nothing is re-run: after the user says the mitigation is applied, the
// product will re-execute the same action on the targets of the group and report which ones are now
// prevented. Here the outcome is canned so the flow can be shown.
//
// Mock outcome: on the first run one target out of five (the 5th, 10th...) is still not prevented, so
// groups of 5+ targets show the partial state. A re-run only re-executes the failed targets and they
// all pass.

export type ValidationState
  = | {
    status: 'validating';
    // Targets being re-run (all of them the first time, only the failed ones afterwards).
    count: number;
  }
  | {
    status: 'done';
    total: number;
    failed: string[];
    attempts: number;
  };

const VALIDATION_DELAY_MS = 2000;

const mockFailures = (names: string[], attempt: number) => (attempt === 1
  ? names.filter((_, index) => index % 5 === 4)
  : []);

export const useMitigationValidation = () => {
  const [store, setStore] = useState<Record<string, ValidationState>>({});

  // `key` identifies the group; `allNames` are all the targets of the group.
  const validate = (key: string, allNames: string[]) => {
    const prev = store[key];
    if (prev?.status === 'validating') return;
    const attempt = prev?.status === 'done' ? prev.attempts + 1 : 1;
    const names = prev?.status === 'done' && prev.failed.length > 0 ? prev.failed : allNames;
    setStore(current => ({
      ...current,
      [key]: {
        status: 'validating',
        count: names.length,
      },
    }));
    setTimeout(() => {
      setStore(current => ({
        ...current,
        [key]: {
          status: 'done',
          total: allNames.length,
          failed: mockFailures(names, attempt),
          attempts: attempt,
        },
      }));
    }, VALIDATION_DELAY_MS);
  };

  // A regenerated mitigation is a different mitigation: the previous validation no longer applies.
  const reset = (keys: string[]) => {
    setStore((current) => {
      const next = { ...current };
      keys.forEach(key => delete next[key]);
      return next;
    });
  };

  return {
    validation: store,
    validate,
    reset,
  };
};
