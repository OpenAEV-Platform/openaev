import { type ImportRowReason, type ReasonSeverity } from '../../../../components/common/import/import-report-types';
import { type Translate } from '../../../../components/i18n';

export interface MarkingDefinitionImportReason {
  // Translated label of the reason chip.
  label: string;
  // Tone of the chip. Defaults to critical (a malformed value); medium is for a conflict with data
  // that already exists.
  severity?: ReasonSeverity;
  // The translated sentence that explains a skipped row, written from the values the backend sends
  // in `import_row_params` (the PARAM_* constants of MarkingImportBatch). Null when there is nothing
  // to say.
  message: (params: Record<string, string>) => string | null;
}

// Everything the screen says about a skipped row, by reason. The record is exhaustive: when the
// backend adds a reason, the compiler asks for its label, tone and sentence here.
const markingDefinitionImportReasons = (t: Translate): Record<ImportRowReason, MarkingDefinitionImportReason> => ({
  MISSING_FIELD: {
    label: t('Missing value'),
    message: () => t('A value is missing: type, definition, color and order are all required.'),
  },
  VALUE_TOO_LONG: {
    label: t('Value too long'),
    message: params => t('The type and the definition must have {max} characters at most.', { max: params.max_length }),
  },
  INVALID_VALUE: {
    label: t('Invalid value'),
    // `column` names the column the value is in.
    message: (params) => {
      switch (params.column) {
        case 'color':
          return t('The color must be a hex value such as #4CAF50.');
        case 'order':
          return t('The order must be a whole number, 0 or more.');
        default:
          return null;
      }
    },
  },
  CONFLICT: {
    label: t('Conflict'),
    severity: 'medium',
    // `earlier_row` is there when the row clashes with a row of the file, absent when it clashes
    // with a marking the tenant already holds.
    message: (params) => {
      const values = {
        row: params.earlier_row,
        color: params.color,
        order: params.order,
      };
      const sentences: string[] = [];
      if (params.known_color !== undefined) {
        const withValues = {
          ...values,
          known: params.known_color,
        };
        sentences.push(params.earlier_row === undefined
          ? t('The color differs: this row has {color}, the existing marking has {known}.', withValues)
          : t('The color differs: this row has {color}, row {row} of the file has {known}.', withValues));
      }
      if (params.known_order !== undefined) {
        const withValues = {
          ...values,
          known: params.known_order,
        };
        sentences.push(params.earlier_row === undefined
          ? t('The order differs: this row has {order}, the existing marking has {known}.', withValues)
          : t('The order differs: this row has {order}, row {row} of the file has {known}.', withValues));
      }
      return sentences.join(' ');
    },
  },
  INSUFFICIENT_ACCESS: {
    label: t('Insufficient access'),
    severity: 'medium',
    message: params => params.highest_order === undefined
      ? t('You hold no marking of its type.')
      : t('Its order ({order}) is above the highest order you hold for its type ({highest}).', {
          order: params.order,
          highest: params.highest_order,
        }),
  },
});

export default markingDefinitionImportReasons;
