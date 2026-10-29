import { Button } from '@filigran/design-system';
import { type FunctionComponent, useState } from 'react';

import { importMarkingDefinitions, USE_MOCK_IMPORT_EXPORT } from '../../../../actions/marking_definitions/marking-definition-actions';
import { buildMockConflictSampleJson } from '../../../../actions/marking_definitions/marking-definition-import-mock';
import { type MarkingDefinitionImportRowReason } from '../../../../actions/marking_definitions/marking-definition-import-types';
import { ImportFileError, type ImportReport } from '../../../../components/common/import/import-report-types';
import ImportReportDialog from '../../../../components/common/import/ImportReportDialog';
import ImportUploader from '../../../../components/common/ImportUploader';
import { useFormatter } from '../../../../components/i18n';
import { download } from '../../../../utils/utils';

interface Props { onImported: () => void }

const MarkingDefinitionImport: FunctionComponent<Props> = ({ onImported }) => {
  const { t } = useFormatter();
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [report, setReport] = useState<ImportReport | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);

  const reasonLabels: Record<MarkingDefinitionImportRowReason, string> = {
    MISSING_FIELD: t('Missing value'),
    INVALID_COLOR: t('Invalid colour'),
    INVALID_ORDER: t('Invalid order'),
    CONFLICT: t('Conflict'),
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
        // A file-level failure (invalid JSON, too big...) imports nothing: show it in the report
        // dialog. Any other failure was already notified by the API layer.
        if (error instanceof ImportFileError) {
          setFileError(error.message);
          setOpen(true);
        }
      })
      .finally(() => setLoading(false));
  };

  return (
    <>
      {USE_MOCK_IMPORT_EXPORT && (
        <Button
          type="button"
          priority="tertiary"
          onClick={() => download(buildMockConflictSampleJson(), 'MarkingDefinitionsSampleWithConflicts.json', 'application/json')}
        >
          {t('Mock: sample file with conflicts')}
        </Button>
      )}
      <ImportUploader
        title={t('Import marking definitions')}
        handleUpload={handleUpload}
        fileAccepted=".json"
        disabled={loading}
      />
      <ImportReportDialog
        open={open}
        onClose={() => setOpen(false)}
        title={t('Import marking definitions')}
        report={report}
        fileError={fileError}
        notice={USE_MOCK_IMPORT_EXPORT ? t('Mock backend: the file was checked with the import rules, but nothing was saved.') : undefined}
        columnLabels={{
          type: t('Type'),
          definition: t('Definition'),
          color: t('Color'),
          order: t('Order'),
        }}
        reasonLabels={reasonLabels}
        reasonSeverities={{ CONFLICT: 'medium' }}
        downloadFileName="MarkingDefinitionsSkipped.json"
      />
    </>
  );
};

export default MarkingDefinitionImport;
