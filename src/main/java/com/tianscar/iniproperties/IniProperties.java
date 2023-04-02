package com.tianscar.iniproperties;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.InvalidPropertiesFormatException;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static com.tianscar.iniproperties.Utils.loadIni;
import static com.tianscar.iniproperties.Utils.storeIni;

/**
 * The {@link IniProperties} class represents a persistent set of
 * ini sections. The {@code IniProperties} can be saved to a stream
 * or loaded from a stream.
 * Because {@code IniProperties} inherits from {@code Hashtable}, the
 * {@code put} and {@code putAll} methods can be applied to a
 * {@code IniProperties} object.  Their use is strongly discouraged as they
 * allow the caller to insert entries whose keys or values are not
 * {@code String}.  The {@code setProperty} method should be used
 * instead.  If the {@code store} or {@code save} method is called
 * on a "compromised" {@code IniProperties} object that contains a
 * non-{@code String} key or value, the call will fail. Similarly,
 * the call to the {@code propertyNames} or {@code list} method
 * will fail if it is called on a "compromised" {@code IniProperties}
 * object that contains a non-{@code String} key.
 *
 * <p>
 * The iterators returned by the {@code iterator} method of this class's
 * "collection views" (that is, {@code entrySet()}, {@code keySet()}, and
 * {@code values()}) may not fail-fast (unlike the Hashtable implementation).
 * These iterators are guaranteed to traverse elements as they existed upon
 * construction exactly once, and may (but are not guaranteed to) reflect any
 * modifications subsequent to construction.
 * <p>
 * The {@link #load(java.io.Reader) load(Reader)} {@code /}
 * {@link #store(java.io.Writer, java.lang.String) store(Writer, String)}
 * methods load and store ini from and to a character based stream.
 *
 * The {@link #load(java.io.InputStream) load(InputStream)} {@code /}
 * {@link #store(java.io.OutputStream, java.lang.String) store(OutputStream, String)}
 * methods work the same way as the load(Reader)/store(Writer, String) pair, except
 * the input/output stream is encoded in ISO 8859-1 character encoding.
 * Characters that cannot be directly represented in this encoding can be written using
 * Unicode escapes as defined in section {&#064;jls  3.3} of
 * <cite>The Java Language Specification</cite>;
 * only a single 'u' character is allowed in an escape
 * sequence.
 *
 * <p> The {@link #loadFromXML(InputStream)} and {@link
 * #storeToXML(OutputStream, String, String)} methods load and store properties
 * in a simple XML format.  By default the UTF-8 character encoding is used,
 * however a specific encoding may be specified if required. Implementations
 * are required to support UTF-8 and UTF-16 and may support other encodings.
 * An XML properties document has the following DOCTYPE declaration:
 *
 * <pre>
 * &lt;!DOCTYPE ini SYSTEM "https://dtd.tianscar.com/ini.dtd"&gt;
 * </pre>
 * Note that the system URI (https://dtd.tianscar.com/ini.dtd) is
 * <i>not</i> accessed when exporting or importing properties; it merely
 * serves as a string to uniquely identify the DTD, which is:
 * <pre>
 *    &lt;?xml version="1.0" encoding="UTF-8"?&gt;
 *
 *    &lt;!-- DTD for ini --&gt;
 *
 *    &lt;!ELEMENT ini ( comment?, ( section | entry )* ) &gt;
 *
 *    &lt;!ATTLIST ini version CDATA #FIXED "1.0"&gt;
 *
 *    &lt;!ELEMENT section ( section | entry )* &gt;
 *
 *    &lt;!ATTLIST section name CDATA #REQUIRED &gt;
 *
 *    &lt;!ELEMENT entry (#PCDATA) &gt;
 *
 *    &lt;!ATTLIST entry key CDATA #REQUIRED&gt;
 * </pre>
 *
 * <p>This class is thread-safe: multiple threads can share a single
 * {@code IniProperties} object without the need for external synchronization.
 *
 * @author  Karstian Lee
 */
public class IniProperties extends FilterProperties {

    private static final long serialVersionUID = 6700047722366213321L;

    /**
     * The comment signs of the ini format.
     */
    private static final String[] COMMENT_SIGNS = new String[] { "#", ";" };

