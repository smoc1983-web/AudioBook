package com.audiobookdownloader;

import java.io.StringReader;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

/** Read one Android preference; caller enforces MAX_BYTES before this streaming parser. */
final class CollectionXmlReader extends DefaultHandler {
    private static final int MAX_DEPTH=32;
    private final StringBuilder value=new StringBuilder();
    private int depth, found;
    private boolean collecting;

    static String read(String xml)throws Exception {
        if(xml.contains("<!DOCTYPE")||xml.contains("<!ENTITY"))
            throw new IllegalArgumentException("Неподдерживаемый XML");
        SAXParserFactory factory=SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XMLReader parser=factory.newSAXParser().getXMLReader();
        // Android and the JVM both support disabling these SAX entity features.
        parser.setFeature("http://xml.org/sax/features/external-general-entities",false);
        parser.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        CollectionXmlReader reader=new CollectionXmlReader();
        parser.setContentHandler(reader);
        parser.setErrorHandler(reader);
        parser.setEntityResolver((publicId,systemId)->{throw new SAXException("Внешние сущности XML запрещены");});
        parser.parse(new InputSource(new StringReader(xml)));
        if(reader.found!=1)throw new IllegalArgumentException("В файле нет коллекций Audiobook");
        return reader.value.toString();
    }
    @Override public void startElement(String uri,String local,String qualified,Attributes attributes)throws SAXException {
        if(++depth>MAX_DEPTH)throw new SAXException("Слишком сложный XML коллекций");
        if(collecting)throw new SAXException("Разметка внутри коллекций не поддерживается");
        if(depth==1)requireRoot(uri,local);
        if(collectionString(uri,local,attributes))beginCollection();
    }
    private void requireRoot(String uri,String local)throws SAXException {
        if(!uri.isEmpty()||!local.equals("map"))throw new SAXException("Это не файл настроек Audiobook");
    }
    private boolean collectionString(String uri,String local,Attributes attributes) {
        return depth==2&&uri.isEmpty()&&local.equals("string")&&"shelves_v1".equals(attributes.getValue("name"));
    }
    private void beginCollection()throws SAXException {
        if(++found>1)throw new SAXException("Повторяющийся список коллекций");
        collecting=true;
    }
    @Override public void endElement(String uri,String local,String qualified) {
        if(depth==2)collecting=false;
        depth--;
    }
    @Override public void characters(char[] chars,int start,int length) {
        if(!collecting)return;
        value.append(chars,start,length);
    }
    @Override public void error(SAXParseException error)throws SAXException {throw error;}
    @Override public void fatalError(SAXParseException error)throws SAXException {throw error;}
}
