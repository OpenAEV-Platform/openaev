import { LibraryBooksOutlined, OpenInNewOutlined, VerifiedOutlined } from '@mui/icons-material';
import { Box, Paper, Typography } from '@mui/material';
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
const ResourceRow = ({ icon: Icon, title, caption, href }: {
  icon: ComponentType<{ sx?: object }>;
  title: ReactNode;
  caption?: ReactNode;
  href?: string;
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
      }}
      >
        <Typography sx={{
          fontSize: 13,
          fontWeight: 600,
          lineHeight: 1.4,
        }}
        >
          {title}
        </Typography>
        {caption && (
          <Typography
            variant="body2"
            sx={{
              color: 'text.secondary',
              fontSize: 12,
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
