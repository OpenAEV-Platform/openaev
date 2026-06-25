import {
  ArrowBack as ArrowBackIcon,
  ArrowDownward as ArrowDownwardIcon,
  ArrowForward as ArrowForwardIcon,
  ArrowUpward as ArrowUpwardIcon,
  CallMerge as CallMergeIcon,
  CallSplit as CallSplitIcon,
  DeleteForever as DeleteForeverIcon,
  DeleteOutline as DeleteOutlineIcon,
  TableRows as TableRowsIcon,
  ViewColumn as ViewColumnIcon,
} from '@mui/icons-material';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import ListSubheader from '@mui/material/ListSubheader';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Tooltip from '@mui/material/Tooltip';
import { type useEditor } from '@tiptap/react';
import { type FC, useEffect, useRef, useState } from 'react';

import { useFormatter } from '../../i18n';

interface RteChain {
  focus(): RteChain;
  run(): boolean;
  addRowBefore(): RteChain;
  addRowAfter(): RteChain;
  deleteRow(): RteChain;
  addColumnBefore(): RteChain;
  addColumnAfter(): RteChain;
  deleteColumn(): RteChain;
  mergeOrSplit(): RteChain;
  mergeCells(): RteChain;
  splitCell(): RteChain;
  toggleHeaderRow(): RteChain;
  toggleHeaderColumn(): RteChain;
  deleteTable(): RteChain;
}

type EditorInstance = ReturnType<typeof useEditor>;

const rteChain = (ed: EditorInstance): RteChain | undefined =>
  ed ? (ed.chain() as unknown as RteChain) : undefined;

interface TableCellMenuProps { editor: EditorInstance }

interface MenuPos {
  x: number;
  y: number;
}
interface FloatingPos {
  top: number;
  left: number;
}

// Detect ProseMirror CellSelection by duck-typing ($anchorCell exists)
const isCellSelection = (editor: EditorInstance) =>
  editor ? '$anchorCell' in editor.state.selection : false;

