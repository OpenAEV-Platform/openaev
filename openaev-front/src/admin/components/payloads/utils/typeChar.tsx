import { type RichTextEditorAdapter } from '@filigran/rich-text-editor';

const typeChar = (
  editor: RichTextEditorAdapter,
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
        editor.setContent(buffer);
        editor.scrollToBottom();
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
