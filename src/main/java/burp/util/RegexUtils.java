package burp.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexUtils {
    /**
     * 生成匹配文本B的正则表达式，并确保匹配唯一
     * @param textA 原始文本A
     * @param textB 选中的文本B
     * @return 生成的正则表达式
     */
    public static String generateRegex(String textA, String textB, int start) {
        if (start == -1) {
            return "Error: TextB is not found in TextA.";
        }

        int beforeLength = 5;
        int afterLength = 5;
        int maxIterations = textA.length();
        int iterations = 0;
        String regex;

        do {
            int beforeStart = Math.max(0, start - beforeLength);
            int afterEnd = Math.min(textA.length(), start + textB.length() + afterLength);
            String before = textA.substring(beforeStart, start);
            String after = textA.substring(start + textB.length(), afterEnd);

            before = escapeRegex(before);
            after = escapeRegex(after);

            if (after.isEmpty()) {
                after = "$";
            }

            regex = before + "(.*?)" + after;

            beforeLength++;
            afterLength++;
            iterations++;
        } while (countMatches(textA, regex) > 1 && iterations < maxIterations);

        return regex;
    }

    /**
     * 统计正则表达式在文本A中的匹配次数
     */
    private static int countMatches(String textA, String regex) {
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(textA);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * 转义正则表达式中的特殊字符，同时将回车、换行、制表符转换为对应的字符串(\r, \n, \t)
     */
    private static String escapeRegex(String str) {
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            switch(c) {
                case '\r':
                    sb.append("\\r");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    // 如果字符是正则元字符，则转义它
                    if (".+*?^$()[]{}|\\".indexOf(c) >= 0) {
                        sb.append("\\").append(c);
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

}
