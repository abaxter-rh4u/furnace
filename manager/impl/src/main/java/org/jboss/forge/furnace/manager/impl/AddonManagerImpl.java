/*
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.jboss.forge.furnace.manager.impl;

import static org.jboss.forge.furnace.manager.impl.request.AddonActionRequestFactory.createDeployRequest;
import static org.jboss.forge.furnace.manager.impl.request.AddonActionRequestFactory.createDisableRequest;
import static org.jboss.forge.furnace.manager.impl.request.AddonActionRequestFactory.createEnableRequest;
import static org.jboss.forge.furnace.manager.impl.request.AddonActionRequestFactory.createInstallRequest;
import static org.jboss.forge.furnace.manager.impl.request.AddonActionRequestFactory.createRemoveRequest;
import static org.jboss.forge.furnace.manager.impl.request.AddonActionRequestFactory.createUpdateRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jboss.forge.furnace.Furnace;
import org.jboss.forge.furnace.addons.AddonId;
import org.jboss.forge.furnace.manager.AddonManager;
import org.jboss.forge.furnace.manager.request.AddonActionRequest;
import org.jboss.forge.furnace.manager.request.DeployRequest;
import org.jboss.forge.furnace.manager.request.DisableRequest;
import org.jboss.forge.furnace.manager.request.EnableRequest;
import org.jboss.forge.furnace.manager.request.InstallRequest;
import org.jboss.forge.furnace.manager.request.RemoveRequest;
import org.jboss.forge.furnace.manager.spi.AddonDependencyResolver;
import org.jboss.forge.furnace.manager.spi.AddonInfo;
import org.jboss.forge.furnace.repositories.AddonRepository;
import org.jboss.forge.furnace.repositories.MutableAddonRepository;
import org.jboss.forge.furnace.util.Assert;
import org.jboss.forge.furnace.versions.Versions;

public class AddonManagerImpl implements AddonManager
{
   private final Furnace furnace;
   private final AddonDependencyResolver resolver;

   public AddonManagerImpl(final Furnace forge, final AddonDependencyResolver resolver)
   {
      this.furnace = forge;
      this.resolver = resolver;
   }

   @Override
   public AddonInfo info(final AddonId addonId)
   {
      return resolver.resolveAddonDependencyHierarchy(addonId);
   }

   @Override
   public InstallRequest install(final AddonId addonId)
   {
      return install(addonId, getDefaultRepository());
   }

   @Override
   public InstallRequest install(final AddonId addonId, final AddonRepository repository)
   {
      MutableAddonRepository mutableRepo = assertMutableRepository(repository);
      AddonInfo addonInfo = info(addonId);
      List<AddonInfo> allAddons = new LinkedList<AddonInfo>();
      collectRequiredAddons(addonInfo, allAddons);
      Map<AddonId, AddonRepository> installedAddonIds = getInstalledAddons();
      List<AddonActionRequest> actions = new ArrayList<AddonActionRequest>();
      for (AddonInfo newAddonInfo : allAddons)
      {
         AddonActionRequest request = createRequest(addonInfo, newAddonInfo, mutableRepo, installedAddonIds);
         if (request != null)
         {
            actions.add(request);
         }
      }
      return createInstallRequest(addonInfo, actions);
   }

   @Override
   public DeployRequest deploy(AddonId id)
   {
      return deploy(id, getDefaultRepository());
   }

   @Override
   public DeployRequest deploy(AddonId id, AddonRepository repository)
   {
      MutableAddonRepository mutableRepo = assertMutableRepository(repository);
      return createDeployRequest(info(id), mutableRepo, furnace);
   }

   @Override
   public RemoveRequest remove(final AddonId id)
   {
      return remove(id, getDefaultRepository());
   }

   @Override
   public RemoveRequest remove(final AddonId id, final AddonRepository repository)
   {
      AddonInfo info = info(id);
      return createRemoveRequest(info, assertMutableRepository(repository), furnace);
   }

   @Override
   public DisableRequest disable(final AddonId id)
   {
      return disable(id, getDefaultRepository());
   }

   @Override
   public DisableRequest disable(final AddonId id, final AddonRepository repository)
   {
      AddonInfo info = info(id);
      return createDisableRequest(info, assertMutableRepository(repository), furnace);
   }

   @Override
   public EnableRequest enable(final AddonId id)
   {
      return enable(id, getDefaultRepository());
   }

   @Override
   public EnableRequest enable(final AddonId id, final AddonRepository repository)
   {
      AddonInfo info = info(id);
      return createEnableRequest(info, assertMutableRepository(repository), furnace);
   }

   /**
    * Calculate the necessary request based in the list of installed addons for a given {@link MutableAddonRepository}
    * 
    * @param addonInfo
    * @param repository
    * @param installedAddons
    * @return
    */
   private AddonActionRequest createRequest(final AddonInfo requestedAddonInfo, final AddonInfo addonInfo,
            final MutableAddonRepository repository,
            final Map<AddonId, AddonRepository> installedAddons)
   {
      final AddonActionRequest request;
      AddonId addon = addonInfo.getAddon();
      if (installedAddons.containsKey(addon))
      {
         // Already contains the installed addon. Update ONLY if the version is SNAPSHOT and if it is the requested
         // addon
         if (Versions.isSnapshot(addonInfo.getAddon().getVersion()) && addonInfo.equals(requestedAddonInfo))
         {
            AddonRepository addonRepository = installedAddons.get(addon);
            if (repository.equals(addonRepository))
            {
               request = createUpdateRequest(addonInfo, addonInfo, repository, furnace);
            }
            else
            {
               request = createDeployRequest(addonInfo, repository, furnace);
            }
         }
         else
         {
            request = null;
         }
      }
      else
      {
         Entry<AddonId, AddonRepository> differentVersionEntry = null;
         for (Entry<AddonId, AddonRepository> addonEntry : installedAddons.entrySet())
         {
            AddonId addonId = addonEntry.getKey();
            if (addonId.getName().equals(addon.getName()))
            {
               differentVersionEntry = addonEntry;
               break;
            }
         }
         if (differentVersionEntry != null)
         {
            //TODO: Review condition below
            if (differentVersionEntry.getKey().getVersion().toString().compareTo(addon.getVersion().toString()) < 0)
            {
               if (repository.equals(differentVersionEntry.getValue()))
               {
                  request = createUpdateRequest(info(differentVersionEntry.getKey()), addonInfo, repository, furnace);
               }
               else
               {
                  request = createDeployRequest(addonInfo, repository, furnace);
               }
            }
            else
            {
               // No update needed. Don't do anything with it
               request = null;
            }
         }
         else
         {
            request = createDeployRequest(addonInfo, repository, furnace);
         }
      }
      return request;
   }

   /**
    * Collect all required addons for a specific addon.
    * 
    * It traverses the whole graph
    * 
    * @param addonInfo
    * @param addons
    */
   private void collectRequiredAddons(AddonInfo addonInfo, List<AddonInfo> addons)
   {
      addons.add(0, addonInfo);
      for (AddonInfo id : addonInfo.getRequiredAddons())
      {
         if (!addons.contains(id))
         {
            collectRequiredAddons(id, addons);
         }
      }
   }

   private MutableAddonRepository getDefaultRepository()
   {
      for (AddonRepository repo : furnace.getRepositories())
      {
         if (repo instanceof MutableAddonRepository)
            return (MutableAddonRepository) repo;
      }
      throw new IllegalStateException(
               "No default mutable repository found in Furnace instance. Have you added one using furnace.addRepository(AddonRepositoryMode.MUTABLE, repository) ?");
   }

   private MutableAddonRepository assertMutableRepository(AddonRepository repository)
   {
      Assert.isTrue(repository instanceof MutableAddonRepository, "Addon repository ["
               + repository.getRootDirectory().getAbsolutePath()
               + "] is not writable.");
      return (MutableAddonRepository) repository;
   }

   /**
    * Returns a {@link Map} of the installed addons with the key as the {@link AddonId} and the value as an
    * {@link AddonRepository} indicating which repository is it from
    */
   private Map<AddonId, AddonRepository> getInstalledAddons()
   {
      Map<AddonId, AddonRepository> addons = new HashMap<AddonId, AddonRepository>();
      for (AddonRepository repository : furnace.getRepositories())
      {
         List<AddonId> listEnabled = repository.listEnabled();
         for (AddonId addonId : listEnabled)
         {
            addons.put(addonId, repository);
         }
      }
      return addons;
   }
}
