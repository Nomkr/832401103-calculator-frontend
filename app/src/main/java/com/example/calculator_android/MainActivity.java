package com.example.calculator_android;

import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Calculator client. Handles keypad input, expression display, backend
 * requests and the history panel. All arithmetic runs on the backend;
 * this activity only renders input and responses.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Calculator";
    private static final String PREFS_NAME = "calculator_preferences";
    private static final String PREF_DARK_MODE = "dark_mode";

    // Ordered fallback list of backend base URLs. The first one that passes
    // the /api/health check is reused for all subsequent requests.
    private static final String[] BACKEND_URLS = {
            "http://192.168.50.12:5000",
            "http://10.0.2.2:5000"
    };
    private volatile String backendUrl = BACKEND_URLS[0];

    private StringBuilder expression = new StringBuilder();

    private TextView tvDisplay;
    private LinearLayout historyPanel;
    private LinearLayout historyList;
    private Button calculateButton;
    private Button themeButton;
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        boolean savedDarkMode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(PREF_DARK_MODE, false);
        AppCompatDelegate.setDefaultNightMode(savedDarkMode
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int contentPadding = dp(16);
            v.setPadding(contentPadding + systemBars.left,
                    contentPadding + systemBars.top,
                    contentPadding + systemBars.right,
                    contentPadding + systemBars.bottom);
            return insets;
        });

        tvDisplay = findViewById(R.id.tv_display);
        historyPanel = findViewById(R.id.history_panel);
        historyList = findViewById(R.id.history_list);
        calculateButton = findViewById(R.id.btn_eq);

        // Digit keys.
        setButton(R.id.btn_0, "0");
        setButton(R.id.btn_1, "1");
        setButton(R.id.btn_2, "2");
        setButton(R.id.btn_3, "3");
        setButton(R.id.btn_4, "4");
        setButton(R.id.btn_5, "5");
        setButton(R.id.btn_6, "6");
        setButton(R.id.btn_7, "7");
        setButton(R.id.btn_8, "8");
        setButton(R.id.btn_9, "9");

        // Operator keys. The UI shows × and ÷; the backend expects * and /.
        setButton(R.id.btn_add, "+");
        setButton(R.id.btn_sub, "-");
        setButton(R.id.btn_mul, "×", "*");
        setButton(R.id.btn_div, "÷", "/");
        setButton(R.id.btn_lparen, "(");
        setButton(R.id.btn_rparen, ")");
        setButton(R.id.btn_dot, ".");

        // Scientific keys.
        setButton(R.id.btn_sqrt, "√");
        setButton(R.id.btn_square, "²");
        setButton(R.id.btn_power, "^");
        setButton(R.id.btn_percent, "%");

        themeButton = findViewById(R.id.btn_theme);
        updateThemeButton();
        themeButton.setOnClickListener(v -> {
            boolean dark = !isDarkMode();
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(PREF_DARK_MODE, dark)
                    .apply();
            AppCompatDelegate.setDefaultNightMode(dark
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO);
        });

        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            expression.setLength(0);
            updateDisplay();
        });

        findViewById(R.id.btn_backspace).setOnClickListener(v -> {
            if (expression.length() > 0) {
                expression.deleteCharAt(expression.length() - 1);
                updateDisplay();
            }
        });

        findViewById(R.id.btn_history).setOnClickListener(v -> {
            boolean show = historyPanel.getVisibility() != View.VISIBLE;
            historyPanel.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) {
                loadHistory();
            }
        });
        findViewById(R.id.btn_clear_history).setOnClickListener(v -> confirmClearHistory());

        findViewById(R.id.btn_eq).setOnClickListener(v -> {
            String expr = expression.toString();
            if (expr.isEmpty()) {
                return;
            }
            calculateButton.setEnabled(false);
            networkExecutor.execute(() -> {
                String resultText = requestCalculate(expr);
                runOnUiThread(() -> {
                    tvDisplay.setText(resultText);
                    calculateButton.setEnabled(true);
                    boolean networkFailed = resultText.startsWith("Request failed: ");
                    if (!networkFailed
                            && resultText.startsWith(displayExpression(expr) + " = ")
                            && historyPanel.getVisibility() == View.VISIBLE) {
                        loadHistory();
                    }
                });
            });
        });

        // Theme changes recreate the activity; restore input, display and
        // history panel state so nothing the user typed or computed is lost.
        if (savedInstanceState != null) {
            expression = new StringBuilder(
                    savedInstanceState.getString("state_expression", ""));
            tvDisplay.setText(savedInstanceState.getString("state_display", "0"));
            if (savedInstanceState.getBoolean("state_history_visible", false)) {
                historyPanel.setVisibility(View.VISIBLE);
                loadHistory();
            }
        }

        checkHealth();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("state_expression", expression.toString());
        outState.putString("state_display", tvDisplay.getText().toString());
        outState.putBoolean("state_history_visible",
                historyPanel.getVisibility() == View.VISIBLE);
    }

    /**
     * Bind a key so it appends {@code expressionText} to the expression,
     * optionally showing a different label on the button.
     */
    private void setButton(int id, String expressionText) {
        setButton(id, expressionText, expressionText);
    }

    private void setButton(int id, String displayText, String expressionText) {
        View button = findViewById(id);
        if (button instanceof Button) {
            ((Button) button).setText(displayText);
        }
        button.setOnClickListener(v -> append(expressionText));
    }

    /** Append one symbol to the expression and refresh the display. */
    private void append(String s) {
        expression.append(s);
        updateDisplay();
    }

    /** Render the current expression, or "0" when empty. */
    private void updateDisplay() {
        String text = expression.toString();
        tvDisplay.setText(text.isEmpty() ? "0" : displayExpression(text));
    }

    /** Convert internal operators (*, /) back to display symbols (×, ÷). */
    private String displayExpression(String text) {
        return text.replace("*", "×").replace("/", "÷");
    }

    private boolean isDarkMode() {
        return (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private void updateThemeButton() {
        if (themeButton != null) {
            themeButton.setText(isDarkMode() ? "Light" : "Dark");
            themeButton.setContentDescription(isDarkMode() ? "Toggle light mode" : "Toggle dark mode");
        }
    }

    /**
     * POST the expression to the backend and return the text to display:
     * the formatted result, the backend's error message, or a network
     * failure hint. Runs on a background thread.
     */
    private String requestCalculate(String expr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(backendUrl + "/api/calculate");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Connection", "close");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setUseCaches(false);
            conn.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("expression", expr);
            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

            // Error responses (>= 400) must be read from getErrorStream().
            int code = conn.getResponseCode();
            InputStream is = (code >= 400) ? conn.getErrorStream() : conn.getInputStream();
            if (is == null) {
                return "Request failed: empty response";
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONObject json = new JSONObject(sb.toString());
            if (json.getBoolean("success")) {
                return displayExpression(expr) + " = " + json.get("result");
            }
            return json.optString("message", "Backend rejected the calculation");
        } catch (org.json.JSONException e) {
            Log.e(TAG, "Backend returned an unparseable response", e);
            return "Request failed: invalid response format";
        } catch (Exception e) {
            Log.e(TAG, "Calculation request failed", e);
            return "Request failed: check if the backend is running";
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** Probe BACKEND_URLS in order and adopt the first one that responds. */
    private void checkHealth() {
        networkExecutor.execute(() -> {
            Exception lastError = null;
            for (String candidate : BACKEND_URLS) {
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(candidate + "/api/health").openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Connection", "close");
                    conn.setUseCaches(false);
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    int code = conn.getResponseCode();
                    Log.d(TAG, "Health check " + candidate + " -> HTTP " + code);
                    if (code == 200) {
                        backendUrl = candidate;
                        return;
                    }
                    lastError = new IllegalStateException("HTTP " + code);
                } catch (Exception e) {
                    lastError = e;
                    Log.w(TAG, "Health check failed: " + candidate, e);
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
            Log.e(TAG, "No backend URL is reachable", lastError);
        });
    }

    /** Fetch history from the backend and render it into the panel. */
    private void loadHistory() {
        networkExecutor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(backendUrl + "/api/history").openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Connection", "close");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                int code = conn.getResponseCode();
                InputStream is = (code >= 400) ? conn.getErrorStream() : conn.getInputStream();
                if (is == null) {
                    throw new IllegalStateException("服务返回空响应");
                }
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
                reader.close();
                JSONObject response = new JSONObject(body.toString());
                JSONArray rows = response.optJSONArray("history");
                runOnUiThread(() -> renderHistory(rows));
            } catch (Exception e) {
                Log.e(TAG, "Failed to load history", e);
                runOnUiThread(() -> {
                    historyList.removeAllViews();
                    addHistoryHint("Failed to load history: "
                            + (e.getMessage() == null ? "network error" : e.getMessage()));
                });
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private void renderHistory(JSONArray rows) {
        historyList.removeAllViews();
        if (rows == null || rows.length() == 0) {
            addHistoryHint("No history yet");
        } else {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject item = rows.optJSONObject(i);
                if (item != null) {
                    addHistoryRow(item);
                }
            }
        }
    }

    /** Build one history row: expression, result, timestamp and a delete key. */
    private void addHistoryRow(JSONObject item) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(2), dp(2), dp(2));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.surface));
        bg.setCornerRadius(dp(10));
        row.setBackground(bg);

        TextView value = new TextView(this);
        value.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        value.setText(displayExpression(item.optString("expression")) + " = "
                + item.optString("result") + "\n" + item.optString("created_at"));
        value.setTextColor(getColor(R.color.btn_digit_text));
        value.setTextSize(14);
        value.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);

        Button delete = new Button(this);
        delete.setText("Delete");
        delete.setAllCaps(false);
        delete.setTextSize(13);
        delete.setTextColor(getColor(R.color.btn_util_text));
        delete.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.btn_util_bg)));
        delete.setOnClickListener(v -> deleteHistory(item.optInt("id", -1)));
        row.addView(value);
        row.addView(delete, new LinearLayout.LayoutParams(dp(64), dp(42)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(4));
        historyList.addView(row, params);
    }

    private void addHistoryHint(String text) {
        TextView hint = new TextView(this);
        hint.setText(text);
        hint.setTextColor(getColor(R.color.text_secondary));
        hint.setTextSize(14);
        hint.setPadding(dp(8), dp(12), dp(8), dp(12));
        historyList.addView(hint);
    }

    /** Delete one record via the API, then reload the list. */
    private void deleteHistory(int id) {
        if (id < 0) {
            return;
        }
        networkExecutor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(backendUrl + "/api/history/" + id)
                        .openConnection();
                conn.setRequestMethod("DELETE");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                if (conn.getResponseCode() >= 400) {
                    throw new IllegalStateException("记录不存在");
                }
                runOnUiThread(this::loadHistory);
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete history", e);
                runOnUiThread(() -> Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private void confirmClearHistory() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Clear history")
                .setMessage("Delete all history records?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> clearHistory())
                .show();
    }

    /** Delete all records via the API, then reload the list. */
    private void clearHistory() {
        networkExecutor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(backendUrl + "/api/history")
                        .openConnection();
                conn.setRequestMethod("DELETE");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                if (conn.getResponseCode() >= 400) {
                    throw new IllegalStateException("清空失败");
                }
                runOnUiThread(this::loadHistory);
            } catch (Exception e) {
                Log.e(TAG, "Failed to clear history", e);
                runOnUiThread(() -> Toast.makeText(this, "Clear failed", Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    /** Convert dp to pixels for programmatic views. */
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        networkExecutor.shutdownNow();
        super.onDestroy();
    }
}
