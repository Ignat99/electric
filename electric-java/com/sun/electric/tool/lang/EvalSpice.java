/* -*- tab-width: 4 -*-
 *
 * Electric(tm) VLSI Design System
 *
 * File: EvalSpice.java
 *
 * Copyright (c) 2005, Static Free Software. All rights reserved.
 *
 * Electric(tm) is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Electric(tm) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sun.electric.tool.lang;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;

import com.sun.electric.tool.Job;
import com.sun.electric.util.TextUtils;
import com.sun.electric.util.TextUtils.UnitScale;

/**
 * Class to evaluate Spice expressions.
 * @author gainsley
 * Date: Aug 17, 2006
 */
public class EvalSpice {

    private String expr;
    private StreamTokenizer tokenizer;
    int openParens;

    public EvalSpice(String expr) {
        this.expr = expr;
        openParens = 0;
    }

    public Object evaluate() {
        if (expr == null) {
            return null;
        }
        StringReader reader = new StringReader(expr);
        tokenizer = new StreamTokenizer(reader);
        tokenizer.parseNumbers();
        tokenizer.ordinaryChar('(');
        tokenizer.ordinaryChar(')');
        tokenizer.ordinaryChar('*');
        tokenizer.ordinaryChar('/');
        tokenizer.ordinaryChar('+');
        tokenizer.ordinaryChar('-');
        tokenizer.ordinaryChar('<');
        tokenizer.ordinaryChar('=');
        tokenizer.ordinaryChar('>');
        tokenizer.ordinaryChar('!');
        tokenizer.ordinaryChar('?');
        tokenizer.ordinaryChar(':');
        tokenizer.wordChars('_', '_');
        try {
            return evalEq().eval();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } catch (ParseException e) {
            String parsed = "";
            if (Job.getDebug()) e.printStackTrace();
            
            try {
                long left = reader.skip(Long.MAX_VALUE);
                parsed = expr.substring(0, expr.length() - (int) left);
            } catch (IOException e2) {
            }
            if (!parsed.equals("")) {
                parsed = ": " + parsed + " <--";
            }
            System.out.println("Spice Parse Error: " + e.getMessage() + parsed + " " + tokenizer.toString());
            System.out.println("   Original string: " + expr);
        }
        return expr;
    }

