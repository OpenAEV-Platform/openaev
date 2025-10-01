package io.openaev.rest.stream.ai;

import java.util.List;

public class AiPrompt {
  public static AiQueryModel generatePrompt(String prompt, AiConfig aiConfig) {
    AiQueryModel aiQueryModel = new AiQueryModel();
    aiQueryModel.setModel(aiConfig.getModel());
    aiQueryModel.setStream(true);
    AiQueryMessageModel aiQueryMessageModel = new AiQueryMessageModel();
    aiQueryMessageModel.setRole("user");
    aiQueryMessageModel.setContent(prompt);
    aiQueryModel.setMessages(List.of(aiQueryMessageModel));
    return aiQueryModel;
  }
}
