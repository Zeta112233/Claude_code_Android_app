package com.portalagent.mcp.tools;

import android.content.Context;
import android.provider.Settings;

import com.portalagent.mcp.AdbCompanionClient;
import com.portalagent.settings.WorkspaceAccessSettingsStore;
import com.portalagent.settings.WorkspaceAccessSettingsStore.AppCapability;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

@RunWith(RobolectricTestRunner.class)
public class AdbToolTest {

    @Before
    public void setUp() {
        context().getSharedPreferences(WorkspaceAccessSettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit();
    }

    @Test
    public void tapForwardsCoordinatesToCompanion() throws Exception {
        Context context = context();
        new WorkspaceAccessSettingsStore(context).setAppAllowed("com.example.allowed", true);
        FakeClient client = new FakeClient(
            new JSONObject().put("ok", true).put("package", "com.example.allowed"),
            new JSONObject()
            .put("ok", true)
            .put("message", "tapped"));
        AdbTool tool = new AdbTool(AdbTool.Kind.TAP, client);

        String raw = tool.call(new JSONObject().put("x", 12).put("y", 34), context);

        Assert.assertEquals("current_activity", client.actions.get(0));
        Assert.assertEquals("tap", client.actions.get(1));
        Assert.assertEquals(12, client.arguments.get(1).optInt("x"));
        Assert.assertEquals(34, client.arguments.get(1).optInt("y"));
        Assert.assertTrue(raw.contains("tapped"));
    }

    @Test
    public void screenshotReturnsImageContentFromCompanion() throws Exception {
        Context context = context();
        new WorkspaceAccessSettingsStore(context).setAppAllowed("com.example.allowed", true);
        FakeClient client = new FakeClient(
            new JSONObject().put("ok", true).put("package", "com.example.allowed"),
            new JSONObject()
            .put("ok", true)
            .put("mime_type", "image/png")
            .put("image_base64", "abc123"));
        AdbTool tool = new AdbTool(AdbTool.Kind.SCREENSHOT, client);

        JSONArray content = new JSONArray(tool.call(new JSONObject(), context));
        JSONObject image = content.getJSONObject(0);

        Assert.assertEquals("current_activity", client.actions.get(0));
        Assert.assertEquals("screenshot", client.actions.get(1));
        Assert.assertEquals("image", image.optString("type"));
        Assert.assertEquals("abc123", image.optString("data"));
        Assert.assertEquals("image/png", image.optString("mimeType"));
    }

    @Test
    public void statusReportsDisconnectedCompanionWithoutThrowing() throws Exception {
        FakeClient client = new FakeClient(new java.io.IOException("connection refused"));
        AdbTool tool = new AdbTool(AdbTool.Kind.GET_STATUS, client);

        JSONArray content = new JSONArray(tool.call(new JSONObject(), context()));
        String text = content.getJSONObject(0).optString("text");

        Assert.assertTrue(text.contains("\"ok\": false"));
        Assert.assertTrue(text.contains("ADB Companion 未连接"));
    }

    @Test
    public void tapRequiresAdbCapabilityInAdditionToInteractionCapability() throws Exception {
        Context context = context();
        WorkspaceAccessSettingsStore store = new WorkspaceAccessSettingsStore(context);
        store.setAppCapabilityAllowed("com.example.allowed", AppCapability.INTERACT, true);
        FakeClient client = new FakeClient(
            new JSONObject().put("ok", true).put("package", "com.example.allowed"),
            new JSONObject().put("ok", true).put("message", "tapped"));
        AdbTool tool = new AdbTool(AdbTool.Kind.TAP, client);

        try {
            tool.call(new JSONObject().put("x", 12).put("y", 34), context);
            Assert.fail("Expected adb.tap to require adb capability");
        } catch (SecurityException e) {
            Assert.assertTrue(e.getMessage().contains("for adb"));
        }

        Assert.assertEquals(1, client.actions.size());
        Assert.assertEquals("current_activity", client.actions.get(0));
    }

    @Test
    public void tapAllowsFocusedAppWhenCurrentFocusIsInputMethod() throws Exception {
        Context context = context();
        Settings.Secure.putString(context.getContentResolver(),
            Settings.Secure.DEFAULT_INPUT_METHOD, "com.baidu.input/.ImeService");
        WorkspaceAccessSettingsStore store = new WorkspaceAccessSettingsStore(context);
        store.setAppCapabilityAllowed("com.example.allowed", AppCapability.INTERACT, true);
        store.setAppCapabilityAllowed("com.example.allowed", AppCapability.ADB, true);
        FakeClient client = new FakeClient(
            new JSONObject()
                .put("ok", true)
                .put("package", "com.baidu.input")
                .put("focused_app_package", "com.example.allowed"),
            new JSONObject().put("ok", true).put("message", "tapped"));
        AdbTool tool = new AdbTool(AdbTool.Kind.TAP, client);

        String raw = tool.call(new JSONObject().put("x", 12).put("y", 34), context);

        Assert.assertEquals("current_activity", client.actions.get(0));
        Assert.assertEquals("tap", client.actions.get(1));
        Assert.assertTrue(raw.contains("tapped"));
    }

    private Context context() {
        return RuntimeEnvironment.getApplication();
    }

    private static final class FakeClient extends AdbCompanionClient {
        private final Queue<JSONObject> responses = new ArrayDeque<>();
        private final Exception error;
        final List<String> actions = new ArrayList<>();
        final List<JSONObject> arguments = new ArrayList<>();

        FakeClient(JSONObject... responses) {
            super("http://127.0.0.1:1", 10);
            for (JSONObject response : responses) {
                this.responses.add(response);
            }
            this.error = null;
        }

        FakeClient(Exception error) {
            super("http://127.0.0.1:1", 10);
            this.error = error;
        }

        @Override
        public JSONObject call(String action, JSONObject arguments) throws Exception {
            this.actions.add(action);
            this.arguments.add(arguments);
            if (error != null) throw error;
            if (responses.isEmpty()) {
                return new JSONObject().put("ok", true);
            }
            return responses.remove();
        }
    }
}
