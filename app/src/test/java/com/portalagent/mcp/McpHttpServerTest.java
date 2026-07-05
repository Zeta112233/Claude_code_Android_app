package com.portalagent.mcp;

import org.junit.Assert;
import org.junit.Test;

public class McpHttpServerTest {

    @Test
    public void uiToolsUseBoundedTimeoutBudgets() {
        Assert.assertEquals(6000L, McpHttpServer.timeoutMsForTool("ui.get_accessibility_tree"));
        Assert.assertEquals(7000L, McpHttpServer.timeoutMsForTool("ui.tap"));
        Assert.assertEquals(9000L, McpHttpServer.timeoutMsForTool("ui.click_text"));
        Assert.assertEquals(12000L, McpHttpServer.timeoutMsForTool("ui.input_text"));
    }

    @Test
    public void slowMediaAndFileToolsUseLongerTimeoutBudgets() {
        Assert.assertEquals(5000L, McpHttpServer.timeoutMsForTool("screen.capture"));
        Assert.assertEquals(25000L, McpHttpServer.timeoutMsForTool("camera.take_photo"));
        Assert.assertEquals(15000L, McpHttpServer.timeoutMsForTool("file.read"));
    }

    @Test
    public void unknownToolsUseDefaultTimeoutBudget() {
        Assert.assertEquals(8000L, McpHttpServer.timeoutMsForTool("android.get_status"));
        Assert.assertEquals(8000L, McpHttpServer.timeoutMsForTool(null));
    }
}
