package com.example.fitplan;

import static com.example.fitplan.Store.id;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity {
    static final int BG = Color.rgb(12, 14, 17),
            CARD = Color.rgb(29, 32, 38),
            FG = Color.rgb(241, 245, 242),
            MUTED = Color.rgb(162, 173, 168),
            ACCENT = Color.rgb(184, 242, 97);
    Store store;
    LinearLayout content;
    android.app.AlertDialog activeDialog;
    String page = "home", detailId = "", metric = "sessions", exerciseId = "";
    String progressReturn = "history", manualReturn = "dailyPlan";
    long selectedWeek;
    JSONObject dailyDraft;
    JSONObject manualHistoryDraft;
    Runnable captureDaily;
    TextView timerLabel;
    TextView elapsedLabel;
    final List<CoachModels.ChatMessage> coachMessages = new ArrayList<>();
    boolean coachProfileExpanded = false;
    boolean coachSending = false;
    int coachEpoch = 0;
    String coachLastPrompt = "";
    private String cachedCoachSkill;
    private Context localized;
    private int generation;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService coachWorker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker =
            new Runnable() {
                public void run() {
                    JSONObject active = store.first("active");
                    if (timerLabel != null) {
                        long left =
                                active == null
                                        ? 0
                                        : Math.max(
                                                0,
                                                (active.optLong("restUntil")
                                                                - System.currentTimeMillis()
                                                                + 999)
                                                        / 1000);
                        timerLabel.setText(
                                left > 0
                                        ? s(
                                                "rest_left",
                                                String.format(
                                                        locale(), "%d:%02d", left / 60, left % 60))
                                        : s("rest_done"));
                    }
                    if (elapsedLabel != null && active != null) {
                        long elapsed =
                                Math.max(0, System.currentTimeMillis() - active.optLong("started"));
                        elapsedLabel.setText(
                                s(
                                        "workout_elapsed",
                                        String.format(
                                                locale(),
                                                "%d:%02d",
                                                elapsed / 60000,
                                                (elapsed / 1000) % 60)));
                    }
                    handler.postDelayed(this, 1000);
                }
            };

    @Override
    protected void onCreate(Bundle state) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(state);
        updateLocale();
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);
        View brand = findViewById(R.id.app_brand);
        LockableScrollView scroll = findViewById(R.id.scroller);
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> updateBrand(brand, y));
        brand.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        updateBrand(brand, scroll.getScrollY()));
        WindowCompat.getInsetsController(getWindow(), findViewById(R.id.main))
                .setAppearanceLightStatusBars(false);
        WindowCompat.getInsetsController(getWindow(), findViewById(R.id.main))
                .setAppearanceLightNavigationBars(false);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {
                    Insets bars =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                                            | WindowInsetsCompat.Type.ime());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
        store = new Store(this);
        store.seed();
        content = findViewById(R.id.content);
        selectedWeek = ReportEngine.weekStart(System.currentTimeMillis(), TimeZone.getDefault());
        if (state != null) {
            page = state.getString("page", "home");
            detailId = state.getString("detail", "");
            progressReturn = state.getString("progressReturn", "history");
            manualReturn = state.getString("manualReturn", "dailyPlan");
            selectedWeek = state.getLong("week", selectedWeek);
            metric = state.getString("metric", "sessions");
            exerciseId = state.getString("exercise", "");
            try {
                String draft = state.getString("draft");
                if (draft != null) dailyDraft = new JSONObject(draft);
                String manual = state.getString("manualHistoryDraft");
                if (manual != null) manualHistoryDraft = new JSONObject(manual);
            } catch (JSONException ignored) {
            }
        }
        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new OnBackPressedCallback(true) {
                            public void handleOnBackPressed() {
                                navigateBack();
                            }
                        });
        show(page);
    }

    private void updateBrand(View brand, int scrollY) {
        float height = Math.max(1, brand.getHeight());
        float progress = Math.max(0, Math.min(1, scrollY / height));
        float eased = progress * progress * (3 - 2 * progress);
        // Follow the scroll directly: no queued animations, relayouts, or scroll resets.
        brand.setPivotX(brand.getPaddingLeft());
        brand.setPivotY(height / 2);
        brand.setScaleX(1 - .35f * eased);
        brand.setScaleY(1 - .35f * eased);
        brand.setAlpha(1 - eased);
        // A slight parallax keeps the shrinking text visible until it has faded out.
        brand.setTranslationY(Math.min(Math.max(0, scrollY), height) * .5f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(ticker);
        if (page.equals("home")) show("home");
    }

    @Override
    protected void onPause() {
        captureDraft();
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        captureDraft();
        state.putString("page", page);
        state.putString("detail", detailId);
        state.putString("progressReturn", progressReturn);
        state.putString("manualReturn", manualReturn);
        state.putLong("week", selectedWeek);
        state.putString("metric", metric);
        state.putString("exercise", exerciseId);
        if (dailyDraft != null) state.putString("draft", dailyDraft.toString());
        if (manualHistoryDraft != null)
            state.putString("manualHistoryDraft", manualHistoryDraft.toString());
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        generation++;
        clearCoachSession();
        worker.shutdownNow();
        coachWorker.shutdownNow();
        handler.removeCallbacksAndMessages(null);
        store.close();
        super.onDestroy();
    }

    void clearCoachSession() {
        coachEpoch++;
        coachSending = false;
        coachLastPrompt = "";
        coachMessages.clear();
    }

    void runCoachTask(Runnable task) {
        coachWorker.execute(task);
    }

    String coachSkillMarkdown() {
        if (cachedCoachSkill == null) {
            cachedCoachSkill = GlmClient.loadSkillMarkdown(this);
        }
        return cachedCoachSkill;
    }

    private void captureDraft() {
        if (captureDaily != null) captureDaily.run();
    }

    String language() {
        return getSharedPreferences("preferences", MODE_PRIVATE).getString("language", "zh");
    }

    Locale locale() {
        String value = language();
        if (value.equals("system"))
            value = Locale.getDefault().getLanguage().equals("zh") ? "zh" : "en";
        return value.equals("en") ? Locale.ENGLISH : Locale.SIMPLIFIED_CHINESE;
    }

    private void updateLocale() {
        Configuration c = new Configuration(getResources().getConfiguration());
        c.setLocale(locale());
        localized = createConfigurationContext(c);
    }

    void setLanguage(String language) {
        captureDraft();
        getSharedPreferences("preferences", MODE_PRIVATE)
                .edit()
                .putString("language", language)
                .apply();
        updateLocale();
        show(page);
    }

    String s(String key, Object... args) {
        return localized.getResources().getString(TextResources.id(key), args);
    }

    String num(double n) {
        NumberFormat f = NumberFormat.getNumberInstance(locale());
        f.setMaximumFractionDigits(1);
        f.setGroupingUsed(false);
        return f.format(n);
    }

    String day() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    String displayDay() {
        return DateFormat.getDateInstance(DateFormat.MEDIUM, locale()).format(new Date());
    }

    String date(long time) {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale())
                .format(new Date(time));
    }

    String shortDate(long time) {
        return new SimpleDateFormat("MM/dd", locale()).format(new Date(time));
    }

    String partName(String value) {
        return s(Catalog.partCode(value));
    }

    String[] partLabels() {
        String[] labels = new String[Catalog.PARTS.length];
        for (int i = 0; i < labels.length; i++) labels[i] = s(Catalog.PARTS[i]);
        return labels;
    }

    boolean containsPart(JSONArray values, String part) {
        for (int i = 0; i < values.length(); i++)
            if (Catalog.partCode(values.optString(i)).equals(part)) return true;
        return false;
    }

    String exerciseName(JSONObject value) {
        int id = Catalog.builtIn(value.optString("exerciseId", value.optString("id")));
        return id < 0 ? value.optString("name") : s("ex" + id);
    }

    String planName(JSONObject value) {
        String demoKey =
                DemoLabels.titleKey(value.optString("demoDataset"), value.optString("name"));
        if (demoKey != null) return s(demoKey);
        String name = value.optString("name");
        boolean auto =
                value.optBoolean("autoName")
                        || (!value.has("autoName")
                                && name.matches("\\d{4}-\\d{2}-\\d{2} 今日计划")
                                && value.has("checkin"));
        if (!auto) return name;
        JSONObject check = value.optJSONObject("checkin");
        String date = check == null ? "" : check.optString("day");
        try {
            Date parsed = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date);
            if (parsed != null)
                date = DateFormat.getDateInstance(DateFormat.MEDIUM, locale()).format(parsed);
        } catch (ParseException ignored) {
        }
        return s("daily_plan", date);
    }

    String sessionNotes(JSONObject session, JSONObject check) {
        String notes = check.optString("notes");
        return "eightweeks-v1".equals(session.optString("demoDataset"))
                        && DemoLabels.NOTE.equals(notes)
                ? s("demo_note")
                : notes;
    }

    String reason(JSONObject row) {
        if (row.optBoolean("userConfirmed")) {
            String current =
                    s(
                            "reason_user",
                            num(row.optDouble("weight")),
                            row.optInt("sets"),
                            row.optInt("reps"));
            JSONObject original = row.optJSONObject("originalPrescription");
            if (original != null
                    && (Double.compare(original.optDouble("weight"), row.optDouble("weight")) != 0
                            || original.optInt("sets") != row.optInt("sets")
                            || original.optInt("reps") != row.optInt("reps")))
                current +=
                        "\n"
                                + s(
                                        "generated"
                                                        .equals(
                                                                row.optString(
                                                                        "originalPrescriptionSource"))
                                                ? "reason_original"
                                                : "reason_before_edit",
                                        num(original.optDouble("weight")),
                                        original.optInt("sets"),
                                        original.optInt("reps"));
            return current;
        }
        String code = row.optString("reasonCode");
        String legacy = row.optString("reason");
        if (code.isEmpty())
            code =
                    legacy.startsWith("暂无历史")
                            ? "first"
                            : legacy.startsWith("采用")
                                    ? "up"
                                    : legacy.startsWith("跟随") ? "down" : "same";
        if (!Arrays.asList("first", "up", "down", "same", "comparable").contains(code))
            code = "same";
        String result = s("reason_" + code);
        JSONObject reference = row.optJSONObject("reference");
        if (code.equals("comparable") && reference != null)
            result +=
                    "\n"
                            + s(
                                    "reason_reference",
                                    date(reference.optLong("finished")),
                                    num(reference.optDouble("weight")),
                                    reference.optInt("sets"),
                                    reference.optInt("reps"));
        return result
                + ((row.optBoolean("reduced") || legacy.contains("恢复状态"))
                        ? s("reason_recovery")
                        : "")
                + ((row.optBoolean("timeLimited") || legacy.contains("可用时间"))
                        ? s("reason_time")
                        : "");
    }

    int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    GradientDrawable shape(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        return bg;
    }

    LinearLayout column() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(12), dp(16), dp(12));
        return c;
    }

    LinearLayout card(LinearLayout parent) {
        LinearLayout c = column();
        c.setBackground(shape(CARD, 24));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(4));
        parent.addView(c, lp);
        return c;
    }

    TextView text(LinearLayout parent, String value, int size) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(size <= 14 ? MUTED : FG);
        v.setPadding(0, dp(6), 0, dp(6));
        parent.addView(v);
        return v;
    }

    Button button(LinearLayout parent, String label, Runnable action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(ACCENT);
        b.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(43, 52, 39)));
        b.setMinHeight(dp(40));
        parent.addView(b, new LinearLayout.LayoutParams(-1, -2));
        b.setOnClickListener(v -> action.run());
        return b;
    }

    void openPlan(JSONObject plan) {
        detailId = plan.optString("id");
        show("planDetail");
    }

    void openHistory(JSONObject session) {
        detailId = session.optString("id");
        show("historyDetail");
    }

    JSONObject find(String kind, String id) {
        JSONArray items = store.list(kind);
        for (int i = 0; i < items.length(); i++)
            if (id.equals(items.optJSONObject(i).optString("id"))) return items.optJSONObject(i);
        return null;
    }

    void navigateBack() {
        if (page.equals("manualHistory")) show(manualReturn);
        else if (page.equals("progress")) show(progressReturn);
        else if (page.equals("planDetail") || page.equals("workout")) show("plans");
        else if (page.equals("plans") || page.equals("library")) show("dailyPlan");
        else if (page.equals("historyDetail")) show("history");
        else if (!page.equals("home")) show("home");
        else finish();
    }

    void show(String route) {
        if (route.equals("weekly")) route = "home";
        if (route.equals("progress") && !page.equals("progress"))
            progressReturn =
                    page.equals("home")
                            ? "home"
                            : page.equals("historyDetail") ? "historyDetail" : "history";
        if (route.equals("manualHistory") && !page.equals("manualHistory"))
            manualReturn = page.equals("plans") || page.equals("library") ? page : "dailyPlan";
        captureDraft();
        captureDaily = null;
        timerLabel = null;
        elapsedLabel = null;
        if (!route.equals("coach")) clearCoachSession();
        generation++;
        page = route;
        content.removeAllViews();
        LinearLayout homeActions = findViewById(R.id.page_actions);
        homeActions.removeAllViews();
        homeActions.setVisibility(View.GONE);
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(content.getWindowToken(), 0);
        LockableScrollView scroller = (LockableScrollView) findViewById(R.id.scroller);
        scroller.setScrollable(!route.equals("workout"));
        scroller.scrollTo(0, 0);
        renderNavigation();
        switch (route) {
            case "dailyPlan":
                new DailyPlanScreen(this).today();
                break;
            case "plans":
                new TrainingScreen(this).plans();
                break;
            case "library":
                new TrainingScreen(this).exercises();
                break;
            case "workout":
                new TrainingScreen(this).workout();
                break;
            case "planDetail":
                {
                    JSONObject p = find("plan", detailId);
                    if (p == null) show("plans");
                    else new TrainingScreen(this).editPlan(p);
                    break;
                }
            case "manualHistory":
                new ManualHistoryScreen(this).render();
                break;
            case "history":
                new RecordsScreen(this).history();
                break;
            case "historyDetail":
                {
                    JSONObject h = find("history", detailId);
                    if (h == null) show("history");
                    else new RecordsScreen(this).historyDetail(h);
                    break;
                }
            case "progress":
                new ProgressScreen(this).render();
                break;
            case "coach":
                new CoachScreen(this).render();
                break;
            case "profile":
                new ProfileScreen(this).render();
                break;
            default:
                page = "home";
                new HomeScreen(this).render();
        }
    }

    private void renderNavigation() {
        LinearLayout nav = findViewById(R.id.navigation);
        nav.removeAllViews();
        nav.setBackground(shape(Color.rgb(36, 40, 46), 32));
        String group =
                (page.equals("home") || (page.equals("progress") && progressReturn.equals("home")))
                        ? "home"
                        : page.equals("coach")
                                ? "coach"
                                : page.equals("profile")
                                        ? "profile"
                                        : page.equals("history")
                                                        || page.equals("historyDetail")
                                                        || page.equals("progress")
                                                ? "records"
                                                : "training";
        String[] routes = {"home", "dailyPlan", "history", "coach", "profile"},
                labels = {"home", "training", "records", "coach", "profile"};
        for (int i = 0; i < routes.length; i++) {
            final String route = routes[i];
            boolean selected = group.equals(labels[i]);
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(2), dp(8), dp(2), dp(8));
            tab.setBackground(shape(selected ? Color.rgb(58, 67, 43) : Color.TRANSPARENT, 26));
            NavIcon icon = new NavIcon(this, i, selected ? ACCENT : MUTED);
            tab.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));
            TextView label = new TextView(this);
            label.setText(s(labels[i]));
            label.setTextSize(11);
            label.setSingleLine(true);
            label.setTextColor(selected ? ACCENT : MUTED);
            label.setGravity(Gravity.CENTER);
            tab.addView(label);
            tab.setContentDescription(s(labels[i]));
            tab.setSelected(selected);
            tab.setFocusable(true);
            tab.setOnClickListener(v -> show(route));
            nav.addView(tab, new LinearLayout.LayoutParams(0, dp(64), 1));
        }
    }

    void trainingTabs() {
        LinearLayout header = new LinearLayout(this);
        content.addView(header);
        TextView label = text(header, s("training"), 20);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        Button add = button(header, s("manual_add"), () -> show("manualHistory"));
        add.setContentDescription(s("manual_title"));
        add.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        tabs(
                new String[] {"dailyPlan", "plans", "library"},
                new String[] {"daily_plan_tab", "my_plans_tab", "library"});
        if (store.first("active") != null)
            button(content, s("continue_workout"), () -> show("workout"));
    }

    void recordTabs() {
        tabs(new String[] {"history", "progress"}, new String[] {"history", "progress_tab"});
    }

    private LinearLayout tabs(String[] routes, String[] labels) {
        LinearLayout row = new LinearLayout(this);
        content.addView(row);
        for (int i = 0; i < routes.length; i++) {
            final String route = routes[i];
            Button b = button(row, s(labels[i]), () -> show(route));
            b.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            if (page.equals(route)) {
                b.setTextColor(BG);
                b.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
            }
        }
        return row;
    }

    void loadReport(long week, Consumer<ReportEngine.Report> callback, Runnable onError) {
        int token = generation;
        Context application = getApplicationContext();
        long now = System.currentTimeMillis();
        TimeZone zone = TimeZone.getDefault();
        worker.execute(() -> {
            try (Store database = new Store(application)) {
                ReportEngine.Report report =
                        ReportEngine.build(
                                HistoryReader.read(database.list("history")), week, now, zone);
                report.targets = DailyTargetReader.read(database.list("plan"), now, zone);
                handler.post(() -> {
                    if (!isDestroyed() && token == generation) {
                        callback.accept(report);
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    if (!isDestroyed() && token == generation) onError.run();
                });
            }
        });
    }

    private static class NavIcon extends View {
        final int type, color;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        NavIcon(Context c, int type, int color) {
            super(c);
            this.type = type;
            this.color = color;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.save();
            c.scale(getWidth() / 24f, getHeight() / 24f);
            paint.setColor(color);
            paint.setStrokeWidth(1.8f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            if (type == 0) {
                Path p = new Path();
                p.moveTo(3, 10);
                p.lineTo(12, 3);
                p.lineTo(21, 10);
                p.moveTo(5, 9);
                p.lineTo(5, 21);
                p.lineTo(19, 21);
                p.lineTo(19, 9);
                p.moveTo(10, 21);
                p.lineTo(10, 14);
                p.lineTo(14, 14);
                p.lineTo(14, 21);
                c.drawPath(p, paint);
            } else if (type == 1) {
                c.drawLine(6, 12, 18, 12, paint);
                c.drawRoundRect(3, 6, 7, 18, 1, 1, paint);
                c.drawRoundRect(17, 6, 21, 18, 1, 1, paint);
            } else if (type == 2) {
                c.drawLine(4, 21, 21, 21, paint);
                c.drawLine(6, 17, 6, 12, paint);
                c.drawLine(12, 17, 12, 7, paint);
                c.drawLine(18, 17, 18, 3, paint);
            } else if (type == 3) {
                Path bubble = new Path();
                bubble.moveTo(4, 6);
                bubble.lineTo(20, 6);
                bubble.lineTo(20, 17);
                bubble.lineTo(11, 17);
                bubble.lineTo(6, 21);
                bubble.lineTo(6, 17);
                bubble.lineTo(4, 17);
                bubble.close();
                c.drawPath(bubble, paint);
                c.drawLine(12, 8.5f, 12, 14.5f, paint);
                c.drawLine(9, 11.5f, 15, 11.5f, paint);
            } else {
                c.drawCircle(12, 7, 4, paint);
                c.drawArc(4, 13, 20, 27, 180, 180, false, paint);
            }
            c.restore();
        }
    }
}