    /**
     * The delimiters of the ini format.
     */
    private static final String[] DELIMITERS = new String[] { "=", ":" };

    /**
     * The sections of this {@link IniProperties}.
     */
    private final ConcurrentHashMap<String, Properties> sections;

    /**
     * The global properties reference of this {@link IniProperties}.
     */
    private final AtomicReference<Properties> globalProperties;

    /**
     * The current section name reference of this {@link IniProperties}.
     */
    private final AtomicReference<String> currentSectionName = new AtomicReference<>(null);

    /**
     * Switches the current section to the specified section,
     * depends on this section name and returns the previous section name,
     * if the section does not exist, will be created automatically.
     * If the section name is {@code null}, will switch to the global properties.
     * @param sectionName the new section name
     * @return the previous section name
     */
    public String switchSection(String sectionName) {
        if (sectionName != null && sectionName.startsWith(".")) {
            if (this.currentSectionName.get() == null) sectionName = sectionName.substring(1);
            else sectionName = this.currentSectionName.get() + sectionName;
        }

        return this.currentSectionName.getAndSet(sectionName);
    }

    /**
     * Switches the current section to the parent section and returns the previous section name,
     * if the section does not exist, will be created automatically.
     * If the parent section does not exist, will switch to the global section.
     *
     * @see #switchSection(String)
     *
     * @return the previous section name
     */
    public String switchToParentSection() {
        return switchSection(Utils.parentSectionName(currentSectionName()));
    }

    /**
     * Returns the parent section of current section.
     * If the parent section does not exist, will return the global section.
     * @return the parent section, or the global section if parent section does not exist
     */
    public Properties parentSection() {
        return parentSection(currentSectionName.get());
    }

    /**
     * Returns the parent section of the specified section name.
     * If the parent section does not exist, will return the global section.
     * @param sectionName the section name
     * @return the parent section, or the global section if parent section does not exist
     */
    public Properties parentSection(String sectionName) {
        return getSection(Utils.parentSectionName(sectionName));
    }

    /**
     * Returns whether the current section has parent section.
     * @return true if the current section has parent section
     */
    public boolean hasParentSection() {
        return Utils.hasParentSection(currentSectionName.get());
    }

    /**
     * Returns the parent section name of current section.
     * If the parent section does not exist, will return {@code null}.
     * @return the parent section name, or {@code null} if parent section does not exist
     */
    public String parentSectionName() {
        return Utils.parentSectionName(currentSectionName.get());
    }

    /**
     * Returns the plain section name of current section.
     * @return the plain section name
     */
    public String plainSectionName() {
        return Utils.plainSectionName(currentSectionName.get());
    }

    /**
     * Returns the plain parent section name of current section.
     * @return the plain parent section name
     */
    public String plainParentSectionName() {
        return Utils.plainParentSectionName(currentSectionName.get());
    }

    /**
     * Returns the current section name.
     * @return the current section name
     */
    public String currentSectionName() {
        return currentSectionName.get();
    }

    /**
     * Creates an empty {@link IniProperties} with no default values.
     */
    public IniProperties() {
        this(8);
    }

    /**
     * Creates an empty {@link IniProperties} with an
     * initial size accommodating the specified number of elements without the
     * need to dynamically resize.
     *
     * @param initialCapacity the section map will be sized to
     *         accommodate this many elements
     * @throws IllegalArgumentException if the initial capacity is less than
     *         zero.
     */
    public IniProperties(int initialCapacity) {
        super(null);
        sections = new ConcurrentHashMap<>(initialCapacity);
        globalProperties = new AtomicReference<>(newSection());
    }

    Properties newSection() {
        return new Properties();
    }

    private Properties currentSection() {
        if (currentSectionName.get() == null) return globalProperties.get();
        else if (getSection(currentSectionName.get()) == null) setSection(currentSectionName.get(), newSection());
        return getSection(currentSectionName.get());
    }

    Properties setSection(String sectionName, Properties section) {
        if (sectionName == null) return globalProperties.getAndSet(section);
        else return sections.put(sectionName, section);
    }

    Properties getSection(String sectionName) {
        if (sectionName == null) return globalProperties.get();
        else return sections.get(sectionName);
    }

    Properties removeSection(String sectionName) {
        return sectionName == null ? null : sections.remove(sectionName);
    }