    /**
     * Evaluate an expression
     * @return the evaluated expression
     * @throws IOException
     * @throws ParseException
     */
    private SimpleEq evalEq() throws IOException, ParseException {
        SimpleEq eq = new SimpleEq();

        while (true) {
            int tt = tokenizer.nextToken();
            if (tt == StreamTokenizer.TT_EOF || tt == StreamTokenizer.TT_EOL) {
                if (openParens > 0) {
                    throw new ParseException("Unmatched open parens");
                }
                return eq;
            } // delimiters come first
            else if (tt == ')') {       // end eq
                openParens--;
                if (openParens < 0) {
                    throw new ParseException("Unmatched close parens");
                }
                Object value = eq.eval();
                if (value instanceof String) {
                    return new SimpleEq("(" + value.toString() + ")", null, null);
                }
                return new SimpleEq(value, null, null);
            } else if (tt == '(') {       // begin new eq
                openParens++;
                if (!eq.addIdentifierOk()) {
                    throw new ParseException("Too many identifiers");
                }
                Object id = evalEq();
                eq.addIdentifier(id);
            } else if (tt == ',') {       // end eq
                return eq;
            } else if (tt == StreamTokenizer.TT_NUMBER) {
                tokenizer.pushBack();
                eq.addIdentifier(parseNumber());
            } else if (tt == StreamTokenizer.TT_WORD) {
                if (tokenizer.sval.equalsIgnoreCase("sin")) {
                    expect('(');
                    openParens++;
                    Object arg = evalEq().eval();
                    if (arg instanceof Double) {
                        arg = new Double(Math.sin(((Double) arg).doubleValue()));
                    } else {
                        arg = "sin" + format(arg);
                    }
                    eq.addIdentifier(arg);
                } else if (tokenizer.sval.equalsIgnoreCase("min")) {
                    expect('(');
                    openParens++;
                    Object m1 = evalEq().eval();
                    Object m2 = evalEq().eval();
                    if ((m1 instanceof Double) && (m2 instanceof Double)) {
                        double a = ((Double) m1).doubleValue();
                        double b = ((Double) m2).doubleValue();
                        m1 = new Double(Math.min(a, b));
                    } else {
                        String m2str = format(m2);
                        // remove extraneous ()'s
                        if (m2str.startsWith("(") && m2str.endsWith(")")) {
                            m2str = m2str.substring(1, m2str.length() - 1);
                        }
                        m1 = "min(" + format(m1) + "," + m2str + ")";
                    }
                    eq.addIdentifier(m1);
                } else if (tokenizer.sval.equalsIgnoreCase("max")) {
                    expect('(');
                    openParens++;
                    Object m1 = evalEq().eval();
                    Object m2 = evalEq().eval();
                    if ((m1 instanceof Double) && (m2 instanceof Double)) {
                        double a = ((Double) m1).doubleValue();
                        double b = ((Double) m2).doubleValue();
                        m1 = new Double(Math.max(a, b));
                    } else {
                        String m2str = format(m2);
                        // remove extraneous ()'s
                        if (m2str.startsWith("(") && m2str.endsWith(")")) {
                            m2str = m2str.substring(1, m2str.length() - 1);
                        }
                        m1 = "max(" + format(m1) + "," + m2str + ")";
                    }
                    eq.addIdentifier(m1);
                } else if (tokenizer.sval.equalsIgnoreCase("abs")) {
                    expect('(');
                    openParens++;
                    Object arg = evalEq().eval();
                    if (arg instanceof Double) {
                        arg = new Double(Math.abs(((Double) arg).doubleValue()));
                    } else {
                        arg = "abs" + format(arg);
                    }
                    eq.addIdentifier(arg);
                } else if (tokenizer.sval.equalsIgnoreCase("sqrt")) {
                    expect('(');
                    openParens++;
                    Object arg = evalEq().eval();
                    if (arg instanceof Double) {
                        arg = new Double(Math.sqrt(((Double) arg).doubleValue()));
                    } else {
                        arg = "sqrt" + format(arg);
                    }
                    eq.addIdentifier(arg);
                } else if (tokenizer.sval.equalsIgnoreCase("int")) {
                    expect('(');
                    openParens++;
                    Object arg = evalEq().eval();
                    if (arg instanceof Double) {
                        arg = new Double((int) (((Double) arg).doubleValue()));
                    } else {
                        arg = "int" + format(arg);
                    }
                    eq.addIdentifier(arg);
                } else {
                    // identifier
                    eq.addIdentifier(tokenizer.sval);
                }
            } else if (tt == '*') {
                eq.addOp(Op.MULT);
            } else if (tt == '/') {
                eq.addOp(Op.DIV);
            } else if (tt == '+') {
                eq.addOp(Op.PLUS);
            } else if (tt == '-') {
                eq.addOp(Op.MINUS);
            } else if (tt == '<') {
                tt = tokenizer.nextToken();
                if (tt == '=') {
                    eq.addOp(Op.LTOE);
                } else {
                    tokenizer.pushBack();
                    eq.addOp(Op.LT);
                }
            } else if (tt == '>') {
                tt = tokenizer.nextToken();
                if (tt == '=') {
                    eq.addOp(Op.GTOE);
                } else {
                    tokenizer.pushBack();
                    eq.addOp(Op.GT);
                }
            } else if (tt == '=') {
                expect('=');
                eq.addOp(Op.EQ);
            } else if (tt == '!') {
                expect('=');
                eq.addOp(Op.NE);
            } else if (tt == '?') {
                eq.addOp(Op.COND);
                /*
                Object arg1 = evalEq().eval();
                if (tokenizer.ttype != ':') throw new ParseException("Expected ':' after conditional");
                Object arg2 = evalEq().eval();
                SimpleEq condval = new SimpleEq(arg1, Op.CONDCHOICE, arg2);
                eq.addIdentifier(condval);
                 */
            } else if (tt == ':') {       // end conditional true arg
                eq.addOp(Op.CONDCHOICE);
            }
        }
    }

