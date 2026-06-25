import { Extension } from '@tiptap/core';

const INDENT_STEP = 2; // rem per level
const MAX_INDENT = 16; // rem max

// ── Type augmentation ─────────────────────────────────────────────────────
declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    indentExtension: {
      indent: () => ReturnType;
      outdent: () => ReturnType;
    };
  }
}

type IndentProps = {
  editor: {
    isActive: (t: string) => boolean;
    getAttributes: (t: string) => Record<string, unknown>;
  };
  commands: { updateAttributes: (t: string, a: Record<string, unknown>) => boolean };
};

const IndentExtension = Extension.create({
  name: 'indentExtension',

  addOptions() {
    return { types: ['paragraph', 'heading', 'blockquote'] as string[] };
  },

  addGlobalAttributes() {
    return [
      {
        types: this.options.types,
        attributes: {
          indent: {
            default: 0,
            parseHTML: (element) => {
              const raw = element.style.paddingLeft;
              if (!raw) return 0;
              return Math.round(Number.parseFloat(raw) / INDENT_STEP);
            },
            renderHTML: (attributes) => {
              if (!attributes.indent) return {};
              return { style: `padding-left: ${(attributes.indent as number) * INDENT_STEP}rem` };
            },
          },
        },
      },
    ];
  },

  addCommands() {
    return {
      indent:
        () =>
          ({ editor, commands }: IndentProps) => {
            for (const type of this.options.types) {
              if (editor.isActive(type)) {
                const current = (editor.getAttributes(type).indent as number) ?? 0;
                if (current * INDENT_STEP >= MAX_INDENT) return false;
                return commands.updateAttributes(type, { indent: current + 1 });
              }
            }
            return false;
          },
      outdent:
        () =>
          ({ editor, commands }: IndentProps) => {
            for (const type of this.options.types) {
              if (editor.isActive(type)) {
                const current = (editor.getAttributes(type).indent as number) ?? 0;
                if (current <= 0) return false;
                return commands.updateAttributes(type, { indent: current - 1 });
              }
            }
            return false;
          },
    };
  },

  addKeyboardShortcuts() {
    const cmds = this.editor.commands as unknown as {
      indent: () => boolean;
      outdent: () => boolean;
    };
    return {
      'Tab': () => cmds.indent(),
      'Shift-Tab': () => cmds.outdent(),
    };
  },
});

export default IndentExtension;
