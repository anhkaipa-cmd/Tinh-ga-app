from pathlib import Path
import re

p = Path('app/src/main/java/com/anhthu/tinhga/MainActivity.java')
s = p.read_text(encoding='utf-8')

s = s.replace('private LinearLayout operatorBar;', 'private LinearLayout calculatorPad;')

s = s.replace(
'''        operatorBar = buildOperatorBar();
        screen.addView(operatorBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));''',
'''        calculatorPad = buildCalculatorPad();
        calculatorPad.setVisibility(View.GONE);
        screen.addView(calculatorPad, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));''')

s = s.replace('activeCalcField = input;', 'showCalculatorPad(input);')
s = s.replace('activeCalcField = qty;', 'showCalculatorPad(qty);')
s = s.replace('activeCalcField = rate;', 'showCalculatorPad(rate);')
s = s.replace('activeCalcField = null;\n                } else {', 'activeCalcField = null;\n                    hideCalculatorPad();\n                } else {')
s = s.replace('activeCalcField = null;\n        recalculate();', 'activeCalcField = null;\n        hideCalculatorPad();\n        recalculate();')

start = s.index('    private LinearLayout buildOperatorBar() {')
end = s.index('    private void fillRemainingIfThisIsOnlyBlank(int rowIndex) {')

replacement = r'''    private LinearLayout buildCalculatorPad() {
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setPadding(dp(5), dp(5), dp(5), dp(7));
        pad.setBackground(rounded(Color.rgb(232, 238, 247), 0, BORDER, 1));

        pad.addView(calcKeyRow(new String[]{"7", "8", "9", "÷"}));
        pad.addView(calcKeyRow(new String[]{"4", "5", "6", "×"}));
        pad.addView(calcKeyRow(new String[]{"1", "2", "3", "−"}));
        pad.addView(calcKeyRow(new String[]{"0", ".", "⌫", "+"}));
        pad.addView(calcKeyRow(new String[]{"=", "XONG"}));
        return pad;
    }

    private LinearLayout calcKeyRow(String[] labels) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(labels.length);
        for (String label : labels) {
            Button b = new Button(this);
            b.setText(label);
            b.setAllCaps(false);
            b.setTextSize(label.equals("XONG") ? 13 : 20);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            boolean primary = label.equals("=") || label.equals("XONG");
            b.setTextColor(primary ? Color.WHITE : NAVY);
            b.setPadding(0, 0, 0, 0);
            b.setFocusable(false);
            b.setFocusableInTouchMode(false);
            b.setBackground(rounded(primary ? BLUE : Color.WHITE, 10,
                    primary ? Color.TRANSPARENT : BORDER, primary ? 0 : 1));
            b.setOnClickListener(v -> handleCalcKey(label));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(47), 1f);
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            row.addView(b, lp);
        }
        return row;
    }

    private void showCalculatorPad(EditText field) {
        activeCalcField = field;
        field.setShowSoftInputOnFocus(false);
        if (calculatorPad != null) calculatorPad.setVisibility(View.VISIBLE);
    }

    private void hideCalculatorPad() {
        if (calculatorPad != null) calculatorPad.setVisibility(View.GONE);
    }

    private void handleCalcKey(String label) {
        EditText field = activeCalcField;
        if (field == null) return;

        if ("XONG".equals(label)) {
            if (isRateField(field)) normalizeRateExpression(field);
            else normalizeIntegerExpression(field);
            recalculate();
            field.clearFocus();
            activeCalcField = null;
            hideCalculatorPad();
            return;
        }

        if ("=".equals(label)) {
            if (isRateField(field)) normalizeRateExpression(field);
            else normalizeIntegerExpression(field);
            recalculate();
            field.requestFocus();
            field.setSelection(field.getText().length());
            return;
        }

        Editable text = field.getText();
        int start = Math.max(0, field.getSelectionStart());
        int end = Math.max(0, field.getSelectionEnd());
        int left = Math.min(start, end);
        int right = Math.max(start, end);

        if ("⌫".equals(label)) {
            if (left != right) {
                text.delete(left, right);
                field.setSelection(left);
            } else if (left > 0) {
                text.delete(left - 1, left);
                field.setSelection(left - 1);
            }
            recalculate();
            return;
        }

        String insert = label;
        if ("−".equals(label)) insert = "-";
        else if ("×".equals(label)) insert = "*";
        else if ("÷".equals(label)) insert = "/";

        boolean binaryOperator = insert.equals("+") || insert.equals("-")
                || insert.equals("*") || insert.equals("/");
        if (binaryOperator && left != right && right == text.length()) {
            left = right;
        }

        text.replace(left, right, insert);
        field.setSelection(left + insert.length());
        recalculate();
    }

    private boolean isRateField(EditText field) {
        for (EditText rate : rateInputs) {
            if (rate == field) return true;
        }
        return false;
    }

    private void setNumericKeyboard(EditText input) {
        input.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setShowSoftInputOnFocus(false);
    }

'''

s = s[:start] + replacement + s[end:]

# Ensure clicking a numeric field also opens the integrated keypad.
s = s.replace('input.setOnClickListener(v -> showCalculatorPad(input));',
              'input.setOnClickListener(v -> showCalculatorPad(input));')
s = s.replace('qty.setOnClickListener(v -> showCalculatorPad(qty));',
              'qty.setOnClickListener(v -> showCalculatorPad(qty));')
s = s.replace('rate.setOnClickListener(v -> showCalculatorPad(rate));',
              'rate.setOnClickListener(v -> showCalculatorPad(rate));')

p.write_text(s, encoding='utf-8')
print('patched custom keypad without parentheses keys')