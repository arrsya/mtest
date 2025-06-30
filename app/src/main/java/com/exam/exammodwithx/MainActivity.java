package com.exam.exammodwithx;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.exam.exammodwithx.databinding.ActivityMainBinding;

import es.dmoral.toasty.Toasty;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private SharedPreferences sharedPreferences;

    private Handler keyPollingHandler;
    private Runnable keyPollingRunnable;
    private static final long POLLING_INTERVAL = 60 * 1000; // 1 menit

    private String androidId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        // Layout harus di-set lebih awal supaya bisa akses binding.androidIdTextView/copyAndroidIdBtn
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Tampilkan Android ID dan tombol salin di semua kondisi
        binding.androidIdTextView.setText("Android ID: " + androidId);
        binding.copyAndroidIdBtn.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Android ID", androidId);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Android ID disalin ke clipboard", Toast.LENGTH_SHORT).show();
        });

        // Cek apakah device terdaftar sebelum cek key!
        KeyAuthManager.checkDeviceAllowed(androidId, new KeyAuthManager.DeviceCheckCallback() {
            @Override
            public void onAllowed() {
                // Device terdaftar, lanjut cek key
                checkKeyAndContinue();
            }

            @Override
            public void onBlocked() {
                // Device tidak terdaftar, tampilkan info dan blok akses
                showBlockedDeviceDialog();
            }

            @Override
            public void onError(Exception e) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Error Device Check")
                        .setMessage("Tidak bisa cek status device. Silakan cek koneksi internet atau hubungi admin.\n\nError: " + e.getMessage())
                        .setPositiveButton("Keluar", (d, w) -> finish())
                        .setCancelable(false)
                        .show();
            }
        });
    }

    private void showBlockedDeviceDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Akses Ditolak")
                .setMessage("Device ini tidak terdaftar di sistem.\nAndroid ID: " + androidId + "\n\nSilakan hubungi admin jika ingin menambahkan device.\n\n(Android ID juga bisa disalin dengan tombol di atas)")
                .setPositiveButton("Keluar", (d, w) -> finish())
                .setCancelable(false)
                .show();
        // Semua fitur lain tetap disable. Tidak lanjut cek key, tidak munculkan tombol masuk, dll.
        binding.masukBtn.setEnabled(false);
        binding.urlEditText.setEnabled(false);
        binding.logoView.setEnabled(false);
    }

    private void checkKeyAndContinue() {
        KeyAuthManager.checkKey(this, new KeyAuthManager.KeyCheckCallback() {
            @Override
            public void onValid() {
                initMainApp();
                startKeyPolling();
            }

            @Override
            public void onInvalid() {
                showKeyDialog(false);
            }

            @Override
            public void onNeedInput() {
                showKeyDialog(true);
            }

            @Override
            public void onError(Exception e) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Error Key Check")
                        .setMessage("Tidak dapat cek key: " + e.getMessage())
                        .setPositiveButton("Coba Lagi", (d, w) -> recreate())
                        .setNegativeButton("Keluar", (d, w) -> finish())
                        .setCancelable(false)
                        .show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Hanya cek key jika device sudah allowed
        KeyAuthManager.checkDeviceAllowed(androidId, new KeyAuthManager.DeviceCheckCallback() {
            @Override
            public void onAllowed() {
                KeyAuthManager.checkKey(MainActivity.this, new KeyAuthManager.KeyCheckCallback() {
                    @Override public void onValid() {}
                    @Override public void onInvalid() { showKeyDialog(false); }
                    @Override public void onNeedInput() { showKeyDialog(true); }
                    @Override public void onError(Exception e) {}
                });
            }
            @Override
            public void onBlocked() { /* Tidak lakukan apa2, tetap blok akses */ }
            @Override
            public void onError(Exception e) { }
        });
    }

    private void startKeyPolling() {
        if (keyPollingHandler != null && keyPollingRunnable != null) {
            keyPollingHandler.removeCallbacks(keyPollingRunnable);
        }
        keyPollingHandler = new Handler(Looper.getMainLooper());
        keyPollingRunnable = new Runnable() {
            @Override
            public void run() {
                KeyAuthManager.checkDeviceAllowed(androidId, new KeyAuthManager.DeviceCheckCallback() {
                    @Override
                    public void onAllowed() {
                        KeyAuthManager.checkKey(MainActivity.this, new KeyAuthManager.KeyCheckCallback() {
                            @Override
                            public void onValid() {
                                keyPollingHandler.postDelayed(keyPollingRunnable, POLLING_INTERVAL);
                            }
                            @Override
                            public void onInvalid() {
                                showKeyDialog(false);
                            }
                            @Override
                            public void onNeedInput() {
                                showKeyDialog(true);
                            }
                            @Override
                            public void onError(Exception e) {
                                keyPollingHandler.postDelayed(keyPollingRunnable, POLLING_INTERVAL);
                            }
                        });
                    }
                    @Override
                    public void onBlocked() {
                        showBlockedDeviceDialog();
                    }
                    @Override
                    public void onError(Exception e) {
                        keyPollingHandler.postDelayed(keyPollingRunnable, POLLING_INTERVAL);
                    }
                });
            }
        };
        keyPollingHandler.postDelayed(keyPollingRunnable, POLLING_INTERVAL);
    }

    private void showKeyDialog(boolean isFirst) {
        EditText keyInput = new EditText(this);
        keyInput.setHint("Masukkan key...");
        new AlertDialog.Builder(this)
            .setTitle("Key Diperlukan")
            .setMessage(isFirst ? "Masukkan key aplikasi:" : "Key salah/berubah. Masukkan key valid:")
            .setView(keyInput)
            .setCancelable(false)
            .setPositiveButton("OK", (dialog, which) -> {
                String input = keyInput.getText().toString().trim();
                if (input.isEmpty()) {
                    showKeyDialog(isFirst);
                    return;
                }
                KeyAuthManager.validateAndSaveKey(this, input, new KeyAuthManager.KeyCheckCallback() {
                    @Override
                    public void onValid() {
                        initMainApp();
                        startKeyPolling();
                    }
                    @Override
                    public void onInvalid() { showKeyDialog(false); }
                    @Override
                    public void onNeedInput() { showKeyDialog(false); }
                    @Override
                    public void onError(Exception e) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Error")
                                .setMessage("Gagal cek key: " + e.getMessage())
                                .setPositiveButton("Ulangi", (d, w) -> showKeyDialog(isFirst))
                                .setCancelable(false)
                                .show();
                    }
                });
            })
            .setNegativeButton("Keluar", (d, w) -> finish())
            .show();
    }

    private void initMainApp() {
        // Sudah panggil setContentView & binding di onCreate!
        sharedPreferences = getSharedPreferences("setting", 0);
        if(sharedPreferences.getBoolean("is_first_time", true)){
            Toasty.Config.getInstance()
                    .setToastTypeface(Typeface.createFromAsset(getAssets(), "PCap Terminal.otf"))
                    .allowQueue(false)
                    .apply();

            Toast tscr = Toasty.custom(MainActivity.this,
                    R.string.credit,
                    ContextCompat.getDrawable(this, R.drawable.laptop512),
                    android.R.color.black, android.R.color.holo_green_light,
                    Toasty.LENGTH_LONG, true, true);

            tscr.show();
            tscr.show();

            Toast.makeText(this,"Tekan logo Tut Wuri untuk melihat fitur dan custom setting", 1).show();
            sharedPreferences.edit().putBoolean("is_first_time", false).apply();
            sharedPreferences.edit().putBoolean("support_zoom", true).apply();
        }

        String url = sharedPreferences.getString("url", null);

        if(url != null){
            binding.urlEditText.setText(url);
        }

        binding.masukBtn.setEnabled(true);
        binding.urlEditText.setEnabled(true);
        binding.logoView.setEnabled(true);

        binding.masukBtn.setOnClickListener((View v) -> {
            String urltv = binding.urlEditText.getText().toString();
            if(urltv.isEmpty()){
                Toast.makeText(this, "Masukkan url dengan benar", 0).show();
                return;
            }
            Intent webAct = new Intent(MainActivity.this, WebviewActivity.class);
            webAct.putExtra("url", urltv);
            sharedPreferences.edit().putString("url", urltv).apply();
            startActivity(webAct);
        });

        binding.logoView.setOnClickListener((View v) -> {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (keyPollingHandler != null && keyPollingRunnable != null) {
            keyPollingHandler.removeCallbacks(keyPollingRunnable);
        }
        this.binding = null;
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
