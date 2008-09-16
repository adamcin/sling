/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.jcr.jcrinstall.jcr.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.jcr.Session;

import org.apache.sling.commons.testing.jcr.RepositoryTestBase;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.jcrinstall.osgi.JcrInstallException;
import org.apache.sling.jcr.jcrinstall.osgi.OsgiController;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.Sequence;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;

/** Test that added/updated/removed resources are
 * 	correctly translated to OsgiController calls.
 */
public class ResourceDetectionTest extends RepositoryTestBase {
    SlingRepository repo;
    Session session;
    private EventHelper eventHelper; 
    private ContentHelper contentHelper;
    private Mockery mockery;
    private Sequence sequence;

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        contentHelper.cleanupContent();
        session.logout();
        eventHelper = null;
        contentHelper = null;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        repo = getRepository();
        session = repo.loginAdministrative(repo.getDefaultWorkspace());
        eventHelper = new EventHelper(session);
        contentHelper = new ContentHelper(session);
        contentHelper.cleanupContent();
        
        mockery = new Mockery();
        sequence = mockery.sequence(getClass().getSimpleName());
    }
    
    protected String getOsgiResourceLocation(String uri) {
        return "jcrinstall://" + uri;
    }
    
    /** Add, update and remove resources from the repository
     *  and verify that the OsgiController receives the
     *  correct messages
     */
    public void testSingleResourceDetection() throws Exception {
        contentHelper.setupContent();
        
        final String dummyJar = "/libs/foo/bar/install/dummy.jar";
        final InputStream data = new ByteArrayInputStream(dummyJar.getBytes());
        final long lastModifiedA = System.currentTimeMillis();
        final long lastModifiedB = lastModifiedA + 1;
        final Set<String> installedUri = new HashSet<String>();
        final OsgiController c = mockery.mock(OsgiController.class);
        
        // Define the whole sequence of calls to OsgiController,
        // Using getLastModified calls to mark the test phases
        mockery.checking(new Expectations() {{
            allowing(c).getInstalledUris(); will(returnValue(installedUri));
            
            one(c).getLastModified("phase1"); 
            inSequence(sequence);
            one(c).getLastModified(dummyJar); will(returnValue(-1L));
            inSequence(sequence);
            one(c).installOrUpdate(with(equal(dummyJar)), with(equal(lastModifiedA)), with(any(InputStream.class)));
            inSequence(sequence);
            
            one(c).getLastModified("phase2"); 
            inSequence(sequence);
            one(c).getLastModified(dummyJar); will(returnValue(lastModifiedA));
            inSequence(sequence);
            
            one(c).getLastModified("phase3"); 
            inSequence(sequence);
            one(c).getLastModified(dummyJar); will(returnValue(lastModifiedA));
            inSequence(sequence);
            one(c).installOrUpdate(with(equal(dummyJar)), with(equal(lastModifiedB)), with(any(InputStream.class)));
            inSequence(sequence);
        }});
        
        final RepositoryObserver ro = new MockRepositoryObserver(repo, c);
        ro.activate(null);
        
        // Add two files, run one cycle must install the bundles
        c.getLastModified("phase1");
        contentHelper.createOrUpdateFile(dummyJar, data, lastModifiedA);
        eventHelper.waitForEvents(5000L);
        ro.runOneCycle();
        installedUri.add(dummyJar);
        
        // Updating with the same timestamp must not call install again
        c.getLastModified("phase2");
        contentHelper.createOrUpdateFile(dummyJar, data, lastModifiedA);
        eventHelper.waitForEvents(5000L);
        ro.runOneCycle();
        
        // Updating with a new timestamp must call install again
        c.getLastModified("phase3");
        contentHelper.createOrUpdateFile(dummyJar, data, lastModifiedB);
        eventHelper.waitForEvents(5000L);
        ro.runOneCycle();
        
        mockery.assertIsSatisfied();
    }
    
    public void testMultipleResourceDetection() throws Exception {
        contentHelper.setupContent();
        
        final String [] resources = {
                "/libs/foo/bar/install/dummy.jar",
                "/libs/foo/bar/install/dummy.cfg",
                "/libs/foo/bar/install/dummy.dp"
        };
        final InputStream data = new ByteArrayInputStream("hello".getBytes());
        final long lastModifiedA = System.currentTimeMillis();
        final Set<String> installedUri = new HashSet<String>();
        final OsgiController c = mockery.mock(OsgiController.class);
        
        // Define the whole sequence of calls to OsgiController,
        mockery.checking(new Expectations() {{
            allowing(c).getInstalledUris(); will(returnValue(installedUri));
            allowing(c).getLastModified(with(any(String.class))); will(returnValue(-1L)); 
            one(c).installOrUpdate(with(equal(resources[0])), with(equal(lastModifiedA)), with(any(InputStream.class)));
            one(c).installOrUpdate(with(equal(resources[1])), with(equal(lastModifiedA)), with(any(InputStream.class)));
            one(c).installOrUpdate(with(equal(resources[2])), with(equal(lastModifiedA)), with(any(InputStream.class)));
        }});
        
        final RepositoryObserver ro = new MockRepositoryObserver(repo, c);
        ro.activate(null);
        
        // Add files, run one cycle must install the bundles
        for(String file : resources) {
            contentHelper.createOrUpdateFile(file, data, lastModifiedA);
        }
        eventHelper.waitForEvents(5000L);
        ro.runOneCycle();
        
        mockery.assertIsSatisfied();
    }
    
    public void testIgnoredFilenames() throws Exception {
        contentHelper.setupContent();
        
        final String [] resources = {
                "/libs/foo/bar/install/dummy.jar",
                "/libs/foo/bar/install/dummy.cfg",
                "/libs/foo/bar/install/dummy.dp"
        };
        
        final String [] ignored  = {
                "/libs/foo/bar/install/_dummy.jar",
                "/libs/foo/bar/install/.dummy.cfg",
                "/libs/foo/bar/install/dummy.longextension"
        };
        
        final InputStream data = new ByteArrayInputStream("hello".getBytes());
        final long lastModifiedA = System.currentTimeMillis();
        final Set<String> installedUri = new HashSet<String>();
        final OsgiController c = mockery.mock(OsgiController.class);
        
        mockery.checking(new Expectations() {{
            allowing(c).getInstalledUris(); will(returnValue(installedUri));
            allowing(c).getLastModified(with(any(String.class))); will(returnValue(-1L)); 
            one(c).installOrUpdate(with(equal(resources[0])), with(equal(lastModifiedA)), with(any(InputStream.class)));
            one(c).installOrUpdate(with(equal(resources[1])), with(equal(lastModifiedA)), with(any(InputStream.class)));
            one(c).installOrUpdate(with(equal(resources[2])), with(equal(lastModifiedA)), with(any(InputStream.class)));
        }});
        
        final RepositoryObserver ro = new MockRepositoryObserver(repo, c);
        ro.activate(null);
        
        for(String file : resources) {
            contentHelper.createOrUpdateFile(file, data, lastModifiedA);
        }
        for(String file : ignored) {
            contentHelper.createOrUpdateFile(file, data, lastModifiedA);
        }
        eventHelper.waitForEvents(5000L);
        
        ro.runOneCycle();
        mockery.assertIsSatisfied();
   }
    
    public void testInitialDeletions() throws Exception {
        contentHelper.setupContent();
        
        final Set<String> installedUri = new HashSet<String>();
        installedUri.add("/libs/foo/bar/install/dummy.jar");
        installedUri.add("/libs/foo/bar/install/dummy.cfg");
        installedUri.add("/libs/watchfolder-is-gone/install/gone.cfg");
        
        final OsgiController c = mockery.mock(OsgiController.class);
        final RepositoryObserver ro = new MockRepositoryObserver(repo, c);
        
        mockery.checking(new Expectations() {{
            allowing(c).getInstalledUris(); will(returnValue(installedUri));
            allowing(c).getLastModified(with(any(String.class))); will(returnValue(-1L)); 
            one(c).uninstall("/libs/foo/bar/install/dummy.jar");
            one(c).uninstall("/libs/foo/bar/install/dummy.cfg");
            one(c).uninstall("/libs/watchfolder-is-gone/install/gone.cfg");
        }});
        
        // Activating with installed resources that are not in
        // the repository must cause them to be uninstalled
        ro.activate(null);
        mockery.assertIsSatisfied();
    }
    
    public void testInitialDeletionsWithException() throws Exception {
        contentHelper.setupContent();
        
        final SortedSet<String> installedUri = new TreeSet<String>();
        installedUri.add("/libs/foo/bar/install/dummy.cfg");
        installedUri.add("/libs/foo/bar/install/dummy.dp");
        installedUri.add("/libs/foo/bar/install/dummy.jar");
        
        final OsgiController c = mockery.mock(OsgiController.class);
        final RepositoryObserver ro = new MockRepositoryObserver(repo, c);
        
        mockery.checking(new Expectations() {{
            allowing(c).getInstalledUris(); will(returnValue(installedUri));
            allowing(c).getLastModified(with(any(String.class))); will(returnValue(-1L)); 
            one(c).uninstall("/libs/foo/bar/install/dummy.cfg");
            inSequence(sequence);
            one(c).uninstall("/libs/foo/bar/install/dummy.dp");
            inSequence(sequence);
            will(throwException(new JcrInstallException("Fake BundleException for testing")));
            one(c).uninstall("/libs/foo/bar/install/dummy.jar");
            inSequence(sequence);
        }});
        
        // Activating with installed resources that are not in
        // the repository must cause them to be uninstalled
        ro.activate(null);
        mockery.assertIsSatisfied();
    }
    
    public void testMultipleResourcesWithException() throws Exception {
        contentHelper.setupContent();
        
        final String [] resources = {
                "/libs/foo/bar/install/dummy.jar",
                "/libs/foo/bar/install/dummy.cfg",
                "/libs/foo/bar/install/dummy.dp"
        };
        final InputStream data = new ByteArrayInputStream("hello".getBytes());
        final long lastModifiedA = System.currentTimeMillis();
        final Set<String> installedUri = new HashSet<String>();
        final OsgiController c = mockery.mock(OsgiController.class);
        
        // Define the whole sequence of calls to OsgiController,
        mockery.checking(new Expectations() {{
            allowing(c).getInstalledUris(); will(returnValue(installedUri));
            allowing(c).getLastModified(with(any(String.class))); will(returnValue(-1L)); 
            one(c).installOrUpdate(with(equal(resources[0])), with(equal(lastModifiedA)), with(any(InputStream.class)));
            one(c).installOrUpdate(with(equal(resources[1])), with(equal(lastModifiedA)), with(any(InputStream.class)));
            will(throwException(new JcrInstallException("Fake BundleException for testing")));
            one(c).installOrUpdate(with(equal(resources[2])), with(equal(lastModifiedA)), with(any(InputStream.class)));
        }});
        
        final RepositoryObserver ro = new MockRepositoryObserver(repo, c);
        ro.activate(null);
        
        // Add files, run one cycle must install the bundles
        for(String file : resources) {
            contentHelper.createOrUpdateFile(file, data, lastModifiedA);
        }
        eventHelper.waitForEvents(5000L);
        ro.runOneCycle();
        
        mockery.assertIsSatisfied();
    }
    
    /** Verify that resources are correctly uninstalled if the folder name regexp changes */
    public void testFolderRegexpChange() throws Exception {
        contentHelper.setupContent();
        
       final String [] resources = {
                "/libs/foo/bar/install/dummy.jar",
                "/libs/foo/wii/install/dummy.cfg",
                "/libs/foo/bar/install/dummy.dp",
                "/libs/foo/wii/install/more.cfg",
        };
        
        final InputStream data = new ByteArrayInputStream("hello".getBytes());
        final long lastModifiedA = System.currentTimeMillis();

        for(String file : resources) {
            contentHelper.createOrUpdateFile(file, data, lastModifiedA);
        }
        
        final Set<String> installedUri = new HashSet<String>();
        final OsgiController c = mockery.mock(OsgiController.class);
        final Properties props = new Properties();
        final ComponentContext ctx = mockery.mock(ComponentContext.class);
        final BundleContext bc = mockery.mock(BundleContext.class);
        final File f = File.createTempFile(getClass().getSimpleName(), "properties");
        f.deleteOnExit();
        
        // Test with first regexp
        mockery.checking(new Expectations() {{
            allowing(ctx).getBundleContext(); will(returnValue(bc));
            allowing(bc).getDataFile("service.properties"); will(returnValue(f));
            allowing(c).getInstalledUris(); will(returnValue(installedUri));
            allowing(c).getLastModified(with(any(String.class))); will(returnValue(-1L)); 
            one(c).installOrUpdate(with(equal(resources[0])), with(equal(lastModifiedA)), with(any(InputStream.class)));
            one(c).installOrUpdate(with(equal(resources[2])), with(equal(lastModifiedA)), with(any(InputStream.class)));
        }});
        
        final MockRepositoryObserver ro = new MockRepositoryObserver(repo, c);
        ro.setProperties(props);
        props.setProperty(RepositoryObserver.FOLDER_NAME_REGEXP_PROPERTY, ".*foo/bar/install$");
        ro.activate(ctx);
        for(String file : resources) {
            contentHelper.createOrUpdateFile(file, data, lastModifiedA);
        }
        eventHelper.waitForEvents(5000L);
        ro.runOneCycle();
        mockery.assertIsSatisfied();
        
        // Test with a different regexp, install.A resources must be uninstalled
        mockery.checking(new Expectations() {{
            allowing(ctx).getBundleContext(); will(returnValue(bc));
            allowing(bc).getDataFile("service.properties"); will(returnValue(f));
            allowing(c).getInstalledUris(); will(returnValue(installedUri));
            allowing(c).getLastModified(with(any(String.class))); will(returnValue(-1L)); 
            one(c).installOrUpdate(with(equal(resources[1])), with(equal(lastModifiedA)), with(any(InputStream.class)));
            one(c).installOrUpdate(with(equal(resources[3])), with(equal(lastModifiedA)), with(any(InputStream.class)));
        }});
        
        props.setProperty(RepositoryObserver.FOLDER_NAME_REGEXP_PROPERTY, ".*foo/wii/install$");
        ro.activate(ctx);
        for(String file : resources) {
            contentHelper.createOrUpdateFile(file, data, lastModifiedA);
        }
        eventHelper.waitForEvents(5000L);
        ro.runOneCycle();
        mockery.assertIsSatisfied();
    }
}