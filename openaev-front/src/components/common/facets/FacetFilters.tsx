import { CheckOutlined } from '@mui/icons-material';
import { Box, ButtonBase, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ReactElement } from 'react';

export interface FacetRow {
  value: string;
  label: string;
  count?: number;
  icon?: () => ReactElement;
  onToggle: () => void;
  checked: boolean;
}

export interface FacetSection {
  id: string;
  label: string;
  rows: FacetRow[];
  /** Optional control rendered under the section title (e.g. a kill chain switcher). */
  headerAction?: ReactElement;
}

// Same anatomy as the integrations marketplace CatalogSidebar row: custom
// checkbox + optional icon + label + optional count badge. Shared by the
// Threat Arsenal sidebar and the inject-contract picker sidebar.
export const FacetRowItem = ({ row }: { row: FacetRow }) => {
  const theme = useTheme();
  const disabled = row.count === 0 && !row.checked;
  return (
    <ButtonBase
      role="checkbox"
      aria-checked={row.checked}
      aria-label={row.label}
      disabled={disabled}
      onClick={row.onToggle}
      sx={{
        'display': 'flex',
        'alignItems': 'center',
        'gap': 1,
        'width': '100%',
        'justifyContent': 'flex-start',
        'padding': theme.spacing(0.5, 1),
        'borderRadius': 1,
        'textAlign': 'left',
        'opacity': disabled ? 0.4 : 1,
        'transition': 'background-color 0.15s ease',
        '&:hover': { backgroundColor: theme.palette.action.hover },
      }}
    >
      <span
        aria-hidden
        style={{
          width: 16,
          height: 16,
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          borderRadius: 2,
          border: `1px solid ${row.checked ? theme.palette.primary.main : theme.palette.divider}`,
          backgroundColor: row.checked ? theme.palette.primary.main : 'transparent',
          boxShadow: row.checked ? `0 0 6px ${alpha(theme.palette.primary.main, 0.5)}` : 'none',
          transition: 'all 0.15s ease',
        }}
      >
        {row.checked && (
          <CheckOutlined sx={{
            fontSize: 12,
            color: theme.palette.primary.contrastText,
          }}
          />
        )}
      </span>
      {row.icon && (
        <Box
          aria-hidden
          sx={{
            'display': 'flex',
            'alignItems': 'center',
            'justifyContent': 'center',
            'flexShrink': 0,
            'color': 'text.secondary',
            // Force a consistent 16px icon size (matching the integrations
            // marketplace sidebar), whatever the underlying icon/image.
            '& svg': { fontSize: 16 },
            '& img': {
              width: 16,
              height: 16,
            },
          }}
        >
          {row.icon()}
        </Box>
      )}
      <Typography
        variant="body2"
        sx={{
          flex: 1,
          minWidth: 0,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {row.label}
      </Typography>
      {row.count !== undefined && (
        <span
          style={{
            fontSize: 11,
            lineHeight: '18px',
            minWidth: 24,
            textAlign: 'center',
            padding: theme.spacing(0, 0.5),
            borderRadius: 2,
            backgroundColor: row.checked
              ? alpha(theme.palette.primary.main, 0.16)
              : theme.palette.action.hover,
            color: row.checked ? theme.palette.primary.main : theme.palette.text.secondary,
          }}
        >
          {row.count}
        </span>
      )}
    </ButtonBase>
  );
};

interface FacetSidebarProps { sections: FacetSection[] }

// The sticky faceted sidebar shell (mirrors the integrations marketplace
// CatalogSidebar): a fixed-width sticky <aside> with one titled section of
// FacetRowItems per facet. Clearing is handled by the toolbar's "Clear
// filters" button (the facets are regular backend filters), so the sidebar
// carries no clear action of its own.
export const FacetSidebar = ({ sections }: FacetSidebarProps) => {
  const theme = useTheme();
  return (
    <aside
      style={{
        width: 250,
        flexShrink: 0,
        position: 'sticky',
        top: theme.spacing(2),
        alignSelf: 'flex-start',
        maxHeight: `calc(100vh - ${theme.spacing(20)})`,
        overflowY: 'auto',
      }}
    >
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(2),
        padding: theme.spacing(2),
        borderRadius: theme.shape.borderRadius,
        border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
        backgroundColor: theme.palette.background.paper,
      }}
      >
        {sections.map((section, sectionIndex) => (
          <section
            key={section.id}
            aria-label={section.label}
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: theme.spacing(0.25),
              ...(sectionIndex > 0
                ? {
                    borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.05)}`,
                    paddingTop: theme.spacing(2),
                  }
                : {}),
            }}
          >
            <Typography
              component="h3"
              sx={{
                fontFamily: theme.typography.h1.fontFamily,
                fontWeight: 600,
                fontSize: 12,
                textTransform: 'uppercase',
                letterSpacing: '0.12em',
                color: 'text.secondary',
                paddingInline: 1,
              }}
            >
              {section.label}
            </Typography>
            {section.headerAction && (
              <Box sx={{
                paddingInline: 1,
                paddingBottom: 0.5,
              }}
              >
                {section.headerAction}
              </Box>
            )}
            {section.rows.map(row => (
              <FacetRowItem key={row.value} row={row} />
            ))}
          </section>
        ))}
      </div>
    </aside>
  );
};
