package com.tianscar.properties;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static com.tianscar.properties.Utils.*;

public class CommentedProperties extends FilterProperties {

    public static CommentedProperties wrap(Properties properties) {
        if (properties instanceof CommentedProperties) return (CommentedProperties) properties;
        else return new CommentedProperties(properties);
    }

    public static CommentedProperties wrap(Properties properties, int initialCapacity) {
        if (properties instanceof CommentedProperties) return (CommentedProperties) properties;
        else return new CommentedProperties(properties, initialCapacity);
    }

    private static final long serialVersionUID = 5896880009984256234L;

    private final ConcurrentHashMap<Object, String> comments;

    private static final String[] COMMENT_SIGNS = new String[] { "#", "!" };
    private static final String[] DELIMITERS = new String[] { "=", ":" };

    private final AtomicReference<String> footer = new AtomicReference<>(null);
    public String getFooter() {
        return footer.get();
    }
    public String setFooter(String footer) {
        return this.footer.getAndSet(footer);
    }

    private CommentedProperties(Properties properties) {
        this(properties, 8);
    }

    private CommentedProperties(Properties properties, int initialCapacity) {
        super(Objects.requireNonNull(properties, "properties cannot be null"));
        comments = new ConcurrentHashMap<>(initialCapacity);
    }

    protected CommentedProperties() {
        super();
        comments = null;
    }

    public Object removeProperty(String key) {
        return remove(key);
    }

    public boolean removeProperty(String key, String value) {
        return remove(key, value);
    }

    public Map<Object, String> comments() {
        return comments;
    }

    public String getComment(Object key) {
        if (containsKey(key)) return comments().get(key);
        else if (defaults instanceof CommentedProperties) return ((CommentedProperties) defaults).getComment(key);
        else return null;
    }

    public String getComment(Object key, String defaultValue) {
        String comment;
        if (containsKey(key)) comment = comments().get(key);
        else if (defaults instanceof CommentedProperties) comment = ((CommentedProperties) defaults).getComment(key, defaultValue);
        else comment = null;
        return comment == null ? defaultValue : comment;
    }

    public String setComment(Object key, String value) {
        return comments().put(key, value);
    }

    public String removeComment(Object key) {
        return comments().remove(key);
    }

    public boolean removeComment(Object key, String value) {
        return comments().remove(key, value);
    }

    public boolean keyHasComment(Object key) {
        return comments().containsKey(key) || (defaults instanceof CommentedProperties ? ((CommentedProperties) defaults).keyHasComment(key) : false);
    }

    public boolean containsComment(String value) {
        return comments().containsValue(value) || (defaults instanceof CommentedProperties ? ((CommentedProperties) defaults).containsComment(value) : false);
    }

    public void store(Writer writer) throws IOException {
        storeProperties(this, COMMENT_SIGNS, DELIMITERS, writer instanceof BufferedWriter ? writer : new BufferedWriter(writer),
                null, false, true, "#", "=", System.lineSeparator());
    }

    @Override
    public void store(Writer writer, String comment) throws IOException {
        storeProperties(this, COMMENT_SIGNS, DELIMITERS, writer instanceof BufferedWriter ? writer : new BufferedWriter(writer),
                comment, false, true, "#", "=", System.lineSeparator());
    }

    public void store(Writer writer, String comment, boolean escUnicode, boolean writeDate, String commentSign, String delimiter) throws IOException {
        storeProperties(this, COMMENT_SIGNS, DELIMITERS, writer instanceof BufferedWriter ? writer : new BufferedWriter(writer),
                comment, escUnicode, writeDate, commentSign, delimiter, System.lineSeparator());
    }

    public void store(Writer writer, String comment, boolean escUnicode, boolean writeDate, String commentSign, String delimiter, String lineSeparator) throws IOException {
        storeProperties(this, COMMENT_SIGNS, DELIMITERS, writer instanceof BufferedWriter ? writer : new BufferedWriter(writer),
                comment, escUnicode, writeDate, commentSign, delimiter, checkLineSeparator(lineSeparator));
    }

    public void store(OutputStream out) throws IOException {
        storeProperties(this, COMMENT_SIGNS, DELIMITERS, new BufferedWriter(new OutputStreamWriter(out, "ISO-8859-1")),
                null, true, true, "#", "=", System.lineSeparator());
    }

