package com.tianscar.properties;

import java.io.*;
import java.util.*;

final class Utils {

    private Utils() {
        throw new UnsupportedOperationException();
    }

    public static boolean matchValue(String[] strings, char value) {
        return matchValue(strings, Character.toString(value));
    }

    public static boolean matchValue(String[] strings, String value) {
        HashSet<String> commentSignsSet = new HashSet<>(strings.length);
        commentSignsSet.addAll(Arrays.asList(strings));
        return commentSignsSet.contains(value);
    }

    public static String makeLineSeparator(boolean cr, boolean lf) {
        StringBuilder builder = new StringBuilder();
        if (cr) builder.append('\r');
        if (lf) builder.append('\n');
        if (builder.length() < 1) return System.lineSeparator();
        else return builder.toString();
    }

    public static void writeHeader(String[] commentSigns, String[] delimiters, Writer writer, String comments,
                                   boolean writeDate, String commentSign, String delimiter, String lineSeparator) throws IOException {
        if (!matchValue(commentSigns, commentSign)) throw new IllegalArgumentException("invalid comment sign");
        if (!matchValue(delimiters, delimiter)) throw new IllegalArgumentException("invalid delimiter");
        if (comments != null) writeComment(commentSigns, writer, comments, lineSeparator, commentSign);
        if (writeDate) {
            writer.write(commentSign);
            writer.write(new Date().toString());
            writer.write(lineSeparator);
        }
    }

    public static void storeIni(IniProperties ini, String[] commentSigns, String[] delimiters, Writer writer, String comments, boolean escUnicode,
                                   boolean writeDate, String commentSign, String delimiter, String lineSeparator) throws IOException {
        writeHeader(commentSigns, delimiters, writer, comments, writeDate, commentSign, delimiter, lineSeparator);
        writeProperties(ini.getSection(null), commentSigns, delimiters, writer, escUnicode, commentSign, delimiter, lineSeparator);
        for (Map.Entry<String, CommentedProperties> sectionEntry : ini.sections.entrySet()) {
            writer.write('[');
            dumpString(commentSigns, delimiters, writer, sectionEntry.getKey(), false, escUnicode);
            writer.write(']');
            writer.write(lineSeparator);
            writeProperties(sectionEntry.getValue(), commentSigns, delimiters, writer, escUnicode, commentSign, delimiter, lineSeparator);
        }
        writer.flush();
    }

    public static void storeProperties(CommentedProperties properties, String[] commentSigns, String[] delimiters,
                                       Writer writer, String comments, boolean escUnicode,
                                       boolean writeDate, String commentSign, String delimiter, String lineSeparator) throws IOException {
        writeHeader(commentSigns, delimiters, writer, comments, writeDate, commentSign, delimiter, lineSeparator);
        writeProperties(properties, commentSigns, delimiters, writer, escUnicode, commentSign, delimiter, lineSeparator);
        writer.flush();
    }

