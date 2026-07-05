package com.portalagent.mcp.tools;

import android.content.Context;

import com.portalagent.mcp.McpTool;
import com.portalagent.settings.WorkspaceAccessSettingsStore;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class UiToolPolicyTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(WorkspaceAccessSettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit();
    }

    @Test
    public void uiTapIsDeniedWhenAccessibilityCannotVerifyForegroundApp() throws Exception {
        UiTool tool = new UiTool(UiTool.Kind.TAP);

        assertDeniedByWorkspacePolicy(tool, new JSONObject().put("x", 1).put("y", 1));
    }

    @Test
    public void uiTreeIsDeniedWhenAccessibilityCannotVerifyForegroundApp() throws Exception {
        UiTreeTool tool = new UiTreeTool();

        assertDeniedByWorkspacePolicy(tool, new JSONObject());
    }

    private void assertDeniedByWorkspacePolicy(McpTool tool, JSONObject args) throws Exception {
        try {
            tool.call(args, context);
            Assert.fail("Expected UI tool to be denied before reaching tool implementation");
        } catch (SecurityException e) {
            Assert.assertTrue(e.getMessage().contains("Workspace access denied"));
            Assert.assertTrue(e.getMessage().contains("accessibility is not enabled"));
        }
    }
}
