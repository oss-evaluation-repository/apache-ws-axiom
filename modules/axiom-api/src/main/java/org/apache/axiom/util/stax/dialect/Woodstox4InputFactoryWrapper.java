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
package org.apache.axiom.util.stax.dialect;

import java.io.InputStream;
import java.io.Reader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class Woodstox4InputFactoryWrapper extends NormalizingXMLInputFactoryWrapper {
    private final boolean wstx276;
    
    public Woodstox4InputFactoryWrapper(XMLInputFactory parent, AbstractStAXDialect dialect, boolean wstx276) {
        super(parent, dialect);
        this.wstx276 = wstx276;
    }

    public XMLStreamReader createXMLStreamReader(InputStream stream, String encoding) throws XMLStreamException {
        return super.createXMLStreamReader(wstx276 ? new CloseShieldInputStream(stream) : stream, encoding);
    }

    public XMLStreamReader createXMLStreamReader(InputStream stream) throws XMLStreamException {
        return super.createXMLStreamReader(wstx276 ? new CloseShieldInputStream(stream) : stream);
    }

    public XMLStreamReader createXMLStreamReader(Reader reader) throws XMLStreamException {
        return super.createXMLStreamReader(wstx276 ? new CloseShieldReader(reader) : reader);
    }

    public XMLStreamReader createXMLStreamReader(String systemId, InputStream stream) throws XMLStreamException {
        return super.createXMLStreamReader(systemId, wstx276 ? new CloseShieldInputStream(stream) : stream);
    }

    public XMLStreamReader createXMLStreamReader(String systemId, Reader reader) throws XMLStreamException {
        return super.createXMLStreamReader(systemId, wstx276 ? new CloseShieldReader(reader) : reader);
    }
}