    public static void writeProperties(CommentedProperties properties, String[] commentSigns, String[] delimiters,
                                       Writer writer, boolean escUnicode,
                                       String commentSign, String delimiter, String lineSeparator) throws IOException {
        Object key;
        Object value;
        String comment;
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            key = entry.getKey();
            value = entry.getValue();
            comment = properties.getComment(key);
            if (comment != null) writeComment(commentSigns, writer, comment, lineSeparator, commentSign);
            dumpString(commentSigns, delimiters, writer, convertToString(key), true, escUnicode);
            writer.write(delimiter);
            dumpString(commentSigns, delimiters, writer, convertToString(value), false, escUnicode);
            writer.write(lineSeparator);
        }
        String footer = properties.getFooter();
        if (footer != null) writeComment(commentSigns, writer, footer, lineSeparator, commentSign);
    }

    public static String convertToString(Object o) {
        if (o instanceof String) return (String) o;
        else if (o != null) return o.toString();
        else throw new IllegalArgumentException("o cannot be null");
    }

    public static void dumpString(String[] commentSigns, String[] delimiters, Writer writer, String string, boolean isKey, boolean escUnicode) throws IOException {
        int index = 0, length = string.length();
        if (!isKey && index < length && string.charAt(index) == ' ') {
            writer.write("\\ ");
            index ++;
        }
        for (; index < length; index++) {
            char ch = string.charAt(index);
            switch (ch) {
                case '\t':
                    writer.write("\\t");
                    break;
                case '\n':
                    writer.write("\\n");
                    break;
                case '\f':
                    writer.write("\\f");
                    break;
                case '\r':
                    writer.write("\\r");
                    break;
                default:
                    if ("\\".indexOf(ch) >= 0 || matchValue(commentSigns, ch) || matchValue(delimiters, ch) || (isKey && ch == ' ')) {
                        writer.write('\\');
                    }
                    if (ch >= ' ' && ch <= '~') {
                        writer.write(ch);
                    }
                    else if (escUnicode) {
                        writer.write(toHexaDecimal(ch));
                    }
                    else writer.write(ch);
                    break;
            }
        }
    }

    public static char[] toHexaDecimal(int ch) {
        char[] hexChars = { '\\', 'u', '0', '0', '0', '0' };
        int hexChar, index = hexChars.length, copyOfCh = ch;
        do {
            hexChar = copyOfCh & 15;
            if (hexChar > 9) {
                hexChar = hexChar - 10 + 'A';
            }
            else {
                hexChar += '0';
            }
            hexChars[-- index] = (char) hexChar;
        } while ((copyOfCh >>>= 4) != 0);
        return hexChars;
    }

    public static void writeComment(String[] commentSigns, Writer writer, String comment, String lineSeparator, String commentSign) throws IOException {
        writer.write(commentSign);
        char[] chars = comment.toCharArray();
        for (int index = 0; index < chars.length; index++) {
            if (chars[index] == '\r' || chars[index] == '\n') {
                int indexPlusOne = index + 1;
                if (chars[index] == '\r' && indexPlusOne < chars.length
                        && chars[indexPlusOne] == '\n') {
                    // "\r\n"
                    continue;
                }
                writer.write(lineSeparator);
                if (indexPlusOne < chars.length && matchValue(commentSigns, chars[indexPlusOne])) {
                    // return char with either comment sign afterward
                    continue;
                }
                writer.write(commentSign);
            } else {
                writer.write(chars[index]);
            }
        }
        writer.write(lineSeparator);
    }


    public static void loadIni(IniProperties ini, String[] commentSigns,
                                      String[] delimiters, Reader reader, boolean readComments) throws IOException {
        CommentedProperties properties;
        ini.setSection(null, properties = ini.newSection());
        boolean readingSectionName = false;
        int sectionNameBegin = -1, sectionNameEnd = -1;
        int mode = NONE, unicode = 0, count = 0;
        char nextChar;
        char[] buf = new char[40];
        StringBuilder comment;
        if (readComments) comment = new StringBuilder();
        else comment = null;
        int offset = 0, keyLength = -1, intVal;
        boolean firstChar = true;

        while (true) {
            intVal = reader.read();
            if (intVal == -1) {
                // if mode is UNICODE but has less than 4 hex digits, should
                // throw an IllegalArgumentException
                if (mode == UNICODE && count < 4) {
                    throw new IllegalArgumentException("Invalid Unicode sequence: expected format \\uxxxx");
                }
                // if mode is SLASH and no data is read, should append '\u0000'
                // to buf
                if (mode == SLASH) {
                    buf[offset++] = '\u0000';
                }
                break;
            }
            nextChar = (char) (intVal & 0xff);

            if (offset == buf.length) {
                char[] newBuf = new char[buf.length * 2];
                System.arraycopy(buf, 0, newBuf, 0, offset);
                buf = newBuf;
            }
            if (mode == UNICODE) {
                int digit = Character.digit(nextChar, 16);
                if (digit >= 0) {
                    unicode = (unicode << 4) + digit;
                    if (++count < 4) {
                        continue;
                    }
                } else if (count <= 4) {
                    throw new IllegalArgumentException("Invalid Unicode sequence: illegal character");
                }
                mode = NONE;
                buf[offset++] = (char) unicode;
                if (nextChar != '\n') {
                    continue;
                }
            }
            if (mode == SLASH) {
                mode = NONE;
                switch (nextChar) {
                    case '\r':
                        mode = CONTINUE; // Look for a following \n
                        continue;
                    case '\n':
                        mode = IGNORE; // Ignore whitespace on the next line
                        continue;
                    case 'b':
                        nextChar = '\b';
                        break;
                    case 'f':
                        nextChar = '\f';
                        break;
                    case 'n':
                        nextChar = '\n';
                        break;
                    case 'r':
                        nextChar = '\r';
                        break;
                    case 't':
                        nextChar = '\t';
                        break;
                    case 'u':
                        mode = UNICODE;
                        unicode = count = 0;
                        continue;
                }
            } else {
                switch (nextChar) {
                    case '[':
                        if (!readingSectionName) {
                            readingSectionName = true;
                            sectionNameBegin = offset;
                            continue;
                        }
                        else break;
                    case ']':
                        sectionNameEnd = offset;
                        continue;
                    default:
                        if (matchValue(commentSigns, nextChar) && firstChar) {
                            while (true) {
                                intVal = reader.read();
                                if (intVal == -1) {
                                    break;
                                }
                                // & 0xff not required
                                nextChar = (char) intVal;
                                if (nextChar == '\r' || nextChar == '\n') {
                                    if (readComments && !readingSectionName) comment.append('\n');
                                    break;
                                }
                                if (readComments && !readingSectionName) comment.append((char) intVal);
                            }
                            continue;
                        }
                        else if (!readingSectionName && matchValue(delimiters, nextChar)) {
                            if (keyLength == -1) { // if parsing the key
                                mode = NONE;
                                keyLength = offset;
                                continue;
                            }
                        }
                        break;
                    case '\n':
                        if (mode == CONTINUE) { // Part of a \r\n sequence
                            mode = IGNORE; // Ignore whitespace on the next line
                            continue;
                        }
                        // fall into the next case
                    case '\r':
                        mode = NONE;
                        firstChar = true;
                        if (offset > 0 || (offset == 0 && keyLength == 0)) {
                            if (keyLength == -1) {
                                keyLength = offset;
                            }
                            String temp = new String(buf, 0, offset);
                            if (readingSectionName && sectionNameBegin != -1 && sectionNameEnd != -1) {
                                if (readComments && comment.length() > 0) {
                                    properties.setFooter(comment.substring(0, comment.length() - 1));
                                    comment.setLength(0);
                                }
                                String sectionName = temp.substring(sectionNameBegin, sectionNameEnd);
                                readingSectionName = false;
                                sectionNameBegin = sectionNameEnd = -1;
                                ini.setSection(sectionName, (properties = ini.newSection()));
                            }
                            else if (!readingSectionName) {
                                String key = temp.substring(0, keyLength);
                                properties.put(key, temp.substring(keyLength));
                                if (readComments && comment.length() > 0) {
                                    properties.setComment(key, comment.substring(0, comment.length() - 1));
                                    comment.setLength(0);
                                }
                            }
                        }
                        keyLength = -1;
                        offset = 0;
                        continue;
                    case '\\':
                        if (mode == KEY_DONE) {
                            keyLength = offset;
                        }
                        mode = SLASH;
                        continue;
                }
                if (Character.isWhitespace(nextChar)) {
                    if (mode == CONTINUE) {
                        mode = IGNORE;
                    }
                    // if key length == 0 or value length == 0
                    if (offset == 0 || offset == keyLength || mode == IGNORE) {
                        continue;
                    }
                    if (keyLength == -1) { // if parsing the key
                        mode = KEY_DONE;
                        continue;
                    }
                }
                if (mode == IGNORE || mode == CONTINUE) {
                    mode = NONE;
                }
            }
            firstChar = false;
            if (mode == KEY_DONE) {
                keyLength = offset;
                mode = NONE;
            }
            buf[offset++] = nextChar;
        }
        if (keyLength == -1 && offset > 0) {
            keyLength = offset;
        }
        if (keyLength >= 0) {
            String temp = new String(buf, 0, offset);
            if (readingSectionName && sectionNameBegin != -1 && sectionNameEnd != -1) {
                String sectionName = temp.substring(sectionNameBegin, sectionNameEnd);
                if (readComments && comment.length() > 0) {
                    properties.setFooter(comment.substring(0, comment.length() - 1));
                    comment.setLength(0);
                }
                ini.setSection(sectionName, (properties = ini.newSection()));
            }
            else if (!readingSectionName) {
                String key = temp.substring(0, keyLength);
                properties.put(key, temp.substring(keyLength));
                if (readComments && comment.length() > 0) {
                    properties.setComment(key, comment.substring(0, comment.length() - 1));
                    comment.setLength(0);
                }
            }
        }
        if (readComments && comment.length() > 0) properties.setFooter(comment.substring(0, comment.length() - 1));
    }

    private static final int NONE = 0, SLASH = 1, UNICODE = 2, CONTINUE = 3, KEY_DONE = 4, IGNORE = 5;
    public static void loadProperties(CommentedProperties properties, String[] commentSigns,
                                      String[] delimiters, Reader reader, boolean readComments) throws IOException {
        int mode = NONE, unicode = 0, count = 0;
        char nextChar;
        char[] buf = new char[40];
        StringBuilder comment;
        if (readComments) comment = new StringBuilder();
        else comment = null;
        int offset = 0, keyLength = -1, intVal;
        boolean firstChar = true;

        while (true) {
            intVal = reader.read();
            if (intVal == -1) {
                // if mode is UNICODE but has less than 4 hex digits, should
                // throw an IllegalArgumentException
                if (mode == UNICODE && count < 4) {
                    throw new IllegalArgumentException("Invalid Unicode sequence: expected format \\uxxxx");
                }
                // if mode is SLASH and no data is read, should append '\u0000'
                // to buf
                if (mode == SLASH) {
                    buf[offset++] = '\u0000';
                }
                break;
            }
            nextChar = (char) (intVal & 0xff);

            if (offset == buf.length) {
                char[] newBuf = new char[buf.length * 2];
                System.arraycopy(buf, 0, newBuf, 0, offset);
                buf = newBuf;
            }
            if (mode == UNICODE) {
                int digit = Character.digit(nextChar, 16);
                if (digit >= 0) {
                    unicode = (unicode << 4) + digit;
                    if (++count < 4) {
                        continue;
                    }
                } else if (count <= 4) {
                    throw new IllegalArgumentException("Invalid Unicode sequence: illegal character");
                }
                mode = NONE;
                buf[offset++] = (char) unicode;
                if (nextChar != '\n') {
                    continue;
                }
            }
            if (mode == SLASH) {
                mode = NONE;
                switch (nextChar) {
                    case '\r':
                        mode = CONTINUE; // Look for a following \n
                        continue;
                    case '\n':
                        mode = IGNORE; // Ignore whitespace on the next line
                        continue;
                    case 'b':
                        nextChar = '\b';
                        break;
                    case 'f':
                        nextChar = '\f';
                        break;
                    case 'n':
                        nextChar = '\n';
                        break;
                    case 'r':
                        nextChar = '\r';
                        break;
                    case 't':
                        nextChar = '\t';
                        break;
                    case 'u':
                        mode = UNICODE;
                        unicode = count = 0;
                        continue;
                }
            } else {
                switch (nextChar) {
                    default:
                        if (matchValue(commentSigns, nextChar) && firstChar) {
                            while (true) {
                                intVal = reader.read();
                                if (intVal == -1) {
                                    break;
                                }
                                // & 0xff not required
                                nextChar = (char) intVal;
                                if (nextChar == '\r' || nextChar == '\n') {
                                    if (readComments) comment.append('\n');
                                    break;
                                }
                                if (readComments) comment.append((char) intVal);
                            }
                            continue;
                        }
                        else if (matchValue(delimiters, nextChar)) {
                            if (keyLength == -1) { // if parsing the key
                                mode = NONE;
                                keyLength = offset;
                                continue;
                            }
                        }
                        break;
                    case '\n':
                        if (mode == CONTINUE) { // Part of a \r\n sequence
                            mode = IGNORE; // Ignore whitespace on the next line
                            continue;
                        }
                        // fall into the next case
                    case '\r':
                        mode = NONE;
                        firstChar = true;
                        if (offset > 0 || (offset == 0 && keyLength == 0)) {
                            if (keyLength == -1) {
                                keyLength = offset;
                            }
                            String temp = new String(buf, 0, offset);
                            String key = temp.substring(0, keyLength);
                            properties.put(key, temp.substring(keyLength));
                            if (readComments) {
                                properties.setComment(key, comment.toString());
                                comment.setLength(0);
                            }
                        }
                        keyLength = -1;
                        offset = 0;
                        continue;
                    case '\\':
                        if (mode == KEY_DONE) {
                            keyLength = offset;
                        }
                        mode = SLASH;
                        continue;
                }
                if (Character.isWhitespace(nextChar)) {
                    if (mode == CONTINUE) {
                        mode = IGNORE;
                    }
                    // if key length == 0 or value length == 0
                    if (offset == 0 || offset == keyLength || mode == IGNORE) {
                        continue;
                    }
                    if (keyLength == -1) { // if parsing the key
                        mode = KEY_DONE;
                        continue;
                    }
                }
                if (mode == IGNORE || mode == CONTINUE) {
                    mode = NONE;
                }
            }
            firstChar = false;
            if (mode == KEY_DONE) {
                keyLength = offset;
                mode = NONE;
            }
            buf[offset++] = nextChar;
        }
        if (keyLength == -1 && offset > 0) {
            keyLength = offset;
        }
        if (keyLength >= 0) {
            String temp = new String(buf, 0, offset);
            String key = temp.substring(0, keyLength);
            properties.put(key, temp.substring(keyLength));
            if (readComments) {
                properties.setComment(key, comment.toString());
                comment.setLength(0);
            }
        }
        if (readComments && comment.length() > 0) properties.setFooter(comment.toString());
    }

}