    /**
     * Returns the current section.
     * @return the current section
     */
    @Override
    public Properties properties() {
        return currentSection();
    }

    /**
     * Prints all sections out to the specified output stream.
     * This method is useful for debugging.
     *
     * @param   out   an output stream.
     * @throws  ClassCastException if any key in sections
     *          is not a string.
     */
    public void listAll(PrintStream out) {
        out.println("-- current section: null --");
        globalProperties.get().list(out);
        for (Map.Entry<String, Properties> sectionEntry : sections.entrySet()) {
            out.println("-- current section: " + sectionEntry.getKey() +" --");
            sectionEntry.getValue().list(out);
        }
    }

    /**
     * Prints all sections out to the specified output stream.
     * This method is useful for debugging.
     *
     * @param   out   an output stream.
     * @throws  ClassCastException if any key in sections
     *          is not a string.
     */
    public void listAll(PrintWriter out) {
        out.println("-- current section: null --");
        globalProperties.get().list(out);
        for (Map.Entry<String, Properties> sectionEntry : sections.entrySet()) {
            out.println("-- current section: " + sectionEntry.getKey() +" --");
            sectionEntry.getValue().list(out);
        }
    }

    /**
     * Calls the {@link #store(OutputStream out, String comments)} method
     * and suppresses IOExceptions that were thrown.
     *
     * @deprecated This method does not throw an IOException if an I/O error
     * occurs while saving the {@link IniProperties}.  The preferred way to save a
     * properties list is via the {@code store(OutputStream out,
     * String comments)} method or the
     * {@code storeToXML(OutputStream os, String comment)} method.
     *
     * @param   out      an output stream.
     * @param   comments   a description of the {@code IniProperties}.
     * @throws     ClassCastException  if this {@code IniProperties} object
     *             contains any keys or values that are not
     *             {@code Strings}.
     */
    @Deprecated
    @Override
    public void save(OutputStream out, String comments) {
        super.save(out, comments);
    }

    /**
     * Writes all sections (includes the global properties) in this
     * {@link IniProperties} to the output character stream in ini
     * format suitable for using the {@link #load(java.io.Reader) load(Reader)}
     * method.
     * <p>
     * Properties from the defaults table of this {@code IniProperties}
     * table (if any) are <i>not</i> written out by this method.
     * <p>
     * If the comments argument is not {@code null}, then an ASCII {@code #}
     * character, the comments string, and a line separator are first written
     * to the output stream. Thus, the {@code comments} can serve as an
     * identifying comment. Any one of a line feed ('\n'), a carriage
     * return ('\r'), or a carriage return followed immediately by a line feed
     * in comments is replaced by a line separator generated by the {@code Writer}
     * and if the next character in comments is not character {@code #} or
     * character {@code ;} then an ASCII {@code #} is written out
     * after that line separator.
     * <p>
     * Next, a comment line is always written, consisting of an ASCII
     * {@code #} character, the current date and time (as if produced
     * by the {@code toString} method of {@code Date} for the
     * current time), and a line separator as generated by the {@code Writer}.
     * <p>
     * Then every entry in this {@code IniProperties} is
     * written out. For each entry the key string is
     * written, then an ASCII {@code =}, then the associated
     * element string. For the key, all space characters are
     * written with a preceding {@code \} character.  For the
     * element, leading space characters, but not embedded or trailing
     * space characters, are written with a preceding {@code \}
     * character. The key and element characters {@code #},
     * {@code ;}, {@code =}, and {@code :} are written
     * with a preceding backslash to ensure that they are properly loaded.
     * <p>
     * After the entries have been written, the output stream is flushed.
     * The output stream remains open after this method returns.
     *
     * @param   writer      an output character stream writer.
     * @param   comments   a description of the {@code IniProperties}.
     * @throws     IOException if writing this {@code IniProperties} to the specified
     *             output stream throws an {@code IOException}.
     * @throws     ClassCastException  if this {@code IniProperties} object
     *             contains any keys or values that are not {@code String}.
     * @throws     NullPointerException  if {@code writer} is {@code null}.
     */
    @Override
    public void store(Writer writer, String comments) throws IOException {
        storeIni(this, COMMENT_SIGNS, DELIMITERS, writer instanceof BufferedWriter ? writer : new BufferedWriter(writer),
                comments, false, true, "#", "=");
    }

