package com.portalagent.mcp.tools;

import android.content.Context;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import com.portalagent.mcp.McpAccessibilityService;
import com.portalagent.mcp.McpTool;
import com.portalagent.mcp.WorkspaceAccessPolicy;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * ui.get_accessibility_tree — reads the current screen's UI node tree.
 *
 * Two modes:
 *   "flat"  (default) — returns a flat list of "interesting" nodes
 *                        (nodes with text, content-desc, or that are clickable/editable).
 *                        Much more concise and Claude-friendly.
 *   "tree"             — full hierarchical JSON tree (can be very large).
 *
 * max_depth limits tree traversal depth (default 20). max_nodes and a short
 * time budget keep large browser/WebView pages from stalling the agent turn.
 */
public class UiTreeTool implements McpTool {

    private static final int DEFAULT_MAX_DEPTH = 20;
    private static final int DEFAULT_MAX_NODES = 400;
    private static final int HARD_MAX_NODES = 1200;
    private static final int MAX_TEXT_CHARS = 500;
    private static final long DEFAULT_TIMEOUT_MS = 2500L;

    @Override public String getName() { return "ui.get_accessibility_tree"; }

    @Override public String getDescription() {
        return "Read the current screen's UI element tree via AccessibilityService. " +
               "Use mode='flat' (default) for a compact list of interactive elements, " +
               "or mode='tree' for the full hierarchy. " +
               "Requires accessibility permission.";
    }

    @Override public String getInputSchema() {
        return "{\"type\":\"object\",\"properties\":{" +
            "\"task_id\":{\"type\":\"string\"}," +
            "\"mode\":{\"type\":\"string\",\"enum\":[\"flat\",\"tree\"],\"default\":\"flat\"," +
                "\"description\":\"flat=compact list of interactive nodes, tree=full hierarchy\"}," +
            "\"max_depth\":{\"type\":\"integer\",\"default\":20," +
                "\"description\":\"Max traversal depth\"}," +
            "\"max_nodes\":{\"type\":\"integer\",\"default\":400," +
                "\"description\":\"Maximum returned nodes before truncating\"}" +
            "}}";
    }

    @Override
    public String call(JSONObject args, Context context) throws Exception {
        WorkspaceAccessPolicy.enforceAccessibilityForeground(context, getName());

        if (!McpAccessibilityService.isRunning()) {
            return textContent("Accessibility permission not granted. " +
                "Please enable 'PortalAgent' in Settings → Accessibility.");
        }

        McpAccessibilityService svc = McpAccessibilityService.getInstance();
        AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        if (root == null) {
            return textContent("Cannot read active window. Make sure the screen is on " +
                "and an app is in the foreground.");
        }

        String mode     = args.optString("mode", "flat");
        int    maxDepth = args.optInt("max_depth", DEFAULT_MAX_DEPTH);
        int    maxNodes = Math.min(HARD_MAX_NODES, Math.max(1,
            args.optInt("max_nodes", DEFAULT_MAX_NODES)));
        TraversalBudget budget = new TraversalBudget(maxNodes);

        JSONObject result = new JSONObject();
        Rect displayBounds = svc.getDisplayBounds();
        Rect activeBounds = svc.getActiveWindowBounds();
        result.put("package",  svc.getCurrentPackage());
        result.put("activity", svc.getCurrentActivity());
        result.put("active_window_package", svc.getActiveWindowPackage());
        result.put("screen_bounds", boundsJson(displayBounds));
        result.put("screen_width", displayBounds.width());
        result.put("screen_height", displayBounds.height());
        result.put("active_window_bounds", boundsJson(activeBounds));
        result.put("last_application_package", svc.getLastApplicationPackage());
        result.put("last_application_activity", svc.getLastApplicationActivity());
        result.put("effective_package", svc.getEffectiveForegroundPackage(getName()));
        result.put("effective_activity", svc.getEffectiveForegroundActivity(getName()));

        try {
            if ("tree".equals(mode)) {
                result.put("mode", "tree");
                result.put("root", buildTree(root, 0, maxDepth, budget));
            } else {
                JSONArray flat = new JSONArray();
                flattenInteresting(root, flat, 0, maxDepth, budget);
                result.put("mode",  "flat");
                result.put("count", flat.length());
                result.put("nodes", flat);
            }
            result.put("visited_nodes", budget.visitedNodes);
            result.put("max_nodes", maxNodes);
            result.put("truncated", budget.truncated);
        } finally {
            root.recycle();
        }

        JSONObject item = new JSONObject();
        item.put("type", "text");
        item.put("text", result.toString(2));
        return new JSONArray().put(item).toString();
    }

    // ── Flat mode: collect "interesting" nodes ────────────────────────────────

