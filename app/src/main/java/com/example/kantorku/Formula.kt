package com.example.kantorku

import java.math.BigDecimal
import java.math.RoundingMode

/** Evaluator rumus sederhana: angka, referensi sel, + - * /, tanda kurung, SUM/AVERAGE/MIN/MAX/COUNT. */
class SheetEval(private val raw: (String) -> String) {
    private val cache = HashMap<String, Double>()
    private val visiting = HashSet<String>()
    private val rangeRegex = Regex("^[A-Za-z]+[0-9]+:[A-Za-z]+[0-9]+")

    fun display(ref: String): String {
        val t = raw(ref)
        if (!t.startsWith("=")) return t
        return try {
            fmt(value(ref))
        } catch (e: Exception) {
            "#ERR"
        }
    }

    private fun fmt(v: Double): String =
        BigDecimal(v).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    private fun value(ref: String): Double {
        cache[ref]?.let { return it }
        if (!visiting.add(ref)) error("cycle")
        try {
            val t = raw(ref).trim()
            val v = Parser(t.substring(1)).parse()
            cache[ref] = v
            return v
        } finally {
            visiting.remove(ref)
        }
    }

    private fun cellNum(ref: String): Double? {
        val t = raw(ref).trim()
        if (t.isEmpty()) return null
        return if (t.startsWith("=")) value(ref) else t.toDoubleOrNull()
    }

    private fun rangeVals(range: String): List<Double> {
        val (a, b) = range.split(":")
        val c1 = a[0].uppercaseChar() - 'A'
        val r1 = a.substring(1).toInt()
        val c2 = b[0].uppercaseChar() - 'A'
        val r2 = b.substring(1).toInt()
        val out = ArrayList<Double>()
        for (r in minOf(r1, r2)..maxOf(r1, r2)) {
            for (c in minOf(c1, c2)..maxOf(c1, c2)) {
                cellNum("${'A' + c}$r")?.let { out.add(it) }
            }
        }
        return out
    }

    private fun applyFn(name: String, v: List<Double>): Double = when (name) {
        "SUM" -> v.sum()
        "AVERAGE" -> if (v.isEmpty()) error("empty") else v.sum() / v.size
        "MIN" -> v.minOrNull() ?: error("empty")
        "MAX" -> v.maxOrNull() ?: error("empty")
        "COUNT" -> v.size.toDouble()
        else -> error("fn")
    }

    private inner class Parser(val s: String) {
        var i = 0

        fun ws() { while (i < s.length && s[i] == ' ') i++ }

        fun expect(c: Char) {
            ws()
            if (i >= s.length || s[i] != c) error("expected $c")
            i++
        }

        fun parse(): Double {
            val v = expr()
            ws()
            if (i != s.length) error("trailing")
            return v
        }

        fun expr(): Double {
            var v = term()
            while (true) {
                ws()
                if (i < s.length && (s[i] == '+' || s[i] == '-')) {
                    val op = s[i++]
                    val r = term()
                    v = if (op == '+') v + r else v - r
                } else return v
            }
        }

        fun term(): Double {
            var v = factor()
            while (true) {
                ws()
                if (i < s.length && (s[i] == '*' || s[i] == '/')) {
                    val op = s[i++]
                    val r = factor()
                    if (op == '*') v *= r else {
                        if (r == 0.0) error("div0")
                        v /= r
                    }
                } else return v
            }
        }

        fun factor(): Double {
            ws()
            if (i >= s.length) error("eof")
            val c = s[i]
            if (c == '-') { i++; return -factor() }
            if (c == '(') {
                i++
                val v = expr()
                expect(')')
                return v
            }
            if (c.isDigit() || c == '.') {
                val st = i
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                return s.substring(st, i).toDouble()
            }
            if (c.isLetter()) {
                val st = i
                while (i < s.length && s[i].isLetter()) i++
                val name = s.substring(st, i).uppercase()
                ws()
                if (i < s.length && s[i] == '(') {
                    i++
                    val vals = args()
                    expect(')')
                    return applyFn(name, vals)
                }
                val ds = i
                while (i < s.length && s[i].isDigit()) i++
                if (ds == i || name.length != 1) error("ref")
                return cellNum(name + s.substring(ds, i)) ?: 0.0
            }
            error("bad char")
        }

        fun args(): List<Double> {
            val out = ArrayList<Double>()
            ws()
            if (i < s.length && s[i] == ')') return out
            while (true) {
                ws()
                val m = rangeRegex.find(s.substring(i))
                if (m != null) {
                    i += m.value.length
                    out.addAll(rangeVals(m.value.uppercase()))
                } else {
                    out.add(expr())
                }
                ws()
                if (i < s.length && (s[i] == ',' || s[i] == ';')) i++ else break
            }
            return out
        }
    }
}
