import { CloseOutlined, SearchOutlined } from '@mui/icons-material';
import { Box, IconButton, InputAdornment, TextField, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useEffect, useRef, useState } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type IconBarElement } from '../common/domains/IconBar-model';

interface Stat {
  id: string;
  label: string;
  value: number | string;
  color?: string;
}

interface Props {
  totalElements: number;
  domainElements: IconBarElement[];
  stats: Stat[];
  searchValue: string;
  onSearchChange: (value: string) => void;
  rightSlot?: ReactNode;
  bottomSlot?: ReactNode;
}

const SEARCH_DEBOUNCE_MS = 300;

const ThreatArsenalHero: FunctionComponent<Props> = ({
  totalElements,
  domainElements,
  stats,
  searchValue,
  onSearchChange,
  rightSlot,
  bottomSlot,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const primary = theme.palette.primary.main;
  const secondary = theme.palette.secondary.main;

  const [localSearch, setLocalSearch] = useState<string>(searchValue);
  const isInternalUpdate = useRef<boolean>(false);

  useEffect(() => {
    if (!isInternalUpdate.current && searchValue !== localSearch) {
      setLocalSearch(searchValue);
    }
    isInternalUpdate.current = false;
  }, [searchValue]);

  useEffect(() => {
    if (localSearch === searchValue) {
      return undefined;
    }
    const handle = window.setTimeout(() => {
      isInternalUpdate.current = true;
      onSearchChange(localSearch);
    }, SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(handle);
  }, [localSearch]);

  return (
    <Box
      component="section"
      aria-label={t('Threat Arsenal')}
      sx={{
        position: 'relative',
        borderRadius: 1,
        padding: {
          xs: 1.5,
          md: 2,
        },
        overflow: 'hidden',
        border: `1px solid ${theme.palette.divider}`,
        background: `linear-gradient(135deg, ${alpha(primary, 0.06)} 0%, ${alpha(secondary, 0.03)} 60%, transparent 100%)`,
      }}
    >
      <Box
        aria-hidden
        sx={{
          position: 'absolute',
          top: -100,
          right: -60,
          width: 220,
          height: 220,
          borderRadius: '50%',
          background: `radial-gradient(circle, ${alpha(primary, 0.14)} 0%, transparent 70%)`,
          pointerEvents: 'none',
        }}
      />

      <Box sx={{
        position: 'relative',
        display: 'flex',
        flexDirection: {
          xs: 'column',
          md: 'row',
        },
        gap: 1.5,
        alignItems: {
          xs: 'stretch',
          md: 'center',
        },
        justifyContent: 'space-between',
      }}
      >
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 1.25,
          minWidth: 0,
        }}
        >
          <Typography
            variant="h6"
            sx={{
              fontWeight: 600,
              margin: 0,
              fontSize: {
                xs: 18,
                md: 20,
              },
              letterSpacing: '-0.01em',
              whiteSpace: 'nowrap',
            }}
          >
            {t('Threat Arsenal')}
          </Typography>

          <Box sx={{
            display: 'flex',
            flexWrap: 'wrap',
            gap: 0.75,
          }}
          >
            <Box
              sx={{
                display: 'inline-flex',
                alignItems: 'baseline',
                gap: 0.5,
                paddingBlock: 0.25,
                paddingInline: 1,
                borderRadius: 1,
                border: `1px solid ${alpha(primary, 0.4)}`,
                backgroundColor: alpha(primary, 0.08),
              }}
            >
              <Typography sx={{
                fontWeight: 700,
                fontSize: 12,
                color: primary,
                fontVariantNumeric: 'tabular-nums',
              }}
              >
                {totalElements}
              </Typography>
              <Typography
                variant="caption"
                sx={{
                  color: 'text.secondary',
                  fontSize: 11,
                }}
              >
                {t('total actions')}
              </Typography>
            </Box>
            {stats.map(stat => (
              <Box
                key={stat.id}
                sx={{
                  display: 'inline-flex',
                  alignItems: 'baseline',
                  gap: 0.5,
                  paddingBlock: 0.25,
                  paddingInline: 1,
                  borderRadius: 1,
                  border: `1px solid ${theme.palette.divider}`,
                  backgroundColor: alpha(theme.palette.background.paper, 0.4),
                }}
              >
                <Typography sx={{
                  fontWeight: 700,
                  fontSize: 12,
                  color: stat.color ?? 'text.primary',
                  fontVariantNumeric: 'tabular-nums',
                }}
                >
                  {stat.value}
                </Typography>
                <Typography
                  variant="caption"
                  sx={{
                    color: 'text.secondary',
                    fontSize: 11,
                  }}
                >
                  {t(stat.label)}
                </Typography>
              </Box>
            ))}
          </Box>
        </Box>

        {rightSlot && (
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
            flexWrap: 'wrap',
            justifyContent: {
              xs: 'flex-start',
              md: 'flex-end',
            },
            flexShrink: 0,
          }}
          >
            {rightSlot}
          </Box>
        )}
      </Box>

      <Box sx={{
        position: 'relative',
        display: 'flex',
        gap: 1.25,
        marginTop: 1.25,
        flexWrap: {
          xs: 'wrap',
          md: 'nowrap',
        },
        alignItems: 'center',
      }}
      >
        <TextField
          placeholder={t('Search across the threat arsenal…')}
          value={localSearch}
          onChange={event => setLocalSearch(event.target.value)}
          variant="outlined"
          size="small"
          autoComplete="off"
          sx={{
            'flex': '0 0 auto',
            'width': {
              xs: '100%',
              md: 320,
            },
            '& .MuiOutlinedInput-root': {
              'borderRadius': 1,
              'backgroundColor': alpha(theme.palette.background.paper, 0.8),
              'transition': theme.transitions.create(['box-shadow', 'border-color']),
              '&:hover .MuiOutlinedInput-notchedOutline': { borderColor: alpha(primary, 0.6) },
              '&.Mui-focused': { boxShadow: `0 0 0 3px ${alpha(primary, 0.16)}` },
            },
            '& .MuiOutlinedInput-input': { paddingBlock: 0.75 },
          }}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchOutlined fontSize="small" sx={{ color: 'text.secondary' }} />
                </InputAdornment>
              ),
              endAdornment: localSearch
                ? (
                    <InputAdornment position="end">
                      <Tooltip title={t('Clear')}>
                        <IconButton
                          aria-label={t('Clear')}
                          size="small"
                          onClick={() => setLocalSearch('')}
                          sx={{ padding: 0.5 }}
                        >
                          <CloseOutlined fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </InputAdornment>
                  )
                : null,
            },
          }}
        />

        <Box
          role="tablist"
          aria-label={t('Domain filters')}
          sx={{
            'flex': 1,
            'minWidth': 0,
            'display': 'flex',
            'flexWrap': 'nowrap',
            'gap': 0.75,
            'overflowX': 'auto',
            'paddingBlock': 0.25,
            '&::-webkit-scrollbar': { height: 4 },
            '&::-webkit-scrollbar-thumb': {
              backgroundColor: theme.palette.action.focus,
              borderRadius: 2,
            },
          }}
        >
          {domainElements.map((element) => {
            const isSelected = element.color === 'success';
            const count = element.count ?? 0;
            return (
              <Tooltip key={element.name} title={`${t(element.name)} — ${count} ${t('actions')}`} enterDelay={400}>
                <Box
                  role="tab"
                  aria-selected={isSelected}
                  tabIndex={0}
                  onClick={element.function}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter' || event.key === ' ') {
                      event.preventDefault();
                      element.function();
                    }
                  }}
                  sx={{
                    'display': 'flex',
                    'alignItems': 'center',
                    'gap': 0.75,
                    'paddingBlock': 0.5,
                    'paddingInline': 1.25,
                    'borderRadius': 1,
                    'cursor': 'pointer',
                    'border': '1px solid',
                    'borderColor': isSelected ? primary : theme.palette.divider,
                    'backgroundColor': isSelected
                      ? alpha(primary, 0.16)
                      : alpha(theme.palette.background.paper, 0.5),
                    'color': isSelected ? primary : theme.palette.text.primary,
                    'whiteSpace': 'nowrap',
                    'flexShrink': 0,
                    'transition': theme.transitions.create(['background-color', 'border-color', 'color']),
                    '& svg': {
                      fontSize: '0.95rem',
                      color: isSelected ? primary : theme.palette.text.secondary,
                    },
                    '&:hover': {
                      backgroundColor: isSelected
                        ? alpha(primary, 0.22)
                        : alpha(primary, 0.08),
                      borderColor: isSelected ? primary : alpha(primary, 0.4),
                    },
                    '&:focus-visible': {
                      outline: `2px solid ${primary}`,
                      outlineOffset: 2,
                    },
                  }}
                >
                  {element.icon()}
                  <Typography
                    variant="body2"
                    sx={{
                      fontWeight: isSelected ? 600 : 500,
                      color: 'inherit',
                      fontSize: 12.5,
                    }}
                  >
                    {t(element.name)}
                  </Typography>
                  <Box
                    component="span"
                    sx={{
                      paddingInline: 0.625,
                      paddingBlock: 0.05,
                      borderRadius: 0.5,
                      fontSize: 10.5,
                      fontWeight: 600,
                      fontVariantNumeric: 'tabular-nums',
                      backgroundColor: isSelected
                        ? alpha(primary, 0.24)
                        : alpha(theme.palette.text.primary, 0.08),
                      color: isSelected ? primary : theme.palette.text.secondary,
                      minWidth: 20,
                      textAlign: 'center',
                    }}
                  >
                    {count}
                  </Box>
                </Box>
              </Tooltip>
            );
          })}
        </Box>
      </Box>

      {bottomSlot && (
        <Box sx={{
          position: 'relative',
          marginTop: 1.25,
        }}
        >
          {bottomSlot}
        </Box>
      )}
    </Box>
  );
};

export default ThreatArsenalHero;
