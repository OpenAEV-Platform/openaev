import { toCsv } from '../../components/common/import/import-report-csv';

// CSV format of the marking definitions export/import (issue #8690): header row, comma separator.
export const MARKING_CSV_COLUMNS = ['type', 'definition', 'color', 'order'] as const;

export const toMarkingDefinitionsCsv = (rows: string[][]) => toCsv(MARKING_CSV_COLUMNS, rows);
