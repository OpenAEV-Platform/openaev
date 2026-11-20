import { type MarkingDefinitionImportRowReason } from '../../../../actions/marking_definitions/marking-definition-import-types';
import { type ImportReportRow } from '../../../../utils/api-types';

type Translate = (message: string, values?: Record<string, string>) => string;

// Writes the sentence of a skipped row from its reason code and the values the backend sends in
// `import_row_params`, so it is translated like the rest of the screen. The names of the values
// are the PARAM_* constants of MarkingImportBatch.
const markingDefinitionImportMessage = (t: Translate, row: ImportReportRow): string | null => {
  const params = row.import_row_params;
  switch (row.import_row_reason as MarkingDefinitionImportRowReason | undefined) {
    case 'MISSING_FIELD':
      return t('A value is missing: type, definition, color and order are all required.');
    case 'VALUE_TOO_LONG':
      return t('The type and the definition must have {max} characters at most.', { max: params.max_length });
    case 'INVALID_COLOR':
      return t('The color must be a hex value such as #4CAF50.');
    case 'INVALID_ORDER':
      return t('The order must be a whole number, 0 or more.');
    case 'CONFLICT': {
      // `earlier_row` is there when the row clashes with a row of the file, absent when it
      // clashes with a marking the tenant already holds.
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
    }
    case 'INSUFFICIENT_ACCESS':
      return params.highest_order === undefined
        ? t('You hold no marking of its type.')
        : t('Its order ({order}) is above the highest order you hold for its type ({highest}).', {
            order: params.order,
            highest: params.highest_order,
          });
    default:
      return null;
  }
};

export default markingDefinitionImportMessage;
