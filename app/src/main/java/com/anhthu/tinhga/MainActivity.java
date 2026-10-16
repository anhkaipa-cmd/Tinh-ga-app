package com.anhthu.tinhga;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int NAVY = Color.rgb(10, 55, 112);
    private static final int BLUE = Color.rgb(22, 135, 248);
    private static final int GREEN = Color.rgb(18, 112, 77);
    private static final int TEXT = Color.rgb(16, 38, 74);
    private static final int MUTED = Color.rgb(109, 128, 159);
    private static final int BORDER = Color.rgb(214, 225, 239);
    private static final int PALE_BLUE = Color.rgb(232, 245, 255);
    private static final int PALE_GREEN = Color.rgb(233, 249, 235);
    private static final int ROW_SELECTED = Color.rgb(240, 248, 255);
    private static final int WARNING = Color.rgb(211, 92, 34);

    private static final int KIND_NONE = 0;
    private static final int KIND_PLAN_EGGS = 1;
    private static final int KIND_PLAN_DOC = 2;
    private static final int KIND_FLOCK = 3;
    private static final int KIND_QTY = 4;
    private static final int KIND_RATE = 5;

    private final String[] defaultFlocks = {"B1", "B4", "B5", "V1", "V6"};
    private final String[] defaultRates = {"73.5", "83.0", "85.5", "90.5", "91.0"};

    private SharedPreferences prefs;
    private EditText plannedEggsInput;
    private EditText plannedDocInput;
    private final CheckBox[] selected = new CheckBox[5];
    private final EditText[] flockInputs = new EditText[5];
    private final EditText[] qtyInputs = new EditText[5];
    private final EditText[] rateInputs = new EditText[5];
    private final TextView[] docOutputs = new TextView[5];
    private final LinearLayout[] rowLayouts = new LinearLayout[5];
    private final boolean[] qtyManual = new boolean[5];
    private final boolean[] qtyAuto = new boolean[5];

    private TextView totalEggsView;
    private TextView totalRateView;
    private TextView totalDocView;
    private TextView diffEggView;
    private TextView diffDocView;
    private TextView keyboardHint;

    private EditText activeField;
    private int activeKind = KIND_NONE;
    private int activeRow = -1;
    private boolean replaceOnNextKey = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(248, 251, 255));
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        prefs = getSharedPreferences("tinh_ga_prefs", MODE_PRIVATE);
        setContentView(buildScreen());
        recalculate();
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(248, 251, 255));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(24));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(buildHeader());
        root.addView(space(8));
        root.addView(buildPlanRow());
        root.addView(space(8));
        root.addView(buildTable());
        root.addView(space(8));
        root.addView(buildDifferences());
        root.addView(space(8));
        root.addView(buildCalculateButton());
        root.addView(space(8));
        root.addView(buildKeyboard());
        return scroll;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(2), dp(2), dp(2), dp(2));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_chick);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        header.addView(icon, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(10), 0, 0, 0);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        titleBox.addView(text("Tính gà", 27, NAVY, true));
        titleBox.addView(text("Tính nhanh số gà nở", 13, MUTED, false));

        Button reset = new Button(this);
        reset.setText("XÓA");
        reset.setAllCaps(false);
        reset.setTextSize(12);
        reset.setTextColor(NAVY);
        reset.setTypeface(Typeface.DEFAULT_BOLD);
        reset.setPadding(dp(8), 0, dp(8), 0);
        reset.setBackground(rounded(Color.WHITE, 13, BORDER, 1));
        reset.setOnClickListener(v -> resetDailyData());
        header.addView(reset, new LinearLayout.LayoutParams(dp(68), dp(42)));
        return header;
    }

    private View buildPlanRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(2f);

        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(94), 1f);
        left.setMarginEnd(dp(5));
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(94), 1f);
        right.setMarginStart(dp(5));
        row.addView(planCard("Trứng dự kiến", true), left);
        row.addView(planCard("DOC dự kiến", false), right);
        return row;
    }

    private LinearLayout planCard(String label, boolean eggs) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.setBackground(rounded(Color.WHITE, 17, Color.rgb(198, 224, 255), 1));
        card.addView(text(label, 13, NAVY, false));

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(22);
        input.setTextColor(eggs ? NAVY : GREEN);
        input.setTypeface(Typeface.DEFAULT_BOLD);
        input.setHint("0");
        input.setHintTextColor(Color.rgb(180, 194, 211));
        input.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        input.setPadding(dp(2), 0, dp(2), 0);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setShowSoftInputOnFocus(false);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setOnClickListener(v -> focusField(input, eggs ? KIND_PLAN_EGGS : KIND_PLAN_DOC, -1));
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) focusField(input, eggs ? KIND_PLAN_EGGS : KIND_PLAN_DOC, -1);
        });
        card.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        if (eggs) plannedEggsInput = input;
        else plannedDocInput = input;
        return card;
    }

    private View buildTable() {
        LinearLayout table = new LinearLayout(this);
        table.setOrientation(LinearLayout.VERTICAL);
        table.setPadding(dp(4), dp(4), dp(4), dp(4));
        table.setBackground(rounded(Color.WHITE, 17, BORDER, 1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(1), dp(4), dp(1), dp(4));
        header.setBackground(rounded(Color.rgb(222, 241, 255), 13, Color.TRANSPARENT, 0));
        header.addView(headerCell("", 0.58f));
        header.addView(headerCell("Flock", 1.15f));
        header.addView(headerCell("Số lượng\ntrứng", 1.55f));
        header.addView(headerCell("Tỷ lệ nở\n(%)", 1.25f));
        header.addView(headerCell("DOC\n(con)", 1.42f));
        table.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        for (int i = 0; i < 5; i++) {
            final int rowIndex = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(1), dp(3), dp(1), dp(3));
            rowLayouts[i] = row;

            CheckBox check = new CheckBox(this);
            check.setText(String.valueOf(i + 1));
            check.setTextSize(12);
            check.setTextColor(NAVY);
            check.setPadding(0, 0, 0, 0);
            check.setButtonTintList(new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{BLUE, Color.rgb(168, 185, 206)}));
            selected[i] = check;
            check.setOnCheckedChangeListener((buttonView, isChecked) -> {
                updateRowHighlights();
                recalculate();
            });
            row.addView(check, weighted(0.58f, dp(50)));

            String savedFlock = prefs.getString("flock_" + i, defaultFlocks[i]);
            EditText flock = tableInput(savedFlock);
            flock.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            flock.setOnClickListener(v -> {
                selected[rowIndex].setChecked(true);
                focusField(flock, KIND_FLOCK, rowIndex);
            });
            flock.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    selected[rowIndex].setChecked(true);
                    focusField(flock, KIND_FLOCK, rowIndex);
                }
            });
            flockInputs[i] = flock;
            row.addView(flock, weighted(1.15f, dp(50)));

            EditText qty = tableInput("");
            qty.setHint("—");
            qty.setInputType(InputType.TYPE_CLASS_NUMBER);
            qty.setOnClickListener(v -> {
                selected[rowIndex].setChecked(true);
                focusField(qty, KIND_QTY, rowIndex);
            });
            qty.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    selected[rowIndex].setChecked(true);
                    focusField(qty, KIND_QTY, rowIndex);
                }
            });
            qtyInputs[i] = qty;
            row.addView(qty, weighted(1.55f, dp(50)));

            String savedRate = prefs.getString("rate_" + i, defaultRates[i]);
            EditText rate = tableInput(savedRate);
            rate.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            rate.setOnClickListener(v -> {
                selected[rowIndex].setChecked(true);
                focusField(rate, KIND_RATE, rowIndex);
            });
            rate.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    selected[rowIndex].setChecked(true);
                    focusField(rate, KIND_RATE, rowIndex);
                }
            });
            rateInputs[i] = rate;
            row.addView(rate, weighted(1.25f, dp(50)));

            TextView doc = text("—", 14, GREEN, true);
            doc.setGravity(Gravity.CENTER);
            doc.setBackground(rounded(PALE_GREEN, 11, Color.TRANSPARENT, 0));
            docOutputs[i] = doc;
            row.addView(doc, weighted(1.42f, dp(50)));

            table.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        }

        LinearLayout total = new LinearLayout(this);
        total.setOrientation(LinearLayout.HORIZONTAL);
        total.setGravity(Gravity.CENTER_VERTICAL);
        total.setPadding(dp(1), dp(3), dp(1), dp(3));

        TextView totalLabel = text("Tổng", 15, Color.WHITE, true);
        totalLabel.setGravity(Gravity.CENTER);
        totalLabel.setBackground(rounded(Color.rgb(43, 83, 128), 11, Color.TRANSPARENT, 0));
        total.addView(totalLabel, weighted(1.73f, dp(52)));

        totalEggsView = totalCell(NAVY, PALE_BLUE);
        totalRateView = totalCell(NAVY, PALE_BLUE);
        totalDocView = totalCell(GREEN, PALE_GREEN);
        total.addView(totalEggsView, weighted(1.55f, dp(52)));
        total.addView(totalRateView, weighted(1.25f, dp(52)));
        total.addView(totalDocView, weighted(1.42f, dp(52)));
        table.addView(total, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        return table;
    }

    private View buildDifferences() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(2f);

        diffEggView = differenceCard("Chênh lệch trứng");
        diffDocView = differenceCard("Chênh lệch DOC");

        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(62), 1f);
        left.setMarginEnd(dp(5));
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(62), 1f);
        right.setMarginStart(dp(5));
        row.addView(diffEggView, left);
        row.addView(diffDocView, right);
        return row;
    }

    private TextView differenceCard(String label) {
        TextView view = text(label + ": —", 13, NAVY, false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(6), dp(4), dp(6), dp(4));
        view.setBackground(rounded(Color.WHITE, 15, BORDER, 1));
        return view;
    }

    private View buildCalculateButton() {
        Button calculate = new Button(this);
        calculate.setText("TÍNH DOC");
        calculate.setAllCaps(false);
        calculate.setTextColor(Color.WHITE);
        calculate.setTextSize(20);
        calculate.setTypeface(Typeface.DEFAULT_BOLD);
        calculate.setBackground(rounded(BLUE, 17, Color.TRANSPARENT, 0));
        calculate.setOnClickListener(v -> recalculate());
        calculate.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        return calculate;
    }

    private View buildKeyboard() {
        LinearLayout keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(dp(6), dp(6), dp(6), dp(6));
        keyboard.setBackground(rounded(Color.rgb(231, 237, 245), 17, Color.TRANSPARENT, 0));

        keyboardHint = text("Chạm vào ô cần nhập", 12, MUTED, false);
        keyboardHint.setGravity(Gravity.CENTER);
        keyboard.addView(keyboardHint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

        keyboard.addView(keyRow(new String[]{"1", "2", "3", "⌫"}, new float[]{1, 1, 1, 1}));
        keyboard.addView(keyRow(new String[]{"4", "5", "6", "B"}, new float[]{1, 1, 1, 1}));
        keyboard.addView(keyRow(new String[]{"7", "8", "9", "V"}, new float[]{1, 1, 1, 1}));
        keyboard.addView(keyRow(new String[]{"0", ".", "Xong"}, new float[]{2, 1, 1}));
        return keyboard;
    }

    private View keyRow(String[] labels, float[] weights) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(sum(weights));
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            Button key = new Button(this);
            key.setText(label);
            key.setAllCaps(false);
            key.setTextSize(label.equals("Xong") ? 15 : 21);
            key.setTextColor(label.equals("Xong") ? Color.WHITE : TEXT);
            key.setTypeface(Typeface.DEFAULT_BOLD);
            key.setPadding(0, 0, 0, 0);
            key.setBackground(rounded(label.equals("Xong") ? BLUE : Color.WHITE, 11, Color.TRANSPARENT, 0));
            key.setOnClickListener(v -> handleKey(label));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(55), weights[i]);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            row.addView(key, lp);
        }
        return row;
    }

    private void focusField(EditText field, int kind, int row) {
        activeField = field;
        activeKind = kind;
        activeRow = row;
        replaceOnNextKey = true;
        field.requestFocus();
        field.selectAll();

        if (keyboardHint != null) {
            if (kind == KIND_PLAN_EGGS) keyboardHint.setText("Đang nhập: Trứng dự kiến");
            else if (kind == KIND_PLAN_DOC) keyboardHint.setText("Đang nhập: DOC dự kiến");
            else if (kind == KIND_FLOCK) keyboardHint.setText("Đang sửa Flock " + (row + 1));
            else if (kind == KIND_QTY) keyboardHint.setText("Đang nhập số lượng Flock " + (row + 1));
            else if (kind == KIND_RATE) keyboardHint.setText("Đang sửa tỷ lệ nở Flock " + (row + 1));
        }
        updateFieldStyles();
    }

    private void handleKey(String key) {
        if (key.equals("Xong")) {
            if (activeField != null) activeField.clearFocus();
            activeField = null;
            activeKind = KIND_NONE;
            activeRow = -1;
            replaceOnNextKey = false;
            keyboardHint.setText("Chạm vào ô cần nhập");
            updateFieldStyles();
            recalculate();
            return;
        }
        if (activeField == null) return;

        if (key.equals("⌫")) {
            backspaceActive();
            afterEdit();
            return;
        }
        if (!isAllowedKey(key)) return;

        if (activeKind == KIND_PLAN_EGGS || activeKind == KIND_PLAN_DOC || activeKind == KIND_QTY) {
            String digits = digitsOnly(activeField.getText().toString());
            if (replaceOnNextKey) digits = "";
            digits += key;
            activeField.setText(formatDigits(digits));
        } else {
            String raw = activeField.getText().toString().trim();
            if (replaceOnNextKey) raw = "";
            if (key.equals(".") && raw.contains(".")) return;
            raw += key;
            if (activeKind == KIND_FLOCK) raw = raw.toUpperCase(Locale.ROOT);
            activeField.setText(raw);
        }
        replaceOnNextKey = false;
        activeField.setSelection(activeField.getText().length());
        afterEdit();
    }

    private boolean isAllowedKey(String key) {
        if (activeKind == KIND_FLOCK) {
            return key.matches("[0-9]") || key.equals("B") || key.equals("V");
        }
        if (activeKind == KIND_RATE) {
            return key.matches("[0-9]") || key.equals(".");
        }
        return key.matches("[0-9]");
    }

    private void backspaceActive() {
        if (replaceOnNextKey) {
            activeField.setText("");
            replaceOnNextKey = false;
            return;
        }
        if (activeKind == KIND_PLAN_EGGS || activeKind == KIND_PLAN_DOC || activeKind == KIND_QTY) {
            String digits = digitsOnly(activeField.getText().toString());
            if (!digits.isEmpty()) digits = digits.substring(0, digits.length() - 1);
            activeField.setText(formatDigits(digits));
        } else {
            String raw = activeField.getText().toString();
            if (!raw.isEmpty()) raw = raw.substring(0, raw.length() - 1);
            activeField.setText(raw);
        }
        activeField.setSelection(activeField.getText().length());
    }

    private void afterEdit() {
        if (activeKind == KIND_QTY && activeRow >= 0) {
            qtyManual[activeRow] = !digitsOnly(qtyInputs[activeRow].getText().toString()).isEmpty();
            qtyAuto[activeRow] = false;
        }
        if (activeKind == KIND_FLOCK && activeRow >= 0) {
            prefs.edit().putString("flock_" + activeRow, flockInputs[activeRow].getText().toString().trim()).apply();
        }
        if (activeKind == KIND_RATE && activeRow >= 0) {
            prefs.edit().putString("rate_" + activeRow, rateInputs[activeRow].getText().toString().trim()).apply();
        }
        recalculate();
    }

    private void recalculate() {
        if (plannedEggsInput == null || plannedDocInput == null) return;

        long plannedEggs = parseInteger(plannedEggsInput);
        long plannedDoc = parseInteger(plannedDocInput);
        boolean hasPlannedEggs = !digitsOnly(plannedEggsInput.getText().toString()).isEmpty();
        boolean hasPlannedDoc = !digitsOnly(plannedDocInput.getText().toString()).isEmpty();

        for (int i = 0; i < 5; i++) {
            if (qtyAuto[i]) {
                qtyInputs[i].setText("");
                qtyAuto[i] = false;
            }
        }

        int autoCandidates = 0;
        int candidate = -1;
        long manualSum = 0;
        for (int i = 0; i < 5; i++) {
            if (selected[i] == null || !selected[i].isChecked()) continue;
            if (qtyManual[i]) {
                manualSum += parseInteger(qtyInputs[i]);
            } else {
                autoCandidates++;
                candidate = i;
            }
        }

        if (hasPlannedEggs && autoCandidates == 1 && candidate >= 0) {
            long remaining = Math.max(0, plannedEggs - manualSum);
            qtyInputs[candidate].setText(formatNumber(remaining));
            qtyAuto[candidate] = true;
        }

        long totalEggs = 0;
        long totalDoc = 0;
        double weightedRateNumerator = 0.0;
        for (int i = 0; i < 5; i++) {
            if (selected[i] == null || !selected[i].isChecked()) {
                if (docOutputs[i] != null) docOutputs[i].setText("—");
                continue;
            }
            long qty = parseInteger(qtyInputs[i]);
            double rate = parseRate(rateInputs[i]);
            long doc = Math.round(qty * rate / 100.0);
            totalEggs += qty;
            totalDoc += doc;
            weightedRateNumerator += qty * rate;
            docOutputs[i].setText(qty > 0 ? formatNumber(doc) : "—");
        }

        double weightedRate = totalEggs > 0 ? weightedRateNumerator / totalEggs : 0.0;
        totalEggsView.setText(formatNumber(totalEggs));
        totalRateView.setText(totalEggs > 0 ? String.format(Locale.US, "%.1f", weightedRate) : "—");
        totalDocView.setText(formatNumber(totalDoc));

        if (hasPlannedEggs) applyDifference(diffEggView, "Chênh lệch trứng", totalEggs - plannedEggs);
        else resetDifference(diffEggView, "Chênh lệch trứng");

        if (hasPlannedDoc) applyDifference(diffDocView, "Chênh lệch DOC", totalDoc - plannedDoc);
        else resetDifference(diffDocView, "Chênh lệch DOC");

        updateRowHighlights();
    }

    private void applyDifference(TextView view, String label, long diff) {
        view.setTypeface(Typeface.DEFAULT_BOLD);
        if (diff == 0) {
            view.setText(label + ": 0");
            view.setTextColor(GREEN);
        } else if (diff > 0) {
            view.setText(label + ": Cao hơn +" + formatNumber(diff));
            view.setTextColor(WARNING);
        } else {
            view.setText(label + ": Thấp hơn -" + formatNumber(Math.abs(diff)));
            view.setTextColor(WARNING);
        }
    }

    private void resetDifference(TextView view, String label) {
        view.setText(label + ": —");
        view.setTextColor(NAVY);
        view.setTypeface(Typeface.DEFAULT);
    }

    private void updateRowHighlights() {
        for (int i = 0; i < 5; i++) {
            if (rowLayouts[i] == null || selected[i] == null) continue;
            boolean on = selected[i].isChecked();
            rowLayouts[i].setBackground(rounded(on ? ROW_SELECTED : Color.TRANSPARENT, 11, Color.TRANSPARENT, 0));
        }
        updateFieldStyles();
    }

    private void updateFieldStyles() {
        for (int i = 0; i < 5; i++) {
            if (flockInputs[i] == null || qtyInputs[i] == null || rateInputs[i] == null || selected[i] == null) continue;
            styleInput(flockInputs[i], flockInputs[i] == activeField, selected[i].isChecked());
            styleInput(qtyInputs[i], qtyInputs[i] == activeField, selected[i].isChecked());
            styleInput(rateInputs[i], rateInputs[i] == activeField, selected[i].isChecked());
        }
    }

    private void styleInput(EditText input, boolean active, boolean rowSelected) {
        int stroke = active ? BLUE : (rowSelected ? Color.rgb(171, 210, 250) : BORDER);
        input.setBackground(rounded(Color.WHITE, 11, stroke, active ? 2 : 1));
    }

    private void resetDailyData() {
        plannedEggsInput.setText("");
        plannedDocInput.setText("");
        for (int i = 0; i < 5; i++) {
            qtyInputs[i].setText("");
            qtyManual[i] = false;
            qtyAuto[i] = false;
            selected[i].setChecked(false);
        }
        activeField = null;
        activeKind = KIND_NONE;
        activeRow = -1;
        replaceOnNextKey = false;
        keyboardHint.setText("Chạm vào ô cần nhập");
        recalculate();
    }

    private EditText tableInput(String value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(value);
        input.setTextSize(14);
        input.setTextColor(TEXT);
        input.setGravity(Gravity.CENTER);
        input.setPadding(dp(3), 0, dp(3), 0);
        input.setShowSoftInputOnFocus(false);
        input.setSelectAllOnFocus(true);
        input.setBackground(rounded(Color.WHITE, 11, BORDER, 1));
        return input;
    }

    private TextView headerCell(String value, float weight) {
        TextView tv = text(value, 12, NAVY, true);
        tv.setGravity(Gravity.CENTER);
        tv.setLines(2);
        tv.setMaxLines(2);
        tv.setLayoutParams(weighted(weight, dp(52)));
        return tv;
    }

    private TextView totalCell(int color, int background) {
        TextView tv = text("0", 14, color, true);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(rounded(background, 11, Color.TRANSPARENT, 0));
        return tv;
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private LinearLayout.LayoutParams weighted(float weight, int height) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, height, weight);
        lp.setMargins(dp(1), 0, dp(1), 0);
        return lp;
    }

    private View space(int valueDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(valueDp)));
        return v;
    }

    private GradientDrawable rounded(int fillColor, int radiusDp, int strokeColor, int strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeWidthDp > 0) drawable.setStroke(dp(strokeWidthDp), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float sum(float[] values) {
        float total = 0f;
        for (float value : values) total += value;
        return total;
    }

    private long parseInteger(EditText input) {
        if (input == null) return 0;
        String digits = digitsOnly(input.getText().toString());
        if (digits.isEmpty()) return 0;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double parseRate(EditText input) {
        if (input == null) return 0.0;
        String raw = input.getText().toString().trim().replace(',', '.');
        if (raw.isEmpty() || raw.equals(".")) return 0.0;
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private String formatDigits(String digits) {
        if (digits == null || digits.isEmpty()) return "";
        try {
            return formatNumber(Long.parseLong(digits));
        } catch (NumberFormatException e) {
            return digits;
        }
    }

    private String formatNumber(long value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }
}
