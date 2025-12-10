import { LibraryBooksOutlined, OpenInNewOutlined } from '@mui/icons-material';
import { Paper, Typography } from '@mui/material';
import { useOutletContext } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { type CatalogConnectorOutput } from '../../../../utils/api-types';
import ConnectorTitle from './ConnectorTitle';

const useStyles = makeStyles()(theme => ({
  content: {
    display: 'grid',
    gap: `0px ${theme.spacing(3)}`,
    gridTemplateColumns: '2fr 1fr',
    marginTop: theme.spacing(3),
  },
  link: {
    display: 'flex',
    gap: theme.spacing(1),
    alignItems: 'center',
  },
  chipInList: {
    fontSize: 12,
    height: 20,
    flexShrink: 0,
    padding: theme.spacing(2),
    alignSelf: 'flex-start',
    textTransform: 'uppercase',
    borderRadius: 4,
  },
  paperConnector: {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(3),
  },
}));

type CatalogContextType = { connector: CatalogConnectorOutput };

const ConnectorDetails = () => {
  // Standard hooks
  const { t, nsdt } = useFormatter();
  const { classes } = useStyles();

  const { connector } = useOutletContext<CatalogContextType>();

  return (
    <>
      <ConnectorTitle
        connectorId={connector.catalog_connector_id}
        connectorLogo={connector.catalog_connector_logo_url}
        connectorTitle={connector.catalog_connector_title}
        connectorType={connector.catalog_connector_type}
        connectorUseCases={connector.catalog_connector_use_cases}
        detailsTitle
      />
      <div className={classes.content}>
        <Typography variant="h4">{t('Overview')}</Typography>
        <Typography variant="h4">{t('Basic Information')}</Typography>

        <Paper variant="outlined" className={`paper ${classes.paperConnector}`}>
          {connector.catalog_connector_description}
        </Paper>
        <Paper variant="outlined" className={`paper ${classes.paperConnector}`}>
          {connector.catalog_connector_source_code
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
                  href={connector.catalog_connector_source_code}
                  rel="noreferrer"
                  className={classes.link}
                >
                  <LibraryBooksOutlined />
                  {connector.catalog_connector_title}
                </a>
              </div>
            )}

          {connector.catalog_connector_subscription_link
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
                  href={connector.catalog_connector_subscription_link}
                  rel="noreferrer"
                  className={classes.link}
                >
                  <OpenInNewOutlined />
                  {t('VENDOR CONTACT')}
                </a>
              </div>
            )}
          {connector.catalog_connector_last_verified_date
            && (
              <div>
                <Typography variant="h3" gutterBottom>{t('Last verified')}</Typography>
                {nsdt(connector.catalog_connector_last_verified_date)}
              </div>
            )}
        </Paper>

      </div>
    </>

  );
};

export default ConnectorDetails;
