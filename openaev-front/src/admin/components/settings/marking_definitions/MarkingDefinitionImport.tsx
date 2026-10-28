import { Button } from '@filigran/design-system';
import { type FunctionComponent, useState } from 'react';

import { importMarkingDefinitions, USE_MOCK_IMPORT_EXPORT } from '../../../../actions/marking_definitions/marking-definition-actions';
import { buildMockConflictSampleCsv } from '../../../../actions/marking_definitions/marking-definition-import-mock';
import {
  MarkingDefinitionImportFileError,
  type MarkingDefinitionImportResult,
} from '../../../../actions/marking_definitions/marking-definition-import-types';
import ImportUploader from '../../../../components/common/ImportUploader';
import { useFormatter } from '../../../../components/i18n';
import { download } from '../../../../utils/utils';
import MarkingDefinitionImportReport from './MarkingDefinitionImportReport';

interface Props { onImported: () => void }

const MarkingDefinitionImport: FunctionComponent<Props> = ({ onImported }) => {
  const { t } = useFormatter();
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [result, setResult] = useState<MarkingDefinitionImportResult | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);

  const handleUpload = (_formData: FormData, file: File) => {
    setLoading(true);
    setResult(null);
    setFileError(null);
    return importMarkingDefinitions(file)
      .then((importResult) => {
        setResult(importResult);
        setOpen(true);
        if (importResult.import_created > 0) {
          onImported();
        }
      })
      .catch((error: unknown) => {
        // A file-level failure (bad header, too big...) imports nothing: show it in the report
        // dialog. Any other failure was already notified by the API layer.
        if (error instanceof MarkingDefinitionImportFileError) {
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
          onClick={() => download(buildMockConflictSampleCsv(), 'MarkingDefinitionsSampleWithConflicts.csv', 'text/csv')}
        >
          {t('Mock: sample file with conflicts')}
        </Button>
      )}
      <ImportUploader
        title={t('Import marking definitions')}
        handleUpload={handleUpload}
        fileAccepted=".csv"
        disabled={loading}
      />
      <MarkingDefinitionImportReport
        open={open}
        onClose={() => setOpen(false)}
        result={result}
        fileError={fileError}
      />
    </>
  );
};

export default MarkingDefinitionImport;
