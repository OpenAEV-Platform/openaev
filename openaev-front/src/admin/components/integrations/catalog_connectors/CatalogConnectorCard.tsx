import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { GroupsOutlined, HelpCenterOutlined } from '@mui/icons-material';
import { Box, Card, CardActionArea, CardContent, Stack, SvgIcon, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { LogoFiligranIcon } from 'filigran-icon';
import { type ReactNode } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import { type ConnectorItem, type ConnectorItemType, prettifyUseCase } from './catalog-facets';
import useChipOverflow from './useChipOverflow';

interface Props {
  connector: ConnectorItem;
  /** Right side of the card footer (deploy button, instance status, migrate action...). */
  footerAction?: ReactNode;
}

// Use-case chips for the footer-left; exported for the lines view.
// Chips that fit are shown, the rest collapse into "+N" — never clipped mid-label.
export const UseCaseChips = ({ useCases }: { useCases: string[] }) => {
  const { containerRef, chipRefs, overflowRef, visibleCount } = useChipOverflow(useCases);

  const hiddenCount = useCases.length - visibleCount;
  // Only what the "+N" stands for: the chips already shown are not repeated.
  const hiddenUseCases = useCases.slice(visibleCount).map(prettifyUseCase).join(', ');

  return (
    <Stack
      ref={containerRef}
      direction="row"
      spacing={1}
      sx={{
        overflow: 'hidden',
        flexWrap: 'nowrap',
        position: 'relative',
        minWidth: 0,
      }}
    >
      {useCases.map((useCase, index) => {
        const isVisible = index < visibleCount;
        return (
          <Box
            key={useCase}
            ref={(el: HTMLDivElement | null) => {
              chipRefs.current[index] = el;
            }}
            // Fixed-size chips, so the overflow count is measurable.
            sx={{
              flexShrink: 0,
              maxWidth: '100%',
              minWidth: 0,
              visibility: isVisible ? 'visible' : 'hidden',
              position: isVisible ? 'relative' : 'absolute',
            }}
          >
            <Tooltip>
              <TooltipTrigger asChild>
                <Chip
                  label={prettifyUseCase(useCase)}
                  severity="info"
                  style={{ maxWidth: '100%' }}
                />
              </TooltipTrigger>
              {prettifyUseCase(useCase) && <TooltipContent>{prettifyUseCase(useCase)}</TooltipContent>}
            </Tooltip>
          </Box>
        );
      })}
      {/* Always mounted, so its width is known before anything is hidden; it is
          only taken out of the flow while there is nothing to count. */}
      <Box
        ref={overflowRef}
        sx={{
          flexShrink: 0,
          visibility: hiddenCount > 0 ? 'visible' : 'hidden',
          position: hiddenCount > 0 ? 'relative' : 'absolute',
        }}
      >
        <Tooltip>
          <TooltipTrigger asChild>
            <Chip label={`+${Math.max(hiddenCount, 1)}`} severity="info" />
          </TooltipTrigger>
          {hiddenUseCases && <TooltipContent>{hiddenUseCases}</TooltipContent>}
        </Tooltip>
      </Box>
    </Stack>
  );
};

const CollapsedUseCaseChips = ({ useCases }: { useCases: string[] }) => {
  if (useCases.length === 0) return null;
  const [first, ...others] = useCases.map(prettifyUseCase);
  return (
    <Stack direction="row" spacing={1} sx={{ minWidth: 0 }}>
      <Box sx={{
        minWidth: 0,
        display: 'flex',
      }}
      >
        <Tooltip>
          <TooltipTrigger asChild>
            <Chip label={first} severity="info" style={{ maxWidth: '100%' }} />
          </TooltipTrigger>
          {first && <TooltipContent>{first}</TooltipContent>}
        </Tooltip>
      </Box>
      {others.length > 0 && (
        <Box sx={{ flexShrink: 0 }}>
          <Tooltip>
            <TooltipTrigger asChild>
              <Chip label={`+${others.length}`} severity="info" />
            </TooltipTrigger>
            <TooltipContent>{others.join(', ')}</TooltipContent>
          </Tooltip>
        </Box>
      )}
    </Stack>
  );
};

const CatalogConnectorCard = ({ connector, footerAction }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const typeLabels: Record<ConnectorItemType, string> = {
    COLLECTOR: t('Collector'),
    INJECTOR: t('Injector'),
    EXECUTOR: t('Executor'),
    SECRETS_PROVIDER: t('Secrets Provider'),
  };

  return (
    // Hover on the wrapper, so it still applies when the inner action area is
    // disabled (cards without a detail page).
    <Box
      data-testid="connector-card"
      sx={{
        'height': '100%',
        '& .MuiCard-root': {
          border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
          transition: 'transform 0.3s ease-in-out, border-color 0.3s ease-in-out, box-shadow 0.3s ease-in-out',
        },
        '&:hover .MuiCard-root': {
          transform: 'translateY(-2px)',
          borderColor: alpha(theme.palette.primary.main, 0.3),
          boxShadow: `0 0 30px ${alpha(theme.palette.primary.main, 0.12)}`,
        },
      }}
    >
      <Card
        variant="outlined"
        sx={{
          height: 280,
          borderRadius: 1,
          display: 'flex',
          // Slightly lighter than the page, so cards pop against the hero. Read
          // from the palette slot this literal was copying, which moved to a
          // library token: the default surface shifts from #0c1524 to #13213e.
          backgroundColor: theme.palette.mode === 'dark' ? theme.palette.background.secondary : undefined,
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
          sx={{
            display: 'flex',
            alignItems: 'stretch',
          }}
        >
          <CardContent
            sx={{
              display: 'flex',
              flexDirection: 'column',
              gap: 2,
              padding: 3,
              height: '100%',
              width: '100%',
            }}
          >
            <Stack direction="row" gap={1.5} alignItems="flex-start" sx={{ width: '100%' }}>
              <Box
                sx={{
                  width: 56,
                  height: 56,
                  flexShrink: 0,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  borderRadius: 1,
                  border: `1px solid ${alpha(theme.palette.text.primary, 0.1)}`,
                  backgroundColor: alpha(theme.palette.text.primary, 0.04),
                }}
              >
                {connector.logoSrc ? (
                  <img
                    src={connector.logoSrc}
                    alt={connector.title}
                    style={{
                      width: 56,
                      height: 56,
                      objectFit: 'contain',
                      borderRadius: 4,
                    }}
                  />
                ) : (
                  <HelpCenterOutlined sx={{
                    fontSize: 32,
                    color: 'text.secondary',
                  }}
                  />
                )}
              </Box>
              <Box sx={{
                flex: 1,
                minWidth: 0,
              }}
              >
                <Typography
                  variant="body2"
                  sx={{
                    color: 'primary.main',
                    fontSize: 12,
                    fontWeight: 500,
                    letterSpacing: '0.06em',
                    textTransform: 'uppercase',
                    marginBottom: 0.5,
                  }}
                >
                  {typeLabels[connector.type]}
                </Typography>
                <Tooltip>
                  <TooltipTrigger asChild>
                    <Typography
                      sx={{
                        fontSize: 15,
                        fontWeight: 600,
                        lineHeight: 1.35,
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                        wordBreak: 'break-word',
                      }}
                    >
                      {connector.title}
                    </Typography>
                  </TooltipTrigger>
                  {connector.title && <TooltipContent side="bottom" align="start">{connector.title}</TooltipContent>}
                </Tooltip>
              </Box>
              {/* Support semantics (same as OpenCTI): the verified flag means
                  supported by Filigran, otherwise supported by the community. */}
              <Tooltip>
                <TooltipTrigger asChild>
                  <span className="inline-flex">
                    {connector.verified ? (
                      <SvgIcon
                        component={LogoFiligranIcon}
                        inheritViewBox
                        color="primary"
                        sx={{
                          fontSize: 20,
                          flexShrink: 0,
                        }}
                      />
                    ) : (
                      <GroupsOutlined
                        sx={{
                          fontSize: 20,
                          flexShrink: 0,
                          // Who supports the connector is a category, not a
                          // state: the secondary ink, not the disabled one.
                          color: 'var(--text-default-secondary)',
                        }}
                      />
                    )}
                  </span>
                </TooltipTrigger>
                <TooltipContent>{connector.verified ? t('Supported by Filigran') : t('Supported by Community')}</TooltipContent>
              </Tooltip>
            </Stack>

            <Box sx={{
              flexGrow: 1,
              overflow: 'hidden',
              width: '100%',
            }}
            >
              {connector.description && (
                <Typography
                  variant="body2"
                  sx={{
                    color: 'text.secondary',
                    lineHeight: 1.5,
                    display: '-webkit-box',
                    WebkitLineClamp: 4,
                    WebkitBoxOrient: 'vertical',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  {connector.description}
                </Typography>
              )}
            </Box>

            {/* Footer: use-case (category) chips on the left, deploy / status on
                the right - mirrors the OpenCTI CardActions layout. */}
            <div style={{
              display: 'flex',
              alignItems: 'flex-end',
              justifyContent: 'space-between',
              gap: theme.spacing(1),
              width: '100%',
            }}
            >
              <CollapsedUseCaseChips useCases={connector.useCases} />
              {footerAction && (
                <div style={{ flexShrink: 0 }}>
                  {footerAction}
                </div>
              )}
            </div>
          </CardContent>
        </CardActionArea>
      </Card>
    </Box>
  );
};

export default CatalogConnectorCard;
