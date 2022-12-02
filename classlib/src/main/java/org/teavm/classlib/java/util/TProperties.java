/*
 *  Copyright 2015 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.classlib.java.util;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.teavm.classlib.java.io.TBufferedInputStream;
import org.teavm.classlib.java.io.TInputStream;
import org.teavm.classlib.java.io.TPrintStream;
import org.teavm.classlib.java.io.TReader;

public class TProperties extends THashtable<Object, Object> {
    /**
     * The default values for keys not found in this {@code Properties}
     * instance.
     */
    protected TProperties defaults;

    private static final int NONE = 0;
    private static final int SLASH = 1;
    private static final int UNICODE = 2;
    private static final int CONTINUE = 3;
    private static final int KEY_DONE = 4;
    private static final int IGNORE = 5;

    public TProperties() {
        super();
    }

    public TProperties(TProperties properties) {
        defaults = properties;
    }

    private void dumpString(StringBuilder buffer, String string, boolean isKey) {
        int index = 0;
        int length = string.length();
        if (!isKey && index < length && string.charAt(index) == ' ') {
            buffer.append("\\ "); //$NON-NLS-1$
            index++;
        }

        for (; index < length; index++) {
            char ch = string.charAt(index);
            switch (ch) {
            case '\t':
                buffer.append("\\t");
                break;
            case '\n':
                buffer.append("\\n");
                break;
            case '\f':
                buffer.append("\\f");
                break;
            case '\r':
                buffer.append("\\r");
                break;
            default:
                if ("\\#!=:".indexOf(ch) >= 0 || (isKey && ch == ' ')) {
                    buffer.append('\\');
                }
                if (ch >= ' ' && ch <= '~') {
                    buffer.append(ch);
                } else {
                    buffer.append(toHexaDecimal(ch));
                }
            }
        }
    }

    private char[] toHexaDecimal(final int ch) {
        char[] hexChars = { '\\', 'u', '0', '0', '0', '0' };
        int hexChar;
        int index = hexChars.length;
        int copyOfCh = ch;
        do {
            hexChar = copyOfCh & 15;
            if (hexChar > 9) {
                hexChar = hexChar - 10 + 'A';
            } else {
                hexChar += '0';
            }
            hexChars[--index] = (char) hexChar;
            copyOfCh >>>= 4;
        } while (copyOfCh != 0);
        return hexChars;
    }

    public String getProperty(String name) {
        Object result = super.get(name);
        String property = result instanceof String ? (String) result : null;
        if (property == null && defaults != null) {
            property = defaults.getProperty(name);
        }
        return property;
    }

    public String getProperty(String name, String defaultValue) {
        Object result = super.get(name);
        String property = result instanceof String ? (String) result : null;
        if (property == null && defaults != null) {
            property = defaults.getProperty(name);
        }
        if (property == null) {
            return defaultValue;
        }
        return property;
    }

    public void list(TPrintStream out) {
        if (out == null) {
            throw new NullPointerException();
        }
        StringBuilder buffer = new StringBuilder(80);
        TEnumeration<?> keys = propertyNames();
        while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            buffer.append(key);
            buffer.append('=');
            String property = (String) super.get(key);
            TProperties def = defaults;
            while (property == null) {
                property = (String) def.get(key);
                def = def.defaults;
            }
            if (property.length() > 40) {
                buffer.append(property.substring(0, 37));
                buffer.append("...");
            } else {
                buffer.append(property);
            }
            out.println(buffer.toString());
            buffer.setLength(0);
        }
    }
    
    
    class LineReader {
        public LineReader(TInputStream inStream) {
            this.inStream = inStream;
            inByteBuf = new byte[8192];
        }

        public LineReader(TReader reader) {
            this.reader = reader;
            inCharBuf = new char[8192];
        }

        byte[] inByteBuf;
        char[] inCharBuf;
        char[] lineBuf = new char[1024];
        int inLimit = 0;
        int inOff = 0;
        TInputStream inStream;
        TReader reader;

        int readLine() throws IOException {
            int len = 0;
            char c = 0;

            boolean skipWhiteSpace = true;
            boolean isCommentLine = false;
            boolean isNewLine = true;
            boolean appendedLineBegin = false;
            boolean precedingBackslash = false;
            boolean skipLF = false;

            while (true) {
                if (inOff >= inLimit) {
                    inLimit = (inStream==null)?reader.read(inCharBuf)
                                              :inStream.read(inByteBuf);
                    inOff = 0;
                    if (inLimit <= 0) {
                        if (len == 0 || isCommentLine) {
                            return -1;
                        }
                        if (precedingBackslash) {
                            len--;
                        }
                        return len;
                    }
                }
                if (inStream != null) {
                    //The line below is equivalent to calling a
                    //ISO8859-1 decoder.
                    c = (char) (0xff & inByteBuf[inOff++]);
                } else {
                    c = inCharBuf[inOff++];
                }
                if (skipLF) {
                    skipLF = false;
                    if (c == '\n') {
                        continue;
                    }
                }
                if (skipWhiteSpace) {
                    if (c == ' ' || c == '\t' || c == '\f') {
                        continue;
                    }
                    if (!appendedLineBegin && (c == '\r' || c == '\n')) {
                        continue;
                    }
                    skipWhiteSpace = false;
                    appendedLineBegin = false;
                }
                if (isNewLine) {
                    isNewLine = false;
                    if (c == '#' || c == '!') {
                        isCommentLine = true;
                        continue;
                    }
                }

                if (c != '\n' && c != '\r') {
                    lineBuf[len++] = c;
                    if (len == lineBuf.length) {
                        int newLength = lineBuf.length * 2;
                        if (newLength < 0) {
                            newLength = Integer.MAX_VALUE;
                        }
                        char[] buf = new char[newLength];
                        System.arraycopy(lineBuf, 0, buf, 0, lineBuf.length);
                        lineBuf = buf;
                    }
                    //flip the preceding backslash flag
                    if (c == '\\') {
                        precedingBackslash = !precedingBackslash;
                    } else {
                        precedingBackslash = false;
                    }
                }
                else {
                    // reached EOL
                    if (isCommentLine || len == 0) {
                        isCommentLine = false;
                        isNewLine = true;
                        skipWhiteSpace = true;
                        len = 0;
                        continue;
                    }
                    if (inOff >= inLimit) {
                        inLimit = (inStream==null)
                                  ?reader.read(inCharBuf)
                                  :inStream.read(inByteBuf);
                        inOff = 0;
                        if (inLimit <= 0) {
                            if (precedingBackslash) {
                                len--;
                            }
                            return len;
                        }
                    }
                    if (precedingBackslash) {
                        len -= 1;
                        //skip the leading whitespace characters in following line
                        skipWhiteSpace = true;
                        appendedLineBegin = true;
                        precedingBackslash = false;
                        if (c == '\r') {
                            skipLF = true;
                        }
                    } else {
                        return len;
                    }
                }
            }
        }
    }

    public synchronized void load(TReader reader) throws IOException {
    	load0(new LineReader(reader));
    }
    
    private void load0 (LineReader lr) throws IOException {
        char[] convtBuf = new char[1024];
        int limit;
        int keyLen;
        int valueStart;
        char c;
        boolean hasSep;
        boolean precedingBackslash;

        while ((limit = lr.readLine()) >= 0) {
            c = 0;
            keyLen = 0;
            valueStart = limit;
            hasSep = false;

            //System.out.println("line=<" + new String(lineBuf, 0, limit) + ">");
            precedingBackslash = false;
            while (keyLen < limit) {
                c = lr.lineBuf[keyLen];
                //need check if escaped.
                if ((c == '=' ||  c == ':') && !precedingBackslash) {
                    valueStart = keyLen + 1;
                    hasSep = true;
                    break;
                } else if ((c == ' ' || c == '\t' ||  c == '\f') && !precedingBackslash) {
                    valueStart = keyLen + 1;
                    break;
                }
                if (c == '\\') {
                    precedingBackslash = !precedingBackslash;
                } else {
                    precedingBackslash = false;
                }
                keyLen++;
            }
            while (valueStart < limit) {
                c = lr.lineBuf[valueStart];
                if (c != ' ' && c != '\t' &&  c != '\f') {
                    if (!hasSep && (c == '=' ||  c == ':')) {
                        hasSep = true;
                    } else {
                        break;
                    }
                }
                valueStart++;
            }
            String key = loadConvert(lr.lineBuf, 0, keyLen, convtBuf);
            String value = loadConvert(lr.lineBuf, valueStart, limit - valueStart, convtBuf);
            put(key, value);
        }
    }
    
    private String loadConvert (char[] in, int off, int len, char[] convtBuf) {
        if (convtBuf.length < len) {
            int newLen = len * 2;
            if (newLen < 0) {
                newLen = Integer.MAX_VALUE;
            }
            convtBuf = new char[newLen];
        }
        char aChar;
        char[] out = convtBuf;
        int outLen = 0;
        int end = off + len;

        while (off < end) {
            aChar = in[off++];
            if (aChar == '\\') {
                aChar = in[off++];
                if(aChar == 'u') {
                    // Read the xxxx
                    int value=0;
                    for (int i=0; i<4; i++) {
                        aChar = in[off++];
                        switch (aChar) {
                          case '0': case '1': case '2': case '3': case '4':
                          case '5': case '6': case '7': case '8': case '9':
                             value = (value << 4) + aChar - '0';
                             break;
                          case 'a': case 'b': case 'c':
                          case 'd': case 'e': case 'f':
                             value = (value << 4) + 10 + aChar - 'a';
                             break;
                          case 'A': case 'B': case 'C':
                          case 'D': case 'E': case 'F':
                             value = (value << 4) + 10 + aChar - 'A';
                             break;
                          default:
                              throw new IllegalArgumentException(
                                           "Malformed \\uxxxx encoding.");
                        }
                     }
                    out[outLen++] = (char)value;
                } else {
                    if (aChar == 't') aChar = '\t';
                    else if (aChar == 'r') aChar = '\r';
                    else if (aChar == 'n') aChar = '\n';
                    else if (aChar == 'f') aChar = '\f';
                    out[outLen++] = aChar;
                }
            } else {
                out[outLen++] = aChar;
            }
        }
        return new String (out, 0, outLen);
    }

    @SuppressWarnings("fallthrough")
    public synchronized void load(TInputStream in) throws IOException {
        if (in == null) {
            throw new NullPointerException();
        }
        int mode = NONE;
        int unicode = 0;
        int count = 0;
        char nextChar;
        char[] buf = new char[40];
        int offset = 0;
        int keyLength = -1;
        int intVal;
        boolean firstChar = true;
        TBufferedInputStream bis = new TBufferedInputStream(in);

        while (true) {
            intVal = bis.read();
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
                    unicode = 0;
                    count = 0;
                    continue;
                }
            } else {
                switch (nextChar) {
                case '#':
                case '!':
                    if (firstChar) {
                        while (true) {
                            intVal = bis.read();
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
                    break;
                case '\n':
                    if (mode == CONTINUE) { // Part of a \r\n sequence
                        mode = IGNORE; // Ignore whitespace on the next line
                        continue;
                    }
                    // fall into the next case
                    break;
                case '\r':
                    if (mode == CONTINUE) { // Part of a \r\n sequence
                        mode = IGNORE; // Ignore whitespace on the next line
                        continue;
                    }

                    mode = NONE;
                    firstChar = true;
                    if (offset > 0 || (offset == 0 && keyLength == 0)) {
                        if (keyLength == -1) {
                            keyLength = offset;
                        }
                        String temp = new String(buf, 0, offset);
                        put(temp.substring(0, keyLength), temp
                                .substring(keyLength));
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
                case ':':
                case '=':
                    if (keyLength == -1) { // if parsing the key
                        mode = NONE;
                        keyLength = offset;
                        continue;
                    }
                    break;
                }
                if (nextChar < 256 && Character.isWhitespace(nextChar)) {
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
            put(temp.substring(0, keyLength), temp.substring(keyLength));
        }
    }

    public TEnumeration<?> propertyNames() {
        THashtable<Object, Object> selected = new THashtable<>();
        selectProperties(selected);
        return selected.keys();
    }

    private void selectProperties(TMap<Object, Object> selected) {
        if (defaults != null) {
            defaults.selectProperties(selected);
        }
        selected.putAll(this);
    }

    @Deprecated
    public void save(OutputStream out, String comment) {
        try {
            store(out, comment);
        } catch (IOException e) {
            // do nothing
        }
    }

    public Object setProperty(String name, String value) {
        return put(name, value);
    }

    public synchronized void store(OutputStream out, String comments) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(out, "ISO8859_1");
        if (comments != null) {
            writeComments(writer, comments);
        }
        writer.write('#');
        writer.write(new TDate().toString());
        writer.write("\n");

        StringBuilder buffer = new StringBuilder(200);
        for (TIterator<TMap.Entry<Object, Object>> iter = entrySet().iterator(); iter.hasNext();) {
            TMap.Entry<Object, Object> entry = iter.next();
            String key = (String) entry.getKey();
            dumpString(buffer, key, true);
            buffer.append('=');
            dumpString(buffer, (String) entry.getValue(), false);
            buffer.append("\n");
            writer.write(buffer.toString());
            buffer.setLength(0);
        }
        writer.flush();
    }

    private void writeComments(Writer writer, String comments) throws IOException {
        writer.write('#');
        char[] chars = comments.toCharArray();
        for (int index = 0; index < chars.length; index++) {
            if (chars[index] == '\r' || chars[index] == '\n') {
                int indexPlusOne = index + 1;
                if (chars[index] == '\r' && indexPlusOne < chars.length
                        && chars[indexPlusOne] == '\n') {
                    // "\r\n"
                    continue;
                }
                writer.write("\n");
                if (indexPlusOne < chars.length
                        && (chars[indexPlusOne] == '#' || chars[indexPlusOne] == '!')) {
                    // return char with either '#' or '!' afterward
                    continue;
                }
                writer.write('#');
            } else {
                writer.write(chars[index]);
            }
        }
        writer.write("\n");
    }

    public TSet<String> stringPropertyNames() {
        TSet<String> selected = new THashSet<>();
        selectPropertyNames(selected);
        return selected;
    }

    private void selectPropertyNames(TSet<String> selected) {
        if (defaults != null) {
            defaults.selectPropertyNames(selected);
        }

        TEnumeration<Object> e = keys();
        while (e.hasMoreElements()) {
            Object key = e.nextElement();
            if (key instanceof String) {
                selected.add((String) key);
            }
        }
    }
}
