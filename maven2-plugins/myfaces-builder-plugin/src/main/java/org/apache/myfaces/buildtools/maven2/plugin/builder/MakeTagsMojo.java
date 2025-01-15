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
package org.apache.myfaces.buildtools.maven2.plugin.builder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.myfaces.buildtools.maven2.plugin.builder.model.ComponentMeta;
import org.apache.myfaces.buildtools.maven2.plugin.builder.model.Model;
import org.apache.myfaces.buildtools.maven2.plugin.builder.utils.BuildException;
import org.apache.myfaces.buildtools.maven2.plugin.builder.utils.MavenPluginConsoleLogSystem;
import org.apache.myfaces.buildtools.maven2.plugin.builder.utils.MyfacesUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.runtime.RuntimeConstants;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;

/**
 * Maven goal to generate Java source code for Component tag classes.
 * 
 * <p>It uses velocity to generate templates, and has the option to define custom templates.</p>
 * <p>The executed template has the following variables available to it:</p>
 * <ul>
 *  <li>utils : Returns an instance of 
 *  org.apache.myfaces.buildtools.maven2.plugin.builder.utils.MyfacesUtils, 
 *  it contains some useful methods.</li>
 *  <li>component : Returns the current instance of
 *   org.apache.myfaces.buildtools.maven2.plugin.builder.model.ComponentMeta</li>
 * </ul>
 * 
 * @version $Id$
 * @requiresDependencyResolution compile
 * @goal make-tags
 * @phase generate-sources
 */
public class MakeTagsMojo extends AbstractBuilderMojo
{
    /**
     * Injected Maven project.
     * 
     * @parameter expression="${project}"
     * @readonly
     */
    private MavenProject project;

    /**
     * Defines the directory where the metadata file (META-INF/myfaces-metadata.xml) is loaded.
     * 
     * @parameter expression="${project.build.directory}/generated-resources/myfaces-builder-plugin"
     */
    private File buildDirectory;

    /**
     * Injected name of file generated by earlier run of BuildMetaDataMojo goal.
     * 
     * @parameter
     */
    private String metadataFile = "META-INF/myfaces-metadata.xml";

    /**
     * The directory used to load templates into velocity environment.
     * 
     * @parameter expression="src/main/resources/META-INF"
     */
    private File templateSourceDirectory;
    
    /**
     * This param is used to search in this folder if some file to
     * be generated exists and avoid generation and duplicate exception.
     * 
     * @parameter expression="src/main/java"
     */    
    private File mainSourceDirectory;

    /**
     * This param is used to search in this folder if some file to
     * be generated exists and avoid generation and duplicate exception.
     * 
     * @parameter
     */        
    private File mainSourceDirectory2;

    /**
     * The name of the template used to generate component tag classes. According to the value on 
     * jsfVersion property the default if this property is not set could be tagClass11.vm (1.1) or
     * tagClass12.vm (1.2)
     * 
     * @parameter 
     */
    private String templateTagName;

    /**
     * The directory where all generated files are created. This directory is added as a
     * compile source root automatically like src/main/java is. 
     * 
     * @parameter expression="${project.build.directory}/generated-sources/myfaces-builder-plugin"
     * @required
     */
    private File generatedSourceDirectory;

    /**
     * Only generate tag classes that contains that package prefix
     * 
     * @parameter
     */
    private String packageContains;

    /**
     * Only generate tag classes that its component starts with this type prefix
     * 
     * @parameter
     */
    private String typePrefix;
    
    /**
     *  Log and continue execution when generating component tag classes.
     *  <p>
     *  If this property is set to false (default), errors when a component tag class is generated stops
     *  execution immediately.
     *  </p>
     * 
     * @parameter
     */
    private boolean force;

    /**
     * Defines the JSF version (1.1 or 1.2), used to take the default templates for each version.
     * <p> 
     * If version is 1.1, the default templateTagName is 'tagClass11.vm' and if version
     * is 1.2 the default templateTagName is 'tagClass12.vm'.
     * </p>
     * 
     * @parameter
     */
    private String jsfVersion;
    
    /**
     * Define the models that should be included when generate component tag classes. If not set, the
     * current model identified by the artifactId is used.
     * <p>
     * Each model built by build-metadata goal has a modelId, that by default is the artifactId of
     * the project. Setting this property defines which objects tied in a specified modelId should
     * be taken into account.  
     * </p>
     * <p>In this case, limit component tag generation only to the components defined in the models 
     * identified by the modelId defined. </p>
     * <p>This is useful when you need to generate files that take information defined on other
     * projects.</p>
     * <p>Example:</p>
     * <pre>
     *    &lt;modelIds&gt;
     *        &lt;modelId>model1&lt;/modelId&gt;
     *        &lt;modelId>model2&lt;/modelId&gt;
     *    &lt;/modelIds&gt;
     * </pre>
     * 
     * @parameter
     */
    private List modelIds;
    
