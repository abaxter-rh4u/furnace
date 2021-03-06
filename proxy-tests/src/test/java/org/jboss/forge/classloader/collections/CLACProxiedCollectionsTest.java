/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.jboss.forge.classloader.collections;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.forge.arquillian.archive.ForgeArchive;
import org.jboss.forge.arquillian.services.LocalServices;
import org.jboss.forge.classloader.mock.collections.Profile;
import org.jboss.forge.classloader.mock.collections.ProfileCommand;
import org.jboss.forge.classloader.mock.collections.ProfileManager;
import org.jboss.forge.classloader.mock.collections.ProfileManagerImpl;
import org.jboss.forge.furnace.addons.AddonId;
import org.jboss.forge.furnace.addons.AddonRegistry;
import org.jboss.forge.furnace.proxy.ClassLoaderAdapterBuilder;
import org.jboss.forge.furnace.proxy.Proxies;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class CLACProxiedCollectionsTest
{
   @Deployment(order = 3)
   public static ForgeArchive getDeployment()
   {
      ForgeArchive archive = ShrinkWrap.create(ForgeArchive.class)
               .addBeansXML()
               .addClasses(ProfileCommand.class, ProfileManager.class, ProfileManagerImpl.class, Profile.class)
               .addAsLocalServices(CLACProxiedCollectionsTest.class);

      return archive;
   }

   @Deployment(name = "dep,1", testable = false, order = 2)
   public static ForgeArchive getDeploymentDep1()
   {
      ForgeArchive archive = ShrinkWrap.create(ForgeArchive.class)
               .addClasses(ProfileCommand.class, ProfileManager.class, Profile.class)
               .addBeansXML();

      return archive;
   }

   @Test
   public void testIterableTypesAreProxied() throws Exception
   {
      AddonRegistry registry = LocalServices.getFurnace(getClass().getClassLoader())
               .getAddonRegistry();
      ClassLoader thisLoader = CLACProxiedCollectionsTest.class.getClassLoader();
      ClassLoader dep1Loader = registry.getAddon(AddonId.from("dep", "1")).getClassLoader();

      Class<?> foreignType = dep1Loader.loadClass(ProfileCommand.class.getName());

      Object delegate = foreignType.newInstance();
      ProfileCommand enhanced = (ProfileCommand) ClassLoaderAdapterBuilder.callingLoader(thisLoader)
               .delegateLoader(dep1Loader).enhance(delegate);

      ProfileManagerImpl manager = new ProfileManagerImpl();
      enhanced.setManager(manager);
      enhanced.configureProfile();
      Assert.assertTrue(Proxies.isForgeProxy(enhanced));
   }

}
