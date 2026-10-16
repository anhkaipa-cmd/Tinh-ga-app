from pathlib import Path

p = Path('app/src/main/java/com/anhthu/tinhga/MainActivity.java')
s = p.read_text(encoding='utf-8')
old = '        pad.addView(calcKeyRow(new String[]{"(", ")", "=", "XONG"}));\n'
new = '        pad.addView(calcKeyRow(new String[]{"=", "XONG"}));\n'
if old not in s:
    raise SystemExit('parentheses keypad row not found')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')
print('removed parentheses keys')
