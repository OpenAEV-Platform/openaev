// ── Public interface — library-agnostic ────────────────────────────────────
export interface RichTextEditorProps {
  /** Current HTML content (controlled). */
  value?: string;
  /** Called on every change with the updated HTML string. */
  onChange?: (html: string) => void;
  onBlur?: () => void;
  disabled?: boolean;
  /**
   * Called once when the editor is ready.
   * Receives the internal editor instance (opaque — avoid depending on it).
   */
  onReady?: (editor: unknown) => void;
}
