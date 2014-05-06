/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.axiom.om.impl.dom;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMCloneOptions;
import org.apache.axiom.om.OMConstants;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMException;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.OMOutputFormat;
import org.apache.axiom.om.OMXMLParserWrapper;
import org.apache.axiom.om.impl.common.IElement;
import org.apache.axiom.om.impl.common.IParentNode;
import org.apache.axiom.om.impl.common.NamespaceIterator;
import org.apache.axiom.om.impl.common.OMChildElementIterator;
import org.apache.axiom.om.impl.common.OMContainerHelper;
import org.apache.axiom.om.impl.common.OMElementHelper;
import org.apache.axiom.om.impl.common.OMNamedInformationItemHelper;
import org.apache.axiom.om.impl.common.OMNamespaceImpl;
import org.apache.axiom.om.impl.common.OMNodeHelper;
import org.apache.axiom.om.impl.common.serializer.push.OutputException;
import org.apache.axiom.om.impl.common.serializer.push.Serializer;
import org.apache.axiom.om.impl.traverse.OMQNameFilterIterator;
import org.apache.axiom.om.impl.traverse.OMQualifiedNameFilterIterator;
import org.apache.axiom.om.impl.util.EmptyIterator;
import org.apache.axiom.om.impl.util.OMSerializerUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.TypeInfo;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;

