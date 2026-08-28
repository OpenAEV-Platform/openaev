import { type ImportResult } from './api-types';
import { MESSAGING$ } from './Environment';

type Translate = (message: string) => string;

/**
 * Displays a sticky "partial import" toast listing every action the backend could not create during
 * an exercise/scenario import (see {@link ImportResult#missingActions}). Product decision: show the
 * full list (type + name of each action), no truncation.
 *
 * The toast is triggered ONLY when {@code missingActions} is defined AND non-empty, so a fully
 * successful import keeps its normal flow untouched.
 *
 * {@code type} and {@code name} are optional on the backend DTO: a generic fallback label is used
 * when either is missing so the rendering never breaks. {@code type} values are shown verbatim (no
 * mapping to translated labels for now).
 *
 * @returns {@code true} when a partial-import toast was displayed, {@code false} otherwise (empty or
 *     missing {@code missingActions}). Callers can use this to skip a full page reload that would
 *     otherwise discard the toast.
 */
// eslint-disable-next-line import/prefer-default-export
export const notifyPartialImport = (
  result: ImportResult | null | undefined,
  t: Translate,
): boolean => {
  const missingActions = result?.missingActions;
  if (!missingActions || missingActions.length === 0) {
    return false;
  }

  const details = missingActions
    .map((action) => {
      const type = action.type && action.type.trim().length > 0 ? action.type : t('Unknown type');
      const name = action.name && action.name.trim().length > 0 ? action.name : t('Unnamed action');
      return `${type}: ${name}`;
    })
    .join(', ');

  const header = t('The import is partial, the following actions could not be created:');
  MESSAGING$.notifyError(`${header} ${details}`, true);
  return true;
};