    /**
     * Parse a number. A number may have the format:
     * <P>
     * number[g|meg|k|m|u|n|p|f]
     * <p>
     * number(e)[-]number
     * @return the parsed number.
     * @throws IOException
     * @throws ParseException
     */
    private Object parseNumber() throws IOException, ParseException {
        int tt = tokenizer.nextToken();
        if (tt == StreamTokenizer.TT_NUMBER) {
            double val = tokenizer.nval;
            // peek ahead to check if exponential, or multiplier
            tokenizer.ordinaryChar('e');
            tokenizer.ordinaryChar('E');
            tt = tokenizer.nextToken();
            if (tt == 'e' || tt == 'E') {
                tt = tokenizer.nextToken();
                boolean minus = false;
                if (tt == '-') {
                    minus = true;
                    tt = tokenizer.nextToken();
                }
                if (tt == StreamTokenizer.TT_NUMBER) {
                    double exp = tokenizer.nval;
                    if (minus) {
                        exp = -1.0 * exp;
                    }
                    val = val * Math.pow(10, exp);
                } else {
                    throw new ParseException("Invalid token");
                }
            } else if (tt == StreamTokenizer.TT_WORD) {
            	UnitScale u = UnitScale.findUnitScaleFromPostfix(tokenizer.sval);
            	if (u == UnitScale.NONE)
            		throw new ParseException("Invalid token");
            	val = val * u.getMultiplier().doubleValue();
            } else {
                tokenizer.pushBack();
            }
            tokenizer.wordChars('e', 'e');
            tokenizer.wordChars('E', 'E');
            return new Double(val);
        }
        throw new ParseException("Expected number");
    }

    private void expect(int token) throws IOException, ParseException {
        int tt = tokenizer.nextToken();
        if (tt != token) {
            throw new ParseException("Expected token " + token);
        }
    }

    // ==================== Parsable Objects ========================
    @SuppressWarnings("serial")
	public static class ParseException extends Exception {

        public ParseException(String msg) {
            super(msg);
        }
    }

    public static class Op {

        public final String name;
        public final int precedence;

        private Op(String name, int precedence) {
            this.name = name;
            this.precedence = precedence;
        }

        public String toString() {
            return name;
        }
        // operators with lower value precedence bind tighter than higher value precedence
        public static final Op MULT = new Op("*", 2);
        public static final Op DIV = new Op("/", 2);
        public static final Op PLUS = new Op("+", 3);
        public static final Op MINUS = new Op("-", 3);
        public static final Op LT = new Op("<", 5);
        public static final Op LTOE = new Op("<=", 5);
        public static final Op GT = new Op(">", 5);
        public static final Op GTOE = new Op(">=", 5);
        public static final Op EQ = new Op("==", 6);
        public static final Op NE = new Op("!=", 6);
        public static final Op LAND = new Op("&&", 10);
        public static final Op LOR = new Op("||", 11);
        public static final Op CONDCHOICE = new Op(":", 12);
        public static final Op COND = new Op("?", 13);
    }
    private static final Double ONE = new Double(1);
    private static final Double ZERO = new Double(0);

    /**
     * A simple equation consists of two Identifiers (operands)
     * that are Doubles, Strings, or other SimpleEq,
     * and an operator *,/,+,-.
     * <P>
     * For a simple equation to be valid, it must define
     * both operands and an operator. However, if the
     * operator is '-', then the left hand operand may
     * be null, to indicate a unary minus.  Additionally,
     * if only the left hand operator is defined, then it
     * is simply one operand.
     */
    public static class SimpleEq {

        protected Object lhop;        // left hand operand
        private Op op;              // operator
        protected Object rhop;        // right hand operand
        boolean neglh = false;
        boolean negrh = false;

        public SimpleEq() {
            this.lhop = null;
            this.op = null;
            this.rhop = null;
        }

        public SimpleEq(Object lhop, Op op, Object rhop) {
            this.lhop = lhop;
            this.op = op;
            this.rhop = rhop;
        }

        public boolean addIdentifierOk() {
            if (lhop == null) {
                return true;
            } else if (rhop == null && op != null) {
                return true;
            } else if (rhop instanceof SimpleEq) {
                return ((SimpleEq) rhop).addIdentifierOk();
            }
            return false;
        }

        public void addIdentifier(Object id) throws ParseException {
            if (lhop == null) {
                lhop = id;
            } else if (rhop == null && op != null) {
                rhop = id;
            } else if (rhop instanceof SimpleEq) {
                ((SimpleEq) rhop).addIdentifier(id);
            } else {
                throw new ParseException("Two operands with no operator");
            }
        }

