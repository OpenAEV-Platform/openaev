import '@tiptap/extension-text-style';

import { Extension } from '@tiptap/core';

/**
 * Custom tiptap extension that adds a `fontSize` attribute to the TextStyle mark.
 * Requires @tiptap/extension-text-style to be loaded alongside this extension.
 *
 * Usage:
 *   rteChain(editor)?.focus().setFontSize('20px').run()
 *   rteChain(editor)?.focus().unsetFontSize().run()
 */
const FontSize = Extension.create({
  name: 'fontSize',

  addOptions() {
    return { types: ['textStyle'] };
  },

  addGlobalAttributes() {
    return [
      {
        types: this.options.types,
        attributes: {
          fontSize: {
            default: null,
            parseHTML: element => element.style.fontSize || null,
            renderHTML: (attributes) => {
              if (!attributes.fontSize) return {};
              return { style: `font-size: ${attributes.fontSize}` };
            },
          },
        },
      },
    ];
  },

  addCommands() {
    return {
      setFontSize:
        (fontSize: string) =>
          ({ chain }) =>
            chain().setMark('textStyle', { fontSize }).run(),
      unsetFontSize:
        () =>
          ({ chain }) =>
            chain().setMark('textStyle', { fontSize: null }).removeEmptyTextStyle().run(),
    };
  },
});

export default FontSize;
