package com.portalagent.mcp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.util.DisplayMetrics;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.RequiresApi;

import com.portalagent.automation.ScreenFingerprint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Accessibility Service that exposes UI control to Claude Code via MCP tools.
 *
 * Enabled by the user in: Settings → Accessibility → PortalAgent
 * Required for: ui.tap, ui.swipe, ui.click_text, ui.input_text,
 *               ui.get_accessibility_tree, app.get_current_activity
 */
public class McpAccessibilityService extends AccessibilityService {

    private static volatile McpAccessibilityService sInstance;

    private volatile String mCurrentPackage  = "";
    private volatile String mCurrentActivity = "";
    private volatile String mLastApplicationPackage = "";
    private volatile String mLastApplicationActivity = "";

    private static final int CLICK_TEXT_MAX_VISITED_NODES = 4000;
    private static final long CLICK_TEXT_TIMEOUT_MS = 2500L;

    // ── Static access ─────────────────────────────────────────────────────────

    public static boolean isRunning() { return sInstance != null; }

    public static McpAccessibilityService getInstance() { return sInstance; }

    // ── Service lifecycle ──────────────────────────────────────────────────────

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            CharSequence cls = event.getClassName();
            String packageName = pkg == null ? "" : pkg.toString();
            String activityName = cls == null ? "" : cls.toString();
            if (pkg != null) mCurrentPackage = packageName;
            if (cls != null) mCurrentActivity = activityName;
            if (isTrackableApplicationPackage(packageName, activityName)) {
                mLastApplicationPackage = packageName;
                mLastApplicationActivity = activityName;
            }
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        sInstance = null;
        super.onDestroy();
    }

    // ── State getters (called by AppTool) ─────────────────────────────────────

    public String getCurrentPackage()  { return mCurrentPackage; }
    public String getCurrentActivity() { return mCurrentActivity; }
    public String getLastApplicationPackage() { return mLastApplicationPackage; }
    public String getLastApplicationActivity() { return mLastApplicationActivity; }

    public String getEffectiveForegroundPackage() {
        return WorkspaceAccessPolicy.effectiveAccessibilityPackage(
            this, mCurrentPackage, mLastApplicationPackage);
    }

    public String getEffectiveForegroundPackage(String toolName) {
        return WorkspaceAccessPolicy.effectiveAccessibilityPackageForTool(
            this, toolName, mCurrentPackage, mCurrentActivity, mLastApplicationPackage,
            getActiveWindowPackage());
    }

    public String getEffectiveForegroundActivity() {
        String effectivePackage = getEffectiveForegroundPackage();
        if (effectivePackage.equals(mLastApplicationPackage)
            && !effectivePackage.equals(mCurrentPackage)) {
            return mLastApplicationActivity;
        }
        return mCurrentActivity;
    }

    public String getEffectiveForegroundActivity(String toolName) {
        String effectivePackage = getEffectiveForegroundPackage(toolName);
        if (effectivePackage.equals(mLastApplicationPackage)
            && !effectivePackage.equals(mCurrentPackage)) {
            return mLastApplicationActivity;
        }
        return mCurrentActivity;
    }

    public String getActiveWindowPackage() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "";
        try {
            CharSequence packageName = root.getPackageName();
            return packageName == null ? "" : packageName.toString();
        } finally {
            root.recycle();
        }
    }

    public Rect getDisplayBounds() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return new Rect(0, 0, metrics.widthPixels, metrics.heightPixels);
    }

    public Rect getActiveWindowBounds() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return getDisplayBounds();
        try {
            Rect bounds = new Rect();
            root.getBoundsInScreen(bounds);
            if (bounds.isEmpty()) return getDisplayBounds();
            return bounds;
        } finally {
            root.recycle();
        }
    }

    public ScreenFingerprint currentScreenFingerprint() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            return new ScreenFingerprint(mCurrentPackage, mCurrentActivity,
                new ArrayList<>(), 0, 0, "");
        }

        FingerprintCollector collector = new FingerprintCollector();
        try {
            collector.rootSummary = shortClass(root.getClassName());
            collectFingerprint(root, 0, collector);
        } finally {
            root.recycle();
        }
        return new ScreenFingerprint(mCurrentPackage, mCurrentActivity, collector.anchors,
            collector.clickableCount, collector.editableCount, collector.rootSummary);
    }

    public boolean screenMatches(ScreenFingerprint expected) {
        if (expected == null) return true;
        ScreenFingerprint current = currentScreenFingerprint();
        if (expected.packageName != null && expected.packageName.length() > 0
            && !expected.packageName.equals(current.packageName)) {
            return false;
        }
        for (String anchor : expected.anchors) {
            if (!current.containsAnchor(anchor)) return false;
        }
        return true;
    }

    // ── Gesture dispatch (ui.tap, ui.swipe) ───────────────────────────────────

    /**
     * Tap at (x, y). Blocks until gesture completes or times out.
     * Requires API 24+.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void tap(float x, float y) throws Exception {
        validatePointInDisplay(x, y);
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, 60))
            .build();
        dispatchGestureSync(gesture);
    }

    /**
     * Swipe from (x1,y1) to (x2,y2) over durationMs milliseconds.
     * Requires API 24+.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void swipe(float x1, float y1, float x2, float y2, int durationMs) throws Exception {
        validatePointInDisplay(x1, y1);
        validatePointInDisplay(x2, y2);
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0,
                Math.max(1, durationMs)))
            .build();
        dispatchGestureSync(gesture);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void dispatchGestureSync(GestureDescription gesture) throws Exception {
        CountDownLatch latch   = new CountDownLatch(1);
        AtomicBoolean  success = new AtomicBoolean(false);
        dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) {
                success.set(true);
                latch.countDown();
            }
            @Override public void onCancelled(GestureDescription g) {
                latch.countDown();
            }
        }, null);
        if (!latch.await(5, TimeUnit.SECONDS)) throw new Exception("Gesture timed out");
        if (!success.get())                    throw new Exception("Gesture was cancelled");
    }

    private void validatePointInDisplay(float x, float y) throws Exception {
        Rect display = getDisplayBounds();
        if (x < display.left || y < display.top || x >= display.right || y >= display.bottom) {
            throw new Exception("Coordinates outside screen bounds: (" + (int) x + ", "
                + (int) y + "), screen=[" + display.left + "," + display.top + ","
                + display.right + "," + display.bottom + "]");
        }
    }

    // ── Node interaction (ui.click_text, ui.input_text) ──────────────────────

    /**
     * Find a node whose text/content-description matches, then tap its center.
     *
     * Uses manual tree traversal (same path as UiTreeTool, proven to work from
     * background threads) rather than findAccessibilityNodeInfosByText + performAction,
     * which deadlock on some Samsung builds when called from any thread.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void clickText(String text, String matchMode) throws Exception {
        float[] center;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) throw new Exception("Cannot read active window — is the screen on?");
        NodeSearchBudget budget = new NodeSearchBudget(
            CLICK_TEXT_MAX_VISITED_NODES, CLICK_TEXT_TIMEOUT_MS);
        try {
            center = findNodeCenter(root, text, matchMode, budget);
        } finally {
            root.recycle();
        }
        if (center == null) {
            String suffix = budget.truncated ? " before search budget expired" : "";
            throw new Exception("Text not found on screen" + suffix + ": \"" + text + "\"");
        }
        tap(center[0], center[1]);
    }

    /**
     * Set text in the currently focused editable field via ACTION_SET_TEXT.
     * Runs on the main thread to avoid binder deadlocks on Samsung builds.
     */
    public void inputText(String text) throws Exception {
        runOnMainThread(() -> {
            AccessibilityNodeInfo target = null;
            AccessibilityNodeInfo root = null;
            try {
                target = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (target != null && !target.isEditable()) {
                    target.recycle();
                    target = null;
                }
                if (target == null) {
                    root = getRootInActiveWindow();
                    target = findFirstEditable(root);
                    if (target != null) target.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                }
                if (target == null) {
                    throw new Exception("No editable input field found — tap one first");
                }
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                boolean ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                if (!ok) throw new Exception("ACTION_SET_TEXT failed — field may not be editable");
            } finally {
                if (target != null) target.recycle();
                if (root != null) root.recycle();
            }
            return null;
        });
    }

    private <T> T runOnMainThread(Callable<T> task) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return task.call();
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                result.set(task.call());
            } catch (Exception e) {
                error.set(e);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) throw new Exception("Main thread operation timed out");
        if (error.get() != null) throw error.get();
        return result.get();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void collectFingerprint(AccessibilityNodeInfo node, int depth,
                                    FingerprintCollector collector) {
        if (node == null || collector == null || depth > 4) return;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        if (text != null && text.length() > 0 && collector.anchors.size() < 24) {
            collector.anchors.add(text.toString());
        }
        if (desc != null && desc.length() > 0 && collector.anchors.size() < 24) {
            collector.anchors.add(desc.toString());
        }
        if (node.isClickable()) collector.clickableCount++;
        if (node.isEditable()) collector.editableCount++;

        if (depth >= 4) return;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            try {
                collectFingerprint(child, depth + 1, collector);
            } finally {
                if (child != null) child.recycle();
            }
        }
    }

    private static String shortClass(CharSequence cls) {
        if (cls == null) return "";
        String s = cls.toString();
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }

    private boolean isTrackableApplicationPackage(String packageName, String activityName) {
        if (packageName == null || packageName.length() == 0) return false;
        if (getPackageName().equals(packageName)
            && !WorkspaceAccessPolicy.isDeclaredActivityName(this, packageName, activityName)) {
            return false;
        }
        if (WorkspaceAccessPolicy.isCurrentInputMethodPackage(this, packageName)) return false;
        return true;
    }

    private AccessibilityNodeInfo findFirstEditable(AccessibilityNodeInfo node) {
        int[] visited = new int[] {0};
        return findFirstEditable(node, visited);
    }

    private AccessibilityNodeInfo findFirstEditable(AccessibilityNodeInfo node, int[] visited) {
        if (node == null || visited[0]++ > 2000) return null;
        if (node.isEditable() && node.isVisibleToUser()) return AccessibilityNodeInfo.obtain(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            try {
                AccessibilityNodeInfo result = findFirstEditable(child, visited);
                if (result != null) return result;
            } finally {
                if (child != null) child.recycle();
            }
        }
        return null;
    }

    private static final class FingerprintCollector {
        final List<String> anchors = new ArrayList<>();
        int clickableCount;
        int editableCount;
        String rootSummary = "";
    }

    /**
     * Traverse the node tree to find a node matching the text, then return its
     * screen-center coordinates. Uses getChild(i) traversal (same as UiTreeTool)
     * to avoid findAccessibilityNodeInfosByText which deadlocks on Samsung.
     *
     * Searches for the matching text node, then walks up to find its clickable
     * ancestor's center. If no clickable ancestor, uses the matching node's own bounds.
     */
    private float[] findNodeCenter(AccessibilityNodeInfo node, String text,
                                    String matchMode, NodeSearchBudget budget) {
        if (node == null) return null;
        if (budget == null || !budget.visit()) return null;
        CharSequence t = node.getText();
        CharSequence d = node.getContentDescription();
        boolean matches;
        if ("exact".equals(matchMode)) {
            matches = (t != null && t.toString().equals(text)) ||
                      (d != null && d.toString().equals(text));
        } else {
            matches = (t != null && t.toString().contains(text)) ||
                      (d != null && d.toString().contains(text));
        }
        if (matches) {
            Rect r = new Rect();
            // try to get clickable ancestor's bounds; fall back to own bounds
            float[] center = clickableCenterOf(node);
            if (center != null) return center;
            node.getBoundsInScreen(r);
            return new float[]{ (r.left + r.right) / 2f, (r.top + r.bottom) / 2f };
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (budget.shouldStop()) break;
            AccessibilityNodeInfo child = node.getChild(i);
            float[] result;
            try {
                result = findNodeCenter(child, text, matchMode, budget);
            } finally {
                if (child != null) child.recycle();
            }
            if (result != null) return result;
        }
        return null;
    }

    private float[] clickableCenterOf(AccessibilityNodeInfo node) {
        if (node == null) return null;
        AccessibilityNodeInfo current = node;
        for (int depth = 0; depth < 20; depth++) {
            if (current.isClickable()) {
                Rect r = new Rect();
                current.getBoundsInScreen(r);
                if (current != node) current.recycle();
                return new float[]{ (r.left + r.right) / 2f, (r.top + r.bottom) / 2f };
            }
            AccessibilityNodeInfo parent = current.getParent();
            if (current != node) current.recycle();
            if (parent == null) return null;
            current = parent;
        }
        if (current != node) current.recycle();
        return null;
    }

    private static final class NodeSearchBudget {
        final int maxVisitedNodes;
        final long deadlineMs;
        int visitedNodes;
        boolean truncated;
        private boolean stop;

        NodeSearchBudget(int maxVisitedNodes, long timeoutMs) {
            this.maxVisitedNodes = Math.max(1, maxVisitedNodes);
            this.deadlineMs = System.currentTimeMillis() + Math.max(1L, timeoutMs);
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

        boolean shouldStop() {
            return stop;
        }
    }
}
