/*
 *  Copyright (C) 2002, 2003 Gargoyle Software Inc. All rights reserved.
 *
 *  This file is part of HtmlUnit. For details on use and redistribution
 *  please refer to the license.html file included with these sources.
 */
package com.gargoylesoftware.htmlunit;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import java.io.StringReader;
import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLAttributes;
import org.apache.xerces.xni.XMLLocator;
import org.apache.xerces.xni.XMLString;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.cyberneko.html.HTMLConfiguration;
import org.cyberneko.html.filters.DefaultFilter;

/**
 * A filter that will execute javascript and pass the result of any document.write calls back into
 * the input stream.
 *
 * @version  $Revision$
 * @author  <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author Noboru Sinohara
 * @author David K. Taylor
 */
public final class ScriptFilter extends DefaultFilter {

    private final HTMLConfiguration configuration_;
    private final HtmlPage htmlPage_;

    private String scriptSource_;
    private String scriptCharset_;
    private StringBuffer scriptBuffer_;
    private StringBuffer newContentBuffer_;
    private String systemId_;
    private int scriptCount_;

    /**
     *  Create an instance
     *
     * @param  config The html configuration
     * @param htmlPage The page that is being loaded.
     */
    public ScriptFilter( final HTMLConfiguration config, final HtmlPage htmlPage ) {
        Assert.notNull("config", config);
        Assert.notNull("htmlPage", htmlPage);
        configuration_ = config;
        htmlPage_ = htmlPage;
        htmlPage_.setScriptFilter(this);
    }


    /**
     * Start document.
     * @param locator The locator
     * @param encoding The encoding.
     * @param augmentations The Augumentations
     * @throws XNIException if a problem occurs.
     */
    public void startDocument(
            final XMLLocator locator,
            final String encoding,
            final Augmentations augmentations )
        throws
            XNIException {

        scriptSource_ = null;
        scriptCharset_ = null;
        scriptBuffer_ = null;
        systemId_ = locator != null ? locator.getLiteralSystemId() : null;
        scriptCount_ = 0;
        super.startDocument( locator, encoding, augmentations );
    }


    /**
     * Start element.
     * @param element The element
     * @param attrs The xml attributes
     * @param augmentations The augmentations.
     * @throws XNIException If a problem occurs
     */
    public void startElement(
            final QName element,
            final XMLAttributes attrs,
            final Augmentations augmentations )
        throws
            XNIException {

        if( element.rawname.equalsIgnoreCase( "script" ) ) {
            final boolean isJavaScript = htmlPage_.isJavaScript( attrs.getValue("type"), attrs.getValue("language") );
            if( isJavaScript ) {
                final String src = attrs.getValue("src");
                final String charset = attrs.getValue("charset");
                if( src != null && src.length() != 0 ) {
                    scriptSource_ = src;
                    scriptCharset_ = charset;
                }

                scriptBuffer_ = new StringBuffer();
            }
        }

        super.startElement( element, attrs, augmentations );
    }


    /**
     * Empty element.
     * @param element The element
     * @param attrs The xml attributes.
     * @param augmentations The augmentations
     * @throws XNIException if a problem occurs.
     */
    public void emptyElement(
            final QName element,
            final XMLAttributes attrs,
            final Augmentations augmentations )
        throws
            XNIException {

        super.emptyElement( element, attrs, augmentations );
    }


    /**
     * Characters.
     * @param text The text
     * @param augmentations The augmentations.
     * @throws XNIException If a problem occurs
     */
    public void characters(
            final XMLString text,
            final Augmentations augmentations )
        throws
            XNIException {

        if( scriptBuffer_ == null ) {
            super.characters( text, augmentations );
        }
        else {
            super.characters( text, augmentations );
            scriptBuffer_.append( text.ch, text.offset, text.length );
        }
    }


    /**
     *  End element.
     * @param element The element
     * @param augmentations The augmentations
     * @throws XNIException if a problem occurs.
     */
    public void endElement(
            final QName element,
            final Augmentations augmentations )
        throws
            XNIException {

        super.endElement( element, augmentations );
        if( scriptBuffer_ != null ) {
            if( element.rawname.equalsIgnoreCase( "script" ) == false ) {
                throw new IllegalStateException("Other elements were contained within the script tag");
            }

            try {
                if( scriptSource_ != null ) {
                    final String result = loadScript(scriptSource_, scriptCharset_);
                    pushResult( result );
                }
                final String script = scriptBuffer_.toString();

                final String result = executeScript(script);
                pushResult( result );
            }
            finally {
                scriptSource_ = null;
                scriptCharset_ = null;
                scriptBuffer_ = null;
            }
        }
    }


    /**
     * Execute an external loaded script.  Any results from document.write
     * calls are returned.
     * @param scriptSource The URL for the external script to execute.
     * @param scriptCharset The character set to decode the external script.
     * @return The script output result.
     */
    private synchronized String loadScript(final String scriptSource, final String scriptCharset) {
        newContentBuffer_ = null;
        //System.err.println ("Load script " + scriptSource);
        htmlPage_.loadExternalJavaScriptFile(scriptSource, scriptCharset);
        if( newContentBuffer_ != null ) {
            final String result = newContentBuffer_.toString();
            newContentBuffer_ = null;
            return result;
        }
        return "";
    }


    /**
     * Execute an embedded script.  Any results from document.write calls
     * are returned.
     * @param script The embedded script to execute.
     * @return The script output result.
     */
    private synchronized String executeScript( final String script ) {
        newContentBuffer_ = null;
        htmlPage_.executeJavaScriptIfPossible(script, "Embedded script", false, null);
        if( newContentBuffer_ == null ) {
            return "";
        }
        else {
            final String result = newContentBuffer_.toString();
            newContentBuffer_ = null;
            return result;
        }
    }


    /**
     * Push the output result from a script execution as an XML input source.
     * The pushed result is from document.write calls in the script.
     * @param result The script output result.
     */
    private synchronized void pushResult( final String result ) {
        if( result.length() != 0 ) {
            final XMLInputSource xmlInputSource = new XMLInputSource(
                null, systemId_, null, new StringReader( result ), "UTF-8" );
            configuration_.pushInputSource( xmlInputSource );
        }
    }


    /**
     * Write the content back into the stream.
     * @param content The content.
     */
    public synchronized void write( final String content ) {
        if( newContentBuffer_ == null ) {
            newContentBuffer_ = new StringBuffer();
        }
        newContentBuffer_.append(content);
    }
}

