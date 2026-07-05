package com.portalagent.chat;

import org.junit.Assert;
import org.junit.Test;

public class ChatMessageTest {

    @Test
    public void assistantStreamingMarksOutputIncomplete() {
        ChatMessage complete = ChatMessage.assistant("done");
        ChatMessage streaming = ChatMessage.assistantStreaming("partial");

        Assert.assertTrue(complete.outputComplete);
        Assert.assertFalse(streaming.outputComplete);
    }

    @Test
    public void toolResultDisplayDetailIsBoundedForLargePayloads() {
        StringBuilder full = new StringBuilder();
        for (int i = 0; i < 20_000; i++) {
            full.append('x');
        }

        ChatMessage message = ChatMessage.toolResult("screen.capture", "image/jpeg", full.toString());

        Assert.assertTrue(message.toolDetail.length() < full.length());
        Assert.assertTrue(message.toolDetail.contains("内容过长"));
    }
}