    /**
     * Writes all sections (includes the global properties) in this
     * {@link IniProperties} to the output character stream in ini
     * format suitable for using the {@link #load(InputStream) load(InputStream)}
     * method.
     * <p>
     * Properties from the defaults table of this {@code IniProperties}
     * table (if any) are <i>not</i> written out by this method.
     * <p>
     * This method outputs the comments, properties keys and values in
     * the same format as specified in
     * {@link #store(java.io.Writer, java.lang.String) store(Writer)},
     * with the following differences:
     * <ul>
     * <li>The stream is written using the ISO 8859-1 character encoding.
     *
     * <li>Characters not in Latin-1 in the comments are written as
     * {@code \u005Cu}<i>xxxx</i> for their appropriate unicode
     * hexadecimal value <i>xxxx</i>.
     *
     * <li>Characters less than {@code \u005Cu0020} and characters greater
     * than {@code \u005Cu007E} in property keys or values are written
     * as {@code \u005Cu}<i>xxxx</i> for the appropriate hexadecimal
     * value <i>xxxx</i>.
     * </ul>
     * <p>
     * After the entries have been written, the output stream is flushed.
     * The output stream remains open after this method returns.
     *
     * @param   out      an output stream.
     * @param   comments   a description of the {@code IniProperties}.
     * @throws     IOException if writing this {@code IniProperties} to the specified
     *             output stream throws an {@code IOException}.
     * @throws     ClassCastException  if this {@code IniProperties} object
     *             contains any keys or values that are not {@code Strings}.
     * @throws     NullPointerException  if {@code out} is {@code null}.
     */
    @Override
    public void store(OutputStream out, String comments) throws IOException {
        Charset charset = Charset.defaultCharset();
        storeIni(this, COMMENT_SIGNS, DELIMITERS, new BufferedWriter(new OutputStreamWriter(out, charset)),
                comments, !charset.name().toLowerCase().contains("utf"), true, "#", "=");
    }

    /**
     * Emits an XML document representing all of the {@link IniProperties} contained
     * in this table.
     *
     * <p> An invocation of this method of the form {@code props.storeToXML(os,
     * comment)} behaves in exactly the same way as the invocation
     * {@code props.storeToXML(os, comment, "UTF-8");}.
     *
     * @param os the output stream on which to emit the XML document.
     * @param comment a description of the {@code IniProperties}, or {@code null}
     *        if no comment is desired.
     * @throws IOException if writing to the specified output stream
     *         results in an {@code IOException}.
     * @throws NullPointerException if {@code os} is null.
     * @throws ClassCastException  if this {@code IniProperties} object
     *         contains any keys or values that are not
     *         {@code String}.
     * @see    #loadFromXML(InputStream)
     */
    @Override
    public void storeToXML(OutputStream os, String comment) throws IOException {
        storeToXML(os, comment, "UTF-8");
    }

    /**
     * Emits an XML document representing all of the properties contained
     * in this {@link IniProperties}, using the specified encoding.
     *
     * <p>The XML document will have the following DOCTYPE declaration:
     * <pre>
     * &lt;!DOCTYPE ini SYSTEM "https://dtd.tianscar.com/ini.dtd"&gt;
     * </pre>
     *
     * <p>If the specified comment is {@code null} then no comment
     * will be stored in the document.
     *
     * <p> An implementation is required to support writing of XML documents
     * that use the "{@code UTF-8}" or "{@code UTF-16}" encoding. An
     * implementation may support additional encodings.
     *
     * <p>The specified stream remains open after this method returns.
     *
     * <p>This method behaves the same as
     * {@linkplain #storeToXML(OutputStream os, String comment, Charset charset)}
     * except that it will {@linkplain java.nio.charset.Charset#forName look up the charset}
     * using the given encoding name.
     *
     * @param os        the output stream on which to emit the XML document.
     * @param comment   a description of the {@code IniProperties}, or {@code null}
     *                  if no comment is desired.
     * @param  encoding the name of a supported
     *                  <a href="../lang/package-summary.html#charenc">
     *                  character encoding</a>
     *
     * @throws IOException if writing to the specified output stream
     *         results in an {@code IOException}.
     * @throws java.io.UnsupportedEncodingException if the encoding is not
     *         supported by the implementation.
     * @throws NullPointerException if {@code os} is {@code null},
     *         or if {@code encoding} is {@code null}.
     * @throws ClassCastException  if this {@code IniProperties} object
     *         contains any keys or values that are not {@code String}.
     * @see    #loadFromXML(InputStream)
     * @see    <a href="http://www.w3.org/TR/REC-xml/#charencoding">Character
     *         Encoding in Entities</a>
     */
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

