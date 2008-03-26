/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.myfaces.buildtools.maven2.plugin.builder.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.apache.commons.digester.Digester;
import org.apache.myfaces.buildtools.maven2.plugin.builder.io.XmlWriter;

/**
 * Stores info about all of the jsf artifacts in the system being processed.
 */
public class Model
{
    static private final Logger _LOG = Logger.getLogger(Model.class.getName());

    private List _converters = new ArrayList(100);
    private List _validators = new ArrayList(100);
    private List _components = new ArrayList(100);
    private List _renderKits = new ArrayList(100);

    private Map _convertersByClass = new TreeMap();
    private Map _validatorsByClass = new TreeMap();
    private Map _componentsByClass = new TreeMap();
    private Map _renderKitsByClass = new TreeMap();

    /**
     * Write this model out as xml.
     * <p>
     * Having a hand-coded method like this is not very elegant; it would be
     * better to do this via some library like Betwixt. However I'm not very
     * familiar with such libs, so hand-coding is quicker for now.
     */
    public static void writeXml(XmlWriter out, Model model)
    {
        out.beginElement("model");

        for (Iterator i = model._components.iterator(); i.hasNext();)
        {
            ComponentMeta c = (ComponentMeta) i.next();
            ComponentMeta.writeXml(out, c);
        }

        for (Iterator i = model._converters.iterator(); i.hasNext();)
        {
            ConverterMeta c = (ConverterMeta) i.next();
            ConverterMeta.writeXml(out, c);
        }

        for (Iterator i = model._validators.iterator(); i.hasNext();)
        {
            ValidatorMeta c = (ValidatorMeta) i.next();
            ValidatorMeta.writeXml(out, c);
        }

        out.endElement("model");
    }

    /**
     * Add digester rules to repopulate a Model instance from an xml file.
     * <p>
     * Having a hand-coded method like this is not very elegant; it would be
     * better to do this via some library like Betwixt. However I'm not very
     * familiar with such libs, so hand-coding is quicker for now.
     */
    public static void addXmlRules(Digester digester)
    {
        String prefix = "model";

        digester.addObjectCreate(prefix, Model.class);
        ComponentMeta.addXmlRules(digester, prefix);
    }

    /**
     * Flatten this model.
     * <p>
     * In the flattened representation, each model object directly contains the
     * data that it inherits from its parents, so that the getter methods return
     * all available metadata, not just the data that was defined directly on
     * that item.
     */
    public void flatten()
    {
        flattenComponentProperties();
    }

    private void flattenComponentProperties()
    {
        // for each component
        // build up a list of its ancestor and interface components
        // for each ancestor
        // for each property
        // if the component does not yet have that property, copy it

        for (Iterator i = _components.iterator(); i.hasNext();)
        {
            ComponentMeta comp = (ComponentMeta) i.next();
            List l = new ArrayList();
            addAncestors(l, comp);
            for (Iterator j = l.iterator(); j.hasNext();)
            {
                ComponentMeta ancestor = (ComponentMeta) j.next();
                copyProps(comp, ancestor);
            }
        }
    }

    private void copyProps(ComponentMeta dst, ComponentMeta src)
    {
        for (Iterator i = src.properties(); i.hasNext();)
        {
            PropertyMeta prop = (PropertyMeta) i.next();
            if (dst.getProperty(prop.getName()) == null)
            {
                dst.addProperty(prop);
            }
            else
            {
                // TODO: consider checking that the redefinition of the
                // property is "compatible".
                _LOG.info("Duplicate prop def for class " + dst.getClassName()
                        + " prop " + prop.getName());
            }

        }
    }

    private void addAncestors(List l, ComponentMeta comp)
    {
        String parentClassName = comp.getParentClassName();
        if (parentClassName != null)
        {
            ComponentMeta parent = (ComponentMeta) _componentsByClass
                    .get(parentClassName);
            l.add(parent);
            addAncestors(l, parent);
        }

        List interfaces = comp.getInterfaceClassNames();
        for (Iterator i = interfaces.iterator(); i.hasNext();)
        {
            String ifaceName = (String) i.next();
            ComponentMeta iface = (ComponentMeta) _componentsByClass
                    .get(ifaceName);
            l.add(ifaceName);
            addAncestors(l, iface);
        }
    }

    /**
     * Holds info about a JSF Converter definition
     */
    public void addConverter(ConverterMeta converter)
    {
        _converters.add(converter);
        _convertersByClass.put(converter.getClassName(), converter);
    }

    /**
     * Returns all converters
     */
    public List getConverters()
    {
        return _converters;
    }

    /**
     * Returns an iterator for all converters
     */
    public Iterator converters()
    {
        return _converters.iterator();
    }

    public ConverterMeta findConverterByClassName(String className)
    {
        return (ConverterMeta) _convertersByClass.get(className);
    }

    /**
     * Holds info about a JSF Converter definition
     */
    public void addValidator(ValidatorMeta validator)
    {
        _validators.add(validator);
        _validatorsByClass.put(validator.getClassName(), validator);
    }

    /**
     * Returns all validators
     */
    public List getValidators()
    {
        return _validators;
    }

    /**
     * Returns an iterator for all validators
     */
    public Iterator validators()
    {
        return _validators.iterator();
    }

    public ValidatorMeta findValidatorByClassName(String className)
    {
        return (ValidatorMeta) _validatorsByClass.get(className);
    }

    /**
     * Adds a component to this faces config document.
     * 
     * @param component
     *            the component to add
     */
    public void addComponent(ComponentMeta component)
    {
        _components.add(component);
        _componentsByClass.put(component.getClassName(), component);
    }

    /**
     * Returns all components
     */
    public List getComponents()
    {
        return _components;
    }

    /**
     * Returns an iterator for all components.
     */
    public Iterator components()
    {
        return _components.iterator();
    }

    public ComponentMeta findComponentByClassName(String className)
    {
        return (ComponentMeta) _componentsByClass.get(className);
    }

    /**
     * Adds a render kit to this faces config document.
     * 
     * @param renderKit
     *            the render kit to add
     */
    public void addRenderKit(RenderKitMeta renderKit)
    {
        _renderKits.add(renderKit);
        _renderKitsByClass.put(renderKit.getClassName(), renderKit);
    }

    public List getRenderKits()
    {
        return _renderKits;
    }

    /**
     * Returns an iterator for all render kits in this faces config.
     * 
     * @return the render kit iterator
     */
    public Iterator renderKits()
    {
        return _renderKits.iterator();
    }

    /**
     * Returns the render kit for this render kit id.
     * 
     * @param renderKitId
     *            the render kit id to find
     */
    private RenderKitMeta findRenderKitByClassName(String className)
    {
        return (RenderKitMeta) _renderKitsByClass.get(className);
    }
}
