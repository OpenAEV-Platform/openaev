import {
  Card,
  CardDescription,
  CardFooter,
  CardHeader,
  CardOverview,
  CardTitle,
  Chip,
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@filigran/design-system';
import { GroupsOutlined, HelpCenterOutlined } from '@mui/icons-material';
import { Box, Stack, SvgIcon } from '@mui/material';
import { LogoFiligranIcon } from 'filigran-icon';
import { type ReactNode } from 'react';
import { Link } from 'react-router';

import CardThumbnail from '../../../../components/common/card/CardThumbnail';
import { useFormatter } from '../../../../components/i18n';
import { type ConnectorItem, type ConnectorItemType, prettifyUseCase } from './catalog-facets';
import useChipOverflow from './useChipOverflow';

interface Props {
  connector: ConnectorItem;
  /** Right side of the card footer (deploy button, migrate action...). */
  footerAction?: ReactNode;
  /** Left side of the card footer, before the support logo (health, last seen, status). */
  footerStatus?: ReactNode;
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
            {/* Shown in full: no tooltip. The "+N" one lists the hidden use cases. */}
            <Chip
              label={prettifyUseCase(useCase)}
              severity="info"
              style={{ maxWidth: '100%' }}
            />
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

// Who supports the connector, at the end of the Deployed tab footer status
// (Figma Design_System_2026, node 8461:13814).
export const ConnectorSupportLogo = ({ verified }: { verified: boolean }) => {
  const { t } = useFormatter();
  // Support semantics (same as OpenCTI): the verified flag means supported by
  // Filigran, otherwise supported by the community.
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        {/* Focusable: the card footer only lets the pointer reach interactive
            elements, and the logo means nothing without its tooltip. */}
        <span
          className="inline-flex"
          tabIndex={0}
          aria-label={verified ? t('Supported by Filigran') : t('Supported by Community')}
        >
          {verified ? (
            <SvgIcon
              component={LogoFiligranIcon}
              inheritViewBox
              color="primary"
              sx={{
                fontSize: 24,
                flexShrink: 0,
              }}
            />
          ) : (
            <GroupsOutlined
              sx={{
                fontSize: 24,
                flexShrink: 0,
                // Who supports the connector is a category, not a
                // state: the secondary ink, not the disabled one.
                color: 'var(--text-default-secondary)',
              }}
            />
          )}
        </span>
      </TooltipTrigger>
      <TooltipContent>{verified ? t('Supported by Filigran') : t('Supported by Community')}</TooltipContent>
    </Tooltip>
  );
};

const CatalogConnectorCard = ({ connector, footerAction, footerStatus }: Props) => {
  const { t } = useFormatter();

  const typeLabels: Record<ConnectorItemType, string> = {
    COLLECTOR: t('Collector'),
    INJECTOR: t('Injector'),
    EXECUTOR: t('Executor'),
    SECRETS_PROVIDER: t('Secrets Provider'),
  };

  return (
    // Library Card (Figma Design_System_2026, node 8461:13814): thumbnail,
    // type and title, use-case chips, description, then a footer with the
    // status on the left and the action on the right.
    <Card
      data-testid="connector-card"
      to={connector.detailUrl}
      linkComponent={connector.detailUrl ? Link : undefined}
    >
      <CardHeader
        icon={(
          // A logo fills the thumbnail; without one, a 24px glyph in the frame.
          <CardThumbnail
            image={connector.logoSrc ? {
              src: connector.logoSrc,
              alt: connector.title,
            } : undefined}
            icon={(
              <HelpCenterOutlined sx={{
                fontSize: 24,
                color: 'text.secondary',
              }}
              />
            )}
          />
        )}
      >
        <CardOverview>{typeLabels[connector.type]}</CardOverview>
        <CardTitle>{connector.title}</CardTitle>
      </CardHeader>
      {connector.useCases.length > 0 && <UseCaseChips useCases={connector.useCases} />}
      {connector.description && <CardDescription>{connector.description}</CardDescription>}
      {(footerStatus || footerAction) && <CardFooter start={footerStatus} end={footerAction} />}
    </Card>
  );
};

export default CatalogConnectorCard;
