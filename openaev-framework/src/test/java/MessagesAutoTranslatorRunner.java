public class MessagesAutoTranslatorRunner {
  public static void main(String[] args) throws Exception {
    String apiKey = System.getenv("DEEPL_API_KEY");
    if (apiKey == null) {
      throw new IllegalStateException("DEEPL_API_KEY not set");
    }

    MessagesAutoTranslator tool = new MessagesAutoTranslator(
        apiKey,
        "messages",                 // base name
        "EN",                       // source language
        List.of("FR", "DE")         // target languages
    );

    tool.run();
  }
}
