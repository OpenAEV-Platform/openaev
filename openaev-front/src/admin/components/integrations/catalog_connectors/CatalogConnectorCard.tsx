import { HelpCenterOutlined, VerifiedOutlined } from '@mui/icons-material';
import { Card, CardActionArea, CardContent, Chip, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type SyntheticEvent } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { type CatalogConnectorOutput } from '../../../../utils/api-types';
import DeployButton from '../common/DeployButton';
import { prettifyUseCase } from './catalog-facets';

const MAX_USE_CASE_CHIPS = 2;

interface Props {
  connector: CatalogConnectorOutput;
  onDeployBtnClick: (e: SyntheticEvent) => void;
}

const CatalogConnectorCard = ({ connector, onDeployBtnClick }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const useCases = connector.catalog_connector_use_cases ?? [];
  const visibleUseCases = useCases.slice(0, MAX_USE_CASE_CHIPS);
  const hiddenUseCasesCount = useCases.length - visibleUseCases.length;

  const chipSx = {
    fontSize: 11,
    height: 20,
    textTransform: 'uppercase',
    borderRadius: 1,
  };

  return (
    <Card
      variant="outlined"
      data-testid="connector-card"
      sx={{
        'position': 'relative',
        'height': '100%',
        'display': 'flex',
        'transition': 'transform 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease',
        '&:hover': {
          transform: 'translateY(-2px)',
          borderColor: alpha(theme.palette.primary.main, 0.5),
          boxShadow: `0 4px 20px ${alpha(theme.palette.primary.main, 0.12)}`,
        },
      }}
    >
      <CardActionArea
        component={Link}
        to={`/admin/integrations/catalog/${connector.catalog_connector_id}`}
        sx={{ display: 'flex' }}
      >
        <CardContent
          sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 1.5,
            height: '100%',
            width: '100%',
          }}
        >
          <header style={{
            display: 'flex',
            alignItems: 'flex-start',
            gap: theme.spacing(1.5),
          }}
          >
            <div style={{
              width: 44,
              height: 44,
              flexShrink: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              border: `1px solid ${theme.palette.divider}`,
              borderRadius: theme.shape.borderRadius,
              backgroundColor: theme.palette.background.default,
            }}
            >
              {connector.catalog_connector_logo_url ? (
                <img
                  src={`/api/images/catalog/connectors/logos/${connector.catalog_connector_logo_url}`}
                  alt={connector.catalog_connector_title}
                  style={{
                    width: 32,
                    height: 32,
                    objectFit: 'contain',
                  }}
                />
              ) : (
                <HelpCenterOutlined sx={{ color: 'text.secondary' }} />
              )}
            </div>
            <Typography
              sx={{
                flex: 1,
                minWidth: 0,
                fontSize: 13.5,
                fontWeight: 600,
                lineHeight: 1.35,
                display: '-webkit-box',
                WebkitLineClamp: 2,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
                wordBreak: 'break-word',
                minHeight: 36,
              }}
            >
              {connector.catalog_connector_title}
            </Typography>
            {connector.catalog_connector_verified && (
              <Tooltip title={t('Verified and tested by OpenAEV')}>
                <VerifiedOutlined
                  color="success"
                  sx={{
                    fontSize: 18,
                    flexShrink: 0,
                  }}
                />
              </Tooltip>
            )}
          </header>
          <div style={{
            display: 'flex',
            flexWrap: 'wrap',
            gap: theme.spacing(0.5),
          }}
          >
            <Chip
              variant="outlined"
              color="primary"
              size="small"
              sx={chipSx}
              label={connector.catalog_connector_type}
            />
            {visibleUseCases.map(useCase => (
              <Chip
                key={useCase}
                variant="outlined"
                color="default"
                size="small"
                sx={chipSx}
                label={prettifyUseCase(useCase)}
              />
            ))}
            {hiddenUseCasesCount > 0 && (
              <Chip
                variant="outlined"
                color="default"
                size="small"
                sx={chipSx}
                label={`+${hiddenUseCasesCount}`}
              />
            )}
          </div>
          {connector.catalog_connector_short_description && (
            <Typography
              variant="body2"
              sx={{
                color: 'text.secondary',
                display: '-webkit-box',
                WebkitLineClamp: 2,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
                minHeight: 38,
              }}
            >
              {connector.catalog_connector_short_description}
            </Typography>
          )}
          <footer style={{
            marginTop: 'auto',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: theme.spacing(1),
          }}
          >
            <Chip
              variant="outlined"
              color="default"
              size="small"
              sx={chipSx}
              label={connector.catalog_connector_manager_supported ? t('External') : t('Built-in')}
            />
            <DeployButton
              onDeployBtnClick={onDeployBtnClick}
              deploymentCount={connector.instance_deployed_count ?? 0}
            />
          </footer>
        </CardContent>
      </CardActionArea>
    </Card>
  );
};

export default CatalogConnectorCard;
