import { IconButton, Menu, MenuContent, MenuItem, MenuSub, MenuSubContent, MenuSubTrigger, MenuTrigger, Text } from '@filigran/design-system';
import { ArrowLeft, ArrowRight, TuneOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../i18n';

interface Props {
  /** Zero-based page. */
  page: number;
  rowsPerPage: number;
  count: number;
  onPageChange: (page: number) => void;
  /** With it, the settings menu offers "Rows per page". */
  onRowsPerPageChange?: (rowsPerPage: number) => void;
  rowsPerPageOptions?: number[];
  /** With it, the settings menu offers "Reset table". */
  onReset?: () => void;
}

/**
 * The pagination of the design system Figma (Pagination 2814:16367): a 36px bar with the previous and
 * next arrows around the range ("26-50/123"), and a settings button whose menu holds "Reset table" and
 * "Rows per page" (10, 25, 50, 100). Tokens as in the Figma: elevation border, small radius, highlight
 * icons, Content/base text, the total in the secondary colour.
 *
 * FDS-WORKAROUND: the library has no Pagination component yet (designed in Figma, not in code) — remove
 * when the library ships it — see the DS ROADMAP.
 */
const FdsPagination: FunctionComponent<Props> = ({
  page,
  rowsPerPage,
  count,
  onPageChange,
  onRowsPerPageChange,
  rowsPerPageOptions = [10, 25, 50, 100],
  onReset,
}) => {
  const { t } = useFormatter();
  const lastPage = Math.max(0, Math.ceil(count / rowsPerPage) - 1);
  const from = count === 0 ? 0 : page * rowsPerPage + 1;
  const to = Math.min(count, (page + 1) * rowsPerPage);
  const hasSettings = !!onReset || !!onRowsPerPageChange;
  const arrowStyle = { color: 'var(--icon-highlight)' };

  return (
    <nav
      aria-label={t('Pagination')}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        height: 36,
        boxSizing: 'border-box',
        border: '1px solid var(--border-elevation-default)',
        borderRadius: 'var(--radius-sm)',
        overflow: 'hidden',
      }}
    >
      <IconButton
        size="sm"
        priority="tertiary"
        aria-label={t('Previous page')}
        disabled={page <= 0}
        onClick={() => onPageChange(page - 1)}
        icon={<ArrowLeft fontSize="small" style={arrowStyle} />}
      />
      <Text
        variant="content-base"
        aria-live="polite"
        style={{
          padding: '0 4px',
          whiteSpace: 'nowrap',
        }}
      >
        {`${from}-${to}`}
        <span className="text-default-secondary">{`/${count}`}</span>
      </Text>
      <IconButton
        size="sm"
        priority="tertiary"
        aria-label={t('Next page')}
        disabled={page >= lastPage}
        onClick={() => onPageChange(page + 1)}
        icon={<ArrowRight fontSize="small" style={arrowStyle} />}
      />
      {hasSettings && (
        <Menu>
          <MenuTrigger asChild>
            <IconButton
              size="sm"
              priority="tertiary"
              aria-label={t('Table settings')}
              icon={<TuneOutlined fontSize="small" style={arrowStyle} />}
              style={{
                borderLeft: '1px solid var(--border-elevation-default)',
                borderRadius: 0,
                height: 34,
                width: 36,
              }}
            />
          </MenuTrigger>
          <MenuContent align="start">
            {onReset && <MenuItem onSelect={onReset}>{t('Reset table')}</MenuItem>}
            {onRowsPerPageChange && (
              <MenuSub>
                <MenuSubTrigger>{t('Rows per page')}</MenuSubTrigger>
                <MenuSubContent>
                  {rowsPerPageOptions.map(option => (
                    <MenuItem key={option} selected={option === rowsPerPage} onSelect={() => onRowsPerPageChange(option)}>
                      {String(option)}
                    </MenuItem>
                  ))}
                </MenuSubContent>
              </MenuSub>
            )}
          </MenuContent>
        </Menu>
      )}
    </nav>
  );
};

export default FdsPagination;
