package com.monassistant.bloqueur;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.List;

/**
 * Service d'accessibilité qui surveille Instagram, TikTok et YouTube.
 * - TikTok : l'app entière est du contenu court -> retour à l'écran d'accueil.
 * - Instagram : si le lecteur de Reels est visible -> bouton "retour".
 * - YouTube : si le lecteur de Shorts est visible -> bouton "retour".
 *
 * Les identifiants de vues ci-dessous peuvent changer quand les applis se
 * mettent à jour ; il suffit alors d'ajouter les nouveaux dans les listes.
 */
public class BlockerService extends AccessibilityService {

    private static final String PKG_INSTAGRAM = "com.instagram.android";
    private static final String PKG_YOUTUBE = "com.google.android.youtube";
    private static final String PKG_TIKTOK_1 = "com.zhiliaoapp.musically";
    private static final String PKG_TIKTOK_2 = "com.ss.android.ugc.trill";

    /** Vues présentes uniquement dans le lecteur de Reels Instagram. */
    private static final String[] INSTAGRAM_REELS_IDS = {
            "clips_viewer_view_pager",
            "clips_viewer_container",
            "clips_video_container",
            "root_clips_layout",
            "clips_ufi_component",
    };

    /** Vues présentes uniquement dans le lecteur de Shorts YouTube. */
    private static final String[] YOUTUBE_SHORTS_IDS = {
            "reel_player_page_container",
            "reel_recycler",
            "reel_watch_player",
            "reel_progress_bar",
            "shorts_player_container",
    };

    /** Délai minimum entre deux actions, pour éviter de "spammer" le retour. */
    private static final long COOLDOWN_MS = 800;
    /** Délai minimum entre deux analyses de l'écran (économie de batterie). */
    private static final long SCAN_INTERVAL_MS = 250;

    private long lastActionAt = 0;
    private long lastScanAt = 0;
    private long lastToastAt = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        long now = SystemClock.uptimeMillis();

        if (now - lastActionAt < COOLDOWN_MS) return;

        // TikTok : on sort de l'appli dès qu'elle s'ouvre.
        if (PKG_TIKTOK_1.equals(pkg) || PKG_TIKTOK_2.equals(pkg)) {
            if (Prefs.isOn(this, Prefs.TIKTOK)) {
                block(GLOBAL_ACTION_HOME, "TikTok bloqué");
            }
            return;
        }

        // Pour les autres, on n'analyse pas l'écran à chaque micro-événement.
        boolean stateChanged = event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        if (!stateChanged && now - lastScanAt < SCAN_INTERVAL_MS) return;
        lastScanAt = now;

        if (PKG_INSTAGRAM.equals(pkg) && Prefs.isOn(this, Prefs.INSTAGRAM)) {
            if (screenContains(pkg, INSTAGRAM_REELS_IDS)) {
                block(GLOBAL_ACTION_BACK, "Reels bloqués");
            }
        } else if (PKG_YOUTUBE.equals(pkg) && Prefs.isOn(this, Prefs.YOUTUBE)) {
            if (screenContains(pkg, YOUTUBE_SHORTS_IDS)) {
                block(GLOBAL_ACTION_BACK, "Shorts bloqués");
            }
        }
    }

    private boolean screenContains(String pkg, String[] ids) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            if (root.getPackageName() == null || !pkg.contentEquals(root.getPackageName())) {
                return false;
            }
            for (String id : ids) {
                List<AccessibilityNodeInfo> nodes =
                        root.findAccessibilityNodeInfosByViewId(pkg + ":id/" + id);
                if (nodes == null) continue;
                boolean found = false;
                for (AccessibilityNodeInfo n : nodes) {
                    if (n != null && n.isVisibleToUser()) found = true;
                    if (n != null) n.recycle();
                }
                if (found) return true;
            }
            return false;
        } finally {
            root.recycle();
        }
    }

    private void block(int action, String message) {
        lastActionAt = SystemClock.uptimeMillis();
        performGlobalAction(action);
        Prefs.increment(this);
        if (lastActionAt - lastToastAt > 3000) {
            lastToastAt = lastActionAt;
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onInterrupt() {
        // Rien à faire.
    }
}