    /**
     * Execute the Mojo.
     */
    public void execute() throws MojoExecutionException
    {
        // This command makes Maven compile the generated source:
        // getProject().addCompileSourceRoot( absoluteGeneratedPath.getPath() );
        try
        {
            project.addCompileSourceRoot( generatedSourceDirectory.getCanonicalPath() );
            
            if (modelIds == null)
            {
                modelIds = new ArrayList();
                modelIds.add(project.getArtifactId());
            }
            File mdFile = new File(buildDirectory, metadataFile);
            Model model = IOUtils.loadModel(mdFile);
            new Flattener(model).flatten();
            
            Properties cacheInfo = new Properties();
            loadCache(cacheInfo);
            generateComponents(model, cacheInfo, mdFile.lastModified() );
            storeCache(cacheInfo);
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("Error generating components", e);
        }
        catch (BuildException e)
        {
            throw new MojoExecutionException("Error generating components", e);
        }
    }
    
    private VelocityEngine initVelocity() throws MojoExecutionException
    {
        File template = new File(templateSourceDirectory, _getTemplateTagName());
        
        if (template.exists())
        {
            getLog().info("Using template from file loader: "+template.getPath());
        }
        else
        {
            getLog().info("Using template from class loader: META-INF/"+_getTemplateTagName());
        }
                
        VelocityEngine velocityEngine = new VelocityEngine();
                
        try
        {
            velocityEngine.setProperty( "resource.loader", "file, class" );
            velocityEngine.setProperty( "file.resource.loader.class",
                    "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
            velocityEngine.setProperty( "file.resource.loader.path", templateSourceDirectory.getPath());
            velocityEngine.setProperty( "class.resource.loader.class",
                    "org.apache.myfaces.buildtools.maven2.plugin.builder.utils.RelativeClasspathResourceLoader" );
            velocityEngine.setProperty( "class.resource.loader.path", "META-INF");            
            velocityEngine.setProperty( "velocimacro.library", "tagClassMacros11.vm");
            velocityEngine.setProperty( "velocimacro.permissions.allow.inline","true");
            velocityEngine.setProperty( "velocimacro.permissions.allow.inline.local.scope", "true");
            velocityEngine.setProperty( "directive.foreach.counter.initial.value","0");
            //velocityEngine.setProperty( RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS,
            //    "org.apache.myfaces.buildtools.maven2.plugin.builder.utils.ConsoleLogSystem" );
            
            velocityEngine.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM,
                    new MavenPluginConsoleLogSystem(this.getLog()));

            velocityEngine.init();
        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Error creating VelocityEngine", e);
        }
        
        return velocityEngine;
    }

