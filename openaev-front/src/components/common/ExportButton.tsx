import { FileDownloadOutlined } from '@mui/icons-material';
import { ToggleButton, Tooltip } from '@mui/material';
import { CSVLink } from 'react-csv';

import { type TagHelper } from '../../actions/helper';
import { exportCsvMapper } from '../../actions/mapper/mapper-actions';
import { useHelper } from '../../store';
import { type SearchPaginationInput } from '../../utils/api-types';
import { exportData } from '../../utils/Environment';
import { download } from '../../utils/utils';
import { useFormatter } from '../i18n';

export interface ExportProps<T> {
  exportType: string;
  exportKeys: string[];
  exportData: T[];
  exportFileName: string;
  searchPaginationInput?: SearchPaginationInput;
}

interface Props<T> {
  totalElements: number;
  exportProps: ExportProps<T>;
}

const ExportButton = <T extends object>({ totalElements, exportProps }: Props<T>) => {
  // Standard hooks
  const { t } = useFormatter();
  // Fetching data
  const { tagsMap } = useHelper((helper: TagHelper) => ({ tagsMap: helper.getTagsMap() }));

  const exportCsvMapperAction = () => {
    exportCsvMapper(exportProps.exportType, exportProps.searchPaginationInput).then(
      (result: {
        data: string;
        filename: string;
      }) => {
        download(result.data, result.filename, 'text/csv');
      },
    );
  };

  if (totalElements > 0) {
    // TODO update all Front exports by Back API exports
    if ('ENDPOINTS' == exportProps.exportType) {
      return (
        <ToggleButton value="export" aria-label="export" size="small" onClick={exportCsvMapperAction}>
          <Tooltip title={t('Export this list')}>
            <FileDownloadOutlined
              color="primary"
              fontSize="small"
            />
          </Tooltip>
        </ToggleButton>
      );
    } else {
      return (
        <CSVLink
          data={exportData(
            exportProps.exportType,
            exportProps.exportKeys,
            exportProps.exportData,
            tagsMap,
          )}
          filename={exportProps.exportFileName}
        >
          <ToggleButton value="export" aria-label="export" size="small">
            <Tooltip title={t('Export this list')}>
              <FileDownloadOutlined
                color="primary"
                fontSize="small"
              />
            </Tooltip>
          </ToggleButton>
        </CSVLink>
      );
    }
  } else {
    return (
      <ToggleButton value="export" aria-label="export" size="small" disabled={true}>
        <Tooltip title={t('Export this list')}>
          <FileDownloadOutlined fontSize="small" />
        </Tooltip>
      </ToggleButton>
    );
  }
};

export default ExportButton;
