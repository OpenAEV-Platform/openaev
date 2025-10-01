import { ContentPasteOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type FunctionComponent, useContext } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type Report } from '../../../../utils/api-types';
import { PermissionsContext } from '../../common/Context';
import ReportPopover from './ReportPopover';

interface Props {
  reports: Report[];
  navigateToReportPage: (id: string) => void;
}

const Reports: FunctionComponent<Props> = ({ reports, navigateToReportPage }) => {
  const { t } = useFormatter();
  const { permissions } = useContext(PermissionsContext);

  if (reports.length === 0) {
    return (
      <div style={{
        textAlign: 'center',
        padding: 20,
      }}
      >
        <i>{t('There is no report for this simulation yet')}</i>
      </div>
    );
  }

  return (
    <List style={{ padding: 0 }}>
      {reports.map((report) => {
        return (
          <ListItem
            key={report.report_id}
            divider={true}
            style={{
              height: 50,
              padding: 0,
            }}
            secondaryAction={permissions.canManage
              && (
                <ReportPopover
                  report={report}
                  actions={['Delete', 'Update']}
                />
              )}
          >
            <ListItemButton onClick={() => navigateToReportPage(report.report_id)}>
              <ListItemIcon>
                <ContentPasteOutlined color="primary" />
              </ListItemIcon>
              <ListItemText>
                {report.report_name}
              </ListItemText>

            </ListItemButton>
          </ListItem>
        );
      })}
    </List>
  );
};

export default Reports;
