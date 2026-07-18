import { GroupsOutlined, HelpCenterOutlined } from '@mui/icons-material';
import { Card, CardActionArea, CardContent, Chip, SvgIcon, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { LogoFiligranIcon } from 'filigran-icon';
import { type ReactNode } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { type ConnectorItem, type ConnectorItemType, prettifyUseCase } from './catalog-facets';
import useCaseIcon from './use-case-icons';

const MAX_USE_CASE_CHIPS = 2;

interface Props {
  connector: ConnectorItem;
  /** Right side of the card footer (deploy button, instance status, migrate action...). */
  footerAction?: ReactNode;
}

const CatalogConnectorCard = ({ connector, footerAction }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const visibleUseCases = connector.useCases.slice(0, MAX_USE_CASE_CHIPS);
  const hiddenUseCasesCount = connector.useCases.length - visibleUseCases.length;

  const typeLabels: Record<ConnectorItemType, string> = {
    COLLECTOR: t('Collector'),
    INJECTOR: t('Injector'),
    EXECUTOR: t('Executor'),
  };

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
        // A Link with an empty `to` would still render href="" (surprising
        // navigation for assistive tech / open-in-new-tab); render a plain
        // action area when the card has no detail page.
        {...(connector.detailUrl != null
          ? {
              component: Link,
              to: connector.detailUrl,
            }
          : { disabled: true })}
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
              {connector.logoSrc ? (
                <img
                  src={connector.logoSrc}
                  alt={connector.title}
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
              {connector.title}
            </Typography>
            {/* Support semantics (same as OpenCTI): the verified flag means
                supported by Filigran, otherwise supported by the community. */}
            <Tooltip title={connector.verified ? t('Supported by Filigran') : t('Supported by Community')}>
              {connector.verified ? (
                <SvgIcon
                  component={LogoFiligranIcon}
                  inheritViewBox
                  color="primary"
                  sx={{
                    fontSize: 16,
                    flexShrink: 0,
                  }}
                />
              ) : (
                <GroupsOutlined
                  color="disabled"
                  sx={{
                    fontSize: 18,
                    flexShrink: 0,
                  }}
                />
              )}
            </Tooltip>
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
              label={typeLabels[connector.type]}
            />
            {visibleUseCases.map((useCase) => {
              const UseCaseIcon = useCaseIcon(useCase);
              return (
                <Chip
                  key={useCase}
                  variant="outlined"
                  color="default"
                  size="small"
                  sx={chipSx}
                  icon={<UseCaseIcon sx={{ fontSize: 12 }} />}
                  label={prettifyUseCase(useCase)}
                />
              );
            })}
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
          {connector.description && (
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
              {connector.description}
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
              label={connector.external ? t('External') : t('Built-in')}
            />
            {footerAction}
          </footer>
        </CardContent>
      </CardActionArea>
    </Card>
  );
};

export default CatalogConnectorCard;
