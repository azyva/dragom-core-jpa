/*
 * Copyright 2015 - 2017 AZYVA INC. INC.
 *
 * This file is part of Dragom.
 *
 * Dragom is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dragom is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Dragom.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.azyva.dragom.model.config.impl.jpa;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.azyva.dragom.model.MutableNode;
import org.azyva.dragom.model.config.DuplicateNodeException;
import org.azyva.dragom.model.config.MutableNodeConfig;
import org.azyva.dragom.model.config.NodeConfig;
import org.azyva.dragom.model.config.NodeConfigTransferObject;
import org.azyva.dragom.model.config.OptimisticLockException;
import org.azyva.dragom.model.config.OptimisticLockHandle;
import org.azyva.dragom.model.config.PluginDefConfig;
import org.azyva.dragom.model.config.PluginKey;
import org.azyva.dragom.model.config.PropertyDefConfig;
import org.azyva.dragom.model.config.impl.simple.SimpleNodeConfigTransferObject;
import org.azyva.dragom.model.plugin.NodePlugin;

/**
 * JPA implementation for {@link NodeConfig} and {@link MutableNodeConfig}.
 *
 * @author David Raymond
 * @see org.azyva.dragom.model.config.impl.jpa
 */
public abstract class JpaNodeConfig implements NodeConfig, MutableNodeConfig {
  protected transient EntityManagerFactory entityManagerFactory;

  /**
   * Indicates that the {@link JpaNodeConfig} is new and has not been finalized
   * yet. This is the state in which it is after having been created using the
   * create methods of {@link JpaConfig} or
   * {@link JpaClassificationNodeConfig}.
   */
  protected transient boolean indNew;

  /**
   * Id.
   */
  protected int id;

  /**
   * Parent {@link JpaClassificationNodeConfig}.
   */
  private JpaClassificationNodeConfig jpaClassificationNodeConfigParent;

  /**
   * Name.
   */
  private String name;

  /**
   * Map of {@link PropertyDefConfig}.
   */
  private Map<String, PropertyDefConfig> mapPropertyDefConfig;

  /**
   * Map of {@link PluginDefConfig}.
   */
  private transient Map<PluginKey, PluginDefConfig> mapPluginDefConfig;

  /**
   * List of {@link PluginDefConfig}.
   *
   * Used for the JPA mapping since it does not seem possible to have null
   * properties in map keys (@{link PluginKey}).
   */
  private List<PluginDefConfig> listPluginDefConfig;

  /**
   * Last modification timestamp.
   *
   * <p>Used for optimistic locking.
   */
  private Timestamp timestampLastMod;

  /**
   * Default constructor.
   *
   * <p>Required for JPA.
   */
  protected JpaNodeConfig() {
  }

  /**
   * Constructor.
   *
   * @param entityManagerFactory EntityManagerFactory.
   */
  protected JpaNodeConfig(EntityManagerFactory entityManagerFactory) {
    this.entityManagerFactory = entityManagerFactory;

    this.indNew = true;

    this.mapPropertyDefConfig = new HashMap<String, PropertyDefConfig>();
    this.mapPluginDefConfig = new HashMap<PluginKey, PluginDefConfig>();
  }

  /**
   * Constructor.
   *
   * @param jpaClassificationNodeConfigParent Parent JpaClassificationNodeConfig.
   */
  JpaNodeConfig(JpaClassificationNodeConfig jpaClassificationNodeConfigParent) {
    this(jpaClassificationNodeConfigParent.getEntityManagerFactory());

    this.jpaClassificationNodeConfigParent = jpaClassificationNodeConfigParent;
  }

  EntityManagerFactory getEntityManagerFactory() {
    return this.entityManagerFactory;
  }

  private void postLoad() {
    if (this.jpaClassificationNodeConfigParent != null) {
      this.entityManagerFactory = this.jpaClassificationNodeConfigParent.getEntityManagerFactory();
    }

    this.mapPluginDefConfig = new HashMap<PluginKey, PluginDefConfig>();

    for(PluginDefConfig pluginDefConfig: this.listPluginDefConfig) {
      this.mapPluginDefConfig.put(new PluginKey(pluginDefConfig.getClassNodePlugin(), pluginDefConfig.getPluginId()), pluginDefConfig);
    }
  }

