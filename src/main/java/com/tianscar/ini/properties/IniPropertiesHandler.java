package com.tianscar.ini.properties;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

import static com.tianscar.ini.properties.Utils.*;

final class IniPropertiesHandler extends DefaultHandler {

    private static final String ELEMENT_INI = "ini";
    private static final String ELEMENT_SECTION = "section";
    private static final String ELEMENT_COMMENT = "comment";
    private static final String ELEMENT_ENTRY = "entry";
    private static final String ATTRIBUTE_KEY = "key";
    private static final String ATTRIBUTE_NAME = "name";
    private static final String ATTRIBUTE_VERSION = "version";

    private static final String DTD_URI = "https://dtd.tianscar.com/ini.dtd";
    private static final String DTD_DECL = "<!DOCTYPE ini SYSTEM \"" + DTD_URI + "\">";
    private static final String DTD = "<!-- DTD for INI -->\n" +
            "\n" +
            "<!ELEMENT ini ( comment?, ( section | entry )* ) >\n" +
            "\n" +
            "<!ATTLIST ini version CDATA #FIXED \"1.0\">\n" +
            "\n" +
            "<!ELEMENT comment (#PCDATA) >\n" +
            "\n" +
            "<!ELEMENT section ( section | entry )* >\n" +
            "\n" +
            "<!ATTLIST section name CDATA #REQUIRED>\n" +
            "\n" +
            "<!ELEMENT entry (#PCDATA) >\n" +
            "\n" +
            "<!ATTLIST entry key CDATA #REQUIRED>";

    private IniProperties ini;
    private StringBuilder stringBuilder;

    private boolean rootElementRead;
    private boolean commentRead;
    private boolean readingEntry;
    private String currentKey;

    public void load(IniProperties ini, InputStream inStream) throws IOException {
        this.ini = Objects.requireNonNull(ini, "properties cannot be null");
        stringBuilder = new StringBuilder();

        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setValidating(true);
        try {
            SAXParser parser = factory.newSAXParser();
            parser.parse(inStream, this);
        } catch (SAXException | ParserConfigurationException e) {
            throw new InvalidPropertiesFormatException(e);
        }
    }