/** Implementation of the org.w3c.dom.Element and org.apache.axiom.om.Element interfaces. */
public class ElementImpl extends ParentNode implements Element, IElement, NamedNode,
        OMConstants {

    private static final Log log = LogFactory.getLog(ElementImpl.class);
    
    protected OMXMLParserWrapper builder;

    protected int state;

    private ParentNode ownerNode;
    
    private NodeImpl previousSibling;

    private NodeImpl nextSibling;

    private int lineNumber;

    /**
     * The namespace of this element. Possible values:
     * <ul>
     * <li><code>null</code> (if the element has no namespace)
     * <li>any {@link OMNamespace} instance, with the following exceptions:
     * <ul>
     * <li>an {@link OMNamespace} instance with a <code>null</code> prefix
     * <li>an {@link OMNamespace} instance with both prefix and namespace URI set to the empty
     * string
     * </ul>
     * </ul>
     */
    protected OMNamespace namespace;

    protected String localName;

    private AttributeMap attributes;

    private static final EmptyIterator EMPTY_ITERATOR = new EmptyIterator();

    public ElementImpl(ParentNode parentNode, String localName, OMNamespace ns, OMXMLParserWrapper builder,
                       OMFactory factory, boolean generateNSDecl) {
        super(factory);
        this.localName = localName;
        this.builder = builder;
        state = builder == null ? COMPLETE : INCOMPLETE;
        if (parentNode != null) {
            parentNode.addChild(this, builder != null);
        }
        this.attributes = new AttributeMap(this);
        namespace = generateNSDecl ? OMNamedInformationItemHelper.handleNamespace(this, ns, false, true) : ns;
    }

    final ParentNode internalGetOwnerNode() {
        return ownerNode;
    }

    final void internalSetOwnerNode(ParentNode ownerNode) {
        this.ownerNode = ownerNode;
    }

    final NodeImpl internalGetPreviousSibling() {
        return previousSibling;
    }
    
    final NodeImpl internalGetNextSibling() {
        return nextSibling;
    }
    
    final void internalSetPreviousSibling(NodeImpl previousSibling) {
        this.previousSibling = previousSibling;
    }
    
    final void internalSetNextSibling(NodeImpl nextSibling) {
        this.nextSibling = nextSibling;
    }

    OMNamespace handleNamespace(String namespaceURI, String prefix) {
        if (prefix.length() == 0 && namespaceURI.length() == 0) {
            OMNamespace namespace = getDefaultNamespace();
            if (namespace != null) {
                declareDefaultNamespace("");
            }
            return null;
        } else {
            OMNamespace namespace = findNamespace(namespaceURI,
                                                  prefix);
            if (namespace == null) {
                namespace = declareNamespace(namespaceURI, prefix.length() > 0 ? prefix : null);
            }
            return namespace;
        }
    }

    // /
    // /org.w3c.dom.Node methods
    // /

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Node#getNodeType()
     */
    public short getNodeType() {
        return Node.ELEMENT_NODE;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Node#getNodeName()
     */ 
    public String getNodeName() {
        if (this.namespace != null) {
            if (this.namespace.getPrefix() == null
                    || "".equals(this.namespace.getPrefix())) {
                return this.localName;
            } else {
                return this.namespace.getPrefix() + ":" + this.localName;
            }
        } else {
            return this.localName;
        }
    }

    /** Returns the value of the namespace URI. */
    public String getNamespaceURI() {
        if (this.namespace == null) {
            return null;
        } else {
            // If the element has no namespace, the result should be null, not
            // an empty string.
            String uri = this.namespace.getNamespaceURI();
            return uri.length() == 0 ? null : uri.intern();
        }
    }

    // /
    // /org.apache.axiom.om.OMNode methods
    // /

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.axiom.om.OMNode#getType()
     */
    public int getType() throws OMException {
        return OMNode.ELEMENT_NODE;
    }

    // /
    // / org.w3c.dom.Element methods
    // /

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Element#getTagName()
     */
    public String getTagName() {
        return this.getNodeName();
    }

    /**
     * Removes an attribute by name.
     *
     * @param name The name of the attribute to remove
     * @see org.w3c.dom.Element#removeAttribute(String)
     */
    public void removeAttribute(String name) throws DOMException {
        if (this.attributes != null) {
            this.attributes.removeNamedItem(name);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Element#removeAttributeNS(java.lang.String,
     *      java.lang.String)
     */
    public void removeAttributeNS(String namespaceURI, String localName)
            throws DOMException {
        if (this.attributes != null) {
            this.attributes.removeNamedItemNS(namespaceURI, localName);
        }
    }

    /**
     * Removes the specified attribute node.
     *
     * @see org.w3c.dom.Element#removeAttributeNode(org.w3c.dom.Attr)
     */
    public Attr removeAttributeNode(Attr oldAttr) throws DOMException {
        if (oldAttr.getOwnerElement() != this) {
            throw DOMUtil.newDOMException(DOMException.NOT_FOUND_ERR);
        }
        attributes.remove((AttrImpl)oldAttr, true);
        return oldAttr;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Element#hasAttribute(java.lang.String)
     */
    public boolean hasAttribute(String name) {
        return this.getAttributeNode(name) != null;
    }

    /**
     * Returns whether the given attribute is available or not.
     *
     * @see org.w3c.dom.Element#hasAttributeNS(String, String)
     */
    public boolean hasAttributeNS(String namespaceURI, String localName) {
        return this.getAttributeNodeNS(namespaceURI, localName) != null;
    }

    /**
     * Looks in the local list of attributes and returns if found. If the local list is null,
     * returns "".
     *
     * @see org.w3c.dom.Element#getAttribute(String)
     */
    public String getAttribute(String name) {
        if (attributes == null) {
            return "";
        } else {
            Attr attr = ((Attr) attributes.getNamedItem(name));
            return (attr != null) ? attr.getValue() : "";
        }
    }

    /**
     * Retrieves an attribute node by name.
     *
     * @see org.w3c.dom.Element#getAttributeNode(String)
     */
    public Attr getAttributeNode(String name) {
        return (this.attributes == null) ? null : (AttrImpl) this.attributes
                .getNamedItem(name);
    }

    /**
     * Retrieves an attribute value by local name and namespace URI.
     *
     * @see org.w3c.dom.Element#getAttributeNS(String, String)
     */
    public String getAttributeNS(String namespaceURI, String localName) {
        if (this.attributes == null) {
            return "";
        }
        Attr attributeNodeNS = this.getAttributeNodeNS(namespaceURI, localName);
        return attributeNodeNS == null ? "" : attributeNodeNS.getValue();
    }

    /**
     * Retrieves an attribute node by local name and namespace URI.
     *
     * @see org.w3c.dom.Element#getAttributeNodeNS(String, String)
     */
    public Attr getAttributeNodeNS(String namespaceURI, String localName) {
        return (this.attributes == null) ? null : (Attr) this.attributes
                .getNamedItemNS(namespaceURI, localName);
    }

    /**
     * Adds a new attribute node.
     *
     * @see org.w3c.dom.Element#setAttributeNode(org.w3c.dom.Attr)
     */
    public Attr setAttributeNode(Attr attr) throws DOMException {
        AttrImpl attrImpl = (AttrImpl) attr;

        checkSameOwnerDocument(attr);

        // check whether the attr is in use
        attrImpl.checkInUse();

        if (attr.getNodeName().startsWith(XMLConstants.XMLNS_ATTRIBUTE + ":")) {
            // This is a ns declaration
            this.declareNamespace(attr.getNodeValue(), DOMUtil
                    .getLocalName(attr.getName()));

            //Don't add this to attr list, since its a namespace
            return attr;
        } else if (attr.getNodeName().equals(XMLConstants.XMLNS_ATTRIBUTE)) {
            this.declareDefaultNamespace(attr.getValue());

            //Don't add this to attr list, since its a namespace
            return attr;
        }
        if (this.attributes == null) {
            this.attributes = new AttributeMap(this);
        }

        return (Attr) this.attributes.setNamedItem(attr);

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Element#setAttribute(java.lang.String, java.lang.String)
     */
    public void setAttribute(String name, String value) throws DOMException {
        // Check for invalid charaters
        if (!DOMUtil.isQualifiedName(name)) {
            throw DOMUtil.newDOMException(DOMException.INVALID_CHARACTER_ERR);
        }
        if (name.startsWith(XMLConstants.XMLNS_ATTRIBUTE + ":")) {
            // This is a ns declaration
            this.declareNamespace(value, DOMUtil.getLocalName(name));
        } else if (name.equals(XMLConstants.XMLNS_ATTRIBUTE)) {
            this.declareDefaultNamespace(value);
        } else {
            this.setAttributeNode(new AttrImpl(ownerDocument(), name, value,
                                               this.factory));
        }

    }

    public Attr setAttributeNodeNS(Attr attr) throws DOMException {
        return setAttributeNodeNS(attr, true, false);
    }
    
    private Attr setAttributeNodeNS(Attr attr, boolean useDomSemantics, boolean generateNSDecl) throws DOMException {
        AttrImpl attrImpl = (AttrImpl) attr;

        if (useDomSemantics) {
            checkSameOwnerDocument(attr);
        }

        // check whether the attr is in use
        attrImpl.checkInUse();

        if (this.attributes == null) {
            this.attributes = new AttributeMap(this);
        }

        // handle the namespaces
        if (generateNSDecl && attr.getNamespaceURI() != null
                && findNamespace(attr.getNamespaceURI(), attr.getPrefix())
                == null) {
            // TODO checkwhether the same ns is declared with a different
            // prefix and remove it
            this.declareNamespace(new OMNamespaceImpl(attr.getNamespaceURI(),
                                                    attr.getPrefix()));
        }

        return (Attr) this.attributes.setAttribute(attr, useDomSemantics);
    }

    /**
     * Adds a new attribute.
     *
     * @see org.w3c.dom.Element#setAttributeNS(String, String, String)
     */
    public void setAttributeNS(String namespaceURI, String qualifiedName,
                               String value) throws DOMException {
        
        if (namespaceURI != null && namespaceURI.length() == 0) {
            namespaceURI = null;
        }
        String localName = DOMUtil.getLocalName(qualifiedName);
        String prefix = DOMUtil.getPrefix(qualifiedName);
        DOMUtil.validateAttrNamespace(namespaceURI, localName, prefix);
        
        AttrImpl attr = (AttrImpl)getAttributeNodeNS(namespaceURI, localName);
        if (attr != null) {
            attr.setPrefix(prefix);
            attr.setValue(value);
        } else {
            if (namespaceURI != null) {
                attr = new AttrImpl(ownerDocument(), localName, value, this.factory);
                attr.internalSetNamespace(new OMNamespaceImpl(namespaceURI, prefix == null ? "" : prefix));
    
                this.setAttributeNodeNS(attr);
            } else {
                // When the namespace is null, the attr name given better not be
                // a qualified name
                // But anyway check and set it
                this.setAttribute(localName, value);
            }
        }

    }

    /** Returns whether this element contains any attribute or not. */
    public boolean hasAttributes() {
        return attributes != null && attributes.getLength() > 0;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Element#getElementsByTagNameNS(java.lang.String,
     *      java.lang.String)
     */
    public NodeList getElementsByTagNameNS(String namespaceURI,
                                           String localName) {
        final QName qname = new QName(namespaceURI, localName);
        return new NodeListImpl() {
            protected Iterator getIterator() {
                return new OMQNameFilterIterator(getDescendants(false), qname);
            }
        };
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.w3c.dom.Element#getElementsByTagName(java.lang.String)
     */
    public NodeList getElementsByTagName(final String name) {
        if (name.equals("*")) {
            return new NodeListImpl() {
                protected Iterator getIterator() {
                    return getDescendants(false);
                }
            };
        } else {
            return new NodeListImpl() {
                protected Iterator getIterator() {
                    return new OMQualifiedNameFilterIterator(
                            getDescendants(false), name);
                }
            };
        }
    }

    // /
    // /OmElement methods
    // /

    /** @see org.apache.axiom.om.OMElement#addAttribute (org.apache.axiom.om.OMAttribute) */
    public OMAttribute addAttribute(OMAttribute attr) {
        // If the attribute already has an owner element then clone the attribute (except if it is owned
        // by the this element)
        OMElement owner = attr.getOwner();
        if (owner != null) {
            if (owner == this) {
                return attr;
            }
            attr = new AttrImpl(null, attr.getLocalName(), attr.getNamespace(),
                    attr.getAttributeValue(), attr.getOMFactory());
        }
        
        OMNamespace namespace = attr.getNamespace();
        if (namespace != null) {
            String uri = namespace.getNamespaceURI();
            if (uri.length() > 0) {
                String prefix = namespace.getPrefix();
                OMNamespace ns2 = findNamespaceURI(prefix);
                if (ns2 == null || !uri.equals(ns2.getNamespaceURI())) {
                    declareNamespace(uri, prefix);
                }
            }
        }

        this.setAttributeNodeNS((Attr) attr, false, true);
        return attr;
    }

    public OMAttribute addAttribute(String localName, String value,
                                    OMNamespace ns) {
        OMNamespace namespace = null;
        if (ns != null) {
            String namespaceURI = ns.getNamespaceURI();
            String prefix = ns.getPrefix();
            namespace = findNamespace(namespaceURI, prefix);
            if (namespace == null) {
                namespace = new OMNamespaceImpl(namespaceURI, prefix != null ? prefix : OMSerializerUtil.getNextNSPrefix());
            }
        }
        return addAttribute(new AttrImpl(null, localName, namespace, value, factory));
    }

    public OMNamespace addNamespaceDeclaration(String uri, String prefix) {
        OMNamespace ns = new OMNamespaceImpl(uri, prefix);
        addNamespaceDeclaration(ns);
        return ns;
    }
    
    public void addNamespaceDeclaration(OMNamespace ns) {
        String prefix = ns.getPrefix();
        setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, prefix.length() == 0 ? XMLConstants.XMLNS_ATTRIBUTE : XMLConstants.XMLNS_ATTRIBUTE + ":" + prefix, ns.getNamespaceURI());
    }

    /**
     * Allows overriding an existing declaration if the same prefix was used.
     *
     * @see org.apache.axiom.om.OMElement#declareNamespace (org.apache.axiom.om.OMNamespace)
     */
    public OMNamespace declareNamespace(OMNamespace namespace) {
        if (namespace != null) {
            String prefix = namespace.getPrefix();
            if (prefix == null) {
                prefix = OMSerializerUtil.getNextNSPrefix();
                namespace = new OMNamespaceImpl(namespace.getNamespaceURI(), prefix);
            }
            if (prefix.length() > 0 && namespace.getNamespaceURI().length() == 0) {
                throw new IllegalArgumentException("Cannot bind a prefix to the empty namespace name");
            }

            if (!namespace.getPrefix().startsWith(XMLConstants.XMLNS_ATTRIBUTE)) {
                setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, prefix.length() == 0 ? XMLConstants.XMLNS_ATTRIBUTE : XMLConstants.XMLNS_ATTRIBUTE + ":" + prefix, namespace.getNamespaceURI());
            }
        }
        return namespace;
    }

    public void undeclarePrefix(String prefix) {
        setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, prefix.length() == 0 ? XMLConstants.XMLNS_ATTRIBUTE : XMLConstants.XMLNS_ATTRIBUTE + ":" + prefix, "");
    }

    public OMNamespace declareNamespace(String uri, String prefix) {
        if ("".equals(prefix)) {
            log.warn("Deprecated usage of OMElement#declareNamespace(String,String) with empty prefix");
            prefix = OMSerializerUtil.getNextNSPrefix();
        }
        
        OMNamespaceImpl ns = new OMNamespaceImpl(uri, prefix);
        return declareNamespace(ns);
    }

    public OMNamespace declareDefaultNamespace(String uri) {
        if (namespace == null && uri.length() > 0
                || namespace != null && namespace.getPrefix().length() == 0 && !namespace.getNamespaceURI().equals(uri)) {
            throw new OMException("Attempt to add a namespace declaration that conflicts with " +
                    "the namespace information of the element");
        }

        setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, XMLConstants.XMLNS_ATTRIBUTE, uri);
        return new OMNamespaceImpl(uri, "");
    }

    public OMNamespace getDefaultNamespace() {
        Attr decl = (Attr)attributes.getNamedItemNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, XMLConstants.XMLNS_ATTRIBUTE);
        if (decl != null) {
            String uri = decl.getValue();
            return uri.length() == 0 ? null : new OMNamespaceImpl(uri, "");
        }

        ParentNode parentNode = parentNode();
        if (parentNode instanceof ElementImpl) {
            ElementImpl element = (ElementImpl) parentNode;
            return element.getDefaultNamespace();
        }
        return null;
    }

    /** @see org.apache.axiom.om.OMElement#findNamespace(String, String) */
    public OMNamespace findNamespace(String uri, String prefix) {

        // check in the current element
        OMNamespace namespace = findDeclaredNamespace(uri, prefix);
        if (namespace != null) {
            return namespace;
        }

        // go up to check with ancestors
        ParentNode parentNode = parentNode();
        if (parentNode != null) {
            // For the OMDocumentImpl there won't be any explicit namespace
            // declarations, so going up the parent chain till the document
            // element should be enough.
            if (parentNode instanceof OMElement) {
                namespace = ((ElementImpl) parentNode).findNamespace(uri,
                                                                     prefix);
                // If the prefix has been redeclared, then ignore the binding found on the ancestors
                if (prefix == null && namespace != null && findDeclaredNamespace(null, namespace.getPrefix()) != null) {
                    namespace = null;
                }
            }
        }

        if (namespace == null && uri != null && prefix != null
                && prefix.equals(OMConstants.XMLNS_PREFIX)
                && uri.equals(OMConstants.XMLNS_URI)) {
            declareNamespace(OMConstants.XMLNS_URI, OMConstants.XMLNS_PREFIX);
            namespace = findNamespace(uri, prefix);
        }
        return namespace;
    }

    public OMNamespace findNamespaceURI(String prefix) {
        if (attributes != null) {
            Attr decl = (Attr)attributes.getNamedItemNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, prefix.length() == 0 ? XMLConstants.XMLNS_ATTRIBUTE : prefix);
            if (decl != null) {
                String namespaceURI = decl.getValue();
                if (prefix != null && prefix.length() > 0 && namespaceURI.length() == 0) {
                    // Prefix undeclaring case (XML 1.1 only)
                    return null;
                } else {
                    return new OMNamespaceImpl(namespaceURI, prefix);
                }
            }
        }
        ParentNode parentNode = parentNode();
        if (parentNode instanceof OMElement) {
            // try with the parent
            return ((OMElement)parentNode).findNamespaceURI(prefix);
        } else {
            return null;
        }
    }

    /**
     * Checks for the namespace <B>only</B> in the current Element. This can also be used to
     * retrieve the prefix of a known namespace URI.
     */
    private OMNamespace findDeclaredNamespace(String uri, String prefix) {

        if (uri == null) {
            Attr decl = (Attr)attributes.getNamedItemNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
                    prefix.length() == 0 ? XMLConstants.XMLNS_ATTRIBUTE : prefix);
            return decl == null ? null : new OMNamespaceImpl(decl.getValue(), prefix);
        }
        // If the prefix is available and uri is available and its the xml
        // namespace
        if (prefix != null && prefix.equals(OMConstants.XMLNS_PREFIX)
                && uri.equals(OMConstants.XMLNS_URI)) {
            return new OMNamespaceImpl(uri, prefix);
        }

        if (prefix == null || "".equals(prefix)) {
            for (int i=0; i<attributes.getLength(); i++) {
                Attr attr = (Attr)attributes.item(i);
                if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attr.getNamespaceURI())) {
                    String declaredUri = attr.getValue();
                    if (declaredUri.equals(uri)) {
                        return new OMNamespaceImpl(uri, attr.getPrefix() == null ? "" : attr.getLocalName());
                    }
                }
            }
        } else {
            Attr decl = (Attr)attributes.getNamedItemNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, prefix);
            if (decl != null) {
                String declaredUri = decl.getValue();
                if (declaredUri.equals(uri)) {
                    return new OMNamespaceImpl(uri, prefix);
                }
            }
        }

        return null;
    }

    /**
     * Returns a named attribute if present.
     *
     * @see org.apache.axiom.om.OMElement#getAttribute (javax.xml.namespace.QName)
     */
    public OMAttribute getAttribute(QName qname) {
        if (this.attributes == null) {
            return null;
        }

        if (qname.getNamespaceURI().equals("")) {
            return (AttrImpl) this.getAttributeNode(qname.getLocalPart());
        } else {
            return (AttrImpl) this.getAttributeNodeNS(qname.getNamespaceURI(),
                                                      qname.getLocalPart());
        }
    }

    /**
     * Returns a named attribute's value, if present.
     *
     * @param qname the qualified name to search for
     * @return Returns a String containing the attribute value, or null.
     */
    public String getAttributeValue(QName qname) {
        OMAttribute attr = getAttribute(qname);
        return (attr == null) ? null : attr.getAttributeValue();
    }

    /**
     * Returns the first Element node.
     *
     * @see org.apache.axiom.om.OMElement#getFirstElement()
     */
    public OMElement getFirstElement() {
        OMNode node = getFirstOMChild();
        while (node != null) {
            if (node.getType() == Node.ELEMENT_NODE) {
                return (OMElement) node;
            } else {
                node = node.getNextOMSibling();
            }
        }
        return null;
    }

    /**
     * Returns the namespace of this element.
     *
     * @see org.apache.axiom.om.OMElement#getNamespace()
     */
    public OMNamespace getNamespace() {
        return namespace;
    }

    /**
     * Returns the QName of this element.
     *
     * @see org.apache.axiom.om.OMElement#getQName()
     */
    public QName getQName() {
        QName qName;
        if (namespace != null) {
            qName = new QName(namespace.getNamespaceURI(), this.localName,
                              namespace.getPrefix());
        } else {
            qName = new QName(this.localName);
        }
        return qName;
    }

    public boolean hasName(QName name) {
        return name.getLocalPart().equals(localName)
                && (namespace == null && name.getNamespaceURI().length() == 0
                 || namespace != null && name.getNamespaceURI().equals(namespace.getNamespaceURI()));
    }

    public String getText() {
        return OMElementHelper.getText(this);
    }

    public Reader getTextAsStream(boolean cache) {
        return OMElementHelper.getTextAsStream(this, cache);
    }

    public QName getTextAsQName() {
        String childText = getText().trim();
        return childText.length() == 0 ? null : resolveQName(childText);
    }

    public void writeTextTo(Writer out, boolean cache) throws IOException {
        OMElementHelper.writeTextTo(this, out, cache);
    }

    public void removeAttribute(OMAttribute attr) {
        if (attr.getOwner() != this) {
            throw new OMException("The attribute is not owned by this element");
        }
        attributes.remove((AttrImpl)attr, false);
    }

    /**
     * Sets the local name.
     *
     * @see org.apache.axiom.om.OMElement#setLocalName(String)
     */
    public void setLocalName(String localName) {
        this.localName = localName;
    }

    public void internalSetNamespace(OMNamespace namespace) {
        this.namespace = namespace;
    }

    public void setNamespace(OMNamespace namespace, boolean declare) {
        this.namespace = OMNamedInformationItemHelper.handleNamespace(this, namespace, false, declare);
    }

    public void setNamespace(OMNamespace namespace) {
        setNamespace(namespace, true);
    }

    public void setNamespaceWithNoFindInCurrentScope(OMNamespace namespace) {
        internalSetNamespace(namespace);
    }

    /**
     * Creates a text node with the given value and adds it to the element.
     *
     * @see org.apache.axiom.om.OMElement#setText(String)
     */
    public void setText(String text) {
        // Remove all existing children
        OMNode child;
        while ((child = getFirstOMChild()) != null) {
            child.detach();
        }
        // Add a new text node
        if (text != null && text.length() > 0) {
            getOMFactory().createOMText(this, text);
        }
    }

    public void setText(QName qname) {
        // Remove all existing children
        OMNode child;
        while ((child = getFirstOMChild()) != null) {
            child.detach();
        }
        // Add a new text node
        if (qname != null) {
            getOMFactory().createOMText(this, qname);
        }
    }

    public void internalSerialize(Serializer serializer,
                                     OMOutputFormat format, boolean cache) throws OutputException {

        serializer.serializeStartpart(this);
        serializer.serializeChildren(this, format, cache);
        serializer.writeEndElement();
    }

    public String toStringWithConsume() throws XMLStreamException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        this.serializeAndConsume(baos);
        return new String(baos.toByteArray());
    }

    /**
     * Overridden toString() for ease of debugging.
     *
     * @see Object#toString()
     */
    public String toString() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
