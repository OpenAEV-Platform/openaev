import { type FunctionComponent, useState } from 'react';

import { importMarkingDefinitions } from '../../../../actions/marking_definitions/marking-definition-actions';
import { ImportFileError, type ImportRowReason } from '../../../../components/common/import/import-report-types';
import ImportReportDialog from '../../../../components/common/import/ImportReportDialog';
import ImportUploader from '../../../../components/common/ImportUploader';
import { useFormatter } from '../../../../components/i18n';
import { type ImportReport } from '../../../../utils/api-types';
import markingDefinitionImportReasons, { type MarkingDefinitionImportReason } from './marking-definition-import-reasons';

interface Props { onImported: () => void }

const MarkingDefinitionImport: FunctionComponent<Props> = ({ onImported }) => {
  const { t } = useFormatter();
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [report, setReport] = useState<ImportReport | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);

  const reasons = markingDefinitionImportReasons(t);
  const reasonEntries = Object.entries(reasons) as [ImportRowReason, MarkingDefinitionImportReason][];
  const reasonLabels = Object.fromEntries(reasonEntries.map(([reason, { label }]) => [reason, label]));
  const reasonSeverities = Object.fromEntries(reasonEntries.flatMap(([reason, { severity }]) => severity ? [[reason, severity]] : []));

  const handleUpload = (_formData: FormData, file: File) => {
    setLoading(true);
    setReport(null);
    setFileError(null);
    return importMarkingDefinitions(file)
      .then((importReport) => {
        setReport(importReport);
        setOpen(true);
        if (importReport.import_created > 0) {
          onImported();
        }
      })
      .catch((error: unknown) => {
        // A file-level failure (empty file, missing column, too big...) imports nothing: show it in
        // the report dialog. Any other failure was already notified by the API layer.
        if (error instanceof ImportFileError) {
          setFileError(error.message);
          setOpen(true);
        }
      })
      .finally(() => setLoading(false));
  };

  return (
    <>
      <ImportUploader
        title={t('Import marking definitions')}
        handleUpload={handleUpload}
        fileAccepted=".csv"
        disabled={loading}
      />
      <ImportReportDialog
        open={open}
        onClose={() => setOpen(false)}
        title={t('Import marking definitions')}
        report={report}
        fileError={fileError}
        columnLabels={{
          type: t('Type'),
          definition: t('Definition'),
          color: t('Color'),
          order: t('Order'),
        }}
        reasonLabels={reasonLabels}
        reasonSeverities={reasonSeverities}
        rowMessage={row => (row.import_row_reason ? reasons[row.import_row_reason]?.message(row.import_row_params) ?? null : null)}
        downloadFileName="MarkingDefinitionsSkipped.csv"
      />
    </>
  );
};

export default MarkingDefinitionImport;
