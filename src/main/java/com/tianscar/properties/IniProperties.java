package com.tianscar.properties;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.tianscar.properties.Utils.*;

public class IniProperties extends CommentedProperties {

    private static final long serialVersionUID = 6700047722366213321L;

    private static final String[] COMMENT_SIGNS = new String[] { "#", ";" };
    private static final String[] DELIMITERS = new String[] { "=", ":" };

    protected final ConcurrentHashMap<String, CommentedProperties> sections;
    protected final AtomicReference<CommentedProperties> globalProperties;

    private final AtomicReference<String> currentSectionName = new AtomicReference<>(null);

    public String getCurrentSectionName() {
        return currentSectionName.get();
    }

    public String setCurrentSectionName(String sectionName) {
        return this.currentSectionName.getAndSet(sectionName);
    }

    private final AtomicInteger propertiesInitialCapacity;

    public int getPropertiesInitialCapacity() {
        return propertiesInitialCapacity.get();
    }

    public int setPropertiesInitialCapacity(int propertiesInitialCapacity) {
        return this.propertiesInitialCapacity.getAndSet(propertiesInitialCapacity);
    }

    public IniProperties() {
        this(8, 8);
    }

    public IniProperties(int initialCapacity, int propertiesInitialCapacity) {
        super();
        sections = new ConcurrentHashMap<>(initialCapacity);
        this.propertiesInitialCapacity = new AtomicInteger(propertiesInitialCapacity);
        globalProperties = new AtomicReference<>(newSection());
    }

    CommentedProperties newSection() {
        return CommentedProperties.wrap(new Properties(propertiesInitialCapacity.get()));
    }

    private CommentedProperties currentSection() {
        if (getCurrentSectionName() == null) return globalProperties.get();
        else if (getSection(getCurrentSectionName()) == null) setSection(getCurrentSectionName(), newSection());
        return getSection(getCurrentSectionName());
    }

    public CommentedProperties setSection(String sectionName, CommentedProperties section) {
        if (sectionName == null) return globalProperties.getAndSet(section);
        else return sections.put(sectionName, section);
    }

    public CommentedProperties getSection(String sectionName) {
        if (sectionName == null) return globalProperties.get();
        else return sections.get(sectionName);
    }

    public CommentedProperties removeSection(String sectionName) {
        return sections.remove(sectionName);
    }

    @Override
    public Properties properties() {
        return currentSection();
    }

    @Override
    public Map<Object, String> comments() {
        return currentSection().comments();
    }

    public void listAll(PrintStream out) {
        for (Map.Entry<String, CommentedProperties> sectionEntry : sections.entrySet()) {
            out.println("-- current section: " + sectionEntry.getKey() +" --");
            sectionEntry.getValue().list(out);
        }
    }

    public void listAll(PrintWriter out) {
        for (Map.Entry<String, CommentedProperties> sectionEntry : sections.entrySet()) {
            out.println("-- current section: " + sectionEntry.getKey() +" --");
            sectionEntry.getValue().list(out);
        }
    }

    public void store(Writer writer) throws IOException {
        storeIni(this, COMMENT_SIGNS, DELIMITERS, writer instanceof BufferedWriter ? writer : new BufferedWriter(writer),
                null, false, true, "#", "=", System.lineSeparator());
    }

    @Override
    public void store(Writer writer, String comment) throws IOException {
        storeIni(this, COMMENT_SIGNS, DELIMITERS, writer instanceof BufferedWriter ? writer : new BufferedWriter(writer),
                comment, false, true, "#", "=", System.lineSeparator());
    }

    public void store(Writer writer, String comment, boolean escUnicode, boolean writeDate, String commentSign, String delimiter) throws IOException {
        storeIni(this, COMMENT_SIGNS, DELIMITERS, writer instanceof BufferedWriter ? writer : new BufferedWriter(writer),
                comment, escUnicode, writeDate, commentSign, delimiter, System.lineSeparator());
    }

