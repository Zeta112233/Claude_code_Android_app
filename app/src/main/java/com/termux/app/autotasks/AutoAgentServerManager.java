package com.termux.app.autotasks;

import androidx.annotation.NonNull;

import com.termux.app.TermuxActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Installs agentserver natively in Termux (statically linked ARM64, no proot required).
 *
 * The binary is bundled as assets/agentserver-native-arm64 and extracted to ~/bin/agentserver.
 * On APK update, the binary is re-extracted if the asset size differs from the on-disk file.
 */
public class AutoAgentServerManager {

    /** Asset name of the bundled agentserver binary (no extension avoids AAPT2 processing). */
    static final String ASSET_NAME = "agentserver-native-arm64";

    /** Destination path relative to filesDir (i.e., $HOME/bin/agentserver). */
    static final String BINARY_REL = "home/bin/agentserver";

    private final TermuxActivity mActivity;
    private volatile boolean mExtractionDone = false;

    public AutoAgentServerManager(@NonNull TermuxActivity activity) {
        mActivity = activity;
        Thread t = new Thread(this::extractBinary, "agentserver-extract");
        t.setDaemon(true);
        t.start();
    }

    /** Block until extraction completes or timeout expires. */
    public void awaitExtraction(long timeoutMs) {
        if (mExtractionDone) return;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!mExtractionDone && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(50); } catch (InterruptedException ignored) { break; }
        }
    }

    /** Absolute path of the installed binary. */
    @NonNull
    public String getBinaryPath() {
        return new File(mActivity.getFilesDir(), BINARY_REL).getAbsolutePath();
    }

    // -------------------------------------------------------------------------

    private void extractBinary() {
        File dest = new File(mActivity.getFilesDir(), BINARY_REL);
        long assetSize = getAssetSize();
        if (dest.exists() && dest.length() == assetSize) {
            dest.setExecutable(true, false);
            mExtractionDone = true;
            return;
        }
        dest.getParentFile().mkdirs();
        File tmp = new File(dest.getParent(), ASSET_NAME + ".tmp");
        try (InputStream in = mActivity.getAssets().open(ASSET_NAME);
             FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
        } catch (IOException e) {
            tmp.delete();
            mExtractionDone = true;
            return;
        }
        if (tmp.renameTo(dest)) {
            dest.setExecutable(true, false);
        } else {
            tmp.delete();
        }
        mExtractionDone = true;
    }

    private long getAssetSize() {
        try (InputStream in = mActivity.getAssets().open(ASSET_NAME)) {
            long size = 0;
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) size += n;
            return size;
        } catch (IOException e) {
            return -1;
        }
    }
}
