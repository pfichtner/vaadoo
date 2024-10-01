package com.github.pfichtner.vaadoo;

import static java.util.regex.Matcher.quoteReplacement;
import static java.util.regex.Pattern.compile;

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Objects;

public final class NamedPlaceholders {

	private static class ExpressionEvaluator {

		private static class Ternary {

			private final String condition;
			private final String trueExpr;
			private final String falseExpr;

			public Ternary(String condition, String trueExpr, String falseExpr) {
				this.condition = condition.trim();
				this.trueExpr = trueExpr.trim();
				this.falseExpr = falseExpr.trim();
			}
		}

		private static enum Operator {
			EQ("==") {
				@Override
				boolean evaluate(boolean eq) {
					return eq;
				}

			},
			NE("!=") {
				@Override
				boolean evaluate(boolean eq) {
					return !eq;
				}

			};

			private final String sign;

			private Operator(String sign) {
				this.sign = sign;
			}

			private static Operator operator(String condition) {
				for (Operator operator : values()) {
					if (condition.contains(operator.sign)) {
						return operator;
					}
				}
				throw new IllegalArgumentException("Unsupported operator in condition: " + condition);
			}

			private String[] split(String condition) {
				return condition.split(sign);
			}

			abstract boolean evaluate(boolean eq);

		}

		private final Function<String, Object> resolver;

		private ExpressionEvaluator(Function<String, Object> resolver) {
			this.resolver = resolver;
		}

		private String evaluate(String expression) {
			expression = expression.trim();
			if (expression.contains("?") && expression.contains(":")) {
				Ternary ternary = splitTernary(expression);
				return evaluateCondition(ternary.condition) ? stripQuotes(ternary.trueExpr)
						: stripQuotes(ternary.falseExpr);
			}
			return evaluateCondition(expression) ? "true" : "false";
		}

		private Ternary splitTernary(String expression) {
			int questionMark = expression.indexOf('?');
			int colon = expression.lastIndexOf(':');
			if (questionMark == -1 || colon == -1 || colon < questionMark) {
				throw new IllegalArgumentException("Invalid ternary expression: " + expression);
			}
			String condition = expression.substring(0, questionMark);
			String trueExpr = expression.substring(questionMark + 1, colon);
			String falseExpr = expression.substring(colon + 1);
			return new Ternary(condition, trueExpr, falseExpr);
		}

		private boolean evaluateCondition(String condition) {
			condition = condition.trim();
			Operator operator = Operator.operator(condition);

			String[] operands = operator.split(condition);
			if (operands.length != 2) {
				throw new IllegalArgumentException("Invalid condition: " + condition);
			}
			Object leftValue = resolver.apply(operands[0].trim());
			Object rightValue = parseValue(operands[1].trim());
			return operator.evaluate(Objects.equal(leftValue, rightValue));
		}

		private Object parseValue(String value) {
			if (value.startsWith("'") && value.endsWith("'")) {
				return value.substring(1, value.length() - 1);
			}
			if ("true".equalsIgnoreCase(value)) {
				return true;
			}
			if ("false".equalsIgnoreCase(value)) {
				return false;
			}
			try {
				return Integer.parseInt(value);
			} catch (NumberFormatException e) {
				return value;
			}
		}

		private String stripQuotes(String str) {
			str = str.trim();
			if (str.startsWith("'") && str.endsWith("'")) {
				return str.substring(1, str.length() - 1);
			}
			return str;
		}
	}

	private static final Pattern placeholderPattern = compile("(\\$)?\\{([^}]+)\\}");

	private NamedPlaceholders() {
		super();
	}

	public static String replace(String template, Map<String, Object> replacements) {
		return replace(template, k -> replacements.getOrDefault(k, k));
	}

	public static String replace(String template, Function<String, Object> resolver) {
		String result = template;

		while (true) {
			Matcher matcher = placeholderPattern.matcher(result);
			StringBuilder sb = new StringBuilder();
			boolean replaced = false;

			while (matcher.find()) {
				String dollarSign = matcher.group(1);
				String content = matcher.group(2);

				String replacement;
				if ("$".equals(dollarSign)) {
					replacement = new ExpressionEvaluator(resolver).evaluate(content);
				} else {
					Object value = resolver.apply(content);
					replacement = value == null ? "{" + content + "}" : value.toString();
				}

				if (!replacement.equals(content)) {
					replaced = true;
					matcher.appendReplacement(sb, quoteReplacement(replacement));
				}
			}

			matcher.appendTail(sb);
			if (!replaced) {
				break;
			}
			result = sb.toString();
		}

		return result;
	}

}