    public void store(Writer writer, String comment, boolean escUnicode, boolean writeDate, String commentSign, String delimiter, boolean cr, boolean lf) throws IOException {
        storeIni(this, COMMENT_SIGNS, DELIMITERS, writer instanceof BufferedWriter ? writer : new BufferedWriter(writer),
                comment, escUnicode, writeDate, commentSign, delimiter, makeLineSeparator(cr, lf));
    }

    public void store(OutputStream out) throws IOException {
        Charset charset = Charset.defaultCharset();
        storeIni(this, COMMENT_SIGNS, DELIMITERS, new BufferedWriter(new OutputStreamWriter(out, charset)),
                null, !charset.name().toLowerCase().contains("utf"), true, "#", "=", System.lineSeparator());
    }

    @Override
    public void store(OutputStream out, String comments) throws IOException {
        Charset charset = Charset.defaultCharset();
        storeIni(this, COMMENT_SIGNS, DELIMITERS, new BufferedWriter(new OutputStreamWriter(out, charset)),
                comments, !charset.name().toLowerCase().contains("utf"), true, "#", "=", System.lineSeparator());
    }

    public void store(OutputStream out, String comments, Charset charset) throws IOException {
        storeIni(this, COMMENT_SIGNS, DELIMITERS, new BufferedWriter(new OutputStreamWriter(out, charset)),
                comments, !charset.name().toLowerCase().contains("utf"), true, "#", "=", System.lineSeparator());
    }

    public void store(OutputStream out, String comments, String encoding) throws IOException {
        storeIni(this, COMMENT_SIGNS, DELIMITERS, new BufferedWriter(new OutputStreamWriter(out, encoding)),
                comments, !encoding.toLowerCase().contains("utf"), true, "#", "=", System.lineSeparator());
    }

    public void store(OutputStream out, String comments, Charset charset, boolean escUnicode, boolean writeDate, String commentSign, String delimiter) throws IOException {
        storeIni(this, COMMENT_SIGNS, DELIMITERS, new BufferedWriter(new OutputStreamWriter(out, charset)),
                comments, escUnicode, writeDate, commentSign, delimiter, System.lineSeparator());
    }

    public void store(OutputStream out, String comments, Charset charset, boolean escUnicode, boolean writeDate, String commentSign, String delimiter, boolean cr, boolean lf) throws IOException {
        storeIni(this, COMMENT_SIGNS, DELIMITERS, new BufferedWriter(new OutputStreamWriter(out, charset)),
                comments, escUnicode, writeDate, commentSign, delimiter, makeLineSeparator(cr, lf));
    }

    public void store(OutputStream out, String comments, String encoding, boolean escUnicode, boolean writeDate, String commentSign, String delimiter, boolean cr, boolean lf) throws IOException {
        storeIni(this, COMMENT_SIGNS, DELIMITERS, new BufferedWriter(new OutputStreamWriter(out, encoding)),
                comments, escUnicode, writeDate, commentSign, delimiter, makeLineSeparator(cr, lf));
    }

    @Override
    public synchronized void load(InputStream inStream) throws IOException {
        loadIni(this, COMMENT_SIGNS, DELIMITERS, new BufferedReader(new InputStreamReader(inStream, "ISO-8859-1")), true);
    }

    @Override
    public String getFooter() {
        return currentSection().getFooter();
    }

    @Override
    public String setFooter(String footer) {
        return currentSection().setFooter(footer);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        IniProperties that = (IniProperties) o;

        if (!sections.equals(that.sections)) return false;
        if (!globalProperties.equals(that.globalProperties)) return false;
        if (!getCurrentSectionName().equals(that.getCurrentSectionName())) return false;
        return getPropertiesInitialCapacity() == that.getPropertiesInitialCapacity();
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + sections.hashCode();
        result = 31 * result + globalProperties.hashCode();
        result = 31 * result + getCurrentSectionName().hashCode();
        result = 31 * result + getPropertiesInitialCapacity();
        return result;
    }

}
