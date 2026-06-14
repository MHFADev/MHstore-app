package com.mhstore.admin.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.mhstore.admin.R;
import com.mhstore.admin.utils.Constants;
import com.mhstore.admin.utils.PrefManager;

import java.util.ArrayList;
import java.util.List;

/**
 * PIN login / setup activity.
 * First run: prompts user to set a new PIN.
 * Subsequent runs: verifies entered PIN.
 */
public class LoginActivity extends AppCompatActivity {

    private static final int PIN_LENGTH = 6;

    private StringBuilder enteredPin = new StringBuilder();
    private boolean isSetupMode = false;
    private String confirmPin = null;
    private boolean awaitingConfirm = false;

    private TextView tvTitle, tvSubtitle;
    private List<ImageButton> pinDots = new ArrayList<>();
    private PrefManager prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        prefs = PrefManager.getInstance(this);
        isSetupMode = !prefs.isPinSet();

        bindViews();
        setupKeypad();
        updateHeader();

        // Request notification permission (Android 13+)
        requestNotificationPermission();

        // Subscribe to FCM topic
        subscribeToAdminTopic();
    }

    private void bindViews() {
        tvTitle    = findViewById(R.id.tv_login_title);
        tvSubtitle = findViewById(R.id.tv_login_sub);

        // Collect PIN dots (6 circles)
        pinDots.add(findViewById(R.id.dot1));
        pinDots.add(findViewById(R.id.dot2));
        pinDots.add(findViewById(R.id.dot3));
        pinDots.add(findViewById(R.id.dot4));
        pinDots.add(findViewById(R.id.dot5));
        pinDots.add(findViewById(R.id.dot6));
    }

    private void setupKeypad() {
        int[] numIds = {
            R.id.btn_1, R.id.btn_2, R.id.btn_3,
            R.id.btn_4, R.id.btn_5, R.id.btn_6,
            R.id.btn_7, R.id.btn_8, R.id.btn_9,
            R.id.btn_0
        };
        int[] nums = {1, 2, 3, 4, 5, 6, 7, 8, 9, 0};

        for (int i = 0; i < numIds.length; i++) {
            final int num = nums[i];
            MaterialButton btn = findViewById(numIds[i]);
            if (btn != null) btn.setOnClickListener(v -> appendDigit(num));
        }

        MaterialButton backspace = findViewById(R.id.btn_backspace);
        if (backspace != null) backspace.setOnClickListener(v -> removeDigit());
    }

    private void updateHeader() {
        if (isSetupMode) {
            if (awaitingConfirm) {
                tvTitle.setText("Konfirmasi PIN");
                tvSubtitle.setText("Masukkan PIN yang sama sekali lagi");
            } else {
                tvTitle.setText("Buat PIN Admin");
                tvSubtitle.setText("Buat PIN 6 digit untuk mengamankan dashboard");
            }
        } else {
            tvTitle.setText("Masuk Admin");
            tvSubtitle.setText("Masukkan PIN 6 digit Anda");
        }
    }

    private void appendDigit(int digit) {
        if (enteredPin.length() >= PIN_LENGTH) return;
        enteredPin.append(digit);
        updateDots();
        if (enteredPin.length() == PIN_LENGTH) {
            handlePinComplete();
        }
    }

    private void removeDigit() {
        if (enteredPin.length() == 0) return;
        enteredPin.deleteCharAt(enteredPin.length() - 1);
        updateDots();
    }

    private void updateDots() {
        int filled = enteredPin.length();
        for (int i = 0; i < pinDots.size(); i++) {
            pinDots.get(i).setImageResource(
                i < filled ? R.drawable.ic_dot_filled : R.drawable.ic_dot_empty
            );
        }
    }

    private void handlePinComplete() {
        String pin = enteredPin.toString();

        if (isSetupMode) {
            if (!awaitingConfirm) {
                // First entry — ask to confirm
                confirmPin = pin;
                awaitingConfirm = true;
                resetEntry();
                updateHeader();
            } else {
                // Confirmation check
                if (pin.equals(confirmPin)) {
                    prefs.setPin(pin);
                    Toast.makeText(this, "✅ PIN berhasil dibuat!", Toast.LENGTH_SHORT).show();
                    navigateToDashboard();
                } else {
                    shakeAndReset("PIN tidak cocok. Coba lagi.");
                    awaitingConfirm = false;
                    confirmPin = null;
                    updateHeader();
                }
            }
        } else {
            if (prefs.verifyPin(pin)) {
                navigateToDashboard();
            } else {
                vibrate();
                shakeAndReset("PIN salah. Coba lagi.");
            }
        }
    }

    private void navigateToDashboard() {
        String orderId    = getIntent().getStringExtra(Constants.EXTRA_ORDER_ID);
        boolean fromNotif = getIntent().getBooleanExtra(Constants.EXTRA_FROM_NOTIF, false);

        if (fromNotif && orderId != null) {
            // Open directly to order detail
            Intent i = new Intent(this, OrderDetailActivity.class);
            i.putExtra(Constants.EXTRA_ORDER_ID, orderId);
            startActivity(i);
        } else {
            startActivity(new Intent(this, MainActivity.class));
        }
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void shakeAndReset(String message) {
        // Shake animation
        Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
        findViewById(R.id.dots_row).startAnimation(shake);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        new android.os.Handler(android.os.Looper.getMainLooper())
            .postDelayed(this::resetEntry, 400);
    }

    private void resetEntry() {
        enteredPin.setLength(0);
        updateDots();
    }

    private void vibrate() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(200);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    Constants.REQUEST_NOTIFICATION_PERM);
            }
        }
    }

    /**
     * FCM topic subscription is handled server-side.
     * No Firebase SDK dependency needed in the app.
     */
    private void subscribeToAdminTopic() {
        // No-op: FCM topic subscription managed via Supabase + server-side logic
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == Constants.REQUEST_NOTIFICATION_PERM) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notifikasi diaktifkan ✓", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
