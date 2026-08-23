package com.dmart.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 최소 기능 JSON 파서/직렬화기. Gson 같은 외부 라이브러리 없이,
 * 이 프로젝트가 실제로 주고받는 "평평한 객체 + 문자열/숫자/불리언/배열" 정도의 JSON만
 * 정확히 처리하면 되므로 직접 구현함 (외부 jar 의존성 없음).
 */
public class JsonUtil {

    // ---------- 파싱 ----------

    public static Object parse(String json) {
        Parser p = new Parser(json);
        Object value = p.parseValue();
        p.skipWhitespace();
        if (p.i != json.length()) {
            throw new IllegalArgumentException("JSON 끝에 남는 문자가 있습니다: " + json.substring(p.i));
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object value = parse(json);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("JSON 객체가 아닙니다: " + json);
        }
        return (Map<String, Object>) value;
    }

    private static class Parser {
        private final String s;
        private int i = 0;

        Parser(String s) {
            this.s = s;
        }

        Object parseValue() {
            skipWhitespace();
            char c = s.charAt(i);
            switch (c) {
                case '{': return parseObjectInternal();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': expectLiteral("true"); return Boolean.TRUE;
                case 'f': expectLiteral("false"); return Boolean.FALSE;
                case 'n': expectLiteral("null"); return null;
                default: return parseNumber();
            }
        }

        Map<String, Object> parseObjectInternal() {
            Map<String, Object> map = new LinkedHashMap<>();
            expectChar('{');
            skipWhitespace();
            if (peek() == '}') {
                i++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expectChar(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char c = s.charAt(i);
                if (c == ',') {
                    i++;
                    continue;
                }
                if (c == '}') {
                    i++;
                    break;
                }
                throw new IllegalArgumentException("잘못된 JSON(object), 위치 " + i);
            }
            return map;
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            expectChar('[');
            skipWhitespace();
            if (peek() == ']') {
                i++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                char c = s.charAt(i);
                if (c == ',') {
                    i++;
                    continue;
                }
                if (c == ']') {
                    i++;
                    break;
                }
                throw new IllegalArgumentException("잘못된 JSON(array), 위치 " + i);
            }
            return list;
        }

        String parseString() {
            expectChar('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    char esc = s.charAt(i++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            String hex = s.substring(i, i + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                            break;
                        default:
                            throw new IllegalArgumentException("알 수 없는 escape: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Double parseNumber() {
            int start = i;
            if (peek() == '-') {
                i++;
            }
            while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            return Double.parseDouble(s.substring(start, i));
        }

        void expectLiteral(String literal) {
            if (!s.startsWith(literal, i)) {
                throw new IllegalArgumentException("잘못된 JSON: '" + literal + "' 예상, 위치 " + i);
            }
            i += literal.length();
        }

        void expectChar(char c) {
            skipWhitespace();
            if (i >= s.length() || s.charAt(i) != c) {
                throw new IllegalArgumentException("잘못된 JSON: '" + c + "' 예상, 위치 " + i);
            }
            i++;
        }

        char peek() {
            skipWhitespace();
            return s.charAt(i);
        }

        void skipWhitespace() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }
    }

    // ---------- 직렬화 ----------

    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString(sb, (String) value);
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value);
        } else if (value instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof Object[]) {
            write(sb, Arrays.asList((Object[]) value));
        } else if (value instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, item);
            }
            sb.append(']');
        } else {
            // LocalDate/LocalDateTime 등 - 문자열로 직렬화
            writeString(sb, value.toString());
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ---------- 응답 객체 조립용 편의 메서드 ----------

    /** JsonUtil.object("userId", 1, "name", "홍길동") 처럼 써서 순서 있는 Map을 간단히 조립 */
    public static Map<String, Object> object(Object... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("키-값 쌍의 개수가 맞아야 합니다");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            map.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return map;
    }
}
