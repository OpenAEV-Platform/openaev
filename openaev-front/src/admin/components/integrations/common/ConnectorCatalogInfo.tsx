import { InfoOutlined, LibraryBooksOutlined, OpenInNewOutlined, VerifiedOutlined } from '@mui/icons-material';
import { Box, Paper, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type ComponentType, type ReactNode } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type CatalogConnectorOutput } from '../../../../utils/api-types';

interface Props { catalogConnector: CatalogConnectorOutput }

const SECTION_LABEL_SX = {
  fontFamily: '"Geologica", sans-serif',
  fontWeight: 600,
  fontSize: 11,
  letterSpacing: '0.12em',
  textTransform: 'uppercase' as const,
  color: 'text.secondary',
  marginBottom: 1.5,
};

// A resource row: framed icon + title + caption, optionally acting as a link.
const ResourceRow = ({ icon: Icon, title, caption, href, endAdornment }: {
  icon: ComponentType<{ sx?: object }>;
  title: ReactNode;
  caption?: ReactNode;
  href?: string;
  endAdornment?: ReactNode;
}) => {
  const theme = useTheme();
  const content = (
    <>
      <div style={{
        width: 32,
        height: 32,
        flexShrink: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        border: `1px solid ${theme.palette.divider}`,
        borderRadius: theme.shape.borderRadius,
        backgroundColor: theme.palette.background.default,
      }}
      >
        <Icon sx={{
          fontSize: 16,
          color: 'primary.main',
        }}
        />
      </div>
      <div style={{
        minWidth: 0,
        display: 'flex',
        flexDirection: 'column',
        flex: 1,
      }}
      >
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 0.5,
        }}
        >
          <Typography sx={{
            fontSize: theme.typography.h3.fontSize,
            fontWeight: 600,
            lineHeight: 1.4,
          }}
          >
            {title}
          </Typography>
          {endAdornment}
        </Box>
        {caption && (
          <Typography
            variant="body2"
            sx={{
              color: 'text.secondary',
              fontSize: theme.typography.h4.fontSize,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {caption}
          </Typography>
        )}
      </div>
    </>
  );
  const rowSx = {
    display: 'flex',
    alignItems: 'center',
    gap: 1.5,
    padding: 1,
    margin: -1,
    borderRadius: 1,
  };
  if (href) {
    return (
      <Box
        component="a"
        target="_blank"
        href={href}
        rel="noreferrer"
        sx={{
          ...rowSx,
          'color': 'inherit',
          'textDecoration': 'none',
          'transition': 'background-color 0.15s ease',
          '&:hover': { backgroundColor: theme.palette.action.hover },
        }}
      >
        {content}
      </Box>
    );
  }
  return <Box sx={rowSx}>{content}</Box>;
};

/**
 * The two-column content of a connector detail page: the full description on
 * the left, the resource links and verification metadata on the right.
 */
const ConnectorCatalogInfo = ({ catalogConnector }: Props) => {
  const theme = useTheme();
  const { t, nsdt } = useFormatter();

  const description = catalogConnector.catalog_connector_description
    || catalogConnector.catalog_connector_short_description;

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'minmax(0, 2fr) minmax(0, 1fr)',
      gap: theme.spacing(3),
      alignItems: 'stretch',
    }}
    >
      <section style={{
        display: 'flex',
        flexDirection: 'column',
      }}
      >
        <Typography sx={SECTION_LABEL_SX}>{t('Description')}</Typography>
        <Paper
          variant="outlined"
          sx={{
            padding: 2,
            borderRadius: 1,
            flex: 1,
          }}
        >
          <Typography
            variant="body1"
            sx={{
              whiteSpace: 'pre-line',
              color: description ? 'text.primary' : 'text.secondary',
            }}
          >
            {description || '-'}
          </Typography>
        </Paper>
      </section>
      <section style={{
        display: 'flex',
        flexDirection: 'column',
      }}
      >
        <Typography sx={SECTION_LABEL_SX}>{t('Basic Information')}</Typography>
        <Paper
          variant="outlined"
          sx={{
            padding: 2,
            borderRadius: 1,
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            gap: 2.5,
          }}
        >
          {catalogConnector.catalog_connector_container_version && (
            <ResourceRow
              icon={VerifiedOutlined}
              title={t('Catalog version')}
              caption={catalogConnector.catalog_connector_container_version}
              endAdornment={(
                <Tooltip title={t('Version referenced in the integration catalog. The running instance may use a different version if it was manually overridden.')}>
                  {/* tabIndex makes the icon keyboard-focusable; the Tooltip
                      title doubles as its accessible name (MUI default). */}
                  <InfoOutlined
                    tabIndex={0}
                    sx={{
                      fontSize: theme.typography.h6.fontSize,
                      color: 'text.secondary',
                    }}
                  />
                </Tooltip>
              )}
            />
          )}
          {catalogConnector.catalog_connector_source_code && (
            <ResourceRow
              icon={LibraryBooksOutlined}
              title={t('Integration documentation and code')}
              caption={t('Deployment guide and source code on GitHub')}
              href={catalogConnector.catalog_connector_source_code}
            />
          )}
          {catalogConnector.catalog_connector_subscription_link && (
            <ResourceRow
              icon={OpenInNewOutlined}
              title={t('VENDOR CONTACT')}
              caption={t('Visit the vendor\'s page to learn more and get in touch')}
              href={catalogConnector.catalog_connector_subscription_link}
            />
          )}
          {catalogConnector.catalog_connector_last_verified_date && (
            <ResourceRow
              icon={VerifiedOutlined}
              title={t('Last verified')}
              caption={nsdt(catalogConnector.catalog_connector_last_verified_date)}
            />
          )}
        </Paper>
      </section>
    </div>
  );
};

export default ConnectorCatalogInfo;