    /**
     * Generates parsed components.
     */
    private void generateComponents(Model model, Properties cachedInfo, long lastModifiedMetadata) throws IOException,
            MojoExecutionException
    {
        File tf = new File(templateSourceDirectory, _getTemplateTagName());
        
        if (isCachingEnabled())
        {
            boolean upToDate = true;
            for (Iterator it = model.getComponents().iterator(); it.hasNext();)
            {
                ComponentMeta component = (ComponentMeta) it.next();
                
                if (component.getTagClass() != null)
                {
                    File f = new File(mainSourceDirectory, StringUtils.replace(
                        component.getTagClass(), ".", "/")+".java");
                    
                    if (!f.exists() && canGenerateComponentTag(component))
                    {
                        if (mainSourceDirectory2 != null)
                        {
                            File f2 = new File(mainSourceDirectory2, StringUtils.replace(
                                    component.getTagClass(), ".", "/")+".java");
                            if (f2.exists())
                            {
                                //Skip
                                continue;
                            }
                        }
    
                        File outFile = new File(generatedSourceDirectory, StringUtils.replace(
                                component.getTagClass(), ".", "/")+".java");
    
                        String lastModifiedString = cachedInfo.getProperty(outFile.getAbsolutePath());
                        if (lastModifiedString == null)
                        {
                            upToDate = false;
                            break;
                        }
                        else if (!outFile.exists())
                        {
                            upToDate = false;
                            break;
                        }
                        else
                        {
                            Long lastModified = Long.valueOf(lastModifiedString);
                            if (lastModified != null && lastModifiedMetadata > lastModified.longValue())
                            {
                                upToDate = false;
                                break;
                            }
                        }
                    }
                }
            }
            if (upToDate && tf != null && tf.exists())
            {
                upToDate = isFileUpToDate(cachedInfo, tf);
            }
            if (upToDate)
            {
                getLog().info("generated component tag files are up to date");
                return;
            }
        }

        VelocityEngine velocityEngine = initVelocity();

        VelocityContext baseContext = new VelocityContext();
        baseContext.put("utils", new MyfacesUtils());
        
        for (Iterator it = model.getComponents().iterator(); it.hasNext();)
        {
            ComponentMeta component = (ComponentMeta) it.next();
            
            if (component.getTagClass() != null)
            {
                File f = new File(mainSourceDirectory, StringUtils.replace(
                    component.getTagClass(), ".", "/")+".java");
                
                if (!f.exists() && canGenerateComponentTag(component))
                {
                    if (mainSourceDirectory2 != null)
                    {
                        File f2 = new File(mainSourceDirectory2, StringUtils.replace(
                                component.getTagClass(), ".", "/")+".java");
                        if (f2.exists())
                        {
                            //Skip
                            continue;
                        }
                    }
                    getLog().info("Generating tag class:"+component.getTagClass());
                    try 
                    {
                        _generateComponent(velocityEngine, component,baseContext, 
                                cachedInfo, lastModifiedMetadata);
                    }
                    catch(MojoExecutionException e)
                    {
                        if (force)
                        {
                            getLog().error(e.getMessage());
                        }
                        else
                        {
                            //Stop execution throwing exception
                            throw e;
                        }
                    }
                }
            }
        }
        if (isCachingEnabled())
        {
            if (tf != null && tf.exists())
            {
                cachedInfo.put(tf.getAbsolutePath(), Long.toString(tf.lastModified()));
            }
        }
    }
    
    public boolean canGenerateComponentTag(ComponentMeta component)
    {
        if ( modelIds.contains(component.getModelId())
                && includePackage(component)
                && includeType(component))
        {
            return true;
        }
        else
        {
            return false;
        }
    }
    
    public boolean includePackage(ComponentMeta component)
    {
        if (packageContains != null)
        {
            if (component.getTagPackage().startsWith(packageContains))
            {
                return true;
            }
            else
            {
                return false;
            }
        }
        else
        {
            return true;
        }        
    }

    public boolean includeType(ComponentMeta component)
    {
        if (typePrefix != null)
        {
            if (component.getType().startsWith(typePrefix))
            {
                return true;
            }
            else
            {
                return false;
            }
        }
        else
        {
            return true;
        }        
    }


    /**
     * Generates a parsed component.
     * 
     * @param component
     *            the parsed component metadata
     */
    private void _generateComponent(VelocityEngine velocityEngine, ComponentMeta component, VelocityContext baseContext,
            Properties cachedInfo, long lastModifiedMetadata)
            throws MojoExecutionException
    {

        Context context = new VelocityContext(baseContext);
        context.put("component", component);

        Writer writer = null;
        File outFile = null;

        try
        {
            outFile = new File(generatedSourceDirectory, StringUtils.replace(
                    component.getTagClass(), ".", "/")+".java");

            if ( !outFile.getParentFile().exists() )
            {
                outFile.getParentFile().mkdirs();
            }

            writer = new OutputStreamWriter(new FileOutputStream(outFile));

            Template template = velocityEngine.getTemplate(_getTemplateTagName());
                        
            template.merge(context, writer);

            writer.flush();
            
            if (isCachingEnabled())
            {
                cachedInfo.put(outFile.getAbsolutePath(), Long.toString(lastModifiedMetadata));
            }
        }
        catch (Exception e)
        {
            throw new MojoExecutionException(
                    "Error merging velocity templates: " + e.getMessage(), e);
        }
        finally
        {
            IOUtil.close(writer);
            writer = null;
        }
    }
    
    private String _getTemplateTagName()
    {
        if (templateTagName == null)
        {
            if (_is12() || _is20())
            {
                return "tagClass12.vm";
            }
            else
            {
                return "tagClass11.vm";
            }
        }
        else
        {
            return templateTagName;            
        }        
    }

    private boolean _is12()
    {
        return "1.2".equals(jsfVersion) || "12".equals(jsfVersion);
    }

    private boolean _is20()
    {
        return "2.0".equals(jsfVersion) || "20".equals(jsfVersion);
    }
}
