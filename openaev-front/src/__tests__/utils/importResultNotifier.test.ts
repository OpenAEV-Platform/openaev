import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type ImportResult } from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';
import { notifyPartialImport } from '../../utils/importResultNotifier';

// Identity translator: assert on the raw English strings the util builds.
const t = (message: string) => message;

describe('notifyPartialImport', () => {
  let notifyErrorSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    notifyErrorSpy = vi.spyOn(MESSAGING$, 'notifyError').mockImplementation(() => undefined);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('when there is nothing to report', () => {
    it('does nothing when result is undefined', () => {
      expect(notifyPartialImport(undefined, t)).toBe(false);
      expect(notifyErrorSpy).not.toHaveBeenCalled();
    });

    it('does nothing when result is null', () => {
      expect(notifyPartialImport(null, t)).toBe(false);
      expect(notifyErrorSpy).not.toHaveBeenCalled();
    });

    it('does nothing when missingActions is undefined', () => {
      expect(notifyPartialImport({}, t)).toBe(false);
      expect(notifyErrorSpy).not.toHaveBeenCalled();
    });

    it('does nothing when missingActions is an empty array', () => {
      expect(notifyPartialImport({ missingActions: [] }, t)).toBe(false);
      expect(notifyErrorSpy).not.toHaveBeenCalled();
    });
  });

  describe('when there are missing actions', () => {
    it('shows a sticky toast and returns true', () => {
      const result: ImportResult = {
        missingActions: [{
          type: 'Injector',
          name: 'My step',
        }],
      };

      expect(notifyPartialImport(result, t)).toBe(true);
      expect(notifyErrorSpy).toHaveBeenCalledTimes(1);
      // Second argument is the "sticky" flag.
      expect(notifyErrorSpy.mock.calls[0][1]).toBe(true);
    });

    it('renders the type verbatim and includes the name', () => {
      const result: ImportResult = {
        missingActions: [{
          type: 'InjectorContract/Payload',
          name: 'Missing payload step',
        }],
      };

      notifyPartialImport(result, t);

      const message = notifyErrorSpy.mock.calls[0][0] as string;
      expect(message).toContain('InjectorContract/Payload: Missing payload step');
    });

    it('lists every missing action without truncation', () => {
      const result: ImportResult = {
        missingActions: [
          {
            type: 'Injector',
            name: 'Step A',
          },
          {
            type: 'InjectorContract/Payload',
            name: 'Step B',
          },
          {
            type: 'Injector',
            name: 'Step C',
          },
        ],
      };

      notifyPartialImport(result, t);

      const message = notifyErrorSpy.mock.calls[0][0] as string;
      expect(message).toContain('Injector: Step A');
      expect(message).toContain('InjectorContract/Payload: Step B');
      expect(message).toContain('Injector: Step C');
    });

    it('falls back to generic labels when type or name is missing or blank', () => {
      const result: ImportResult = {
        missingActions: [
          { name: 'No type here' },
          { type: 'Injector' },
          {
            type: '   ',
            name: '   ',
          },
          {},
        ],
      };

      notifyPartialImport(result, t);

      const message = notifyErrorSpy.mock.calls[0][0] as string;
      expect(message).toContain('Unknown type: No type here');
      expect(message).toContain('Injector: Unnamed action');
      expect(message).toContain('Unknown type: Unnamed action');
    });
  });
});
