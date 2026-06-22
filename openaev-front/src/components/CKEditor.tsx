import { lazy, Suspense } from 'react';
import type { Editor } from '@tiptap/core';

import Loader from './Loader';
import type { RichTextEditorProps } from './common/rich_text_editor';

// ── CKEditor-compatible public API ─────────────────────────────────────────
// Kept for backward compatibility with existing consumers.
export type FakeEditorInstance = { getData: () => string };
export type CKEditorOnChangeFn = (event: unknown, editor: FakeEditorInstance) => void;

export interface CKEditorProps {
  data?: string;
  onChange?: CKEditorOnChangeFn;
  onBlur?: () => void;
  disabled?: boolean;
  /** Called when the editor is ready. Receives the TipTap Editor instance. */
  onReady?: (editor: Editor) => void;
  /** Legacy CKEditor props — ignored */
  id?: string;
  disableWatchdog?: boolean;
  toolbarDropdownSize?: string;
}

// ── Lazy-loaded implementation ─────────────────────────────────────────────
const LazyRichTextEditor = lazy(() => import('./common/rich_text_editor/RichTextEditor'));

/**
 * Backward-compatible wrapper around RichTextEditor.
 * Adapts the CKEditor v5 API (onChange(event, editor)) to the generic
 * RichTextEditor API (onChange(html)).
 */
const CKEditor = ({ data, onChange, onBlur, disabled, onReady, ..._ }: CKEditorProps) => {
  const rteProps: RichTextEditorProps = {
    value: data,
    onChange: onChange
      ? (html: string) => onChange(null, { getData: () => html })
      : undefined,
    onBlur,
    disabled,
    onReady: onReady as RichTextEditorProps['onReady'],
  };

  return (
    <Suspense fallback={<Loader variant="inElement" />}>
      <LazyRichTextEditor {...rteProps} />
    </Suspense>
  );
};

export default CKEditor;