  protected JpaClassificationNodeConfig getJpaClassificationNodeConfigParent() {
    return this.jpaClassificationNodeConfigParent;
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public PropertyDefConfig getPropertyDefConfig(String name) {
    return this.mapPropertyDefConfig.get(name);
  }

  @Override
  public boolean isPropertyExists(String name) {
    return this.mapPropertyDefConfig.containsKey(name);
  }

  @Override
  public List<PropertyDefConfig> getListPropertyDefConfig() {
    // A copy is returned to prevent the internal Map from being modified by the
    // caller. Ideally, an unmodifiable List view of the Collection returned by
    // Map.values should be returned, but that does not seem possible.
    return new ArrayList<PropertyDefConfig>(this.mapPropertyDefConfig.values());
  }

  @Override
  public PluginDefConfig getPluginDefConfig(Class<? extends NodePlugin> classNodePlugin, String pluginId) {
    return this.mapPluginDefConfig.get(new PluginKey(classNodePlugin, pluginId));
  }

  @Override
  public boolean isPluginDefConfigExists(Class<? extends NodePlugin> classNodePlugin, String pluginId) {
    return this.mapPluginDefConfig.containsKey(new PluginKey(classNodePlugin, pluginId));
  }

  @Override
  public List<PluginDefConfig> getListPluginDefConfig() {
    // A copy is returned to prevent the internal Map from being modified by the
    // caller. Ideally, an unmodifiable List view of the Collection returned by
    // Map.values should be returned, but that does not seem possible.
    return new ArrayList<PluginDefConfig>(this.mapPluginDefConfig.values());
  }

  @Override
  public boolean isNew() {
    return this.indNew;
  }

  /**
   * Check whether the {@link OptimisticLockHandle} corresponds to the current state
   * of the data it represents.
   * <p>
   * If optimisticLockHandle is null, nothing is done.
   * <p>
   * If optimisticLockHandle is not null and is locked
   * ({@link OptimisticLockHandle#isLocked}), its state must correspond to the state
   * of the data it represents, otherwise {@link OptimisticLockException} is thrown.
   * <p>
   * If optimisticLockHandle is not null and is not locked, it is simply locked to
   * the current state of the data, unless indRequireLock, in which case an
   * exception is thrown.
   *
   * @param optimisticLockHandle OptimisticLockHandle. Can be null.
   * @param indRequireLock Indicates if it is required that the OptimisticLockHandle
   *   be locked.
   */
  protected void checkOptimisticLock(OptimisticLockHandle optimisticLockHandle, boolean indRequireLock) {
    if (optimisticLockHandle != null) {
      if (optimisticLockHandle.isLocked()) {
        if (!((JpaOptimisticLockHandle)optimisticLockHandle).getTimestampLastMod().equals(this.timestampLastMod)) {
          throw new OptimisticLockException();
        }
      } else {
        if (indRequireLock) {
          throw new RuntimeException("Lock required.");
        }

        ((JpaOptimisticLockHandle)optimisticLockHandle).setTimestampLastMod(this.timestampLastMod);
      }
    }
  }

  @Override
  public OptimisticLockHandle createOptimisticLockHandle(boolean indLock) {
    return new JpaOptimisticLockHandle(indLock ? this.timestampLastMod : null);
  }

  @Override
  public boolean isOptimisticLockValid(OptimisticLockHandle optimisticLockHandle) {
    return (((JpaOptimisticLockHandle)optimisticLockHandle).getTimestampLastMod().equals(this.timestampLastMod));
  }

  @Override
  public NodeConfigTransferObject getNodeConfigTransferObject(OptimisticLockHandle optimisticLockHandle)
      throws OptimisticLockException {
    NodeConfigTransferObject nodeConfigTransferObject;

    this.checkOptimisticLock(optimisticLockHandle, false);

    nodeConfigTransferObject = new SimpleNodeConfigTransferObject();

    nodeConfigTransferObject.setName(this.name);

    for(PropertyDefConfig propertyDefConfig: this.mapPropertyDefConfig.values()) {
      nodeConfigTransferObject.setPropertyDefConfig(propertyDefConfig);
    }

    for(PluginDefConfig pluginDefConfig: this.mapPluginDefConfig.values()) {
      nodeConfigTransferObject.setPluginDefConfig(pluginDefConfig);
    }

    return nodeConfigTransferObject;
  }

  /**
   * Called by subclasses to extract the data from a {@link NodeConfigTransferObject} and set
   * them within the JpaNodeConfig.
   * <p>
   * Uses the indNew variable, but does not reset it. It is intended to be reset by
   * the subclass caller method, {@link MutableNodeConfig#setNodeConfigTransferObject}.
   * <p>
   * The reason for not directly implementing
   * MutableNodeConfig.setNodeConfigValueTransferObject is that subclasses can have
   * other tasks to perform.
   * <p>
   * If optimisticLockHandle is null, no optimistic lock is managed.
   * <p>
   * If optimisticLockHandle is not null, it must be locked
   * ({@link OptimisticLockHandle#isLocked}) and its state must correspond to the
   * state of the data it represents, otherwise {@link OptimisticLockException} is
   * thrown. The state of the OptimisticLockHandle is updated to the new revision of
   * the JpaNodeConfig.
   *
   * @param nodeConfigTransferObject NodeConfigTransferObject.
   * @param optimisticLockHandle OptimisticLockHandle. Can be null.
   * @throws OptimisticLockException Can be thrown only if optimisticLockHandle is
   *   not null. This is a RuntimeException that may be of interest to
   *   the caller.
   * @throws DuplicateNodeException When the new configuration data would introduce
   *   a duplicate {@link MutableNode} within the parent. This is a RuntimeException
   *   that may be of interest to the caller.
   */
  protected void extractNodeConfigTransferObject(NodeConfigTransferObject nodeConfigTransferObject, OptimisticLockHandle optimisticLockHandle)
      throws OptimisticLockException, DuplicateNodeException {
    String previousName;
    EntityManager entityManager;

    this.checkOptimisticLock(optimisticLockHandle, !this.indNew);

    if ((nodeConfigTransferObject.getName() == null) && (this.jpaClassificationNodeConfigParent != null)) {
      throw new RuntimeException("Name of NodeConfigTrnmsferObject must not be null for non-root JpaClassificationNodeConfig.");
    }

    if ((nodeConfigTransferObject.getName() != null) && (this.jpaClassificationNodeConfigParent == null)) {
      throw new RuntimeException("Name of NodeConfigTrnmsferObject must be null for root JpaClassificationNodeConfig.");
    }

    previousName = this.name;
    this.name = nodeConfigTransferObject.getName();

    entityManager = this.entityManagerFactory.createEntityManager();

    try {
      entityManager.getTransaction().begin();

      this.mapPropertyDefConfig.clear();

      for(PropertyDefConfig propertyDefConfig: nodeConfigTransferObject.getListPropertyDefConfig()) {
        this.mapPropertyDefConfig.put(propertyDefConfig.getName(),  propertyDefConfig);
      }

      this.mapPluginDefConfig.clear();

      // Since JPA does not seem to support null properties in Map keys (PluginKey),
      // we need to go though a simple List for the persistence layer.
      if (this.listPluginDefConfig == null) {
        this.listPluginDefConfig = new ArrayList<PluginDefConfig>();
      } else {
        this.listPluginDefConfig.clear();
      }

      for(PluginDefConfig pluginDefConfig: nodeConfigTransferObject.getListPluginDefConfig()) {
        this.mapPluginDefConfig.put(new PluginKey(pluginDefConfig.getClassNodePlugin(), pluginDefConfig.getPluginId()), pluginDefConfig);
        this.listPluginDefConfig.add(pluginDefConfig);
      }

      this.timestampLastMod = new Timestamp(System.currentTimeMillis());;

      if (this.indNew) {
        JpaNodeConfig jpaNodeConfig;

        try {
//          if (this.jpaClassificationNodeConfigParent != null) {
//            entityManager.merge(this.jpaClassificationNodeConfigParent);
//          }

          jpaNodeConfig = entityManager.merge(this);
          this.id = jpaNodeConfig.id;
          entityManager.getTransaction().commit();
        } catch (EntityExistsException eee) {
          throw new DuplicateNodeException();
        }

        if (this.jpaClassificationNodeConfigParent != null) {
          this.jpaClassificationNodeConfigParent.setJpaNodeConfigChild(this);
        }
      } else {
        try {
          entityManager.merge(this);
          entityManager.getTransaction().commit();
        } catch (EntityExistsException eee) {
          this.name = previousName;
          throw new DuplicateNodeException();
        }

        if ((this.jpaClassificationNodeConfigParent != null) && (!this.name.equals(previousName))) {
          this.jpaClassificationNodeConfigParent.renameJpaNodeConfigChild(previousName, this.name);
        }
      }
    } finally {
      entityManager.close();
    }

    if (optimisticLockHandle != null) {
      ((JpaOptimisticLockHandle)optimisticLockHandle).setTimestampLastMod(this.timestampLastMod);
    }
  }

  @Override
  public void delete() {
    if (!this.indNew) {
      JpaNodeConfig jpaNodeConfig;

      EntityManager entityManager;

      entityManager = this.entityManagerFactory.createEntityManager();

      try {
        jpaNodeConfig = entityManager.find(JpaNodeConfig.class, this.id);
        entityManager.getTransaction().begin();
        entityManager.remove(jpaNodeConfig);
        entityManager.getTransaction().commit();
      } finally {
        entityManager.close();
      }

      if (this.jpaClassificationNodeConfigParent != null) {
        this.jpaClassificationNodeConfigParent.removeChildNodeConfig(this.name);
        this.jpaClassificationNodeConfigParent = null;
      }
    }
  }
}
