package com.aibot.travelplanner.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("ai")
public class AiConnectionService {

    private final ChatClient chatClient;

    public AiConnectionService(ChatModel chatModel) {
        this.chatClient = ChatClient.create(chatModel);
    }

    public String testConnection() {
        String reply;
        try {
            // A fixed, small prompt checks the connection without sending trip data.
            reply = chatClient.prompt()
                    .user("Reply with exactly: Travel planner AI connection is working.")
                    .call()
                    .content();
        } catch (RuntimeException exception) {
            // Do not expose provider payloads, credentials, or internal exception messages.
            throw new AiUnavailableException();
        }
        if (reply == null || reply.isBlank()) {
            throw new AiUnavailableException();
        }
        return reply.trim();
    }
}