const TableCellMenu: FC<TableCellMenuProps> = ({ editor }) => {
  const { t } = useFormatter();
  const [menuPos, setMenuPos] = useState<MenuPos | null>(null);
  const [floatingPos, setFloatingPos] = useState<FloatingPos | null>(null);
  // Track whether selection is multi-cell so we keep floatingPos visible
  const multiCellRef = useRef(false);

  // ── Left-click menu on single cell ──────────────────────────────────────
  useEffect(() => {
    if (!editor) return;

    const handleClick = (e: MouseEvent) => {
      // Don't open menu when a multi-cell selection is active
      if (isCellSelection(editor)) return;

      let target = e.target as HTMLElement | null;
      while (target && target.tagName !== 'TD' && target.tagName !== 'TH') {
        if (target === editor.view.dom) return;
        target = target.parentElement;
      }
      if (!target) return;

      const rect = target.getBoundingClientRect();
      setMenuPos({
        x: rect.left,
        y: rect.bottom,
      });
    };

    let dom: HTMLElement | null = null;

    const attach = () => {
      if (editor.isDestroyed) return;
      try {
        dom = editor.view.dom as HTMLElement;
        dom.addEventListener('click', handleClick);
      } catch {
        // view not ready yet
      }
    };

    // attach immediately if view is already available, otherwise wait for 'create'
    try {
      editor.view.dom; // probe — throws if not ready
      attach();
    } catch {
      editor.on('create', attach);
    }

    return () => {
      editor.off('create', attach);
      dom?.removeEventListener('click', handleClick);
    };
  }, [editor]);

  // ── Floating merge bar (appears on multi-cell CellSelection) ────────────
  useEffect(() => {
    if (!editor) return;

    const update = () => {
      if (!isCellSelection(editor)) {
        multiCellRef.current = false;
        setFloatingPos(null);
        return;
      }

      multiCellRef.current = true;

      try {
        const sel = editor.state.selection as unknown as { $anchorCell: { pos: number } };
        const domInfo = editor.view.domAtPos(sel.$anchorCell.pos + 1);
        let node = (domInfo.node instanceof Text ? domInfo.node.parentElement : domInfo.node) as HTMLElement | null;
        while (node && node.tagName !== 'TD' && node.tagName !== 'TH') {
          node = node.parentElement;
        }
        if (!node) { setFloatingPos(null); return; }

        const rect = node.getBoundingClientRect();
        setFloatingPos({
          top: rect.top - 40,
          left: rect.left,
        });
      } catch {
        setFloatingPos(null);
      }
    };

    editor.on('selectionUpdate', update);
    return () => { editor.off('selectionUpdate', update); };
  }, [editor]);

  const closeMenu = () => setMenuPos(null);
  const run = (fn: () => void) => () => { fn(); closeMenu(); };

  const iconSx = { fontSize: '1rem' };

  return (
    <>
      {/* ── Floating merge/split bar for multi-cell selection ── */}
      {floatingPos && (
        <Paper
          elevation={4}
          onMouseDown={e => e.preventDefault()}
          sx={{
            position: 'fixed',
            top: floatingPos.top,
            left: floatingPos.left,
            zIndex: 1400,
            display: 'flex',
            alignItems: 'center',
            gap: '2px',
            px: '6px',
            height: 32,
            borderRadius: '6px',
            userSelect: 'none',
          }}
        >
          <Tooltip title={t('Merge selected cells')} placement="top" arrow>
            <IconButton
              size="small"
              sx={{
                'p': '3px',
                'color': 'text.secondary',
                '&:hover': { color: 'text.primary' },
              }}
              onClick={() => rteChain(editor)?.focus().mergeCells().run()}
            >
              <CallMergeIcon sx={iconSx} />
            </IconButton>
          </Tooltip>
          <Tooltip title={t('Split cell')} placement="top" arrow>
            <IconButton
              size="small"
              sx={{
                'p': '3px',
                'color': 'text.secondary',
                '&:hover': { color: 'text.primary' },
              }}
              onClick={() => rteChain(editor)?.focus().splitCell().run()}
            >
              <CallSplitIcon sx={iconSx} />
            </IconButton>
          </Tooltip>
        </Paper>
      )}

      {/* ── Right-click context menu ── */}
      <Menu
        open={Boolean(menuPos)}
        onClose={closeMenu}
        anchorReference="anchorPosition"
        anchorPosition={menuPos ? {
          top: menuPos.y,
          left: menuPos.x,
        } : undefined}
        slotProps={{ paper: { sx: { minWidth: 220 } } }}
      >
        {/* ── Row ── */}
        <ListSubheader sx={{
          lineHeight: '28px',
          fontSize: '0.65rem',
          fontWeight: 700,
          letterSpacing: '0.08em',
          textTransform: 'uppercase',
        }}
        >
          {t('Row')}
        </ListSubheader>
        <MenuItem dense onClick={run(() => rteChain(editor)?.focus().addRowBefore().run())}>
          <ListItemIcon><ArrowUpwardIcon sx={iconSx} /></ListItemIcon>
          <ListItemText primary={t('Insert row above')} slotProps={{ primary: { sx: { fontSize: '0.82rem' } } }} />
        </MenuItem>
        <MenuItem dense onClick={run(() => rteChain(editor)?.focus().addRowAfter().run())}>
          <ListItemIcon><ArrowDownwardIcon sx={iconSx} /></ListItemIcon>
          <ListItemText primary={t('Insert row below')} slotProps={{ primary: { sx: { fontSize: '0.82rem' } } }} />
        </MenuItem>
        <MenuItem dense onClick={run(() => rteChain(editor)?.focus().deleteRow().run())} sx={{ color: 'error.main' }}>
          <ListItemIcon>
            <DeleteOutlineIcon sx={{
              ...iconSx,
              color: 'error.main',
            }}
            />
          </ListItemIcon>
          <ListItemText primary={t('Delete row')} slotProps={{ primary: { sx: { fontSize: '0.82rem' } } }} />
        </MenuItem>

        <Divider />

        {/* ── Column ── */}
        <ListSubheader sx={{
          lineHeight: '28px',
          fontSize: '0.65rem',
          fontWeight: 700,
          letterSpacing: '0.08em',
          textTransform: 'uppercase',
        }}
        >
          {t('Column')}
        </ListSubheader>
        <MenuItem dense onClick={run(() => rteChain(editor)?.focus().addColumnBefore().run())}>
          <ListItemIcon><ArrowBackIcon sx={iconSx} /></ListItemIcon>
          <ListItemText primary={t('Insert column left')} slotProps={{ primary: { sx: { fontSize: '0.82rem' } } }} />
        </MenuItem>
        <MenuItem dense onClick={run(() => rteChain(editor)?.focus().addColumnAfter().run())}>
          <ListItemIcon><ArrowForwardIcon sx={iconSx} /></ListItemIcon>
          <ListItemText primary={t('Insert column right')} slotProps={{ primary: { sx: { fontSize: '0.82rem' } } }} />
        </MenuItem>
        <MenuItem dense onClick={run(() => rteChain(editor)?.focus().deleteColumn().run())} sx={{ color: 'error.main' }}>
          <ListItemIcon>
            <DeleteOutlineIcon sx={{
              ...iconSx,
              color: 'error.main',
            }}
            />
          </ListItemIcon>
          <ListItemText primary={t('Delete column')} slotProps={{ primary: { sx: { fontSize: '0.82rem' } } }} />
        </MenuItem>

        <Divider />

        {/* ── Cells ── */}
        <ListSubheader sx={{
          lineHeight: '28px',
          fontSize: '0.65rem',
          fontWeight: 700,
          letterSpacing: '0.08em',
          textTransform: 'uppercase',
        }}
        >
          {t('Cells')}
        </ListSubheader>
        <MenuItem dense onClick={run(() => rteChain(editor)?.focus().mergeCells().run())}>
          <ListItemIcon><CallMergeIcon sx={iconSx} /></ListItemIcon>
          <ListItemText primary={t('Merge cells')} slotProps={{ primary: { sx: { fontSize: '0.82rem' } } }} />
        </MenuItem>
        <MenuItem dense onClick={run(() => rteChain(editor)?.focus().splitCell().run())}>
          <ListItemIcon><CallSplitIcon sx={iconSx} /></ListItemIcon>
          <ListItemText primary={t('Split cell')} slotProps={{ primary: { sx: { fontSize: '0.82rem' } } }} />
        </MenuItem>
        <MenuItem dense onClick={run(() => rteChain(editor)?.focus().toggleHeaderRow().run())}>
          <ListItemIcon><TableRowsIcon sx={iconSx} /></ListItemIcon>
          <ListItemText primary={t('Toggle header row')} slotProps={{ primary: { sx: { fontSize: '0.82rem' } } }} />
        </MenuItem>
        <MenuItem dense onClick={run(() => rteChain(editor)?.focus().toggleHeaderColumn().run())}>
          <ListItemIcon><ViewColumnIcon sx={iconSx} /></ListItemIcon>
          <ListItemText primary={t('Toggle header column')} slotProps={{ primary: { sx: { fontSize: '0.82rem' } } }} />
        </MenuItem>

        <Divider />

        <MenuItem dense onClick={run(() => rteChain(editor)?.focus().deleteTable().run())} sx={{ color: 'error.main' }}>
          <ListItemIcon>
            <DeleteForeverIcon sx={{
              ...iconSx,
              color: 'error.main',
            }}
            />
          </ListItemIcon>
          <ListItemText primary={t('Delete table')} slotProps={{ primary: { sx: { fontSize: '0.82rem' } } }} />
        </MenuItem>
      </Menu>
    </>
  );
};

export default TableCellMenu;