//            this.build();
            this.serialize(baos);
        } catch (XMLStreamException e) {
            throw new RuntimeException("Can not serialize OM Element " + this.getLocalName(), e);
        }
        return new String(baos.toByteArray());
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.axiom.om.OMElement#getChildElements()
     */
    public Iterator getChildElements() {
        return new OMChildElementIterator(getFirstElement());
    }

    /** @see org.apache.axiom.om.OMElement#getAllDeclaredNamespaces() */
    public Iterator getAllDeclaredNamespaces() throws OMException {
        return new NSDeclIterator(attributes);
    }

    public Iterator getNamespacesInScope() {
        return new NamespaceIterator(this);
    }

    public NamespaceContext getNamespaceContext(boolean detached) {
        return OMElementHelper.getNamespaceContext(this, detached);
    }

    /** @see org.apache.axiom.om.OMElement#getAllAttributes() */
    public Iterator getAllAttributes() {
        if (attributes == null) {
            return EMPTY_ITERATOR;
        }
        ArrayList list = new ArrayList();
        for (int i = 0; i < attributes.getLength(); i++) {
            OMAttribute item = (OMAttribute) attributes.getItem(i);
            if (item.getNamespace() == null
                    || !(item.getNamespace() != null && XMLConstants.XMLNS_ATTRIBUTE_NS_URI
                    .equals(item.getNamespace().getNamespaceURI()))) {
                list.add(item);
            }
        }

        return list.iterator();
    }

    /**
     * Returns the local name of this element node
     *
     * @see org.w3c.dom.Node#getLocalName()
     */
    public String getLocalName() {
        return this.localName;
    }

    /**
     * Returns the namespace prefix of this element node
     *
     * @see org.w3c.dom.Node#getPrefix()
     */
    public String getPrefix() {
        return NamedNodeHelper.getPrefix(this);
    }

    public void setPrefix(String prefix) throws DOMException {
        NamedNodeHelper.setPrefix(this, prefix);
    }

    public QName resolveQName(String qname) {
        int idx = qname.indexOf(':');
        if (idx == -1) {
            OMNamespace ns = getDefaultNamespace();
            return ns == null ? new QName(qname) : new QName(ns.getNamespaceURI(), qname, "");
        } else {
            String prefix = qname.substring(0, idx);
            OMNamespace ns = findNamespace(null, prefix);
            return ns == null ? null : new QName(ns.getNamespaceURI(), qname.substring(idx+1), prefix);
        }
    }

    public OMElement cloneOMElement() {
        return (OMElement)clone(new OMCloneOptions());
    }

    final ParentNode shallowClone(OMCloneOptions options, ParentNode targetParent, boolean namespaceRepairing) {
        ElementImpl clone;
        if (options.isPreserveModel()) {
            clone = (ElementImpl)createClone(options, targetParent, namespaceRepairing);
        } else {
            clone = new ElementImpl(targetParent, localName, namespace, null, factory, namespaceRepairing);
        }
        for (int i=0, l=attributes.getLength(); i<l; i++) {
            AttrImpl attr = (AttrImpl)attributes.item(i);
            AttrImpl clonedAttr = (AttrImpl)attr.clone(options, null, true, false);
            clonedAttr.isSpecified(attr.isSpecified());
            clone.setAttributeNodeNS(clonedAttr, false, namespaceRepairing && !XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attr.getNamespaceURI()));
        }
        return clone;
    }

    protected OMElement createClone(OMCloneOptions options, ParentNode targetParent, boolean generateNSDecl) {
        return new ElementImpl(targetParent, localName, namespace, null, factory, generateNSDecl);
    }
    
    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    /** Returns the set of attributes of this node and the namespace declarations available. */
    public NamedNodeMap getAttributes() {
        return attributes;
    }

    /**
     * Returns the namespace uri, given the prefix. If it is not found at this element, searches the
     * parent.
     *
     * @param prefix
     * @return Returns namespace.
     */
    public String getNamespaceURI(String prefix) {
        OMNamespace ns = this.findNamespaceURI(prefix);
        return (ns != null) ? ns.getNamespaceURI() : null;
    }

    public void discard() throws OMException {
        OMElementHelper.discard(this);
    }

    /*
     * DOM-Level 3 methods
     */

    public void setIdAttribute(String name, boolean isId) throws DOMException {
        //find the attr
        AttrImpl tempAttr = (AttrImpl) this.getAttributeNode(name);
        if (tempAttr == null) {
            throw DOMUtil.newDOMException(DOMException.NOT_FOUND_ERR);
        }

        this.updateIsId(isId, tempAttr);
    }

    public void setIdAttributeNS(String namespaceURI, String localName, boolean isId)
            throws DOMException {
        //find the attr
        AttrImpl tempAttr = (AttrImpl) this.getAttributeNodeNS(namespaceURI, localName);
        if (tempAttr == null) {
            throw DOMUtil.newDOMException(DOMException.NOT_FOUND_ERR);
        }

        this.updateIsId(isId, tempAttr);
    }

    public void setIdAttributeNode(Attr idAttr, boolean isId) throws DOMException {
        //find the attr
        Iterator attrIter = this.getAllAttributes();
        AttrImpl tempAttr = null;
        while (attrIter.hasNext()) {
            AttrImpl attr = (AttrImpl) attrIter.next();
            if (attr.equals(idAttr)) {
                tempAttr = attr;
                break;
            }
        }

        if (tempAttr == null) {
            throw DOMUtil.newDOMException(DOMException.NOT_FOUND_ERR);
        }

        this.updateIsId(isId, tempAttr);
    }

    /**
     * Updates the id state of the attr and notifies the document
     *
     * @param isId
     * @param tempAttr
     */
    private void updateIsId(boolean isId, AttrImpl tempAttr) {
        tempAttr.isId = isId;
        if (isId) {
            ownerDocument().addIdAttr(tempAttr);
        } else {
            ownerDocument().removeIdAttr(tempAttr);
        }
    }

    public TypeInfo getSchemaTypeInfo() {
        // TODO TODO
        throw new UnsupportedOperationException("TODO");
    }

    /* (non-Javadoc)
      * @see org.apache.axiom.om.OMNode#buildAll()
      */
    public void buildWithAttachments() {
        if (state == INCOMPLETE) {
            this.build();
        }
        Iterator iterator = getChildren();
        while (iterator.hasNext()) {
            OMNode node = (OMNode) iterator.next();
            node.buildWithAttachments();
        }
    }

    void normalize(DOMConfigurationImpl config) {
        if (config.isEnabled(DOMConfigurationImpl.NAMESPACES)) {
            OMNamespace namespace = getNamespace();
            if (namespace == null) {
                if (getDefaultNamespace() != null) {
                    declareDefaultNamespace("");
                }
            } else {
                OMNamespace namespaceForPrefix = findNamespaceURI(namespace.getPrefix());
                if (namespaceForPrefix == null || !namespaceForPrefix.getNamespaceURI().equals(namespace.getNamespaceURI())) {
                    declareNamespace(namespace);
                }
            }
        }
        super.normalize(config);
    }

    public final OMXMLParserWrapper getBuilder() {
        return builder;
    }

    public final int getState() {
        return state;
    }

    public final boolean isComplete() {
        return state == COMPLETE;
    }

    public final void setComplete(boolean complete) {
        state = complete ? COMPLETE : INCOMPLETE;
        ParentNode parentNode = parentNode();
        if (parentNode != null) {
            if (!complete) {
                parentNode.setComplete(false);
            } else {
                parentNode.notifyChildComplete();
            }
        }
    }

    public final void discarded() {
        state = DISCARDED;
    }

    OMNode detach(boolean useDomSemantics) {
        if (state == INCOMPLETE) {
            build();
        }
        return super.detach(useDomSemantics);
    }

    public final void build() {
        OMContainerHelper.build(this);
    }

    public final OMNode getNextOMSibling() throws OMException {
        return OMNodeHelper.getNextOMSibling(this);
    }

    public final Node getNextSibling() {
        return (Node)getNextOMSibling();
    }

    public final IParentNode getIParentNode() {
        return parentNode();
    }
    
    public final void removeChildren() {
        OMContainerHelper.removeChildren(this);
    }

    public final String lookupNamespaceURI(String specifiedPrefix) {
        String namespace = this.getNamespaceURI();
        String prefix = this.getPrefix();
        // First check for namespaces implicitly defined by the namespace prefix/URI of the element
        // TODO: although the namespace != null condition conforms to the specs, it is likely incorrect; see XERCESJ-1586
        if (namespace != null
                && (prefix == null && specifiedPrefix == null
                        || prefix != null && prefix.equals(specifiedPrefix))) {
            return namespace;
        }
        // looking in attributes
        if (this.hasAttributes()) {
            NamedNodeMap map = this.getAttributes();
            int length = map.getLength();
            for (int i = 0; i < length; i++) {
                Node attr = map.item(i);
                namespace = attr.getNamespaceURI();
                if (namespace != null && namespace.equals(XMLConstants.XMLNS_ATTRIBUTE_NS_URI)) {
                    // At this point we know that either the prefix of the attribute is null and
                    // the local name is "xmlns" or the prefix is "xmlns" and the local name is the
                    // namespace prefix declared by the namespace declaration. We check that constraint
                    // when the attribute is created.
                    String attrPrefix = attr.getPrefix();
                    if ((specifiedPrefix == null && attrPrefix == null)
                            || (specifiedPrefix != null && attrPrefix != null
                                    && attr.getLocalName().equals(specifiedPrefix))) {
                        String value = attr.getNodeValue();
                        return value.length() > 0 ? value : null;
                    }
                }
            }
        }
        // looking in ancestor
        ParentNode parent = parentNode();
        return parent == null || parent instanceof Document ? null : parent.lookupNamespaceURI(specifiedPrefix);
    }

    public final void checkChild(OMNode child) {
    }
}
