import {
  IconButton,
  Menu,
  MenuContent,
  MenuItem,
  MenuSub,
  MenuSubContent,
  MenuSubTrigger,
  MenuTrigger,
  Text,
} from '@filigran/design-system';
import { ArrowLeft, ArrowRight, TuneOutlined } from '@mui/icons-material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../i18n';

// FDS-WORKAROUND: Figma DS Pagination (Design_System_2026 2814:16367) built from library parts — remove when the library ships Pagination — see DS Figma 2814:16367

interface Props {
  /** Zero-based page shown. */
  page: number;
  rowsPerPage: number;
  /** Total number of rows. */
  count: number;
  onPageChange: (page: number) => void;
  /** With options, the tune menu offers "Rows per page" and "Reset table". Without, there is no tune button. */
  rowsPerPageOptions?: number[];
  onRowsPerPageChange?: (rowsPerPage: number) => void;
}

/** "26-50 / 123" between previous and next, and the table settings behind the tune button. */
const FdsPagination: FunctionComponent<Props> = ({ page, rowsPerPage, count, onPageChange, rowsPerPageOptions, onRowsPerPageChange }) => {
  const { t } = useFormatter();
  const lastPage = Math.max(0, Math.ceil(count / rowsPerPage) - 1);
  const from = count === 0 ? 0 : page * rowsPerPage + 1;
  const to = Math.min(count, (page + 1) * rowsPerPage);
  const hasSettings = !!onRowsPerPageChange && (rowsPerPageOptions?.length ?? 0) > 1;

  const changeRowsPerPage = (next: number) => {
    onRowsPerPageChange?.(next);
    onPageChange(0);
  };

  return (
    <div style={{
      display: 'inline-flex',
      alignItems: 'center',
      border: '1px solid var(--border-elevation-default)',
      borderRadius: 4,
    }}
    >
      <IconButton
        priority="tertiary"
        aria-label={t('Previous page')}
        icon={<ArrowLeft />}
        disabled={page <= 0}
        onClick={() => onPageChange(page - 1)}
      />
      <Text as="span" variant="content-base">{`${from}-${to}`}</Text>
      <Text as="span" variant="content-base" className="text-default-secondary">{`/${count}`}</Text>
      <IconButton
        priority="tertiary"
        aria-label={t('Next page')}
        icon={<ArrowRight />}
        disabled={page >= lastPage}
        onClick={() => onPageChange(page + 1)}
      />
      {hasSettings && (
        <Menu modal={false}>
          <MenuTrigger asChild>
            <IconButton priority="tertiary" aria-label={t('Table settings')} icon={<TuneOutlined />} />
          </MenuTrigger>
          <MenuContent align="start">
            <MenuItem onSelect={() => changeRowsPerPage(rowsPerPageOptions?.[0] ?? rowsPerPage)}>{t('Reset table')}</MenuItem>
            <MenuSub>
              <MenuSubTrigger>{t('Rows per page')}</MenuSubTrigger>
              <MenuSubContent>
                {rowsPerPageOptions?.map(option => (
                  <MenuItem key={option} selected={option === rowsPerPage} onSelect={() => changeRowsPerPage(option)}>
                    {String(option)}
                  </MenuItem>
                ))}
              </MenuSubContent>
            </MenuSub>
          </MenuContent>
        </Menu>
      )}
    </div>
  );
};

/** The pagination at the right end of its own row, under a table or list. */
export const FdsPaginationRow: FunctionComponent<Props> = props => (
  <div style={{
    display: 'flex',
    justifyContent: 'flex-end',
    padding: '8px 0',
  }}
  >
    <FdsPagination {...props} />
  </div>
);

export default FdsPagination;
