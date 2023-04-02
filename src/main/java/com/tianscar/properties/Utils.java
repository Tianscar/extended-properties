package com.tianscar.properties;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
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

    public static void writeHeader(String[] commentSigns, String[] delimiters, Writer writer, String comments,
                                   boolean writeDate, String commentSign, String delimiter) throws IOException {
        if (!matchValue(commentSigns, commentSign)) throw new IllegalArgumentException("invalid comment sign");
        if (!matchValue(delimiters, delimiter)) throw new IllegalArgumentException("invalid delimiter");
        if (comments != null) writeComment(commentSigns, writer, comments, commentSign);
        if (writeDate) {
            writer.write(commentSign);
            writer.write(new Date().toString());
            writer.write('\n');
        }
    }

    public static void storeIni(IniProperties ini, String[] commentSigns, String[] delimiters, Writer writer, String comments, boolean escUnicode,
                                   boolean writeDate, String commentSign, String delimiter) throws IOException {
        writeHeader(commentSigns, delimiters, writer, comments, writeDate, commentSign, delimiter);
        writeProperties(ini.getSection(null), commentSigns, delimiters, writer, escUnicode, delimiter);
        Properties section;
        for (Map.Entry<String, Properties> sectionEntry : ini.sections().entrySet()) {
            if ((section = sectionEntry.getValue()).isEmpty()) continue;
            writer.write('[');
            dumpString(commentSigns, delimiters, writer, sectionEntry.getKey(), false, escUnicode);
            writer.write(']');
            writer.write('\n');
            writeProperties(section, commentSigns, delimiters, writer, escUnicode, delimiter);
        }
        writer.flush();
    }

    public static void writeProperties(Properties properties, String[] commentSigns, String[] delimiters,
                                       Writer writer, boolean escUnicode, String delimiter) throws IOException {
        Object key;
        Object value;
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            key = entry.getKey();
            value = entry.getValue();
            dumpString(commentSigns, delimiters, writer, (String) key, true, escUnicode);
            writer.write(delimiter);
            dumpString(commentSigns, delimiters, writer, (String) value, false, escUnicode);
            writer.write('\n');
        }
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

    public static void writeComment(String[] commentSigns, Writer writer, String comment, String commentSign) throws IOException {
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
                writer.write('\n');
                if (indexPlusOne < chars.length && matchValue(commentSigns, chars[indexPlusOne])) {
                    // return char with either comment sign afterward
                    continue;
                }
                writer.write(commentSign);
            } else {
                writer.write(chars[index]);
            }
        }
        writer.write('\n');
    }


    public static void loadIni(IniProperties ini, String[] commentSigns, String[] delimiters, Reader reader) throws IOException {
        Properties properties;
        ini.setSection(null, properties = ini.newSection());
        boolean readingSectionName = false;
        String lastSectionName = null;
        int sectionNameBegin = -1, sectionNameEnd = -1;
        int mode = NONE, unicode = 0, count = 0;
        char nextChar;
        char[] buf = new char[40];
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
                                    break;
                                }
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
                                String sectionName = temp.substring(sectionNameBegin, sectionNameEnd);
                                if (sectionName.startsWith(".")) sectionName = lastSectionName == null ?
                                        sectionName.substring(1) : lastSectionName + sectionName;
                                lastSectionName = sectionName;
                                readingSectionName = false;
                                sectionNameBegin = sectionNameEnd = -1;
                                ini.setSection(sectionName, (properties = ini.newSection()));
                            }
                            else if (!readingSectionName) {
                                String key = removeQuotes(temp.substring(0, keyLength));
                                properties.put(key, removeQuotes(temp.substring(keyLength)));
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
                if (sectionName.startsWith(".")) sectionName = lastSectionName == null ?
                        sectionName.substring(1) : lastSectionName + sectionName;
                ini.setSection(sectionName, (properties = ini.newSection()));
            }
            else if (!readingSectionName) {
                String key = removeQuotes(temp.substring(0, keyLength));
                properties.put(key, removeQuotes(temp.substring(keyLength)));
            }
        }
    }

    public static String removeQuotes(String str) {
        if (str.startsWith("\"") && str.endsWith("\"")) return str.substring(1, str.length() - 1);
        else if (str.startsWith("'") && str.endsWith("'")) return str.substring(1, str.length() - 1);
        else return str;
    }

    private static final int NONE = 0, SLASH = 1, UNICODE = 2, CONTINUE = 3, KEY_DONE = 4, IGNORE = 5;
    public static void loadProperties(Properties properties, String[] commentSigns,
                                      String[] delimiters, Reader reader) throws IOException {
        int mode = NONE, unicode = 0, count = 0;
        char nextChar;
        char[] buf = new char[40];
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
                                    break;
                                }
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
        }
    }

    public static boolean hasParentSection(String sectionName) {
        return sectionName != null && sectionName.lastIndexOf('.') != -1;
    }

    public static String parentSectionName(String sectionName) {
        if (sectionName == null) return null;
        else {
            int dotIndex = sectionName.lastIndexOf('.');
            if (dotIndex == -1) return null;
            else return sectionName.substring(0, dotIndex);
        }
    }

    public static String plainSectionName(String sectionName) {
        if (sectionName == null) return null;
        else {
            int dotIndex = sectionName.lastIndexOf('.');
            if (dotIndex == -1) return sectionName;
            else return sectionName.substring(dotIndex + 1);
        }
    }

    public static String plainParentSectionName(String sectionName) {
        if (sectionName == null) return null;
        else {
            String parentSectionName = parentSectionName(sectionName);
            if (parentSectionName == null) return null;
            int dotIndex = parentSectionName.lastIndexOf('.');
            if (dotIndex == -1) return parentSectionName;
            else return parentSectionName.substring(dotIndex + 1);
        }
    }

}
