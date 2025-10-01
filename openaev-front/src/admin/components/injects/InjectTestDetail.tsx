import { Card, CardContent, CardHeader, Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';

import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import { type InjectTestStatusOutput } from '../../../utils/api-types';
import { truncate } from '../../../utils/String';
import InjectIcon from '../common/injects/InjectIcon';
import GlobalExecutionTraces from '../common/injects/status/traces/GlobalExecutionTraces';

interface Props {
  open: boolean;
  handleClose: () => void;
  injectTestStatus: InjectTestStatusOutput | undefined;
}

const InjectTestDetail = ({
  open,
  handleClose,
  injectTestStatus,
}: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  return (
    <Drawer
      open={open}
      handleClose={handleClose}
      title={t('Test Details')}
    >
      <div>
        <Card elevation={0} style={{ marginBottom: theme.spacing(3) }}>
          {injectTestStatus
            ? (
                <CardHeader
                  sx={{ backgroundColor: theme.palette.background.default }}
                  avatar={(
                    <InjectIcon
                      isPayload={false}
                      type={injectTestStatus.inject_type}
                      variant="list"
                    />
                  )}

                />
              ) : (
                <Paper variant="outlined" style={{ padding: theme.spacing(3) }}>
                  <Typography variant="body1">{t('No data available')}</Typography>
                </Paper>
              )}
          <CardContent style={{
            fontSize: 18,
            textAlign: 'center',
          }}
          >
            {truncate(injectTestStatus?.inject_title, 80)}
          </CardContent>
        </Card>
        {injectTestStatus && <GlobalExecutionTraces injectStatus={injectTestStatus} />}
      </div>
    </Drawer>
  );
};

export default InjectTestDetail;
