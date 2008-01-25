/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.jcr.resource.internal.helper.bundle;

import static org.apache.jackrabbit.JcrConstants.NT_FILE;
import static org.apache.jackrabbit.JcrConstants.NT_FOLDER;
import static org.apache.sling.api.resource.ResourceMetadata.CREATION_TIME;
import static org.apache.sling.api.resource.ResourceMetadata.MODIFICATION_TIME;
import static org.apache.sling.api.resource.ResourceMetadata.RESOLUTION_PATH;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceProvider;
import org.apache.sling.jcr.resource.internal.helper.Descendable;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A Resource that wraps a Bundle entry */
public class BundleResource implements Resource, Descendable {

    /** default log */
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ResourceProvider resourceProvider;
    
    private final Bundle bundle;

    private final String path;

    private URL url;

    private final String resourceType;

    private final ResourceMetadata metadata;

    public static BundleResource getResource(ResourceProvider resourceProvider, Bundle bundle, String path) {

        // if the entry has no trailing slash, try to with a trailing
        // slash in case the entry would be a folder
        if (!path.endsWith("/")) {
            BundleResource br = getResource(resourceProvider, bundle, path + "/");
            if (br != null) {
                return br;
            }
        }

        // has trailing slash or not a folder, try path itself
        URL entry = bundle.getEntry(path);
        if (entry != null) {
            return new BundleResource(resourceProvider, bundle, path);
        }

        // the bundle does not contain the path
        return null;
    }

    public BundleResource(ResourceProvider resourceProvider, Bundle bundle, String path) {
        this.resourceProvider = resourceProvider;
        this.bundle = bundle;
        this.path = path.endsWith("/")
                ? path.substring(0, path.length() - 1)
                : path;
        this.resourceType = path.endsWith("/") ? NT_FOLDER : NT_FILE;

        metadata = new ResourceMetadata();
        metadata.put(RESOLUTION_PATH, path);
        metadata.put(CREATION_TIME, bundle.getLastModified());
        metadata.put(MODIFICATION_TIME, bundle.getLastModified());
    }

    public String getPath() {
        return path;
    }

    public String getResourceType() {
        return resourceType;
    }

    public ResourceMetadata getResourceMetadata() {
        return metadata;
    }

    public ResourceProvider getResourceProvider() {
        return resourceProvider;
    }
    
    @SuppressWarnings("unchecked")
    public <Type> Type adaptTo(Class<Type> type) {
        if (type == InputStream.class) {
            return (Type) getInputStream(); // unchecked cast
        } else if (type == URL.class) {
            return (Type) getURL(); // unchecked cast
        }

        // fall back to nothing
        return null;
    }

    public String toString() {
        return "BundleResource, type=" + resourceType + ", path=" + path;
    }

    // ---------- internal -----------------------------------------------------

    /**
     * Returns a stream to the bundle entry if it is a file. Otherwise returns
     * <code>null</code>.
     */
    private InputStream getInputStream() {
        // implement this for files only
        if (isFile()) {
            try {
                URL url = getURL();
                if (url != null) {
                    return url.openStream();
                }
            } catch (IOException ioe) {
                log.error(
                    "getInputStream: Cannot get input stream for " + this, ioe);
            }
        }

        // otherwise there is no stream
        return null;
    }

    private URL getURL() {
        if (url == null) {
            try {
                url = new URL(BundleResourceURLStreamHandler.PROTOCOL, null,
                    -1, path, new BundleResourceURLStreamHandler(bundle));
            } catch (MalformedURLException mue) {
                log.error("getURL: Cannot get URL for " + this, mue);
            }
        }

        return url;
    }

    // ---------- Descendable interface ----------------------------------------

    public Iterator<Resource> listChildren() {
        return new BundleResourceIterator(this);
    }

    public Resource getDescendent(String relPath) {

        // only consider folder resources for descendents
        if (!isFile()) {
            URL descendent = bundle.getEntry(path + relPath);
            if (descendent != null) {
                new BundleResource(resourceProvider, bundle, descendent.getPath());
            }
        }

        return null;
    }

    Bundle getBundle() {
        return bundle;
    }

    boolean isFile() {
        return NT_FILE.equals(getResourceType());
    }
}
