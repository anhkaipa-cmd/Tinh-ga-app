from pathlib import Path

p = Path('app/src/main/java/com/anhthu/tinhga/MainActivity.java')
s = p.read_text(encoding='utf-8')

old = '''    private void setNumericKeyboard(EditText input) {
        input.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setShowSoftInputOnFocus(false);
    }
'''
new = '''    private void setNumericKeyboard(EditText input) {
        // Keep the field editable by our custom calculator keypad without
        // Android's DigitsKeyListener filtering out *, /, (, ).
        input.setRawInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
                        | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setShowSoftInputOnFocus(false);
    }
'''
if old not in s:
    raise SystemExit('setNumericKeyboard block not found')
s = s.replace(old, new, 1)

old_sel = '        field.setSelection(left + insert.length());\n        recalculate();\n'
new_sel = '''        int target = Math.min(field.getText().length(), left + insert.length());
        field.setSelection(target);
        recalculate();
'''
if old_sel not in s:
    raise SystemExit('operator selection block not found')
s = s.replace(old_sel, new_sel, 1)

p.write_text(s, encoding='utf-8')
print('fixed keypad runtime crash')
