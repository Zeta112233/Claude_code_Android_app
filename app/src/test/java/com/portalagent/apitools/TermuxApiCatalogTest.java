package com.portalagent.apitools;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

public class TermuxApiCatalogTest {

    @Test
    public void catalogListsEveryEmbeddedReceiverMethod() throws Exception {
        String receiver = readSource("src/main/java/com/termux/api/TermuxApiReceiver.java");
        Set<String> methods = new HashSet<>();

        Assert.assertEquals(45, TermuxApiCatalog.entries().size());
        for (TermuxApiCatalog.Entry entry : TermuxApiCatalog.entries()) {
            Assert.assertTrue("Duplicate API method: " + entry.apiMethod, methods.add(entry.apiMethod));
            Assert.assertTrue("Receiver missing API method: " + entry.apiMethod,
                receiver.contains("case \"" + entry.apiMethod + "\":"));
        }
    }

    @Test
    public void catalogExposesBridgePolicyAndReadOnlyHttpEndpoints() {
        TermuxApiCatalog.Entry battery = find("BatteryStatus");
        TermuxApiCatalog.Entry smsSend = find("SmsSend");

        Assert.assertEquals("/battery", battery.httpEndpoint);
        Assert.assertEquals("http", battery.bridge);
        Assert.assertEquals("termux-battery-status", battery.command);
        Assert.assertNull(smsSend.httpEndpoint);
        Assert.assertEquals("sensitive_write", smsSend.risk);
        Assert.assertTrue(smsSend.permissions.contains("android.permission.SEND_SMS"));
    }

    @Test
    public void httpBridgeSourcePublishesCatalogEndpoint() throws Exception {
        String source = readSource("src/main/java/com/portalagent/setup/ApiHttpBridgeServer.java");

        Assert.assertTrue(source.contains("\"/termux-api/catalog\""));
        Assert.assertTrue(source.contains("TermuxApiCatalog.toJsonString()"));
        Assert.assertTrue(source.contains("TermuxApiCatalog.httpEndpointsJsonArray()"));
    }

    private static String readSource(String relativePath) throws Exception {
        File file = resolveProjectFile(relativePath);
        Assert.assertTrue("Missing source file: " + file.getAbsolutePath(), file.isFile());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static File resolveProjectFile(String relativePath) {
        File file = new File(relativePath);
        if (!file.isFile()) {
            file = new File("app/" + relativePath);
        }
        return file;
    }

    private static TermuxApiCatalog.Entry find(String apiMethod) {
        for (TermuxApiCatalog.Entry entry : TermuxApiCatalog.entries()) {
            if (entry.apiMethod.equals(apiMethod)) return entry;
        }
        Assert.fail("Missing API method: " + apiMethod);
        return null;
    }
}
