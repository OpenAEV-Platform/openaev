import type { Editor } from '@tiptap/core';

const typeChar = (
  editor: Editor,
  submittedText: string,
  onComplete: (value: string) => void,
) => {
  return new Promise((resolve) => {
    const chunkSize = 40;
    const lines: string[] = [];
    for (let i = 0; i < submittedText.length; i += chunkSize) {
      lines.push(submittedText.slice(i, i + chunkSize));
    }
    let index = 0;
    let buffer = '';

    const typeNext = () => {
      if (index < lines.length) {
        const line = lines[index];
        buffer += line;
        editor.commands.setContent(buffer);

        // Scroll the editor's contenteditable div to the bottom
        const editorEl = editor.view.dom as HTMLElement;
        if (editorEl) {
          editorEl.scrollTop = editorEl.scrollHeight;
        }
        index++;
        setTimeout(typeNext, 150);
        onComplete(buffer);
      } else {
        onComplete(buffer);
        resolve(submittedText);
      }
    };

    if (submittedText) {
      typeNext();
    }
  });
};

export default typeChar;
