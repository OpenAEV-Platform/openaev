import { Dialog, ToggleButton, Tooltip } from '@mui/material';
import { FilePdfBox } from 'mdi-material-ui';
import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';
import type { TDocumentDefinitions } from 'pdfmake/interfaces';
import { type FunctionComponent, useState } from 'react';
import { useDispatch } from 'react-redux';

import { type UserHelper } from '../actions/helper';
import { useHelper } from '../store';
import { sendErrorToBackend } from '../utils/Action';
import { MESSAGING$ } from '../utils/Environment';
import { useFormatter } from './i18n';
import Loader from './Loader';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
(pdfMake as any).addVirtualFileSystem(pdfFonts);

interface Props {
  getPdfDocDefinition: () => Promise<TDocumentDefinitions>;
  pdfName: string;
}

const ExportPdfButton: FunctionComponent<Props> = ({ getPdfDocDefinition, pdfName }) => {
  const { t } = useFormatter();
  const [exporting, setExporting] = useState<boolean>(false);
  const dispatch = useDispatch();

  const { user } = useHelper((helper: UserHelper) => ({ user: helper.getMe() }));

  const changeUserTheme = (theme: string) => dispatch({
    type: 'DATA_UPDATE_SUCCESS',
    payload: {
      entities: {
        users: {
          [user.user_id]: {
            ...user,
            user_theme: theme,
            user_exporting: false,
          },
        },
      },
    },
  });
  const timeout = (ms: number) => new Promise(res => setTimeout(res, ms));

  const onExportPdf = async () => {
    setExporting(true);
    if (user.user_theme !== 'light') {
      changeUserTheme('light');
      await timeout(500);
    }
    getPdfDocDefinition()
      .then((pdfDocDefinition: TDocumentDefinitions) => {
        pdfMake.createPdf(pdfDocDefinition).download(`${pdfName}.pdf`);
      })
      .catch((e) => {
        sendErrorToBackend(e, { componentStack: 'ExportPdfButton' });
        MESSAGING$.notifyError(t('An error occurred during PDF generation.'));
      })
      .finally(() => {
        if (user.user_theme !== 'light') {
          changeUserTheme(user.user_theme);
        }
        setExporting(false);
      });
  };

  return (
    <div>
      <Tooltip title={t('Export to PDF')}>
        <ToggleButton value="exportPdf" onClick={onExportPdf}>
          <FilePdfBox fontSize="small" color="primary" />
        </ToggleButton>
      </Tooltip>
      <Dialog
        PaperProps={{
          elevation: 1,
          sx: { backgroundColor: 'rgba(0, 0, 0, 0.8)' },
        }}
        open={exporting}
        keepMounted={true}
        fullScreen={true}
      >
        <Loader />
      </Dialog>
    </div>
  );
};

export default ExportPdfButton;