    private void flattenInteresting(AccessibilityNodeInfo node, JSONArray out,
                                     int depth, int maxDepth, TraversalBudget budget) throws Exception {
        if (node == null || depth > maxDepth || !budget.visit()) return;

        CharSequence text    = node.getText();
        CharSequence desc    = node.getContentDescription();
        boolean hasText      = text != null && text.length() > 0;
        boolean hasDesc      = desc != null && desc.length() > 0;
        boolean isClickable  = node.isClickable();
        boolean isEditable   = node.isEditable();
        boolean isScrollable = node.isScrollable();

        if (hasText || hasDesc || isClickable || isEditable || isScrollable) {
            if (!budget.emit()) return;
            JSONObject n = new JSONObject();
            if (hasText)  n.put("text",         budget.text(text));
            if (hasDesc)  n.put("content_desc",  budget.text(desc));
            n.put("class",      shortClass(node.getClassName()));
            n.put("bounds",     boundsJson(node));
            if (isClickable)  n.put("clickable",  true);
            if (isEditable)   n.put("editable",   true);
            if (isScrollable) n.put("scrollable", true);
            if (!node.isEnabled()) n.put("enabled", false);
            out.put(n);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            if (budget.shouldStop()) break;
            AccessibilityNodeInfo child = node.getChild(i);
            try {
                flattenInteresting(child, out, depth + 1, maxDepth, budget);
            } finally {
                if (child != null) child.recycle();
            }
        }
    }

    // ── Tree mode: full hierarchy ─────────────────────────────────────────────

    private JSONObject buildTree(AccessibilityNodeInfo node, int depth, int maxDepth,
                                 TraversalBudget budget)
            throws Exception {
        if (node == null || depth > maxDepth || !budget.visit() || !budget.emit()) return null;

        JSONObject obj = new JSONObject();
        obj.put("class",  shortClass(node.getClassName()));

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        if (text != null && text.length() > 0) obj.put("text",         budget.text(text));
        if (desc != null && desc.length() > 0) obj.put("content_desc",  budget.text(desc));

        obj.put("bounds",     boundsJson(node));
        obj.put("clickable",  node.isClickable());
        obj.put("enabled",    node.isEnabled());
        obj.put("editable",   node.isEditable());
        obj.put("scrollable", node.isScrollable());
        obj.put("focused",    node.isFocused());

        JSONArray children = new JSONArray();
        if (depth < maxDepth) {
            for (int i = 0; i < node.getChildCount(); i++) {
                if (budget.shouldStop()) break;
                AccessibilityNodeInfo child = node.getChild(i);
                try {
                    JSONObject childObj = buildTree(child, depth + 1, maxDepth, budget);
                    if (childObj != null) children.put(childObj);
                } finally {
                    if (child != null) child.recycle();
                }
            }
        }
        obj.put("children", children);
        return obj;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static JSONArray boundsJson(AccessibilityNodeInfo node) throws Exception {
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        return boundsJson(r);
    }

    private static JSONArray boundsJson(Rect r) throws Exception {
        JSONArray a = new JSONArray();
        a.put(r.left); a.put(r.top); a.put(r.right); a.put(r.bottom);
        return a;
    }

    /** "android.widget.TextView" → "TextView" */
    private static String shortClass(CharSequence cls) {
        if (cls == null) return "";
        String s = cls.toString();
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }

    private static String textContent(String msg) throws Exception {
        JSONObject item = new JSONObject();
        item.put("type", "text");
        item.put("text", msg);
        return new JSONArray().put(item).toString();
    }

    private static final class TraversalBudget {
        final int maxReturnedNodes;
        final int maxVisitedNodes;
        final long deadlineMs;
        int returnedNodes;
        int visitedNodes;
        boolean truncated;
        private boolean stop;

        TraversalBudget(int maxReturnedNodes) {
            this.maxReturnedNodes = maxReturnedNodes;
            this.maxVisitedNodes = Math.max(maxReturnedNodes, maxReturnedNodes * 6);
            this.deadlineMs = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS;
        }

        boolean visit() {
            if (stop) return false;
            if (visitedNodes >= maxVisitedNodes || System.currentTimeMillis() > deadlineMs) {
                truncated = true;
                stop = true;
                return false;
            }
            visitedNodes++;
            return true;
        }

        boolean emit() {
            if (stop) return false;
            if (returnedNodes >= maxReturnedNodes) {
                truncated = true;
                stop = true;
                return false;
            }
            returnedNodes++;
            return true;
        }

        boolean shouldStop() {
            return stop;
        }

        String text(CharSequence value) {
            if (value == null) return "";
            String text = value.toString();
            if (text.length() <= MAX_TEXT_CHARS) return text;
            truncated = true;
            return text.substring(0, MAX_TEXT_CHARS) + "...";
        }
    }
}