    /**
     * Emits an XML document representing all of the properties contained
     * in this {@link IniProperties}, using the specified encoding.
     *
     * <p>The XML document will have the following DOCTYPE declaration:
     * <pre>
     * &lt;!DOCTYPE ini SYSTEM "https://dtd.tianscar.com/ini.dtd"&gt;
     * </pre>
     *
     * <p>If the specified comment is {@code null} then no comment
     * will be stored in the document.
     *
     * <p> An implementation is required to support writing of XML documents
     * that use the "{@code UTF-8}" or "{@code UTF-16}" encoding. An
     * implementation may support additional encodings.
     *
     * <p> Unmappable characters for the specified charset will be encoded as
     * numeric character references.
     *
     * <p>The specified stream remains open after this method returns.
     *
     * @param os        the output stream on which to emit the XML document.
     * @param comment   a description of the {@code IniProperties}, or {@code null}
     *                  if no comment is desired.
     * @param charset   the charset
     *
     * @throws IOException if writing to the specified output stream
     *         results in an {@code IOException}.
     * @throws NullPointerException if {@code os} or {@code charset} is {@code null}.
     * @throws ClassCastException  if this {@code Properties} object
     *         contains any keys or values that are not {@code Strings}.
     * @see    #loadFromXML(InputStream)
     * @see    <a href="http://www.w3.org/TR/REC-xml/#charencoding">Character
     *         Encoding in Entities</a>
     */
    @Override
    public void storeToXML(OutputStream os, String comment, Charset charset) throws IOException {
        Objects.requireNonNull(os, "OutputStream");
        Objects.requireNonNull(charset, "Charset");
        if (!(os instanceof BufferedOutputStream)) os = new BufferedOutputStream(os);
        IniPropertiesHandler handler = new IniPropertiesHandler();
        handler.store(this, os, comment, charset);
    }

    /**
     * Reads an ini file from the input
     * byte stream. The input stream is assumed to use
     * the ISO 8859-1 character encoding; that is each byte is one Latin1
     * character. Characters not in Latin1, and certain special characters,
     * are represented in keys and elements using Unicode escapes as defined in
     * section {&#064;jls  3.3} of
     * <cite>The Java Language Specification</cite>.
     * <p>
     * The specified stream remains open after this method returns.
     *
     * @param      inStream   the input stream.
     * @throws     IOException  if an error occurred when reading from the
     *             input stream.
     * @throws     IllegalArgumentException if the input stream contains a
     *             malformed Unicode escape sequence.
     * @throws     NullPointerException if {@code inStream} is {@code null}.
     */
    @Override
    public synchronized void load(InputStream inStream) throws IOException {
        loadIni(this, COMMENT_SIGNS, DELIMITERS, new BufferedReader(new InputStreamReader(inStream, "ISO-8859-1")));
    }

