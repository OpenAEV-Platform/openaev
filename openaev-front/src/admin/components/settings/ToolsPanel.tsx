import { List, ListItem, ListItemText, Paper, Typography } from '@mui/material';

import { useFormatter } from '../../../components/i18n';
import ItemBoolean from '../../../components/ItemBoolean';
import type { PlatformSettings } from '../../../utils/api-types';

interface ToolsPanelProps { settings: PlatformSettings }

const ToolsPanel = ({ settings }: ToolsPanelProps) => {
  const { t } = useFormatter();

  return (
    <>
      <Typography variant="h4">{t('Tools')}</Typography>
      <Paper
        variant="outlined"
        sx={{ padding: theme => `${theme.spacing(1)}` }}
      >
        <List sx={{ padding: 0 }}>
          <ListItem divider>
            <ListItemText primary={t('JAVA Virtual Machine')} />
            <ItemBoolean status={null} variant="large" neutralLabel={settings?.java_version} />
          </ListItem>
          <ListItem divider>
            <ListItemText primary={t('PostgreSQL')} />
            <ItemBoolean status={null} variant="large" neutralLabel={settings?.postgre_version} />
          </ListItem>
          <ListItem divider>
            <ListItemText primary={t('RabbitMQ')} />
            <ItemBoolean status={null} variant="large" neutralLabel={settings?.rabbitmq_version} />
          </ListItem>
          {settings.analytics_engine_type && (
            <ListItem divider>
              <ListItemText primary={t(settings.analytics_engine_type)} />
              <ItemBoolean status={null} variant="large" neutralLabel={settings?.analytics_engine_version} />
            </ListItem>
          )}
          <ListItem divider>
            <ListItemText primary={t('Telemetry manager')} />
            <ItemBoolean status={settings?.telemetry_manager_enable} variant="large" label={settings?.telemetry_manager_enable ? t('Enable') : t('Disabled')} />
          </ListItem>
          <ListItem divider>
            <ListItemText primary={t('SMTP')} />
            <ItemBoolean status={settings?.smtp_service_available === 'true'} variant="large" label={settings?.smtp_service_available === 'true' ? t('Enable') : t('Disabled')} />
          </ListItem>
          <ListItem divider>
            <ListItemText primary={t('IMAP')} />
            <ItemBoolean status={settings?.imap_service_available === 'true'} variant="large" label={settings?.imap_service_available === 'true' ? t('Enable') : t('Disabled')} />
          </ListItem>
        </List>
      </Paper>
    </>
  );
};

export default ToolsPanel;
