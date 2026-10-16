package com.anhthu.tinhga;

final class ExpressionEvaluator {
    private final String expression;
    private int pos = -1;
    private int ch;

    private ExpressionEvaluator(String expression) {
        this.expression = normalize(expression);
    }

    static double evaluate(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty expression");
        }
        ExpressionEvaluator parser = new ExpressionEvaluator(raw);
        double value = parser.parse();
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Invalid result");
        }
        return value;
    }

    static boolean isValid(String raw) {
        try {
            evaluate(raw);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String normalize(String raw) {
        return raw.trim()
                .replace(',', '.')
                .replace('×', '*')
                .replace('÷', '/')
                .replace('x', '*')
                .replace('X', '*');
    }

    private void nextChar() {
        ch = (++pos < expression.length()) ? expression.charAt(pos) : -1;
    }

    private boolean eat(int charToEat) {
        while (ch == ' ') nextChar();
        if (ch == charToEat) {
            nextChar();
            return true;
        }
        return false;
    }

    private double parse() {
        nextChar();
        double x = parseExpression();
        while (ch == ' ') nextChar();
        if (pos < expression.length()) {
            throw new IllegalArgumentException("Unexpected character");
        }
        return x;
    }

    private double parseExpression() {
        double x = parseTerm();
        for (;;) {
            if (eat('+')) x += parseTerm();
            else if (eat('-')) x -= parseTerm();
            else return x;
        }
    }

    private double parseTerm() {
        double x = parseFactor();
        for (;;) {
            if (eat('*')) x *= parseFactor();
            else if (eat('/')) {
                double divisor = parseFactor();
                if (Math.abs(divisor) < 1e-12) {
                    throw new IllegalArgumentException("Division by zero");
                }
                x /= divisor;
            } else return x;
        }
    }

    private double parseFactor() {
        if (eat('+')) return parseFactor();
        if (eat('-')) return -parseFactor();

        double x;
        int startPos = this.pos;
        if (eat('(')) {
            x = parseExpression();
            if (!eat(')')) throw new IllegalArgumentException("Missing )");
        } else if ((ch >= '0' && ch <= '9') || ch == '.') {
            while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
            String number = expression.substring(startPos, this.pos).trim();
            if (number.isEmpty() || number.equals(".")) {
                throw new IllegalArgumentException("Invalid number");
            }
            x = Double.parseDouble(number);
        } else {
            throw new IllegalArgumentException("Unexpected character");
        }
        return x;
    }
}
