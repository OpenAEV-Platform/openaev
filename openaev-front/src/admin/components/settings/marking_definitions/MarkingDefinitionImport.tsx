import { type FunctionComponent, useState } from 'react';

import { importMarkingDefinitions } from '../../../../actions/marking_definitions/marking-definition-actions';
import { type MarkingDefinitionImportRowReason } from '../../../../actions/marking_definitions/marking-definition-import-types';
import { ImportFileError } from '../../../../components/common/import/import-report-types';
import ImportReportDialog from '../../../../components/common/import/ImportReportDialog';
import ImportUploader from '../../../../components/common/ImportUploader';
import { useFormatter } from '../../../../components/i18n';
import { type ImportReport } from '../../../../utils/api-types';

interface Props { onImported: () => void }

const MarkingDefinitionImport: FunctionComponent<Props> = ({ onImported }) => {
  const { t } = useFormatter();
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [report, setReport] = useState<ImportReport | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);

  const reasonLabels: Record<MarkingDefinitionImportRowReason, string> = {
    MISSING_FIELD: t('Missing value'),
    VALUE_TOO_LONG: t('Value too long'),
    INVALID_COLOR: t('Invalid colour'),
    INVALID_ORDER: t('Invalid order'),
    CONFLICT: t('Conflict'),
    INSUFFICIENT_ACCESS: t('Insufficient access'),
  };

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
        reasonSeverities={{
          CONFLICT: 'medium',
          INSUFFICIENT_ACCESS: 'medium',
        }}
        downloadFileName="MarkingDefinitionsSkipped.csv"
      />
    </>
  );
};

export default MarkingDefinitionImport;
