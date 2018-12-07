package com.dohman.captionsscanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.SparseArray;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.text.Line;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "MainActivity";
    private final Context context = this;

    // Variables for the ban thing
    public static final Handler handler = new Handler();
    public static int calls = 0;
    public static boolean banned = false;
    private int countdown = 60;
    private int bannedHour;
    private int allowedHour;
    public static boolean warned = false;

    private DrawerLayout drawer;
    private SurfaceView surfaceView;
    private CameraSource cameraSource;

    private TextView ccTV;
    private TextView wordsScannedTV;

    private String upperLine;
    private String lowerLine;

    public static int wordsScanned;

    private boolean isButtonDown = false;
    private final int RequestCameraPermissionID = 1001;
    private int offset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Checking ban status
        banned = SettingsActivity.getBanBoolean(context);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Drawer + NavigationView
        drawer = findViewById(R.id.drawer_layout);
        NavigationView navView = findViewById(R.id.nav_view);
        View headerView = navView.getHeaderView(0);
        navView.setNavigationItemSelectedListener(this);

        // ActionBarDrawerToggle
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this,
                drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        // Declarations
        surfaceView = findViewById(R.id.surfaceview);
        ccTV = findViewById(R.id.tv_cc);
        wordsScannedTV = headerView.findViewById(R.id.words_scanned);

        // Checking if there is a stored count in prefs
        String str = SettingsActivity.getDefaults("words_scanned", context);
        if (str != null)
            wordsScanned = Integer.parseInt(str);

        updateTextCount();

        makeWordsClickable(); // TODO: DEVELOPER ONLY, REMOVE LATER!

        setButtons();

        // Initialize the scanner
        initWordScanner();

        // This prevents API calls abuse
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (banned) {
                    Log.d(TAG, "seconds left: BANNED!");
                    Date checkDate = new Date();
                    long now = checkDate.getTime();
                    long banStart = Long.parseLong(SettingsActivity.getDefaults("ban_start", context));
                    long diffinMs = now - banStart;
                    long diffinSec = TimeUnit.MILLISECONDS.toSeconds(diffinMs);
                    if (diffinSec >= 7200) { // 2 hours
                        banned = false;
                        SettingsActivity.setBanBoolean(false, context);
                        calls = 0;
                    }
                } else {
                    countdown--;
                    Log.d(TAG, "seconds left: " + countdown + " calls (max 6 per minute): " + calls);
                    if (countdown <= 0) {
                        countdown = 60;
                        calls = 0;
                        warned = false;
                    }
                    if (calls == 5) {
                        if (!warned)
                            warned = true;
                    }
                    if (calls >= 7) {
                        // User gets banned from API calling for 2 hours
                        // 7 calls in 60 seconds is more than 1 call in 10 seconds = obviously abuse
                        if (!banned) {
                            banned = true;
                            SettingsActivity.setBanBoolean(true, context);
                            Date bannedDate = new Date();
                            SettingsActivity.setDefaults("ban_start", String.valueOf(bannedDate.getTime()), context);
                            Calendar timeBanned = Calendar.getInstance();
                            timeBanned.setTime(new Date());
                            bannedHour = timeBanned.get(Calendar.HOUR_OF_DAY);
                            int minutes = timeBanned.get(Calendar.MINUTE);
                            allowedHour = bannedHour + 2;
                            SettingsActivity.setDefaults("allowed_hour", String.valueOf(allowedHour), context);
                            SettingsActivity.setDefaults("minutes", String.valueOf(minutes), context);
                        }
                    }
                }

                handler.postDelayed(this, 1000);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.nav_words:
                Intent wordsIntent = new Intent(context, StoredWordsActivity.class);
                startActivity(wordsIntent);
                break;

            case R.id.nav_settings:
                Intent settingsIntent = new Intent(context, SettingsActivity.class);
                startActivity(settingsIntent);
                break;

            case R.id.nav_rate:
                try {
                    Uri marketUri = Uri.parse("market://details?id=" + getPackageName());
                    Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
                    startActivity(marketIntent);
                } catch (ActivityNotFoundException e) { // Throws if no Google Play app installed
                    Uri marketUri = Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName());
                    Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
                    startActivity(marketIntent);
                }
                break;

            case R.id.nav_reportbug:
                Intent emailIntent = new Intent(Intent.ACTION_SEND);
                emailIntent.setType("plain/text");
                emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"captionsscanner@outlook.com"});
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Bug Report - Captions Scanner");
                startActivity(Intent.createChooser(emailIntent, "Choose email client..."));
        }

        drawer.closeDrawer(GravityCompat.START);
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        SettingsActivity.setDefaults("words_scanned", String.valueOf(wordsScanned), context);
        updateTextCount();
    }

    private void updateTextCount() {
        if (SettingsActivity.getDefaults("words_scanned", context) == null) {
            String res = getString(R.string.words_scanned);
            String formatted = String.format(res, 0);
            wordsScannedTV.setText(formatted);
        } else {
            String res = getString(R.string.words_scanned);
            String formatted = String.format(res, Integer.parseInt(SettingsActivity.getDefaults("words_scanned", context)));
            wordsScannedTV.setText(formatted);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setButtons() {
        Button scanButton = findViewById(R.id.scan_button);

        // Hold to run scanner, release to keep upperLine on screen
        scanButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    isButtonDown = true;
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    isButtonDown = false;
                }
                return Boolean.parseBoolean(null);
            }
        });
    }

    private void initWordScanner() {
        final TextRecognizer textRecognizer = new TextRecognizer.Builder(context).build();
        if (!textRecognizer.isOperational()) {
            Log.w(TAG, "Detector dependencies are not yet available");
        } else {
            cameraSource = new CameraSource.Builder(context, textRecognizer)
                    .setFacing(CameraSource.CAMERA_FACING_BACK)
                    .setRequestedPreviewSize(1920, 1080)
                    .setRequestedFps(2f)
                    .setAutoFocusEnabled(true)
                    .build();
            surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder surfaceHolder) {
                    try {
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.CAMERA},
                                    RequestCameraPermissionID);
                            return;
                        }
                        cameraSource.start(surfaceView.getHolder());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

                }

                @Override
                public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                    cameraSource.stop();
                }
            });

            textRecognizer.setProcessor(new Detector.Processor<TextBlock>() {
                @Override
                public void release() {

                }

                @SuppressWarnings("unchecked")
                @Override
                public void receiveDetections(Detector.Detections<TextBlock> detections) {
                    final SparseArray<TextBlock> textBlocks = detections.getDetectedItems();
                    if (textBlocks.size() != 0 && isButtonDown) {
                        ccTV.post(new Runnable() {
                            @Override
                            public void run() {
                                for (int i = 0; i < textBlocks.size(); ++i) {
                                    TextBlock textBlock = textBlocks.valueAt(0);
                                    List<Line> lines = (List<Line>) textBlock.getComponents();
                                    try {
                                        upperLine = lines.get(0).getValue();
                                        if (lines.get(1).getValue() == null) {
                                            lowerLine = "";
                                        } else {
                                            lowerLine = lines.get(1).getValue();
                                        }
                                    } catch (IndexOutOfBoundsException e) {
                                        e.printStackTrace();
                                    }
                                }
                                StringBuilder fullLine = new StringBuilder();
                                fullLine.append(upperLine);
                                fullLine.append("\n");
                                fullLine.append(lowerLine);
                                //makeWordsClickable(); // TODO ENABLE LATER!
                                ccTV.setText(fullLine);
                                upperLine = "";
                                lowerLine = "";
                            }
                        });
                    }
                }
            });
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void makeWordsClickable() {
        ccTV.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    offset = ccTV.getOffsetForPosition(event.getX(), event.getY());
                    Intent intent = new Intent(context, WordActivity.class);
                    try {
                        intent.putExtra("CHOSEN_WORD", findWord(ccTV.getText().toString(), offset));
                        startActivity(intent);
                    } catch (StringIndexOutOfBoundsException e) {
                        Toast.makeText(context, "Try again", Toast.LENGTH_SHORT).show();
                    }
                }
                return false;
            }
        });
    }

    private String findWord(String str, int offset) {
        if (str.length() == offset) {
            offset--;  // Without this code, user will get exception when touching end of the text
        }

        if (str.charAt(offset) == ' ') {
            offset--;
        }

        int startIndex = offset;
        int endIndex = offset;

        try {
            while (str.charAt(startIndex) != ' ' && str.charAt(startIndex) != '\n') {
                startIndex--;
            }
        } catch (StringIndexOutOfBoundsException e) {
            e.printStackTrace();
            startIndex = 0;
        }

        try {
            while (str.charAt(endIndex) != ' ' && str.charAt(endIndex) != '\n') {
                endIndex++;
            }
        } catch (StringIndexOutOfBoundsException e) {
            e.printStackTrace();
            endIndex = str.length();
        }

        return str.substring(startIndex, endIndex);
    }

    public static void addToCount() {
        wordsScanned++;
    }
}