package com.exam.exammodwithx;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Cek key dulu, baru lanjut ke aplikasi utama
        KeyAuthManager.checkKey(this, new KeyAuthManager.KeyCheckCallback() {
            @Override
            public void onValid() {
                // Key valid, lanjut ke fitur utama
                initMainApp();
            }

            @Override
            public void onInvalid() {
                // Key salah/berubah
                showKeyDialog(false);
            }

            @Override
            public void onNeedInput() {
                // Belum pernah input key atau expired
                showKeyDialog(true);
            }

            @Override
            public void onError(Exception e) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Error")
                        .setMessage("Tidak dapat cek key: " + e.getMessage())
                        .setPositiveButton("Coba Lagi", (d, w) -> recreate())
                        .setNegativeButton("Keluar", (d, w) -> finish())
                        .setCancelable(false)
                        .show();
            }
        });
    }

    // Dialog input key
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
                    public void onValid() { initMainApp(); }
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

    // Semua kode fitur utama aplikasi tetap di sini, hanya dipanggil setelah key valid
    private void initMainApp() {
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sharedPreferences = getSharedPreferences("setting", 0);
        if (sharedPreferences.getBoolean("is_first_time", true)) {
            Toasty.Config.getInstance()
                    .setToastTypeface(Typeface.createFromAsset(getAssets(), "PCap Terminal.otf"))
                    .allowQueue(false)
                    .apply();

            Toast tscr = Toasty.custom(MainActivity.this,
                    R.string.credit,
                    ContextCompat.getDrawable(this, R.drawable.laptop512),
                    android.R.color.black, android.R.color.holo_green_light,
                    Toasty.LENGTH_LONG, true, true);

            tscr.show(); // Menampilkan credit saat mulai aplikasi
            tscr.show();

            Toast.makeText(this, "Tekan logo Tut Wuri untuk melihat fitur dan custom setting", 1).show();
            sharedPreferences.edit().putBoolean("is_first_time", false).apply();
            sharedPreferences.edit().putBoolean("support_zoom", true).apply();
        }

        String url = sharedPreferences.getString("url", null);

        if (url != null) {
            binding.urlEditText.setText(url);
        }

        binding.masukBtn.setOnClickListener((View v) -> {
            String urltv = binding.urlEditText.getText().toString();
            if (urltv.isEmpty()) { // Tidak dimuat saat url kosong
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
        this.binding = null;
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
