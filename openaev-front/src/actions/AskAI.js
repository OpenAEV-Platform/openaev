import { fetchEventSource } from '@microsoft/fetch-event-source';

import { buildUri } from '../utils/Action';

/**
 * Ask execution of a specific prompt type
 * Usage example
 *   const [textAi, setTextAi] = useState('');
 *   const ask = async () => askAI('EMAIL', 'Question?', (data) => setTextAi(data));
 */
export const askAI = async (uri, input, eventCallback) => {
  let aiContent = '';
  return new Promise((resolve, reject) => {
    fetchEventSource(buildUri(`/api${uri}`), {
      method: 'POST',
      body: JSON.stringify(input),
      headers: {
        'Accept': 'text/event-stream',
        'Content-Type': 'application/json',
      },
      onmessage(event) {
        const data = JSON.parse(event.data);
        aiContent += data.chunk_content;
        eventCallback(aiContent);
      },
      onclose() {
        resolve();
      },
      onerror(err) {
        reject(err);
      },
    });
  });
};

export const aiFixSpelling = async (content, format, eventCallback) => {
  return askAI('/ai/fix_spelling', {
    ai_content: content,
    ai_format: format,
  }, eventCallback);
};

export const aiMakeShorter = async (content, format, eventCallback) => {
  return askAI('/ai/make_shorter', {
    ai_content: content,
    ai_format: format,
  }, eventCallback);
};

export const aiMakeLonger = async (content, format, eventCallback) => {
  return askAI('/ai/make_longer', {
    ai_content: content,
    ai_format: format,
  }, eventCallback);
};

export const aiChangeTone = async (content, tone, format, eventCallback) => {
  return askAI('/ai/change_tone', {
    ai_content: content,
    ai_tone: tone,
    ai_format: format,
  }, eventCallback);
};

export const aiSummarize = async (content, format, eventCallback) => {
  return askAI('/ai/summarize', {
    ai_content: content,
    ai_format: format,
  }, eventCallback);
};

export const aiExplain = async (content, eventCallback) => {
  return askAI('/ai/explain', { ai_content: content }, eventCallback);
};

export const aiGenMessage = async (context, input, paragraphs, tone, sender, recipient, format, eventCallback) => {
  return askAI(
    '/ai/generate_message',
    {
      ai_context: context,
      ai_input: input,
      ai_paragraphs: paragraphs,
      ai_tone: tone,
      ai_sender: sender,
      ai_recipient: recipient,
      ai_format: format,
    },
    eventCallback,
  );
};

export const aiGenSubject = async (context, input, paragraphs, tone, sender, recipient, format, eventCallback) => {
  return askAI(
    '/ai/generate_subject',
    {
      ai_context: context,
      ai_input: input,
      ai_paragraphs: paragraphs,
      ai_tone: tone,
      ai_sender: sender,
      ai_recipient: recipient,
      ai_format: format,
    },
    eventCallback,
  );
};

export const aiGenMedia = async (context, input, paragraphs, tone, author, format, eventCallback) => {
  return askAI(
    '/ai/generate_media',
    {
      ai_context: context,
      ai_input: input,
      ai_paragraphs: paragraphs,
      ai_tone: tone,
      ai_author: author,
      ai_format: format,
    },
    eventCallback,
  );
};
