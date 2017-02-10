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
import java.util.List;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.azyva.dragom.model.MutableNode;
import org.azyva.dragom.model.config.DuplicateNodeException;
import org.azyva.dragom.model.config.MutableNodeConfig;
import org.azyva.dragom.model.config.NodeConfig;
import org.azyva.dragom.model.config.NodeConfigTransferObject;
import org.azyva.dragom.model.config.NodeType;
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
  protected EntityManagerFactory entityManagerFactory;

  /**
   * Associated NodeData. If null it means the JpaNodeConfig is new and has not been
   * finalized yet. This is the state in which it is after having been created using
   * the create methods of {@link JpaConfig} or
   * {@link JpaClassificationNodeConfig}.
   */
  NodeData nodeData;

  /**
   * Parent {@link JpaClassificationNodeConfig}.
   */
  private JpaClassificationNodeConfig jpaClassificationNodeConfigParent;

  /**
   * Constructor.
   *
   * @param nodeData NodeData. null for new JpaNodeConfig.
   * @param entityManagerFactory EntityManagerFactory.
   */
  protected JpaNodeConfig(EntityManagerFactory entityManagerFactory, NodeData nodeData) {
    this.entityManagerFactory = entityManagerFactory;

    this.nodeData = nodeData;
  }

  /**
   * Constructor.
   *
   * @param nodeData NodeData. null for new JpaNodeConfig.
   * @param jpaClassificationNodeConfigParent Parent JpaClassificationNodeConfig.
   */
  JpaNodeConfig(JpaClassificationNodeConfig jpaClassificationNodeConfigParent, NodeData nodeData) {
    this(jpaClassificationNodeConfigParent.getEntityManagerFactory(), nodeData);

    this.jpaClassificationNodeConfigParent = jpaClassificationNodeConfigParent;
  }

  EntityManagerFactory getEntityManagerFactory() {
    return this.entityManagerFactory;
  }

  protected JpaClassificationNodeConfig getJpaClassificationNodeConfigParent() {
    return this.jpaClassificationNodeConfigParent;
  }

  @Override
  public String getName() {
    return this.nodeData.getName();
  }

  @Override
  public synchronized PropertyDefConfig getPropertyDefConfig(String name) {
    return this.nodeData.getMapPropertyDefConfig().get(name);
  }

  @Override
  public synchronized boolean isPropertyExists(String name) {
    return this.nodeData.getMapPropertyDefConfig().containsKey(name);
  }

  @Override
  public synchronized List<PropertyDefConfig> getListPropertyDefConfig() {
    // A copy is returned to prevent the internal Map from being modified by the
    // caller. Ideally, an unmodifiable List view of the Collection returned by
    // Map.values should be returned, but that does not seem possible.
    return new ArrayList<PropertyDefConfig>(this.nodeData.getMapPropertyDefConfig().values());
  }

  @Override
  public synchronized PluginDefConfig getPluginDefConfig(Class<? extends NodePlugin> classNodePlugin, String pluginId) {
    return this.nodeData.getMapPluginDefConfig().get(new PluginKey(classNodePlugin, pluginId));
  }

  @Override
  public synchronized boolean isPluginDefConfigExists(Class<? extends NodePlugin> classNodePlugin, String pluginId) {
    return this.nodeData.getMapPluginDefConfig().containsKey(new PluginKey(classNodePlugin, pluginId));
  }

  @Override
  public synchronized List<PluginDefConfig> getListPluginDefConfig() {
    // A copy is returned to prevent the internal Map from being modified by the
    // caller. Ideally, an unmodifiable List view of the Collection returned by
    // Map.values should be returned, but that does not seem possible.
    return new ArrayList<PluginDefConfig>(this.nodeData.getMapPluginDefConfig().values());
  }

  @Override
  public boolean isNew() {
    return this.nodeData == null;
  }

  private enum OptimisticLockCheckContext {
    /**
     * Getting a {@link NodeConfigTransferObject} on an existing JpaNodeConfig.
     */
    GET,

    /**
     * Getting or setting a {@link NodeConfigTransferObject} on a new
     * JpaNodeConfig.
     */
    NEW,

    /**
     * Updating an existing JpaNodeConfig by setting a
     * {@link NodeConfigTransferObject}.
     */
    UPDATE
  }

  /**
   * Check whether the {@link JpaOptimisticLockHandle} corresponds to the current
   * state of the data it represents.
   * <p>
   * If jpaOptimisticLockHandle is null, nothing is done.
   * <p>
   * If optimisticLockCheckContext is NEW, jpaOptimisticLockHandle must not be
   * locked.
   * <p>
   * If optimisticLockCheckContext is UPDATE, jpaOptimisticLockHandle must be
   * locked.
   * <p>
   * If jpaOptimisticLockHandle is not null and is locked
   * ({@link JpaOptimisticLockHandle#isLocked}), its state must correspond to the
   * state of the data it represents, otherwise {@link OptimisticLockException} is
   * thrown.
   * <p>
   * If jpaOptimisticLockHandle is not null and is not locked, it is simply locked
   * to the current state of the data.
   *
   * @param jpaOptimisticLockHandle JpaOptimisticLockHandle. Can be null.
   * @param optimisticLockCheckContext OptimisticLockCheckContext.
   */
  protected void checkOptimisticLock(JpaOptimisticLockHandle jpaOptimisticLockHandle, OptimisticLockCheckContext optimisticLockCheckContext) {
    if (jpaOptimisticLockHandle != null) {
      if (jpaOptimisticLockHandle.isLocked()) {
        if (optimisticLockCheckContext == OptimisticLockCheckContext.NEW) {
          throw new RuntimeException("OptimisticLockHandle must not be locked for a new JpaNodeConfig.");
        }

        if (!jpaOptimisticLockHandle.getTimestampLastMod().equals(this.nodeData.getTimestampLastMod())) {
          throw new OptimisticLockException();
        }
      } else {
        if (optimisticLockCheckContext == OptimisticLockCheckContext.UPDATE) {
          throw new RuntimeException("OptimisticLockHandle must be locked for an existing JpaNodeConfig.");
        }

        jpaOptimisticLockHandle.setTimestampLastMod(this.nodeData.getTimestampLastMod());
      }
    }
  }

  @Override
  public OptimisticLockHandle createOptimisticLockHandle(boolean indLock) {
    return new JpaOptimisticLockHandle(indLock ? this.nodeData.getTimestampLastMod() : null);
  }

  @Override
  public boolean isOptimisticLockValid(OptimisticLockHandle optimisticLockHandle) {
    return (((JpaOptimisticLockHandle)optimisticLockHandle).getTimestampLastMod().equals(this.nodeData.getTimestampLastMod()));
  }

  @Override
  public synchronized NodeConfigTransferObject getNodeConfigTransferObject(OptimisticLockHandle optimisticLockHandle)
      throws OptimisticLockException {
    NodeConfigTransferObject nodeConfigTransferObject;

    this.checkOptimisticLock((JpaOptimisticLockHandle)optimisticLockHandle, this.nodeData == null ? OptimisticLockCheckContext.NEW : OptimisticLockCheckContext.GET);

    nodeConfigTransferObject = new SimpleNodeConfigTransferObject();

    if (this.nodeData != null) {

      nodeConfigTransferObject.setName(this.nodeData.getName());

      for(PropertyDefConfig propertyDefConfig: this.nodeData.getMapPropertyDefConfig().values()) {
        nodeConfigTransferObject.setPropertyDefConfig(propertyDefConfig);
      }

      for(PluginDefConfig pluginDefConfig: this.nodeData.getMapPluginDefConfig().values()) {
        nodeConfigTransferObject.setPluginDefConfig(pluginDefConfig);
      }
    }

    return nodeConfigTransferObject;
  }

  /**
   * Called by subclasses to extract the data from a {@link NodeConfigTransferObject} and set
   * them within the JpaNodeConfig.
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
    boolean indNew;
    String previousName = null;
    EntityManager entityManager;

    this.checkOptimisticLock((JpaOptimisticLockHandle)optimisticLockHandle, this.nodeData == null ? OptimisticLockCheckContext.NEW : OptimisticLockCheckContext.UPDATE);

    if ((nodeConfigTransferObject.getName() == null) && (this.jpaClassificationNodeConfigParent != null)) {
      throw new RuntimeException("Name of NodeConfigTrnmsferObject must not be null for non-root JpaClassificationNodeConfig.");
    }

    if ((nodeConfigTransferObject.getName() != null) && (this.jpaClassificationNodeConfigParent == null)) {
      throw new RuntimeException("Name of NodeConfigTrnmsferObject must be null for root JpaClassificationNodeConfig.");
    }

    indNew = (this.nodeData == null);

    if (indNew) {
      this.nodeData =
          new NodeData(
              this.getNodeType() == NodeType.CLASSIFICATION ? 'C' : 'M',
              this.getJpaClassificationNodeConfigParent() == null ? null : this.getJpaClassificationNodeConfigParent().nodeData);
    } else {
      previousName = this.nodeData.getName();

      this.nodeData.getMapPropertyDefConfig().clear();
      this.nodeData.getMapPluginDefConfig().clear();
    }

    this.nodeData.setName(nodeConfigTransferObject.getName());

    entityManager = this.entityManagerFactory.createEntityManager();

    try {
      for(PropertyDefConfig propertyDefConfig: nodeConfigTransferObject.getListPropertyDefConfig()) {
        this.nodeData.getMapPropertyDefConfig().put(propertyDefConfig.getName(),  propertyDefConfig);
      }

      for(PluginDefConfig pluginDefConfig: nodeConfigTransferObject.getListPluginDefConfig()) {
        this.nodeData.getMapPluginDefConfig().put(new PluginKey(pluginDefConfig.getClassNodePlugin(), pluginDefConfig.getPluginId()), pluginDefConfig);
      }

      this.nodeData.setTimestampLastMod(new Timestamp(System.currentTimeMillis()));

      try {
        entityManager.getTransaction().begin();

        if (indNew) {
          entityManager.persist(this.nodeData);
        } else {
          this.nodeData = entityManager.merge(this.nodeData);
        }

        entityManager.getTransaction().commit();
      } catch (EntityExistsException eee) {
        throw new DuplicateNodeException();
      }


      if (indNew) {
        if (this.jpaClassificationNodeConfigParent != null) {
          this.jpaClassificationNodeConfigParent.setJpaNodeConfigChild(this);
        }
      } else {
        if ((this.jpaClassificationNodeConfigParent != null) && (!this.nodeData.getName().equals(previousName))) {
          this.jpaClassificationNodeConfigParent.renameJpaNodeConfigChild(previousName, this.nodeData.getName());
        }
      }
    } finally {
      entityManager.close();
    }

    if (optimisticLockHandle != null) {
      ((JpaOptimisticLockHandle)optimisticLockHandle).setTimestampLastMod(this.nodeData.getTimestampLastMod());
    }
  }

  @Override
  public void delete() {
    if (this.nodeData != null) {
      EntityManager entityManager;
      NodeData nodeData;

      entityManager = this.entityManagerFactory.createEntityManager();

      try {
        nodeData = entityManager.find(NodeData.class, this.nodeData.getId());
        entityManager.getTransaction().begin();
        entityManager.remove(nodeData);
        entityManager.getTransaction().commit();
      } finally {
        entityManager.close();
      }

      if (this.jpaClassificationNodeConfigParent != null) {
        this.jpaClassificationNodeConfigParent.removeChildNodeConfig(this.nodeData.getName());
        this.jpaClassificationNodeConfigParent = null;
      }
    }
  }
}