        public void addOp(Op operator) throws ParseException {
            if (lhop == null && operator == Op.MINUS && !neglh) {
                neglh = true;               // unary minus on left hand operand
            } else if (lhop == null) {
                throw new ParseException("Operator " + operator + " with no left hand operand");
            } // lhop defined from here on
            else if (op == null && rhop == null) {
                this.op = operator;
            } else if (op != null && rhop == null && operator == Op.MINUS && !negrh) {
                negrh = true;               // unary minus on right hand operand
            } else if (op != null && rhop != null) {
                if (rhop instanceof SimpleEq) {
                    ((SimpleEq) rhop).addOp(operator);
                } else {
                    // operators with lower value precedence bind tighter than higher value precedence
                    if (operator.precedence < op.precedence) {
                        // bind right
                        rhop = new SimpleEq(rhop, operator, null);
                        // retain proper negation associations
                        ((SimpleEq) rhop).neglh = negrh;
                        negrh = false;
                    } else {
                        // bind left
                        lhop = new SimpleEq(lhop, op, rhop);
                        this.op = operator;
                        this.rhop = null;
                        // retain proper negation associations
                        ((SimpleEq) lhop).neglh = neglh;
                        ((SimpleEq) lhop).negrh = negrh;
                        this.neglh = false;
                        this.negrh = false;
                    }
                }
            } else {
                throw new ParseException(("Two operators in a row"));
            }
        }

        /**
         * Return either a Double, if the equation can be
         * resolved numerically, or a String representing
         * the equation after any numerical resolution can be done.
         * @return a Double or a String
         */
        public Object eval() throws ParseException {
            if (lhop instanceof SimpleEq) {
                lhop = ((SimpleEq) lhop).eval();
            }
            if (rhop instanceof SimpleEq) {
                rhop = ((SimpleEq) rhop).eval();
            }

            if (op == Op.CONDCHOICE) {
                return this;
            }

            if (op == Op.COND && (rhop instanceof SimpleEq)) {
                SimpleEq condval = (SimpleEq) rhop;
                if ((lhop instanceof Double) && (condval.lhop instanceof Double) && (condval.rhop instanceof Double)) {
                    double cond = ((Double) lhop).doubleValue();
                    if (neglh) {
                        cond = -1.0 * cond;
                    }
                    double valt = ((Double) condval.lhop).doubleValue();
                    if (condval.neglh) {
                        valt = -1.0 * valt;
                    }
                    double valf = ((Double) condval.rhop).doubleValue();
                    if (condval.negrh) {
                        valf = -1.0 * valf;
                    }
                    if (cond == 0) {
                        return valf;
                    }
                    return valt;
                }
                String neglhstr = condval.neglh ? "-" : "";
                String negrhstr = condval.negrh ? "-" : "";
                rhop = neglhstr + format(condval.lhop) + " : " + negrhstr + format(condval.rhop);
            } else if ((lhop instanceof Double) && (rhop instanceof Double)) {
                double lh = ((Double) lhop).doubleValue();
                double rh = ((Double) rhop).doubleValue();
                if (neglh) {
                    lh = -1.0 * lh;
                }
                if (negrh) {
                    rh = -1.0 * rh;
                }
                if (op == Op.MULT) {
                    return new Double(lh * rh);
                } else if (op == Op.DIV) {
                    return new Double(lh / rh);
                } else if (op == Op.PLUS) {
                    return new Double(lh + rh);
                } else if (op == Op.MINUS) {
                    return new Double(lh - rh);
                } else if (op == Op.LT) {
                    return lh < rh ? ONE : ZERO;
                } else if (op == Op.LTOE) {
                    return lh <= rh ? ONE : ZERO;
                } else if (op == Op.GT) {
                    return lh > rh ? ONE : ZERO;
                } else if (op == Op.GTOE) {
                    return lh >= rh ? ONE : ZERO;
                } else if (op == Op.EQ) {
                    return lh == rh ? ONE : ZERO;
                } else if (op == Op.NE) {
                    return lh != rh ? ONE : ZERO;
                } else if (op == Op.LAND) {
                    return (lh != 0 && rh != 0) ? ONE : ZERO;
                } else if (op == Op.LOR) {
                    return (lh != 0 || rh != 0) ? ONE : ZERO;
                }
            } else if (op == null && rhop == null) {
                if (neglh) {
                    if (lhop instanceof Double) {
                        return -1.0 * ((Double) lhop).doubleValue();
                    }
                    return "-" + lhop.toString();
                }
                return lhop;
            }

            // can't resolve numerically
            String neglhstr = neglh ? "-" : "";
            String negrhstr = negrh ? "-" : "";
            String lhstr = (lhop == null ? "?" : format(lhop));
            String rhstr = (rhop == null ? "?" : format(rhop));
            return neglhstr + lhstr + " " + op + " " + negrhstr + rhstr;
        }
    }

    private static String format(Object obj) {
        // must be format double postfix, otherwise get 0 for numbers less than 0.001
        if (obj instanceof Double) {
            return TextUtils.formatDoublePostFix(((Double) obj).doubleValue());
        }
        return obj.toString();
    }
}
