package df.root;

import android.app.Activity;
import android.content.Context;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements IReporter {

    private static final String TAG = "dfroot";

    private ActivityMainBinding binding;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final Executor mExec = Executors.newSingleThreadExecutor();
    private final StringBuilder logBuffer = new StringBuilder();
    private File lastLogFile;
    private boolean running;
    private boolean runArmed;
    private boolean advancedLog;
    private boolean expertMode;
    private String exploitPhase = "";
    private int cleanupSteps;
    private float seg1;
    private float pillPercent = 0.48f;

    private static final float KSU_CIRCLE_DP = 54f;
    private static final float KSU_GAP_DP = 12f;
    private static final float PILL_INSET_FRACTION = 0.26f;
    private android.animation.ValueAnimator ksuMorph;
    private android.animation.ValueAnimator updateMorph;
    private TextView titleView;
    private int headlinePx;
    private D2Status.Result mOdin;
    private D2Writer.Vault mVault;
    private boolean mSuWasPresent;
    private Runnable mRootWatch;
    private volatile boolean mAutorunTried;
    private static final long ROOT_WATCH_MS = 2000L;
    private boolean moduleRefresh;

    private Context mDeCtx;
    private String suManagerPkg;
    private CharSequence suManagerLabel;
    private boolean failedRun;
    private String lastFailReason;

    private static final String[] CARD_KEYS = {"sumanager", "autorun", "modules", "log"};
    private final android.widget.ImageButton[] hideButtons =
            new android.widget.ImageButton[CARD_KEYS.length];
    private final List<String> hiddenCards = new ArrayList<>();
    private boolean logHiddenForDrag;
    private boolean logAllowed;
    private static final long LOG_FADE_MS = 180L;
    private static final float HIDE_BTN_DP = 30f;
    private View acceptOverlay;

    @Override
    public void report(String msg) {
        Log.i(TAG, msg.trim());
        mMain.post(() -> {
            for (String line : msg.split("\n", -1)) {
                driveProgress(line.trim());
            }
            for (String line : msg.split("\n", -1)) {
                String t = line.trim();
                if (t.isEmpty()) continue;
                if (t.matches("\\d+\\s*(\\u2026|\\.{3})?")) continue;
                if (t.contains("hook=") || t.equals("ksud start: SUCCESS") || t.contains("ERROR - ")) continue;
                t = t.replaceAll("\\+0x[0-9a-fA-F]+$", "");
                t = stripHeader(t);
                appendLog(t);
            }
            binding.outputScroll.post(() -> binding.outputScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void appendLog(String line) {
        binding.outputView.append(styleLogLine(line));
        binding.outputView.append("\n");
        logBuffer.append(line).append('\n');
        saveLog();
    }

    private void driveProgress(String t) {
        if (t.isEmpty()) return;
        int err = t.indexOf("ERROR - ");
        if (err >= 0) {
            lastFailReason = t.substring(err + "ERROR - ".length()).trim();
        }
        switch (t) {
            case "=== setup ===":
                exploitPhase = "setup";
                setSeg1(0.05f);
                break;
            case "=== exploit (patching files) ===":
                exploitPhase = "exploit";
                setSeg1(0.15f);
                break;
            case "=== init  ===":
                exploitPhase = "init";
                setSeg1(0.98f);
                break;
            case "=== cleanup ===":
                exploitPhase = "cleanup";
                cleanupSteps = 0;
                break;
            default:
                break;
        }
        if (exploitPhase.equals("setup") && t.startsWith("found ko_target:")) setSeg1(0.10f);
        if (exploitPhase.equals("exploit")) {
            if (t.startsWith("patch: crash_dump64")) setSeg1(0.30f);
            else if (t.contains("<- dfroot.ko")) setSeg1(0.50f);
            else if (t.startsWith("Finding symbol offsets")) setSeg1(0.60f);
            else if (t.contains("<- shellcode")) setSeg1(0.75f);
            else if (t.contains("<- trampoline")) setSeg1(0.90f);
            else if (t.startsWith("Triggering hook")) setSeg1(0.95f);
        }
        if (t.startsWith("libc++: loading custom module")) setSeg1(1f);
        if (t.startsWith("kernel module: launching bootstrap")) setSeg2(0.14f, "Verification", 0xFFFFFFFF);
        if (t.startsWith("bootstrap: loading app preferences file")) setSeg2(0.28f, "Verification", 0xFFFFFFFF);
        if (t.startsWith("bootstrap: cloning zygote env") || t.startsWith("bootstrap: WARNING - clone zygote env failed")) setSeg2(0.42f, "Verification", 0xFFFFFFFF);
        if (t.startsWith("bootstrap: setting partitions ro") || t.startsWith("bootstrap: WARNING - set partitions ro failed")) setSeg2(0.57f, "Verification", 0xFFFFFFFF);
        if (t.startsWith("bootstrap: disabling ksu modules")) setSeg2(0.71f, "Verification", 0xFFFFFFFF);
        if (t.startsWith("bootstrap: starting SU daemon")) setSeg2(0.85f, "Verification", 0xFFFFFFFF);
        if (t.equals("ksud start: SUCCESS")) {
            setSeg1(1f);
            setSeg2(1f, "Verified", 0xFFFFFFFF);
        }
        if (exploitPhase.equals("cleanup") && t.startsWith("restoring")) cleanupSteps++;
    }

    private void setSeg1(float p) {
        seg1 = p;
        binding.twoStep.setSeg1(p, Math.round(p * 100) + "%");
    }

    private void setSeg2(float p, String label, int color) {
        binding.twoStep.setSeg2(p, label, color);
    }

    private int logWindowPx() {
        return getResources().getDimensionPixelSize(R.dimen.log_window_height);
    }

    private void setAdvancedLog(boolean on) {
        advancedLog = on;
        binding.switchAdvancedLog.setChecked(on);
        createDeviceProtectedStorageContext()
                .getSharedPreferences("dfroot", MODE_PRIVATE)
                .edit().putBoolean("advanced_log", on).apply();
        updateLogVisibility();
    }

    private void updateLogVisibility() {
        boolean hasRun = logBuffer.length() > 0 || (lastLogFile != null && lastLogFile.exists());
        logAllowed = advancedLog && hasRun && !hiddenCards.contains("log");
        binding.cardStack.setLogGap(logAllowed ? logWindowPx() : 0);
        fadeLog(logAllowed && !logHiddenForDrag);
        setShareButtonVisible(hasRun && !running);
    }

    private void fadeLog(boolean show) {
        final View box = binding.logWindow;
        boolean visible = box.getVisibility() == View.VISIBLE;
        float target = show ? 1f : 0f;
        if (visible == show && box.getAlpha() == target) return;
        box.animate().cancel();
        if (show) box.setVisibility(View.VISIBLE);
        box.animate().alpha(target).setDuration(LOG_FADE_MS)
                .withEndAction(() -> {
                    if (box.getAlpha() <= 0f) box.setVisibility(View.GONE);
                })
                .start();
    }

    private void setShareButtonVisible(boolean show) {
        android.widget.ImageButton b = binding.btnShareLog;
        boolean wasVisible = b.getVisibility() == View.VISIBLE && b.getAlpha() > 0.99f;
        if (show && !wasVisible) {
            b.setVisibility(View.VISIBLE);
            b.setAlpha(0f);
            b.setScaleX(0.6f);
            b.setScaleY(0.6f);
            b.postDelayed(() -> b.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start(), 150);
        } else if (show) {
            b.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120).start();
        } else if (wasVisible) {
            b.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(200)
                    .withEndAction(() -> b.setVisibility(View.GONE)).start();
        }
    }

    private static String stripHeader(String t) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^===\\s*(.*?)\\s*===$").matcher(t);
        if (!m.matches()) return t;
        String h = m.group(1).toUpperCase();
        if (h.equals("EXPLOIT (PATCHING FILES)")) return "PATCHING";
        return h;
    }

    private static final String LOG_HEADER = "SYSTEM";

    private boolean isHeader(String line) {
        return line.equals("SETUP") || line.equals("PATCHING")
                || line.equals("EXPLOIT (PATCHING FILES)")
                || line.equals("INIT") || line.equals("CLEANUP")
                || line.startsWith("EXPLOIT FAILED")
                || line.equals(LOG_HEADER);
    }

    private CharSequence styleLogLine(String line) {
        SpannableString ss = new SpannableString(line);
        if (isHeader(line)) {
            ss.setSpan(new StyleSpan(Typeface.BOLD), 0, line.length(), 0);
            ss.setSpan(new RelativeSizeSpan(1.3f), 0, line.length(), 0);
            ss.setSpan(new ForegroundColorSpan(0xFFFFFFFF), 0, line.length(), 0);
        } else {
            ss.setSpan(new ForegroundColorSpan(0xFFB3B3B3), 0, line.length(), 0);
        }
        return ss;
    }

    private void saveLog() {
        try (FileOutputStream out = new FileOutputStream(lastLogFile)) {
            out.write(logBuffer.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException ignored) {}
        createDeviceProtectedStorageContext()
                .getSharedPreferences("dfroot", MODE_PRIVATE)
                .edit().putInt("log_boot_count", bootCount()).apply();
    }

    private int bootCount() {
        try {
            return android.provider.Settings.Global.getInt(getContentResolver(),
                    android.provider.Settings.Global.BOOT_COUNT, -1);
        } catch (Exception e) {
            return -1;
        }
    }

    private String readLastLog() {
        if (lastLogFile == null || !lastLogFile.exists()) return "";
        try (java.io.FileInputStream in = new java.io.FileInputStream(lastLogFile);
             java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) != -1; ) bos.write(buf, 0, n);
            String log = bos.toString(java.nio.charset.StandardCharsets.UTF_8.name()).trim();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(===\\s*exploit\\s+(?:success|failed[^=]*)\\s*===)",
                            java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(log);
            StringBuffer sb = new StringBuffer();
            while (m.find()) m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(m.group().toLowerCase()));
            m.appendTail(sb);
            return sb.toString();
        } catch (IOException e) {
            return "";
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ScaleFix.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDeCtx = createDeviceProtectedStorageContext();
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyRunPillGeometry(0f);

        Log.i(TAG, "version " + BuildConfig.VERSION_NAME);

        // Принудительно показываем плашку "Fetch KSU" и вешаем открытие ссылки на релизы
        binding.updateLabel.setText("Fetch KSU");
        binding.updatePill.setVisibility(View.VISIBLE);
        binding.updatePill.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            openUrl("https://github.com/tiann/KernelSU/releases/latest");
        });

        android.view.View.OnClickListener openRecovery = v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            showRecoveryPopup();
        };
        binding.odinPill.setOnClickListener(openRecovery);
        binding.odinPill.setOnLongClickListener(v -> {
            openRecovery.onClick(v);
            return true;
        });
        mExec.execute(this::refreshRecovery);

        loadSuManagerPref();
        binding.rowSuManager.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            showSuManagerPopup();
        });

        moduleRefresh = true;
        binding.switchModules.setChecked(!mDeCtx.getSharedPreferences("dfroot", MODE_PRIVATE)
                .getBoolean("disable_modules", false));
        moduleRefresh = false;
        binding.modulesSubtitle.setText(binding.switchModules.isChecked()
                ? "Enabled - Active at next reroot"
                : "Disabled - No Modules on reroot");
        binding.switchModules.setOnCheckedChangeListener((btn, on) -> {
            if (moduleRefresh) return;
            btn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            applyModuleState(on);
        });

        binding.toolbar.post(() -> {
            Typeface base = null;
            try {
                base = getResources().getFont(R.font.inter_vf);
            } catch (RuntimeException notAFont) {}
            if (base == null) return;
            Typeface bold = Typeface.create(base, 700, false);
            binding.odinLabel.setTypeface(bold);
            binding.updateLabel.setTypeface(bold);
            for (int i = 0; i < binding.toolbar.getChildCount(); i++) {
                View child = binding.toolbar.getChildAt(i);
                if (child instanceof TextView) {
                    titleView = (TextView) child;
                    titleView.setTypeface(bold);
                    headlinePx = Math.round(titleView.getPaint().measureText("DirtyFrag"));
                }
            }
        });

        if (new File("/dev/df").exists()) {
            setRootedState();
        }

        lastLogFile = new File(createDeviceProtectedStorageContext().getFilesDir(), "last_run.log");
        int savedBoot = createDeviceProtectedStorageContext()
                .getSharedPreferences("dfroot", MODE_PRIVATE).getInt("log_boot_count", -1);
        if (lastLogFile.exists() && savedBoot != bootCount()) {
            lastLogFile.delete();
            createDeviceProtectedStorageContext()
                    .getSharedPreferences("dfroot", MODE_PRIVATE)
                    .edit().putBoolean("last_run_success", false).apply();
        }
        String last = readLastLog();
        boolean hasLastLog = !last.isEmpty();
        if (hasLastLog) {
            appendLog(LOG_HEADER);
            for (String l : last.split("\n")) {
                String t = stripHeader(l.trim());
                if (t.isEmpty() || t.equals("LAST RUN") || t.equals("EXPLOIT SUCCESS") || t.equals(LOG_HEADER)) continue;
                appendLog(t);
            }
        }

        advancedLog = createDeviceProtectedStorageContext()
                .getSharedPreferences("dfroot", MODE_PRIVATE)
                .getBoolean("advanced_log", false);
        binding.switchAdvancedLog.setChecked(advancedLog);
        binding.switchAdvancedLog.setOnCheckedChangeListener((btn, checked) -> setAdvancedLog(checked));
        updateLogVisibility();

        boolean rootedNow = new File("/dev/df").exists();
        boolean lastSuccess = hasLastLog
                && createDeviceProtectedStorageContext()
                        .getSharedPreferences("dfroot", MODE_PRIVATE)
                        .getBoolean("last_run_success", false);
        boolean lastFailed = hasLastLog && last.toLowerCase().contains("=== exploit failed");
        if (rootedNow || lastSuccess) {
            binding.twoStep.setSeg1(1f, "100%");
            binding.twoStep.setSeg2(1f, "Verified", 0xFFFFFFFF);
        } else if (lastFailed) {
            setFailedState();
        }

        binding.btnRun.setOnClickListener(v -> {
            if (running) return;
            if (!runArmed) {
                runArmed = true;
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                binding.btnRun.setText("Are you sure");
                return;
            }
            runArmed = false;
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            running = true;
            binding.btnRun.setEnabled(false);
            binding.btnRun.setText("Running");
            binding.btnRun.setTextColor(0xFF6E6E6E);
            binding.btnRun.setBackgroundTintList(ColorStateList.valueOf(0xFF1F1F1F));
            binding.btnRun.setStrokeColor(ColorStateList.valueOf(0xFF1F1F1F));
            binding.outputView.setText("");
            logBuffer.setLength(0);
            if (lastLogFile.exists()) lastLogFile.delete();
            createDeviceProtectedStorageContext()
                    .getSharedPreferences("dfroot", MODE_PRIVATE)
                    .edit().putBoolean("last_run_success", false).apply();
            appendLog(LOG_HEADER);
            binding.twoStep.reset();
            setCompactButton(true, false);
            updateLogVisibility();
            mExec.execute(this::runExploit);
        });

        binding.btnKsu.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            openKsu();
        });

        binding.btnRun.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    break;
                default:
                    break;
            }
            return false;
        });

        binding.btnShareLog.setOnClickListener(v -> shareLog());

        binding.btnMenu.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            float md = getResources().getDisplayMetrics().density;
            final android.widget.PopupWindow[] pwRef = {null};

            android.widget.LinearLayout box = new android.widget.LinearLayout(this);
            box.setOrientation(android.widget.LinearLayout.VERTICAL);
            box.setBackgroundResource(R.drawable.popup_bg);
            box.setPadding(0, (int) (6 * md), 0, (int) (6 * md));

            TextView ghRow = new TextView(this);
            ghRow.setBackgroundResource(R.drawable.menu_row_highlight);
            ghRow.setText("Open Github Page");
            ghRow.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
            ghRow.setPadding((int) (20 * md), 0, 0, 0);
            ghRow.setTextColor(0xFFE8E8E8);
            ghRow.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
            ghRow.setOnClickListener(v2 -> {
                v2.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                openUrl("https://github.com/mitschud/DirtyFrag");
            });
            box.addView(ghRow, new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, (int) (46 * md)));

            TextView rlRow = new TextView(this);
            rlRow.setBackgroundResource(R.drawable.menu_row_highlight);
            rlRow.setText("Reset UI Layout");
            rlRow.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
            rlRow.setPadding((int) (20 * md), 0, 0, 0);
            rlRow.setTextColor(0xFFE8E8E8);
            rlRow.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
            rlRow.setOnClickListener(v2 -> {
                v2.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (pwRef[0] != null) pwRef[0].dismiss();
                resetCardLayout();
            });
            box.addView(rlRow, new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, (int) (46 * md)));

            android.widget.LinearLayout exRow = new android.widget.LinearLayout(this);
            exRow.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
            exRow.setPadding((int) (20 * md), 0, (int) (20 * md), 0);
            exRow.setBackgroundResource(R.drawable.menu_row_highlight);
            TextView exText = new TextView(this);
            exText.setText("Expert Mode");
            exText.setTextColor(0xFFE8E8E8);
            exText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
            exRow.addView(exText, new android.widget.LinearLayout.LayoutParams(
                    0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            OneUiSwitch exSwitch = new OneUiSwitch(this);
            exSwitch.setCheckedQuiet(expertMode);
            exRow.addView(exSwitch, new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            exSwitch.setOnCheckedChangeListener((view, checked) -> {
                expertMode = checked;
                createDeviceProtectedStorageContext()
                        .getSharedPreferences("dfroot", MODE_PRIVATE)
                        .edit().putBoolean("expert_mode", expertMode).apply();
                applyExpertMode();
            });
            exRow.setOnClickListener(v2 -> exSwitch.performClick());
            box.addView(exRow, new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, (int) (46 * md)));

            android.widget.LinearLayout.LayoutParams sepLp =
                    new android.widget.LinearLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) md));
            sepLp.setMargins((int) (20 * md), 0, (int) (20 * md), 0);
            android.view.View sep1 = new android.view.View(this);
            sep1.setBackgroundColor(0xFF3F3F3F);
            box.addView(sep1, 1, sepLp);
            android.view.View sep2 = new android.view.View(this);
            sep2.setBackgroundColor(0xFF3F3F3F);
            android.widget.LinearLayout.LayoutParams sep2Lp =
                    new android.widget.LinearLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) md));
            sep2Lp.setMargins((int) (20 * md), 0, (int) (20 * md), 0);
            box.addView(sep2, 3, sep2Lp);

            box.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int pw_w = Math.max(box.getMeasuredWidth(), (int) (190 * md));
            pwRef[0] = showToolbarCard(box, pw_w, md);
        });

        ComponentName bootReceiver = new ComponentName(this, BootReceiver.class);
        int state = getPackageManager().getComponentEnabledSetting(bootReceiver);
        boolean bootEnabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        binding.switchBootStart.setChecked(bootEnabled);
        binding.switchBootStart.setOnCheckedChangeListener((btn, checked) -> {
            getPackageManager().setComponentEnabledSetting(bootReceiver,
                checked ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
            binding.switchAutoSoftReboot.setEnabled(checked);
        });

        boolean autoSoftReboot = createDeviceProtectedStorageContext()
                .getSharedPreferences("dfroot", MODE_PRIVATE)
                .getBoolean("auto_soft_reboot", false);
        binding.switchAutoSoftReboot.setChecked(autoSoftReboot);
        binding.switchAutoSoftReboot.setEnabled(bootEnabled);
        binding.switchAutoSoftReboot.setOnCheckedChangeListener((btn, checked) ->
            createDeviceProtectedStorageContext()
                .getSharedPreferences("dfroot", MODE_PRIVATE)
                .edit().putBoolean("auto_soft_reboot", checked).apply());

        expertMode = createDeviceProtectedStorageContext()
                .getSharedPreferences("dfroot", MODE_PRIVATE)
                .getBoolean("expert_mode", false);
        applyExpertMode();

        setupCardStack();
        updateRunButton();
    }

    private void applyExpertMode() {
        boolean ok = expertMode;
        binding.tvAutorunTitle.setText(ok ? "Autorun" : "Autorun (Expert)");
        binding.tvRebootTitle.setText(ok ? "Auto reboot" : "Auto reboot (Expert)");
        binding.tvAutorunTitle.setTextColor(ok ? 0xFFFFFFFF : 0xFF6E6E6E);
        binding.tvAutorunDesc.setTextColor(ok ? 0xFF9E9E9E : 0xFF5A5A5A);
        binding.tvRebootTitle.setTextColor(ok ? 0xFFFFFFFF : 0xFF6E6E6E);
        binding.tvRebootDesc.setTextColor(ok ? 0xFF9E9E9E : 0xFF5A5A5A);
        binding.switchBootStart.setEnabled(ok);
        binding.switchAutoSoftReboot.setEnabled(ok && binding.switchBootStart.isChecked());
    }

    private DraggableCardView cardForKey(String key) {
        if (key == null) return null;
        switch (key) {
            case "sumanager": return binding.cardSuManager;
            case "autorun": return binding.cardBoot;
            case "modules": return binding.cardModules;
            case "log": return binding.cardLog;
            default: return null;
        }
    }

    private String keyForCard(View card) {
        for (String key : CARD_KEYS) {
            if (cardForKey(key) == card) return key;
        }
        return null;
    }

    private void setupCardStack() {
        final CardStackLayout stack = binding.cardStack;
        stack.setElevation(10 * getResources().getDisplayMetrics().density);
        stack.setLogCard(binding.cardLog);
        stack.setLogWindow(binding.logWindow);
        stack.setLogGap(logAllowed ? logWindowPx() : 0);
        stack.setListener(new CardStackLayout.Listener() {
            @Override
            public void onCardLifted(View card) {
                if (advancedLog) setAdvancedLog(false);
                setLogHiddenForDrag(true);
                setAcceptOverlay(false);
                showHideButton(card);
                followBadgeDuringSettle(card);
            }

            @Override
            public void onCardMoved(View card) {
                placeHideButton(card);
            }

            @Override
            public void onCardDropped(View card, boolean moved) {
                setLogHiddenForDrag(false);
                setAcceptOverlay(true);
                placeHideButton(card);
                followBadgeDuringSettle(card);
                if (moved) saveCardLayout();
            }

            @Override
            public void onCardSelectionCleared() {
                setAcceptOverlay(false);
                hideAllHideButtons();
            }
        });

        final float md = getResources().getDisplayMetrics().density;
        for (int i = 0; i < CARD_KEYS.length; i++) {
            final DraggableCardView card = cardForKey(CARD_KEYS[i]);
            card.setHideable(!"sumanager".equals(CARD_KEYS[i]));
            android.widget.ImageButton b = new android.widget.ImageButton(this);
            b.setBackgroundResource(R.drawable.bg_card_hide_button);
            b.setImageResource(R.drawable.ic_card_hide);
            b.setImageTintList(ColorStateList.valueOf(0xFF3A3A3C));
            b.setScaleType(android.widget.ImageView.ScaleType.CENTER);
            b.setPadding(0, 0, 0, 0);
            b.setContentDescription("Hide card");
            b.setElevation(26 * md);
            b.setVisibility(View.GONE);
            b.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                hideCard(card);
            });
            int size = (int) (HIDE_BTN_DP * md);
            android.widget.RelativeLayout.LayoutParams lp =
                    new android.widget.RelativeLayout.LayoutParams(size, size);
            binding.getRoot().addView(b, lp);
            hideButtons[i] = b;
        }

        List<View> seq = new ArrayList<>();
        for (String key : CardLayoutStore.loadOrder(mDeCtx, CARD_KEYS)) {
            DraggableCardView c = cardForKey(key);
            if (c != null && !seq.contains(c)) seq.add(c);
        }
        stack.applyOrder(seq);
        hiddenCards.addAll(CardLayoutStore.loadHidden(mDeCtx, CARD_KEYS));
        for (String key : hiddenCards) {
            DraggableCardView c = cardForKey(key);
            if (c != null && c.isHideable()) c.setVisibility(View.GONE);
        }
        stack.applyOrder(seq);
        updateLogVisibility();

        android.widget.RelativeLayout.LayoutParams alp =
                new android.widget.RelativeLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        acceptOverlay = new View(this);
        acceptOverlay.setClickable(true);
        acceptOverlay.setElevation(5 * md);
        acceptOverlay.setVisibility(View.GONE);
        acceptOverlay.setOnClickListener(v -> stack.clearSelection());
        binding.getRoot().addView(acceptOverlay, alp);
    }

    private void setAcceptOverlay(boolean on) {
        if (acceptOverlay != null) {
            acceptOverlay.setVisibility(on ? View.VISIBLE : View.GONE);
        }
    }

    private android.widget.ImageButton hideButtonFor(View card) {
        String key = keyForCard(card);
        if (key == null) return null;
        for (int i = 0; i < CARD_KEYS.length; i++) {
            if (CARD_KEYS[i].equals(key)) return hideButtons[i];
        }
        return null;
    }

    private void showHideButton(View card) {
        hideAllHideButtons();
        android.widget.ImageButton b = hideButtonFor(card);
        if (b == null || !(card instanceof DraggableCardView) || !((DraggableCardView) card).isHideable()) return;
        b.setVisibility(View.VISIBLE);
        placeHideButton(card);
    }

    private void hideAllHideButtons() {
        for (android.widget.ImageButton b : hideButtons) {
            if (b != null) b.setVisibility(View.GONE);
        }
        binding.cardStack.clearKeepTouchArea();
    }

    private void placeHideButton(View card) {
        android.widget.ImageButton b = hideButtonFor(card);
        if (b == null || b.getVisibility() != View.VISIBLE) return;
        float md = getResources().getDisplayMetrics().density;
        int size = b.getWidth() > 0 ? b.getWidth() : Math.round(HIDE_BTN_DP * md);
        int[] at = new int[2];
        int[] root = new int[2];
        card.getLocationInWindow(at);
        binding.getRoot().getLocationInWindow(root);
        float bx = at[0] - root[0] - size / 2f;
        float by = at[1] - root[1] - size / 2f;
        b.setX(bx);
        b.setY(by);
        CardStackLayout stack = binding.cardStack;
        stack.setKeepTouchArea(bx - stack.getX(), by - stack.getY(),
                bx + size - stack.getX(), by + size - stack.getY());
    }

    private void followBadgeDuringSettle(final View card) {
        binding.cardStack.postDelayed(new Runnable() {
            private int ticks = 16;

            @Override
            public void run() {
                placeHideButton(card);
                if (--ticks > 0) binding.cardStack.postDelayed(this, 16);
            }
        }, 16);
    }

    private void hideCard(DraggableCardView card) {
        String key = keyForCard(card);
        if (key == null || !card.isHideable() || hiddenCards.contains(key)) return;
        hiddenCards.add(key);
        updateLogVisibility();
        binding.cardStack.clearSelection();
        hideAllHideButtons();
        binding.cardStack.hideCard(card);
        saveCardLayout();
    }

    private void resetCardLayout() {
        CardStackLayout stack = binding.cardStack;
        stack.clearSelection();
        hideAllHideButtons();
        hiddenCards.clear();
        List<View> seq = new ArrayList<>();
        for (String key : CARD_KEYS) {
            DraggableCardView card = cardForKey(key);
            seq.add(card);
            stack.showCard(card);
        }
        stack.applyOrder(seq);
        updateLogVisibility();
        saveCardLayout();
    }

    private void saveCardLayout() {
        List<String> keys = new ArrayList<>();
        for (View v : binding.cardStack.cardOrder()) {
            String key = keyForCard(v);
            if (key != null && !keys.contains(key)) keys.add(key);
        }
        CardLayoutStore.saveOrder(mDeCtx, keys);
        CardLayoutStore.saveHidden(mDeCtx, hiddenCards);
    }

    private void setLogHiddenForDrag(boolean hidden) {
        if (logHiddenForDrag == hidden) return;
        logHiddenForDrag = hidden;
        if (hidden) fadeLog(false);
        else updateLogVisibility();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSuManagerPref();
        updateRunButton();
        startRootWatch();
    }

    @Override
    protected void onPause() {
        stopRootWatch();
        super.onPause();
    }

    private void startRootWatch() {
        stopRootWatch();
        mSuWasPresent = D2Writer.suPresent();
        mRootWatch = new Runnable() {
            @Override
            public void run() {
                boolean up = D2Writer.suPresent();
                if (up != mSuWasPresent) {
                    mSuWasPresent = up;
                    if (!up) mAutorunTried = false;
                    mExec.execute(MainActivity.this::refreshRecovery);
                }
                mMain.postDelayed(this, ROOT_WATCH_MS);
            }
        };
        mMain.postDelayed(mRootWatch, ROOT_WATCH_MS);
    }

    private void stopRootWatch() {
        if (mRootWatch != null) {
            mMain.removeCallbacks(mRootWatch);
            mRootWatch = null;
        }
    }

    private void setRootedState() {
        setRootedState(true);
    }

    private void applyModuleState(boolean on) {
        SharedPreferences sp = mDeCtx.getSharedPreferences("dfroot", MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        if (on) ed.remove("disable_modules");
        else ed.putBoolean("disable_modules", true);
        ed.apply();
        binding.modulesSubtitle.setText(on
                ? "Enabled - Active at next reroot"
                : "Disabled - No Modules on reroot");
    }

    private static final class SuManagerEntry {
        final String packageName;
        final CharSequence label;

        SuManagerEntry(String pkg, CharSequence label) {
            this.packageName = pkg;
            this.label = label;
        }
    }

    private List<SuManagerEntry> scanSuManagers() {
        List<SuManagerEntry> out = new ArrayList<>();
        PackageManager pm = getPackageManager();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if (ai.packageName.equals(getPackageName())) continue;
            if (ai.nativeLibraryDir == null) continue;
            if (!new File(ai.nativeLibraryDir, "libksud.so").exists()) continue;
            out.add(new SuManagerEntry(ai.packageName, pm.getApplicationLabel(ai)));
        }
        out.sort((a, b) -> a.label.toString().compareToIgnoreCase(b.label.toString()));
        return out;
    }

    private void loadSuManagerPref() {
        SharedPreferences sp = mDeCtx.getSharedPreferences("dfroot", MODE_PRIVATE);
        String saved = sp.getString("su_manager", null);
        suManagerPkg = null;
        suManagerLabel = null;
        if (saved != null) {
            for (SuManagerEntry e : scanSuManagers()) {
                if (e.packageName.equals(saved)) {
                    suManagerPkg = e.packageName;
                    suManagerLabel = e.label;
                    break;
                }
            }
            if (suManagerPkg == null) sp.edit().remove("su_manager").apply();
        }
        binding.suManagerSubtitle.setText(suManagerPkg == null
                ? "Not selected - required"
                : suManagerLabel + "  (" + suManagerPkg + ")");
        binding.suManagerSubtitle.setTextColor(suManagerPkg == null ? 0xFFE57373 : 0xFF9E9E9E);
    }

    private void showSuManagerPopup() {
        float md = getResources().getDisplayMetrics().density;
        List<SuManagerEntry> apps = scanSuManagers();
        final android.widget.PopupWindow[] pwRef = {null};

        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.popup_bg);
        box.setPadding(0, (int) (6 * md), 0, (int) (6 * md));

        if (apps.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("No app with libksud.so found");
            none.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
            none.setPadding((int) (20 * md), (int) (12 * md), (int) (20 * md), (int) (12 * md));
            none.setTextColor(0xFF6E6E6E);
            none.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
            box.addView(none);
        }

        for (SuManagerEntry e : apps) {
            android.widget.LinearLayout col = new android.widget.LinearLayout(this);
            col.setOrientation(android.widget.LinearLayout.VERTICAL);
            col.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
            col.setBackgroundResource(R.drawable.menu_row_highlight);
            col.setPadding((int) (20 * md), 0, (int) (20 * md), 0);

            android.widget.LinearLayout line = new android.widget.LinearLayout(this);
            line.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            line.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
            TextView t = new TextView(this);
            t.setText(e.label);
            t.setTextColor(0xFFE8E8E8);
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
            line.addView(t, new android.widget.LinearLayout.LayoutParams(
                    0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (e.packageName.equals(suManagerPkg)) {
                android.view.View dot = new android.view.View(this);
                dot.setBackgroundResource(R.drawable.dot_white);
                android.widget.LinearLayout.LayoutParams dotLp =
                        new android.widget.LinearLayout.LayoutParams(
                                (int) (7 * md), (int) (7 * md));
                dotLp.setMargins((int) (7 * md), 0, 0, 0);
                line.addView(dot, dotLp);
            }
            col.addView(line, new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, (int) (26 * md)));

            TextView sub = new TextView(this);
            sub.setText(e.packageName);
            sub.setTextColor(0xFF8E8E8E);
            sub.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
            col.addView(sub);

            col.setOnClickListener(v2 -> {
                v2.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                selectSuManager(e);
                if (pwRef[0] != null) pwRef[0].dismiss();
            });
            box.addView(col, new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, (int) (56 * md)));
        }

        box.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int cardW = binding.cardSuManager.getWidth();
        int pw_w = cardW > 0 ? cardW : Math.max(box.getMeasuredWidth(), (int) (240 * md));

        final android.widget.PopupWindow pw = new android.widget.PopupWindow(box,
                pw_w, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                android.graphics.Color.TRANSPARENT));
        pw.setOutsideTouchable(true);
        pwRef[0] = pw;
        pw.setOnDismissListener(() ->
                binding.dimOverlay.animate().alpha(0f).setDuration(150)
                        .withEndAction(() -> binding.dimOverlay.setVisibility(View.GONE)).start());

        box.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 26 * md);
            }
        });
        box.setClipToOutline(true);
        box.setElevation(48 * md);

        int[] loc = new int[2];
        binding.cardSuManager.getLocationOnScreen(loc);
        int x = (getResources().getDisplayMetrics().widthPixels - pw_w) / 2;
        int y = loc[1] + binding.cardSuManager.getHeight() + (int) (4 * md);
        binding.dimOverlay.setVisibility(View.VISIBLE);
        binding.dimOverlay.setAlpha(0f);
        binding.dimOverlay.animate().alpha(0.5f).setDuration(150).start();
        box.setPivotX(0f);
        box.setPivotY(0f);
        box.setScaleX(0.9f);
        box.setScaleY(0.9f);
        box.setAlpha(0f);
        pw.showAtLocation(binding.cardSuManager,
                android.view.Gravity.TOP | android.view.Gravity.START, x, y);
        box.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private void selectSuManager(SuManagerEntry e) {
        mDeCtx.getSharedPreferences("dfroot", MODE_PRIVATE)
                .edit().putString("su_manager", e.packageName).apply();
        suManagerPkg = e.packageName;
        suManagerLabel = e.label;
        binding.suManagerSubtitle.setText(e.label + "  (" + e.packageName + ")");
        binding.suManagerSubtitle.setTextColor(0xFF9E9E9E);
        updateRunButton();
    }

    private boolean canRun() {
        return suManagerPkg != null && !failedRun && !new File("/dev/df").exists();
    }

    private void updateRunButton() {
        if (running) return;
        if (new File("/dev/df").exists()) {
            setRootedState(true);
            return;
        }
        if (failedRun) {
            setFailedState();
            return;
        }
        boolean ok = suManagerPkg != null;
        binding.btnRun.setEnabled(ok);
        binding.btnRun.setText("Run exploit");
        binding.btnRun.setTextColor(ok ? 0xFFE0E0E0 : 0xFF6E6E6E);
        binding.btnRun.setBackgroundTintList(ColorStateList.valueOf(ok ? 0xFF7A7A7A : 0xFF1F1F1F));
        binding.btnRun.setStrokeColor(ColorStateList.valueOf(ok ? 0xFFA6A6A6 : 0xFF1F1F1F));
    }

    private void refreshRecovery() {
        D2Status.Result r = D2Status.read(this);
        SharedPreferences sp = d2Prefs();
        dropStaleVault(sp);
        boolean armed = sp.getBoolean(PREF_D2_ARMED, false);
        boolean canAsk = D2Writer.suPresent() && r.supported;
        D2Writer.Vault vault = canAsk ? D2Writer.status(this) : loadVault(sp);
        D2Writer.Vault answered = autorunAtRoot(sp, canAsk, vault);
        if (canAsk) saveVault(sp, vault);
        if (answered != vault) saveVault(sp, answered);
        final D2Writer.Vault v = answered;
        mMain.post(() -> {
            mOdin = r;
            mVault = v;
            applyRecoveryDot(r, v, armed);
        });
    }

    private D2Writer.Vault autorunAtRoot(SharedPreferences sp, boolean canAsk, D2Writer.Vault v) {
        if (!canAsk || mAutorunTried) return v;
        if (!sp.getBoolean("expert_mode", false) || !sp.getBoolean(PREF_D2_AUTO, false)) return v;
        if (v != null && v.ok && v.atCommand == 1) return v;
        mAutorunTried = true;
        mMain.post(() -> appendLog("recovery: autorun writer - this root has no authorisation byte, writing it"));
        D2Writer.Vault written = D2Writer.authorize(this);
        if (written != null) mMain.post(() -> appendLog("recovery: " + verdictLine(true, written)));
        return written != null ? written : v;
    }

    private boolean bootPathEnabled() {
        return getPackageManager().getComponentEnabledSetting(
                new ComponentName(this, BootReceiver.class)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    private void applyRecoveryDot(D2Status.Result r, D2Writer.Vault v, boolean armed) {
        final int lime = 0xFFAEEA00;
        final int red = 0xFFE57373;
        final int grey = 0xFF6E6E6E;
        final int amber = 0xFFFFB300;

        int dot;
        if (v != null && v.ok) {
            dot = v.gated() ? red : lime;
        } else if (armed) {
            dot = amber;
        } else {
            dot = grey;
        }
        binding.odinDot.setBackgroundTintList(ColorStateList.valueOf(dot));
    }

    private void writeDmcFlag(boolean toOn, java.util.function.Consumer<D2Writer.Vault> onDone) {
        if (mOdin != null && !mOdin.supported) {
            onDone.accept(mVault);
            return;
        }
        SharedPreferences sp = d2Prefs();
        sp.edit().putBoolean(PREF_D2_ARMED, toOn).apply();
        mExec.execute(() -> {
            D2Writer.Vault v = toOn ? D2Writer.authorize(this) : D2Writer.clear(this);
            saveVault(sp, v);
            mMain.post(() -> {
                mVault = v;
                applyRecoveryDot(mOdin, v, toOn);
                String verdict = verdictLine(toOn, v);
                appendLog("recovery: " + verdict);
                onDone.accept(v);
            });
        });
    }

    private static String verdictLine(boolean armed, D2Writer.Vault v) {
        if (!v.ok) return (armed ? "could not switch on: " : "could not clear: ") + v.state;
        if (armed) return v.atCommand == 1 ? "recovery mode on - download mode authorised" : "not authorised (" + v.state + ")";
        return v.atCommand == 0 ? "recovery mode off - authorisation cleared" : "still authorised (" + v.state + ")";
    }

    private static final String PREF_D2_ARMED = "d2_at_command";
    private static final String PREF_D2_AUTO = "d2_auto";
    private static final String PREF_D2_OK = "d2_vault_ok";
    private static final String PREF_D2_STATE = "d2_vault_state";
    private static final String PREF_D2_AT = "d2_at_command_value";
    private static final String PREF_D2_LOCK = "d2_lock";
    private static final String PREF_D2_MAINT = "d2_maintenance";
    private static final String PREF_D2_DETAIL = "d2_vault_detail";
    private static final String PREF_D2_MONO = "d2_vault_mono";

    private SharedPreferences d2Prefs() {
        return mDeCtx.getSharedPreferences("dfroot", MODE_PRIVATE);
    }

    private void saveVault(SharedPreferences sp, D2Writer.Vault v) {
        sp.edit()
                .putBoolean(PREF_D2_OK, v.ok)
                .putString(PREF_D2_STATE, v.state)
                .putInt(PREF_D2_AT, v.atCommand)
                .putInt(PREF_D2_LOCK, v.lock)
                .putInt(PREF_D2_MAINT, v.maintenance)
                .putString(PREF_D2_DETAIL, v.detail)
                .putLong(PREF_D2_MONO, android.os.SystemClock.elapsedRealtime())
                .apply();
    }

    private static D2Writer.Vault loadVault(SharedPreferences sp) {
        if (!vaultIsFresh(sp)) return null;
        return new D2Writer.Vault(sp.getBoolean(PREF_D2_OK, false),
                sp.getString(PREF_D2_STATE, ""),
                sp.getInt(PREF_D2_AT, -1),
                sp.getInt(PREF_D2_LOCK, -1),
                sp.getInt(PREF_D2_MAINT, -1),
                sp.getString(PREF_D2_DETAIL, ""));
    }

    private static boolean vaultIsFresh(SharedPreferences sp) {
        long stamp = sp.getLong(PREF_D2_MONO, -1L);
        return stamp >= 0 && stamp <= android.os.SystemClock.elapsedRealtime();
    }

    private void dropStaleVault(SharedPreferences sp) {
        if (vaultIsFresh(sp)) return;
        if (!sp.getBoolean(PREF_D2_ARMED, false) && !sp.contains(PREF_D2_STATE)) return;
        sp.edit().remove(PREF_D2_ARMED)
                .remove(PREF_D2_STATE).remove(PREF_D2_OK).remove(PREF_D2_AT)
                .remove(PREF_D2_LOCK).remove(PREF_D2_MAINT).remove(PREF_D2_DETAIL)
                .remove(PREF_D2_MONO).apply();
    }

    private void showRecoveryPopup() {
        D2Status.Result r = mOdin;
        if (r == null) {
            r = D2Status.read(this);
            mOdin = r;
        }
        float md = getResources().getDisplayMetrics().density;
        final D2Status.Result shown = r;
        final SharedPreferences sp = d2Prefs();
        dropStaleVault(sp);
        final D2Writer.Vault vault = mVault;

        final android.widget.TextView[] verdictValue = new android.widget.TextView[1];

        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.popup_bg);
        box.setPadding(0, (int) (6 * md), 0, (int) (6 * md));

        box.addView(odinRow("DL/Recovery", dlRecoveryLabel(shown, vault), verdictValue), odinRowLp(md));
        box.addView(odinSep(md), odinSepLp(md));
        box.addView(odinRow("Lockscreen", shown.lockscreenLabel()), odinRowLp(md));
        box.addView(odinSep(md), odinSepLp(md));
        box.addView(odinRow("Maintenance Mode", shown.maintenanceLabel()), odinRowLp(md));
        box.addView(odinSep(md), odinSepLp(md));
        box.addView(odinRow("Device", shown.deviceLabel()), odinRowLp(md));

        final boolean writerLive = expertMode && shown.supported;

        box.addView(odinSep(md), odinSepLp(md));
        final OneUiSwitch[] writeSw = new OneUiSwitch[1];
        final TextView[] writeName = new TextView[1];
        box.addView(odinToggleRow(switchName("Recovery Writer", writerLive, shown.supported),
                vault != null && vault.ok && vault.atCommand == 1, writerLive,
                writeSw, writeName), odinRowLp(md));
        if (writerLive) {
            writeSw[0].setOnCheckedChangeListener((view, want) -> {
                writeSw[0].setEnabled(false);
                writeName[0].setText(want ? "Writing flag ..." : "Clearing flag ...");
                writeDmcFlag(want, answered -> {
                    verdictValue[0].setText(dlRecoveryLabel(shown, answered));
                    writeName[0].setText(switchName("Recovery Writer", writerLive, shown.supported));
                    writeSw[0].setChecked(answered != null && answered.ok && answered.atCommand == 1);
                    writeSw[0].setEnabled(writerLive);
                });
            });
        }

        box.addView(odinSep(md), odinSepLp(md));
        final OneUiSwitch[] autoSw = new OneUiSwitch[1];
        final boolean wantAuto = sp.getBoolean(PREF_D2_AUTO, false);
        String autoName = switchName("Autorun Writer", writerLive, shown.supported);
        if (wantAuto && writerLive && !bootPathEnabled()) autoName += " (at reroot)";
        box.addView(odinToggleRow(autoName, wantAuto, writerLive, autoSw, null), odinRowLp(md));
        autoSw[0].setOnCheckedChangeListener((view, checked) -> sp.edit().putBoolean(PREF_D2_AUTO, checked).apply());

        int pw_w = Math.min((int) (300 * md), getResources().getDisplayMetrics().widthPixels - (int) (16 * md));
        showToolbarCard(box, pw_w, md);
    }

    private android.widget.PopupWindow showToolbarCard(View box, int pw_w, float md) {
        box.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 26 * md);
            }
        });
        box.setClipToOutline(true);
        box.setElevation(72 * md);

        int[] loc = new int[2];
        binding.menuAnchor.getLocationOnScreen(loc);
        int cardRight = loc[0] + binding.menuAnchor.getWidth();
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int room = (int) (40 * md);
        int roomLeft = Math.max(0, cardRight - pw_w);
        int roomRight = Math.max(0, screenW - cardRight);
        int padL = Math.min(room, roomLeft);
        int padR = Math.min(room, roomRight);
        int padT = Math.min(room, Math.max(0, loc[1] + (int) (2 * md)));

        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setPadding(padL, padT, padR, room);
        frame.addView(box, new android.widget.FrameLayout.LayoutParams(
                pw_w, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

        final android.widget.PopupWindow pw = new android.widget.PopupWindow(frame,
                pw_w + padL + padR, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        pw.setOutsideTouchable(true);
        frame.setOnTouchListener((view, ev) -> {
            if (ev.getAction() != android.view.MotionEvent.ACTION_DOWN) return false;
            int[] at = new int[2];
            box.getLocationOnScreen(at);
            boolean onCard = ev.getRawX() >= at[0] && ev.getRawX() < at[0] + box.getWidth()
                    && ev.getRawY() >= at[1] && ev.getRawY() < at[1] + box.getHeight();
            if (!onCard) pw.dismiss();
            return !onCard;
        });
        pw.setOnDismissListener(() ->
                binding.dimOverlay.animate().alpha(0f).setDuration(150)
                        .withEndAction(() -> binding.dimOverlay.setVisibility(View.GONE)).start());

        binding.dimOverlay.setVisibility(View.VISIBLE);
        binding.dimOverlay.setAlpha(0f);
        binding.dimOverlay.animate().alpha(0.75f).setDuration(150).start();
        box.setPivotX(pw_w);
        box.setPivotY(0f);
        box.setScaleX(0.85f);
        box.setScaleY(0.9f);
        box.setAlpha(0f);
        pw.showAtLocation(binding.menuAnchor,
                android.view.Gravity.TOP | android.view.Gravity.START,
                cardRight - pw_w - padL, loc[1] + (int) (2 * md) - padT);
        box.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
        return pw;
    }

    private android.widget.LinearLayout odinRow(String label, String value) {
        return odinRow(label, value, null);
    }

    private android.widget.LinearLayout odinRow(String label, String value, android.widget.TextView[] slot) {
        float md = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        row.setPadding((int) (20 * md), 0, (int) (20 * md), 0);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(0xFF9E9E9E);
        name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        row.addView(name, new android.widget.LinearLayout.LayoutParams(
                0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView state = new TextView(this);
        state.setText(value);
        state.setTextColor(0xFFE8E8E8);
        state.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        row.addView(state, new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        if (slot != null) slot[0] = state;
        return row;
    }

    private android.widget.LinearLayout odinToggleRow(String label, boolean checked,
                                                      boolean enabled,
                                                      OneUiSwitch[] switchSlot,
                                                      android.widget.TextView[] labelSlot) {
        float md = getResources().getDisplayMetrics().density;
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        row.setPadding((int) (20 * md), 0, (int) (20 * md), 0);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(enabled ? 0xFF9E9E9E : 0xFF6F6F6F);
        name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        row.addView(name, new android.widget.LinearLayout.LayoutParams(
                0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        OneUiSwitch sw = new OneUiSwitch(this);
        sw.setCheckedQuiet(checked);
        sw.setEnabled(enabled);
        if (!enabled) sw.setAlpha(0.4f);
        row.addView(sw, new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

        row.setOnClickListener(v -> sw.performClick());
        if (switchSlot != null) switchSlot[0] = sw;
        if (labelSlot != null) labelSlot[0] = name;
        return row;
    }

    private static android.widget.LinearLayout.LayoutParams odinRowLp(float md) {
        return new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, (int) (46 * md));
    }

    private android.view.View odinSep(float md) {
        android.view.View sep = new android.view.View(this);
        sep.setBackgroundColor(0xFF3F3F3F);
        return sep;
    }

    private static android.widget.LinearLayout.LayoutParams odinSepLp(float md) {
        android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(1, (int) md));
        lp.setMargins((int) (20 * md), 0, (int) (20 * md), 0);
        return lp;
    }

    private static String dlRecoveryLabel(D2Status.Result est, D2Writer.Vault v) {
        if (!est.supported) return "Not gated here";
        if (v != null && v.ok) return v.gated() ? "D2 Blocked" : "Allowed";
        return "Unknown";
    }

    private static String switchName(String base, boolean live, boolean supported) {
        if (!supported) return base + " (no gate)";
        return live ? base : base + " (Expert)";
    }

    private void setFailedState() {
        failedRun = true;
        binding.twoStep.setFailed(true);
        binding.twoStep.setSeg1(seg1, "Failure");
        binding.twoStep.setSeg2(1f, "Reboot", 0xFFE57373);
        binding.btnRun.setEnabled(false);
        binding.btnRun.setText("Run exploit");
        binding.btnRun.setTextColor(0xFF6E6E6E);
        binding.btnRun.setBackgroundTintList(ColorStateList.valueOf(0xFF1F1F1F));
        binding.btnRun.setStrokeColor(ColorStateList.valueOf(0xFF1F1F1F));
        setCompactButton(false, false);
    }

    private void setRootedState(boolean rooted) {
        runArmed = false;
        boolean ok = !rooted && canRun();
        int fg = (rooted || !ok) ? 0xFF6E6E6E : 0xFFE0E0E0;
        int bg = (rooted || !ok) ? 0xFF1F1F1F : 0xFF7A7A7A;
        int stroke = (rooted || !ok) ? 0xFF1F1F1F : 0xFFA6A6A6;
        binding.btnRun.setEnabled(ok);
        binding.btnRun.setText(rooted ? "Rooted" : "Run exploit");
        binding.btnRun.setTextColor(fg);
        binding.btnRun.setBackgroundTintList(ColorStateList.valueOf(bg));
        binding.btnRun.setStrokeColor(ColorStateList.valueOf(stroke));
        setCompactButton(rooted, rooted);
    }

    private void setCompactButton(boolean compact, boolean ksuLit) {
        float measured = ((View) binding.btnRun.getParent()).getWidth();
        if (measured <= 0) measured = getResources().getDisplayMetrics().widthPixels;
        final float parentW = measured;
        float density = getResources().getDisplayMetrics().density;
        float target = 0.48f;
        if (compact) {
            float extrasPx = (KSU_GAP_DP + KSU_CIRCLE_DP) * density;
            target = Math.max(0.20f, 0.48f - extrasPx / parentW);
        }
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(pillPercent, target);
        a.setDuration(250);
        a.addUpdateListener(anim -> {
            pillPercent = (float) anim.getAnimatedValue();
            applyRunPillGeometry(parentW);
        });
        a.start();
        int ksuPillPx = (int) (target * parentW);
        int ksuCirclePx = (int) (KSU_CIRCLE_DP * density);
        binding.btnKsu.setEnabled(ksuLit);
        boolean wasVisible = binding.btnKsu.getVisibility() == View.VISIBLE && binding.btnKsu.getAlpha() > 0.99f;
        binding.btnKsu.setBackgroundTintList(ColorStateList.valueOf(ksuLit ? 0xFFB0B0B0 : 0xFF1F1F1F));
        binding.ksuChevron.setImageTintList(ColorStateList.valueOf(ksuLit ? 0xFF1F1F1F : 0xFF6E6E6E));
        if (compact && !wasVisible) {
            setKsuWidth(ksuCirclePx);
            binding.ksuLabel.setAlpha(0f);
            binding.btnKsu.setVisibility(View.VISIBLE);
            binding.btnKsu.setAlpha(0f);
            binding.btnKsu.setScaleX(0.6f);
            binding.btnKsu.setScaleY(0.6f);
            binding.btnKsu.postDelayed(() -> binding.btnKsu.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f).setDuration(180)
                    .withEndAction(() -> {
                        settleKsuTint(ksuLit);
                        morphKsuToPill(ksuLit, ksuPillPx, ksuCirclePx);
                    }).start(), 150);
        } else if (compact) {
            settleKsuTint(ksuLit);
            morphKsuToPill(ksuLit, ksuPillPx, ksuCirclePx);
        } else {
            binding.ksuLabel.animate().alpha(0f).setDuration(150).start();
            binding.btnKsu.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        binding.btnKsu.setVisibility(View.GONE);
                        setKsuWidth(ksuCirclePx);
                    })
                    .start();
        }
    }

    private void settleKsuTint(boolean ksuLit) {
        if (!ksuLit) return;
        android.animation.ValueAnimator g = android.animation.ValueAnimator.ofFloat(0f, 1f);
        g.setStartDelay(250);
        g.setDuration(300);
        g.addUpdateListener(anim -> binding.btnKsu
                .setBackgroundTintList(ColorStateList.valueOf(
                        mixColor(0xFFB0B0B0, 0xFF9A9A9E, (float) anim.getAnimatedValue()))));
        g.start();
    }

    private void morphKsuToPill(boolean lit, int pillPx, int circlePx) {
        int from = binding.btnKsu.getWidth();
        int to = lit ? pillPx : circlePx;
        if (from <= 0 || from == to) return;
        if (ksuMorph != null && ksuMorph.isRunning()) ksuMorph.cancel();
        ksuMorph = android.animation.ValueAnimator.ofInt(from, to);
        ksuMorph.setDuration(250);
        ksuMorph.setInterpolator(new android.view.animation.DecelerateInterpolator());
        ksuMorph.addUpdateListener(anim -> setKsuWidth((int) anim.getAnimatedValue()));
        ksuMorph.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                binding.btnKsu.requestLayout();
            }
        });
        ksuMorph.start();
        binding.ksuLabel.animate().alpha(lit ? 1f : 0f)
                .setStartDelay(lit ? 120 : 0).setDuration(220).start();
    }

    private void applyRunPillGeometry(float parentW) {
        if (parentW <= 0) parentW = ((View) binding.btnRun.getParent()).getWidth();
        if (parentW <= 0) parentW = getResources().getDisplayMetrics().widthPixels;
        android.widget.RelativeLayout.LayoutParams lp =
                (android.widget.RelativeLayout.LayoutParams) binding.btnRun.getLayoutParams();
        lp.leftMargin = (int) (PILL_INSET_FRACTION * parentW);
        lp.width = (int) (pillPercent * parentW);
        binding.btnRun.setLayoutParams(lp);
        android.widget.RelativeLayout.LayoutParams shareLp =
                (android.widget.RelativeLayout.LayoutParams) binding.btnShareLog.getLayoutParams();
        if (shareLp.width > 0) {
            float gap = KSU_GAP_DP * getResources().getDisplayMetrics().density;
            shareLp.leftMargin = Math.max(0, lp.leftMargin - Math.round(gap) - shareLp.width);
            binding.btnShareLog.setLayoutParams(shareLp);
        }
    }

    private void setKsuWidth(int px) {
        android.widget.RelativeLayout.LayoutParams lp =
                (android.widget.RelativeLayout.LayoutParams) binding.btnKsu.getLayoutParams();
        lp.width = px;
        binding.btnKsu.setLayoutParams(lp);
    }

    private void openKsu() {
        if (suManagerPkg == null) return;
        Intent launch = getPackageManager().getLaunchIntentForPackage(suManagerPkg);
        if (launch == null) {
            Log.i(TAG, "no launch intent for " + suManagerPkg);
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
    }

    private static int mixColor(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000
                | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8)
                | Math.round(ab + (bb - ab) * t);
    }

    private void shareLog() {
        String log = logBuffer.length() > 0 ? logBuffer.toString() : readLastLog();
        if (log.trim().isEmpty()) return;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, "dirtyfrag_log.txt");
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                    out.write(log.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "save log failed", e);
        }
        try {
            startActivity(new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS));
        } catch (Exception e) {
            Log.e(TAG, "open downloads failed", e);
        }
    }

    private void runExploit() {
        SharedPreferences sp = mDeCtx.getSharedPreferences("dfroot", MODE_PRIVATE);
        boolean autoSoftReboot = sp.getBoolean("auto_soft_reboot", false);
        sp.edit().putBoolean("soft_reboot", autoSoftReboot).apply();
        lastFailReason = null;
        try {
            int rc = ExploitRunner.run(mDeCtx, this);
            if (rc != 0) {
                String why = lastFailReason != null ? lastFailReason
                        : rc == 1 ? "ksud nonzero exit"
                        : rc == 2 ? "check logcat & dmesg"
                        : "failed to patch files";
                report("\n=== exploit failed: " + why + " ===\n");
            }
            mDeCtx.getSharedPreferences("dfroot", MODE_PRIVATE)
                    .edit().putBoolean("last_run_success", rc == 0).apply();
        } catch (Exception e) {
            Log.e(TAG, "exploit exception", e);
            report("\nexception: " + e + "\n");
        } finally {
            mMain.post(() -> {
                running = false;
                boolean rooted = new File("/dev/df").exists();
                if (rooted) {
                    setRootedState(true);
                    binding.twoStep.setSeg2(1f, "Verified", 0xFFFFFFFF);
                } else {
                    setFailedState();
                    setRootedState(false);
                }
                updateLogVisibility();
                if (rooted) mExec.execute(MainActivity.this::refreshRecovery);
            });
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Log.i(TAG, "no browser for " + url);
        }
    }

}
