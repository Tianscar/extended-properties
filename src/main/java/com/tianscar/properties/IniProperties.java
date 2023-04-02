package com.tianscar.properties;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.tianscar.properties.Utils.*;

public class IniProperties extends FilterProperties {

    private static final long serialVersionUID = 6700047722366213321L;

    private static final String[] COMMENT_SIGNS = new String[] { "#", ";" };
    private static final String[] DELIMITERS = new String[] { "=", ":" };

    protected final ConcurrentHashMap<String, Properties> sections;
    protected final AtomicReference<Properties> globalProperties;

    private final AtomicReference<String> currentSectionName = new AtomicReference<>(null);

    public String switchSection(String sectionName) {
        if (sectionName != null && sectionName.startsWith(".")) {
            if (this.currentSectionName.get() == null) sectionName = sectionName.substring(1);
            else sectionName = this.currentSectionName.get() + sectionName;
        }
        return this.currentSectionName.getAndSet(sectionName);
    }

    public Properties parentSection() {
        return parentSection(currentSectionName.get());
    }

    public Properties parentSection(String sectionName) {
        return getSection(parentSectionName(sectionName));
    }

    public boolean hasParentSection() {
        return hasParentSection(currentSectionName.get());
    }

    public boolean hasParentSection(String sectionName) {
        return sectionName != null && sectionName.lastIndexOf('.') != -1;
    }

    public String parentSectionName() {
        return parentSectionName(currentSectionName.get());
    }

    public String parentSectionName(String sectionName) {
        if (sectionName == null) return null;
        else {
            int dotIndex = sectionName.lastIndexOf('.');
            if (dotIndex == -1) return null;
            else return sectionName.substring(0, dotIndex);
        }
    }

    public String plainSectionName() {
        return plainSectionName(currentSectionName.get());
    }

    public String plainSectionName(String sectionName) {
        if (sectionName == null) return null;
        else {
            int dotIndex = sectionName.lastIndexOf('.');
            if (dotIndex == -1) return sectionName;
            else return sectionName.substring(dotIndex + 1);
        }
    }

    public String plainParentSectionName() {
        return plainParentSectionName(currentSectionName.get());
    }

    public String plainParentSectionName(String sectionName) {
        if (sectionName == null) return null;
        else {
            String parentSectionName = parentSectionName(sectionName);
            if (parentSectionName == null) return null;
            int dotIndex = parentSectionName.lastIndexOf('.');
            if (dotIndex == -1) return parentSectionName;
            else return parentSectionName.substring(dotIndex + 1);
        }
    }

    public String currentSectionName() {
        return currentSectionName.get();
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

    Properties newSection() {
        return new Properties(propertiesInitialCapacity.get());
    }

    private Properties currentSection() {
        if (currentSectionName.get() == null) return globalProperties.get();
        else if (getSection(currentSectionName.get()) == null) setSection(currentSectionName.get(), newSection());
        return getSection(currentSectionName.get());
    }

    public Properties setSection(String sectionName, Properties section) {
        if (sectionName == null) return globalProperties.getAndSet(section);
        else return sections.put(sectionName, section);
    }

    public Properties getSection(String sectionName) {
        if (sectionName == null) return globalProperties.get();
        else return sections.get(sectionName);
    }

    public Properties removeSection(String sectionName) {
        return sections.remove(sectionName);
    }

    @Override
    public Properties properties() {
        return currentSection();
    }

    public void listAll(PrintStream out) {
        out.println("-- current section: null --");
        globalProperties.get().list(out);
        for (Map.Entry<String, Properties> sectionEntry : sections.entrySet()) {
            out.println("-- current section: " + sectionEntry.getKey() +" --");
            sectionEntry.getValue().list(out);
        }
    }

    public void listAll(PrintWriter out) {
        out.println("-- current section: null --");
        globalProperties.get().list(out);
        for (Map.Entry<String, Properties> sectionEntry : sections.entrySet()) {
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

    public void store(Writer writer, String comment, boolean escUnicode, boolean writeDate, String commentSign, String delimiter, String lineSeparator) throws IOException {
        storeIni(this, COMMENT_SIGNS, DELIMITERS, writer instanceof BufferedWriter ? writer : new BufferedWriter(writer),
                comment, escUnicode, writeDate, commentSign, delimiter, checkLineSeparator(lineSeparator));
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

    public void store(OutputStream out, String comments, Charset charset, boolean escUnicode, boolean writeDate, String commentSign, String delimiter, String lineSeparator) throws IOException {
        storeIni(this, COMMENT_SIGNS, DELIMITERS, new BufferedWriter(new OutputStreamWriter(out, charset)),
                comments, escUnicode, writeDate, commentSign, delimiter, checkLineSeparator(lineSeparator));
    }

    public void store(OutputStream out, String comments, String encoding, boolean escUnicode, boolean writeDate, String commentSign, String delimiter, String lineSeparator) throws IOException {
        storeIni(this, COMMENT_SIGNS, DELIMITERS, new BufferedWriter(new OutputStreamWriter(out, encoding)),
                comments, escUnicode, writeDate, commentSign, delimiter, checkLineSeparator(lineSeparator));
    }

    public void storeToXML(OutputStream os) throws IOException {
        storeToXML(os, null, "UTF-8");
    }

    @Override
    public void storeToXML(OutputStream os, String comment) throws IOException {
        storeToXML(os, comment, "UTF-8");
    }

    @Override
    public void storeToXML(OutputStream os, String comment, String encoding) throws IOException {
        Objects.requireNonNull(os);
        Objects.requireNonNull(encoding);

        try {
            Charset charset = Charset.forName(encoding);
            storeToXML(os, comment, charset);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            throw new UnsupportedEncodingException(encoding);
        }
    }

    @Override
    public void storeToXML(OutputStream os, String comment, Charset charset) throws IOException {
        Objects.requireNonNull(os, "OutputStream");
        Objects.requireNonNull(charset, "Charset");
        if (!(os instanceof BufferedOutputStream)) os = new BufferedOutputStream(os);
        IniPropertiesHandler handler = new IniPropertiesHandler();
        handler.store(this, os, comment, charset);
    }

    @Override
    public synchronized void load(InputStream inStream) throws IOException {
        loadIni(this, COMMENT_SIGNS, DELIMITERS, new BufferedReader(new InputStreamReader(inStream, "ISO-8859-1")));
    }

    @Override
    public synchronized void loadFromXML(InputStream in) throws IOException {
        Objects.requireNonNull(in);
        if (!(in instanceof BufferedInputStream)) in = new BufferedInputStream(in);
        IniPropertiesHandler handler = new IniPropertiesHandler();
        handler.load(this, in);
        in.close();
    }

    public Map<String, Properties> sections() {
        return sections;
    }

    public Properties globalProperties() {
        return globalProperties.get();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (!super.equals(o)) return false;

        IniProperties that = (IniProperties) o;

        if (!sections.equals(that.sections)) return false;
        if (!globalProperties.get().equals(that.globalProperties.get())) return false;
        if (currentSectionName.get() != null ? !currentSectionName.get().equals(that.currentSectionName.get()) : that.currentSectionName.get() != null)
            return false;
        return getPropertiesInitialCapacity() == that.getPropertiesInitialCapacity();
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + sections.hashCode();
        result = 31 * result + globalProperties.get().hashCode();
        result = 31 * result + (currentSectionName.get() != null ? currentSectionName.get().hashCode() : 0);
        result = 31 * result + getPropertiesInitialCapacity();
        return result;
    }

}
