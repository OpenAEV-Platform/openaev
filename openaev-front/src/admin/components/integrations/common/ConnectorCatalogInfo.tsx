import { LibraryBooksOutlined, OpenInNewOutlined } from '@mui/icons-material';
import { Paper, Typography } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { type CatalogConnectorOutput } from '../../../../utils/api-types';

interface Props { catalogConnector: CatalogConnectorOutput }

const useStyles = makeStyles()(theme => ({
  content: {
    display: 'grid',
    gap: `0px ${theme.spacing(3)}`,
    gridTemplateColumns: '2fr 1fr',
    marginTop: theme.spacing(3),
  },
  paperConnector: {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(3),
  },
  link: {
    display: 'flex',
    gap: theme.spacing(1),
    alignItems: 'center',
  },
}));

const ConnectorCatalogInfo = ({ catalogConnector }: Props) => {
  const { t, nsdt } = useFormatter();
  const { classes } = useStyles();

  return (
    <div className={classes.content}>
      <Typography variant="h4">{t('Overview')}</Typography>
      <Typography variant="h4">{t('Basic Information')}</Typography>

      <Paper variant="outlined" className={`paper ${classes.paperConnector}`}>
        {catalogConnector.catalog_connector_description}
      </Paper>
      <Paper variant="outlined" className={`paper ${classes.paperConnector}`}>
        {catalogConnector.catalog_connector_source_code
          && (
            <div>
              <Typography
                variant="h3"
                gutterBottom
              >
                {t('Integration documentation and code')}
              </Typography>
              <a
                target="_blank"
                href={catalogConnector.catalog_connector_source_code}
                rel="noreferrer"
                className={classes.link}
              >
                <LibraryBooksOutlined />
                {catalogConnector.catalog_connector_title}
              </a>
            </div>
          )}

        {catalogConnector.catalog_connector_subscription_link
          && (
            <div>
              <Typography
                variant="h3"
                gutterBottom
              >
                {t('Visit the vendor\'s page to learn more and get in touch')}
              </Typography>
              <a
                target="_blank"
                href={catalogConnector.catalog_connector_subscription_link}
                rel="noreferrer"
                className={classes.link}
              >
                <OpenInNewOutlined />
                {t('VENDOR CONTACT')}
              </a>
            </div>
          )}
        {catalogConnector.catalog_connector_last_verified_date
          && (
            <div>
              <Typography variant="h3" gutterBottom>{t('Last verified')}</Typography>
              {nsdt(catalogConnector.catalog_connector_last_verified_date)}
            </div>
          )}
      </Paper>
    </div>
  );
};

export default ConnectorCatalogInfo;