    private static void writeEntries(XMLStreamWriter writer, Properties properties) throws XMLStreamException {
        if (properties.size() > 0) {
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
                String key = (String) entry.getKey();
                String value = (String) entry.getValue();
                writer.writeStartElement(ELEMENT_ENTRY);
                writer.writeAttribute(ATTRIBUTE_KEY, key);
                writer.writeCharacters(value);
                writer.writeEndElement();
            }
        }
    }

    private static class IniTreeNode {
        public String name;
        public Properties properties;
        public final List<IniTreeNode> children;
        public IniTreeNode() {
            this(null, null);
        }
        public IniTreeNode(String name, Properties properties) {
            this.name = name;
            this.properties = properties;
            children = new ArrayList<>();
        }
        public boolean hasChild(String name) {
            for (IniTreeNode child : children) {
                if (Objects.equals(child.name, name)) return true;
            }
            return false;
        }
        public IniTreeNode getChild(String name) {
            for (IniTreeNode child : children) {
                if (Objects.equals(child.name, name)) return child;
            }
            return null;
        }
        public void write(XMLStreamWriter writer) throws XMLStreamException {
            for (IniTreeNode child : children) {
                writer.writeStartElement(ELEMENT_SECTION);
                writer.writeAttribute(ATTRIBUTE_NAME, child.name);
                writeEntries(writer, child.properties);
                child.write(writer);
                writer.writeEndElement();
            }
        }
    }

    public void store(IniProperties ini, OutputStream outStream, String comment, Charset charset) throws IOException {
        try {
            XMLOutputFactory factory = XMLOutputFactory.newInstance();
            IndentingXMLStreamWriter writer = new IndentingXMLStreamWriter(factory.createXMLStreamWriter(outStream, charset.name()));
            writer.setIndent("    ");
            writer.writeStartDocument(charset.name(), "1.0");
            writer.writeDTD(DTD_DECL);
            writer.writeStartElement(ELEMENT_INI);
            writer.writeAttribute(ATTRIBUTE_VERSION, "1.0");
            if (comment != null && comment.length() > 0) {
                writer.writeStartElement(ELEMENT_COMMENT);
                writer.writeCharacters(comment);
                writer.writeEndElement();
            }
            synchronized(ini) {
                writeEntries(writer, ini.globalProperties());
                IniTreeNode tree = new IniTreeNode();
                IniTreeNode last;
                List<Map.Entry<String, Properties>> tmp = new LinkedList<>();
                String sectionName;
                Properties sectionProperties;
                for (Map.Entry<String, Properties> sections : ini.sections().entrySet()) {
                    last = tree;
                    sectionName = sections.getKey();
                    do {
                        tmp.add(new AbstractMap.SimpleEntry<>(plainSectionName(sectionName), ini.getSection(sectionName)));
                        sectionName = parentSectionName(sectionName);
                    }
                    while (sectionName != null);
                    Collections.reverse(tmp);
                    for (Map.Entry<String, Properties> tmpEntry : tmp) {
                        sectionName = tmpEntry.getKey();
                        sectionProperties = tmpEntry.getValue();
                        if (!last.hasChild(sectionName)) {
                            if (sectionProperties.isEmpty()) break;
                            last.children.add(new IniTreeNode(sectionName, sectionProperties));
                        }
                        last = last.getChild(sectionName);
                    }
                    tmp.clear();
                }
                tree.write(writer);
            }
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.flush();
            writer.close();
        }
        catch (XMLStreamException e) {
            if (e.getCause() instanceof UnsupportedEncodingException) throw (UnsupportedEncodingException) e.getCause();
            else throw new IOException(e);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (readingEntry) stringBuilder.append(ch, start, length);
    }

    @Override
    public void startDocument() throws SAXException {
        rootElementRead = false;
        commentRead = false;
        readingEntry = false;
        currentKey = null;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        switch (qName) {
            case ELEMENT_COMMENT:
                if (commentRead) {
                    fatalError(new SAXParseException("Only one comment element may be allowed. "
                            + "The content of element type \"ini\" must match \"(comment?,section*)\"", null));
                    break;
                }
                else commentRead = true;
                break;
            case ELEMENT_ENTRY:
                readingEntry = true;
                currentKey = attributes.getValue(ATTRIBUTE_KEY);
                if (currentKey == null) {
                    fatalError(new SAXParseException("Attribute \"key\" is required and must be specified for element type \"entry\"", null));
                }
                break;
            case ELEMENT_SECTION:
                String sectionName = attributes.getValue(ATTRIBUTE_NAME);
                if (sectionName == null) {
                    fatalError(new SAXParseException("Attribute \"name\" is required and must be specified for element type \"section\"", null));
                }
                ini.switchSection((ini.currentSectionName() == null ? "" : ".") + sectionName);
                break;
            case ELEMENT_INI:
                if (!rootElementRead) {
                    rootElementRead = true;
                    break;
                }
            default:
                readingEntry = false;
                fatalError(new SAXParseException("Invalid element type: " + qName, null));
                break;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        switch (qName) {
            case ELEMENT_ENTRY:
                if (readingEntry) {
                    readingEntry = false;
                    ini.setProperty(currentKey, stringBuilder.toString());
                    currentKey = null;
                    stringBuilder.delete(0, stringBuilder.length());
                }
                break;
            case ELEMENT_SECTION:
                ini.switchSection(ini.parentSectionName());
                break;
            case ELEMENT_COMMENT:
                if (commentRead) break;
            case ELEMENT_INI:
                if (rootElementRead) break;
            default:
                readingEntry = false;
                fatalError(new SAXParseException("Invalid element type: " + qName, null));
                break;
        }
    }

    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws IOException, SAXException {
        if (DTD_URI.equals(systemId)) return new InputSource(new ByteArrayInputStream(DTD.getBytes("UTF-8")));
        else throw new SAXException("Invalid system identifier: " + systemId);
    }

}