    @Override
    public void store(OutputStream out, String comments) throws IOException {
        storeProperties(this, COMMENT_SIGNS, DELIMITERS, new BufferedWriter(new OutputStreamWriter(out, "ISO-8859-1")),
                comments, true, true, "#", "=", System.lineSeparator());
    }

    public void store(OutputStream out, String comments, Charset charset) throws IOException {
        storeProperties(this, COMMENT_SIGNS, DELIMITERS, new BufferedWriter(new OutputStreamWriter(out, charset)),
                comments, !charset.name().toLowerCase().contains("utf"), true, "#", "=", System.lineSeparator());
    }

    public void store(OutputStream out, String comments, String encoding) throws IOException {
        storeProperties(this, COMMENT_SIGNS, DELIMITERS, new BufferedWriter(new OutputStreamWriter(out, encoding)),
                comments, !encoding.toLowerCase().contains("utf"), true, "#", "=", System.lineSeparator());
    }

    public void store(OutputStream out, String comments, Charset charset,
                      boolean escUnicode, boolean writeDate, String commentSign, String delimiter) throws IOException {
        storeProperties(this, COMMENT_SIGNS, DELIMITERS, new BufferedWriter(new OutputStreamWriter(out, charset)),
                comments, escUnicode, writeDate, commentSign, delimiter, System.lineSeparator());
    }

    public void store(OutputStream out, String comments, Charset charset,
                      boolean escUnicode, boolean writeDate, String commentSign, String delimiter, String lineSeparator) throws IOException {
        storeProperties(this, COMMENT_SIGNS, DELIMITERS, new BufferedWriter(new OutputStreamWriter(out, charset)),
                comments, escUnicode, writeDate, commentSign, delimiter, checkLineSeparator(lineSeparator));
    }

    public void store(OutputStream out, String comments, String encoding,
                      boolean escUnicode, boolean writeDate, String commentSign, String delimiter, String lineSeparator) throws IOException {
        storeProperties(this, COMMENT_SIGNS, DELIMITERS, new BufferedWriter(new OutputStreamWriter(out, encoding)),
                comments, escUnicode, writeDate, commentSign, delimiter, checkLineSeparator(lineSeparator));
    }

    @Override
    public synchronized void load(Reader reader) throws IOException {
        loadProperties(this, COMMENT_SIGNS, DELIMITERS, reader instanceof BufferedReader ? reader : new BufferedReader(reader), true);
    }

    public synchronized void load(Reader reader, boolean readComments) throws IOException {
        loadProperties(this, COMMENT_SIGNS, DELIMITERS, reader instanceof BufferedReader ? reader : new BufferedReader(reader), readComments);
    }

    @Override
    public synchronized void load(InputStream inStream) throws IOException {
        loadProperties(this, COMMENT_SIGNS, DELIMITERS, new BufferedReader(new InputStreamReader(inStream, "ISO-8859-1")), true);
    }

    public synchronized void load(InputStream inStream, boolean readComments) throws IOException {
        loadProperties(this, COMMENT_SIGNS, DELIMITERS, new BufferedReader(new InputStreamReader(inStream, "ISO-8859-1")), readComments);
    }

    public synchronized void load(InputStream inStream, Charset charset) throws IOException {
        loadProperties(this, COMMENT_SIGNS, DELIMITERS, new BufferedReader(new InputStreamReader(inStream, charset)), true);
    }

    public synchronized void load(InputStream inStream, String encoding) throws IOException {
        loadProperties(this, COMMENT_SIGNS, DELIMITERS, new BufferedReader(new InputStreamReader(inStream, encoding)), true);
    }

    public synchronized void load(InputStream inStream, boolean readComments, Charset charset) throws IOException {
        loadProperties(this, COMMENT_SIGNS, DELIMITERS, new BufferedReader(new InputStreamReader(inStream, charset)), readComments);
    }

    public synchronized void load(InputStream inStream, boolean readComments, String encoding) throws IOException {
        loadProperties(this, COMMENT_SIGNS, DELIMITERS, new BufferedReader(new InputStreamReader(inStream, encoding)), readComments);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (!super.equals(o)) return false;

        CommentedProperties that = (CommentedProperties) o;

        if (!comments().equals(that.comments())) return false;
        return getFooter() != null ? getFooter().equals(that.getFooter()) : that.getFooter() == null;
    }

    public boolean equalsIgnoreComments(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (comments() != null ? comments().hashCode() : 0);
        result = 31 * result + (getFooter() != null ? getFooter().hashCode() : 0);
        return result;
    }

}