    /**
     * Reads an ini file from the input character stream.
     *
     * <p>
     * <a id="unicodeescapes"></a>
     * Characters in keys and elements can be represented in escape
     * sequences similar to those used for character and string literals
     * (see sections {&#064;jls  3.3} and {&#064;jls  3.10.6} of
     * <cite>The Java Language Specification</cite>).
     *
     * The differences from the character escape sequences and Unicode
     * escapes used for characters and strings are:
     *
     * <ul>
     * <li> Octal escapes are not recognized.
     *
     * <li> The character sequence {@code \b} does <i>not</i>
     * represent a backspace character.
     *
     * <li> The method does not treat a backslash character,
     * {@code \}, before a non-valid escape character as an
     * error; the backslash is silently dropped.  For example, in a
     * Java string the sequence {@code "\z"} would cause a
     * compile time error.  In contrast, this method silently drops
     * the backslash.  Therefore, this method treats the two character
     * sequence {@code "\b"} as equivalent to the single
     * character {@code 'b'}.
     *
     * <li> Escapes are not necessary for single and double quotes;
     * however, by the rule above, single and double quote characters
     * preceded by a backslash still yield single and double quote
     * characters, respectively.
     *
     * <li> Only a single 'u' character is allowed in a Unicode escape
     * sequence.
     *
     * </ul>
     * <p>
     * The specified stream remains open after this method returns.
     *
     * @param   reader   the input character stream.
     * @throws  IOException  if an error occurred when reading from the
     *          input stream.
     * @throws  IllegalArgumentException if a malformed Unicode escape
     *          appears in the input.
     * @throws  NullPointerException if {@code reader} is {@code null}.
     */
    @Override
    public synchronized void load(Reader reader) throws IOException {
        loadIni(this, COMMENT_SIGNS, DELIMITERS, reader instanceof BufferedReader ? reader : new BufferedReader(reader));
    }

    /**
     * Loads all of the ini represented by the XML document on the
     * specified input stream into this {@link IniProperties}.
     *
     * <p>The XML document must have the following DOCTYPE declaration:
     * <pre>
     * &lt;!DOCTYPE ini SYSTEM "https://dtd.tianscar.com/ini.dtd"&gt;
     * </pre>
     * Furthermore, the document must satisfy the properties DTD described
     * above.
     *
     * <p> An implementation is required to read XML documents that use the
     * "{@code UTF-8}" or "{@code UTF-16}" encoding. An implementation may
     * support additional encodings.
     *
     * <p>The specified stream is closed after this method returns.
     *
     * @param in the input stream from which to read the XML document.
     * @throws IOException if reading from the specified input stream
     *         results in an {@code IOException}.
     * @throws java.io.UnsupportedEncodingException if the document's encoding
     *         declaration can be read and it specifies an encoding that is not
     *         supported
     * @throws InvalidPropertiesFormatException Data on input stream does not
     *         constitute a valid XML document with the mandated document type.
     * @throws NullPointerException if {@code in} is {@code null}.
     * @see    #storeToXML(OutputStream, String, String)
     * @see    <a href="http://www.w3.org/TR/REC-xml/#charencoding">Character
     *         Encoding in Entities</a>
     */
    @Override
    public synchronized void loadFromXML(InputStream in) throws IOException {
        Objects.requireNonNull(in);
        if (!(in instanceof BufferedInputStream)) in = new BufferedInputStream(in);
        IniPropertiesHandler handler = new IniPropertiesHandler();
        handler.load(this, in);
        in.close();
    }

    /**
     * Returns the sections for this {@link IniProperties}.
     * The map is backed by this {@code IniProperties}, so changes to the map are reflected in this {@code IniProperties}, and vice-versa.
     * @return the sections
     */
    public Map<String, Properties> sections() {
        return sections;
    }

    /**
     * Removes all empty sections for this {@link IniProperties}.
     */
    public void trim() {
        synchronized (sections) {
            for (Map.Entry<String, Properties> entry : sections.entrySet()) {
                if (entry.getValue().isEmpty()) sections.remove(entry.getKey());
            }
        }
    }

    /**
     * Returns the global properties for this {@link IniProperties}.
     * The {@link Properties} is backed by this {@code IniProperties}, so changes to the {@code Properties}
     * are reflected in this {@code IniProperties}, and vice-versa.
     * @return the global properties
     */
    public Properties globalProperties() {
        return globalProperties.get();
    }

    /**
     * Compares the specified Object with this {@link IniProperties} for equality, subclasses allowed,
     * this method checks sections and global properties.
     * @param o object to be compared for equality with this {@code IniProperties}
     * @return true if the specified Object is equal to this {@code IniProperties}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IniProperties)) return false;

        IniProperties that = (IniProperties) o;

        if (!sections.equals(that.sections)) return false;
        return globalProperties.get().equals(that.globalProperties.get());
    }

    /**
     * Returns the hash code value for sections and global properties.
     * @return the hash code value for sections and global properties
     */
    @Override
    public int hashCode() {
        int result = sections.hashCode();
        result = 31 * result + globalProperties.get().hashCode();
        return result;
    }

}
