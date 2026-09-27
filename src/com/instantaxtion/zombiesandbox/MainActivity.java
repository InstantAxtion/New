package com.instantaxtion.zombiesandbox;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;
import android.text.InputType;
import android.widget.EditText;
import android.view.View;
import android.view.WindowManager;

public class MainActivity extends Activity {
    private GameView game;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        game = new GameView(this);
        game.codePrompt = new GameView.CodePrompt() {
            @Override
            public void ask(String current) {
                askCode(current);
            }
        };
        game.shareHandler = new GameView.ShareHandler() {
            @Override
            public void share(Bitmap picture) {
                saveAndShare(picture);
            }
        };
        setContentView(game);
    }

    private Bitmap pendingShot;

    /** Saves a screenshot to the photo gallery and opens the share sheet. */
    private void saveAndShare(Bitmap picture) {
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingShot = picture;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            return;
        }
        try {
            String url = MediaStore.Images.Media.insertImage(getContentResolver(), picture,
                    "Zombie City " + System.currentTimeMillis(), "Zombie City Sandbox screenshot");
            if (url == null) throw new IllegalStateException();
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("image/png");
            send.putExtra(Intent.EXTRA_STREAM, Uri.parse(url));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, "Share screenshot"));
            Toast.makeText(this, "Screenshot saved to your photos", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't save the screenshot", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == 1 && pendingShot != null && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
            saveAndShare(pendingShot);
        else if (requestCode == 1) Toast.makeText(this, "Can't save screenshots without storage permission", Toast.LENGTH_SHORT).show();
        pendingShot = null;
    }

    /** A native text box for typing (or copying) a city code. */
    private void askCode(String current) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setText(current);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("City code")
                .setMessage("Type a code to play that city, or share this one so friends can play it.")
                .setView(input)
                .setPositiveButton("Use code", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        game.cityCodeEntered(input.getText().toString());
                        hideSystemUi();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        hideSystemUi();
                    }
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        game.resume();
    }

    @Override
    protected void onPause() {
        game.pause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (!game.onBack()) super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        game.release();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }
}
