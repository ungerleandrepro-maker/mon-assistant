package com.monassistant.bloqueur;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Écran principal : état du blocage, interrupteurs par appli, compteur. */
public class MainActivity extends Activity {

    private static final int GREEN = Color.parseColor("#3F6F66");
    private static final int ORANGE = Color.parseColor("#C85A30");
    private static final int INK = Color.parseColor("#16211D");
    private static final int SOFT = Color.parseColor("#55655C");

    private TextView status;
    private TextView counter;
    private Button enableButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(32), dp(20), dp(32));
        root.setBackgroundColor(Color.parseColor("#EEF2F0"));

        TextView title = text("Bloqueur Shorts", 28, INK);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = text("Reels Instagram, TikTok et Shorts YouTube : on ferme automatiquement.", 15, SOFT);
        subtitle.setPadding(0, dp(6), 0, dp(20));
        root.addView(subtitle);

        status = text("", 16, Color.WHITE);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.addView(status);

        enableButton = new Button(this);
        enableButton.setText("Activer dans les réglages d'accessibilité");
        enableButton.setOnClickListener(v -> openAccessibilitySettings());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        root.addView(enableButton, lp);

        TextView section = text("Que bloquer ?", 18, INK);
        section.setTypeface(Typeface.DEFAULT_BOLD);
        section.setPadding(0, dp(28), 0, dp(8));
        root.addView(section);

        root.addView(toggle("Reels Instagram", Prefs.INSTAGRAM));
        root.addView(toggle("TikTok (toute l'appli)", Prefs.TIKTOK));
        root.addView(toggle("Shorts YouTube", Prefs.YOUTUBE));

        counter = text("", 15, SOFT);
        counter.setPadding(0, dp(20), 0, 0);
        root.addView(counter);

        TextView help = text(
                "Si le bouton d'activation est grisé (Android 13+) : Réglages → Applications → "
                        + "Bloqueur Shorts → menu ⋮ en haut à droite → « Autoriser les paramètres restreints », "
                        + "puis réessayez.", 13, SOFT);
        help.setPadding(0, dp(28), 0, dp(8));
        root.addView(help);

        Button appInfo = new Button(this);
        appInfo.setText("Ouvrir les infos de l'appli");
        appInfo.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        root.addView(appInfo);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor("#EEF2F0"));
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean on = isServiceEnabled();
        status.setText(on ? "✓  Blocage actif" : "✗  Blocage inactif — activez-le ci-dessous");
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(on ? GREEN : ORANGE);
        status.setBackground(bg);
        enableButton.setVisibility(on ? View.GONE : View.VISIBLE);
        counter.setText("Contenus courts bloqués : " + Prefs.count(this));
    }

    private View toggle(String label, String key) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        s.setTextColor(INK);
        s.setPadding(0, dp(10), 0, dp(10));
        s.setChecked(Prefs.isOn(this, key));
        s.setOnCheckedChangeListener((b, checked) -> Prefs.set(this, key, checked));
        return s;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.START);
        return t;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    private void openAccessibilitySettings() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName me = new ComponentName(this, BlockerService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        for (String s : splitter) {
            ComponentName c = ComponentName.unflattenFromString(s);
            if (me.equals(c)) return true;
        }
        return false;
    }
}
