import { IconButton, Text } from '@filigran/design-system';
import { AddOutlined, CloseOutlined } from '@mui/icons-material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type ReviewView } from './reviewViews';

interface Props {
  builtIn: ReviewView[];
  saved: ReviewView[];
  activeId: string;
  counts: Record<string, number>;
  onSelect: (view: ReviewView) => void;
  onAdd: () => void;
  onDelete: (view: ReviewView) => void;
}

/**
 * The left rail of the review pages, after GitHub's dashboards: the built-in views of the role with
 * their counts, then the person's saved views and a "+" to save the current search and filters.
 * Same shell as the Active tab's filter sidebar, but one view at a time (a navigation, not facets).
 */
const ReviewViewsSidebar: FunctionComponent<Props> = ({ builtIn, saved, activeId, counts, onSelect, onAdd, onDelete }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const row = (view: ReviewView) => {
    const active = view.id === activeId;
    return (
      <li
        key={view.id}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(0.5),
          borderRadius: theme.shape.borderRadius,
          backgroundColor: active ? alpha(theme.palette.primary.main, 0.12) : undefined,
          boxShadow: active ? `inset 3px 0 0 ${theme.palette.primary.main}` : undefined,
        }}
      >
        <button
          type="button"
          aria-current={active ? 'page' : undefined}
          onClick={() => onSelect(view)}
          style={{
            all: 'unset',
            cursor: 'pointer',
            flex: 1,
            display: 'flex',
            alignItems: 'center',
            gap: theme.spacing(1),
            padding: theme.spacing(0.75, 1),
            minWidth: 0,
          }}
        >
          <Text
            variant={active ? 'content-base-bold' : 'content-base'}
            style={{
              flex: 1,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {view.saved ? view.label : t(view.label)}
          </Text>
          <Text variant="content-caption" className="text-default-secondary">{String(counts[view.id] ?? 0)}</Text>
        </button>
        {view.saved && (
          <IconButton
            size="sm"
            priority="tertiary"
            icon={<CloseOutlined fontSize="small" />}
            aria-label={t('Delete view {name}', { name: view.label })}
            onClick={() => onDelete(view)}
          />
        )}
      </li>
    );
  };

  const list = {
    listStyle: 'none',
    margin: 0,
    padding: 0,
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 2,
  };

  return (
    <aside style={{
      width: 250,
      flexShrink: 0,
      position: 'sticky',
      top: theme.spacing(2),
      alignSelf: 'flex-start',
    }}
    >
      <nav
        aria-label={t('Views')}
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
          padding: theme.spacing(2),
          borderRadius: theme.shape.borderRadius,
          border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
          backgroundColor: theme.palette.background.paper,
        }}
      >
        <ul style={list}>{builtIn.map(row)}</ul>
        <div style={{
          borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.05)}`,
          paddingTop: theme.spacing(1.5),
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(0.5),
        }}
        >
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            paddingLeft: theme.spacing(1),
          }}
          >
            <Text variant="content-compact-bold" className="text-default-secondary">{t('Views')}</Text>
            <IconButton size="sm" priority="tertiary" icon={<AddOutlined fontSize="small" />} aria-label={t('Save the current filters as a view')} onClick={onAdd} />
          </div>
          {saved.length === 0
            ? <Text variant="content-compact" className="text-default-secondary" style={{ paddingLeft: theme.spacing(1) }}>{t('No saved views')}</Text>
            : <ul style={list}>{saved.map(row)}</ul>}
        </div>
      </nav>
    </aside>
  );
};

export default ReviewViewsSidebar;
