package com.mhstore.admin.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.view.animation.AnimationSet;
import android.widget.TextView;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.mhstore.admin.R;
import com.mhstore.admin.utils.Constants;
import com.mhstore.admin.utils.PrefManager;

/**
 * Splash screen — shows MHStore logo for SPLASH_DELAY_MS, then routes:
 *   • No PIN set → LoginActivity (setup mode)
 *   • PIN set    → LoginActivity (verify mode)
 *
 * If opened from a notification (EXTRA_FROM_NOTIF = true),
 * the LOGIN activity will automatically pass to OrderDetail after verify.
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Animate logo
        animateLogo();

        // Extract notification extras for pass-through
        String orderId    = getIntent().getStringExtra(Constants.EXTRA_ORDER_ID);
        boolean fromNotif = getIntent().getBooleanExtra(Constants.EXTRA_FROM_NOTIF, false);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
            if (fromNotif && orderId != null) {
                intent.putExtra(Constants.EXTRA_ORDER_ID, orderId);
                intent.putExtra(Constants.EXTRA_FROM_NOTIF, true);
            }
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, Constants.SPLASH_DELAY_MS);
    }

    private void animateLogo() {
        LinearLayout logoWrap = findViewById(R.id.logo_wrap);
        if (logoWrap == null) return;

        // Scale + Fade in
        ScaleAnimation scale = new ScaleAnimation(0.75f, 1.0f, 0.75f, 1.0f,
            Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        AlphaAnimation fade  = new AlphaAnimation(0f, 1f);
        AnimationSet set = new AnimationSet(true);
        set.addAnimation(scale); set.addAnimation(fade);
        set.setDuration(600);
        set.setFillAfter(true);
        logoWrap.startAnimation(set);
    }
}
