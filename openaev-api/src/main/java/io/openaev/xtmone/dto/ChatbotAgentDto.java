package io.openaev.xtmone.dto;

/**
 * Lightweight view of a chatbot agent exposed to the frontend by {@link
 * io.openaev.xtmone.XtmOneProxyApi}.
 */
public record ChatbotAgentDto(String id, String name, String slug, String description) {}
