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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.azyva.dragom.model.config.ClassificationNodeConfig;
import org.azyva.dragom.model.config.Config;
import org.azyva.dragom.model.config.DuplicateNodeException;
import org.azyva.dragom.model.config.MutableClassificationNodeConfig;
import org.azyva.dragom.model.config.MutableModuleConfig;
import org.azyva.dragom.model.config.NodeConfig;
import org.azyva.dragom.model.config.NodeConfigTransferObject;
import org.azyva.dragom.model.config.NodeType;
import org.azyva.dragom.model.config.OptimisticLockException;
import org.azyva.dragom.model.config.OptimisticLockHandle;

/**
 * JPA implementation for {@link ClassificationNodeConfig}.
 *
 * @author David Raymond
 * @see org.azyva.dragom.model.config.impl.jpa
 */
public class JpaClassificationNodeConfig extends JpaNodeConfig implements ClassificationNodeConfig, MutableClassificationNodeConfig {
  /**
   * Containing JpaConfig. null if this JpaClassificationNodeConfig is not
   * the root JpaClassificationNodeConfig.
   */
  private JpaConfig jpaConfig;

  /**
   * Map of child {@link NodeConfig}.
   */
  private Map<String, JpaNodeConfig> mapJpaNodeConfigChild;

  /**
   * Constructor for root ClassificationNodeConfig.
   *
   * @param nodeData NodeData. null for new JpaClassificationNodeConfig.
   * @param jpaConfig JpaConfig holding this root ClassificationNodeConfig.
   */
  JpaClassificationNodeConfig(JpaConfig jpaConfig, NodeData nodeData) {
    super(jpaConfig.getEntityManagerFactory(), nodeData);

    this.jpaConfig = jpaConfig;
  }

  /**
   * Constructor for non-root ClassificationNodeConfig.
   *
   * @param nodeData NodeData. null for new JpaClassificationNodeConfig.
   * @param jpaClassificationNodeConfigParent Parent JpaClassificationNodeConfig.
   */
  public JpaClassificationNodeConfig(JpaClassificationNodeConfig jpaClassificationNodeConfigParent, NodeData nodeData) {
    super(jpaClassificationNodeConfigParent, nodeData);
  }

  @Override
  public NodeType getNodeType() {
    return NodeType.CLASSIFICATION;
  }

  @SuppressWarnings("unchecked")
  private void ensureCreateChildNodeConfig() {
    if (this.mapJpaNodeConfigChild == null) {
      EntityManager entityManager;
      Query query;
      List<NodeData> listNodeData;

      entityManager = this.entityManagerFactory.createEntityManager();

      query = entityManager.createNamedQuery("getChildNodeData");
      query.setParameter("parentNodeData", this.nodeData);

      listNodeData = query.getResultList();

      this.mapJpaNodeConfigChild = new HashMap<String, JpaNodeConfig>();

      for(NodeData nodeData: listNodeData) {
        if (nodeData.getType() == 'C') {
          this.mapJpaNodeConfigChild.put(nodeData.getName(), new JpaClassificationNodeConfig(this, nodeData));
        } else {
          this.mapJpaNodeConfigChild.put(nodeData.getName(), new JpaModuleConfig(this, nodeData));
        }
      }
    }
  }

  @Override
  public synchronized List<NodeConfig> getListChildNodeConfig() {
    this.ensureCreateChildNodeConfig();

    // A copy is returned to prevent the internal Map from being modified by the
    // caller. Ideally, an unmodifiable List view of the Collection returned by
    // Map.values should be returned, but that does not seem possible.
    return new ArrayList<NodeConfig>(this.mapJpaNodeConfigChild.values());
  }

  @Override
  public synchronized NodeConfig getNodeConfigChild(String name) {
    this.ensureCreateChildNodeConfig();

    return this.mapJpaNodeConfigChild.get(name);
  }

  @Override
  public synchronized void setNodeConfigTransferObject(NodeConfigTransferObject NodeConfigTransferObject, OptimisticLockHandle optimisticLockHandle) throws OptimisticLockException, DuplicateNodeException {
    boolean indNew;

    // Must check before calling extractNodeConfigTransferObject since the latter sets
    // nodeData.
    indNew = (this.nodeData == null);

    this.extractNodeConfigTransferObject(NodeConfigTransferObject, optimisticLockHandle);

    if (indNew) {
      if (this.jpaConfig != null) {
        this.jpaConfig.setJpaClassificationNodeConfigRoot(this);
      }
    }
  }

  /**
   * Sets a child {@link NodeConfig}.
   * <p>
   * This method is called by
   * {@link JpaNodeConfig#extractNodeConfigTransferObject}.
   *
   * @param jpaNodeConfigChild Child jpaNodeConfig.
   * @throws DuplicateNodeException When a JpaNodeConfig already exists with the
   *   same name.
   */
  synchronized void setJpaNodeConfigChild(JpaNodeConfig jpaNodeConfigChild) throws DuplicateNodeException {
    // No need to ensure the child NodeConfig's are loaded since it was done in the
    // create method.
    if (this.mapJpaNodeConfigChild.containsKey(jpaNodeConfigChild.getName())) {
      throw new DuplicateNodeException();
    }

    this.mapJpaNodeConfigChild.put(jpaNodeConfigChild.getName(), jpaNodeConfigChild);
  }

  /**
   * Renames a child {@link NodeConfig}.
   * <p>
   * This method is called by
   * {@link JpaNodeConfig#extractNodeConfigTransferObject}.
   *
   * @param currentName Current name.
   * @param newName New name.
   * @throws DuplicateNodeException When a JpaNodeConfig already exists with the
   *   same name.
   */
  synchronized void renameJpaNodeConfigChild(String currentName, String newName) throws DuplicateNodeException {
    if (!this.mapJpaNodeConfigChild.containsKey(currentName)) {
      throw new RuntimeException("JpaNodeConfig with current name " + currentName + " not found.");
    }

    if (this.mapJpaNodeConfigChild.containsKey(newName)) {
      throw new DuplicateNodeException();
    }

    this.mapJpaNodeConfigChild.put(newName, this.mapJpaNodeConfigChild.remove(currentName));
  }

  /**
   * Removes a child {@link NodeConfig}.
   * <p>
   * This method is called by {@link JpaNodeConfig#delete}.
   *
   * @param childNodeName Name of the child NodeConig.
   */
  synchronized void removeChildNodeConfig(String childNodeName) {
    if (this.mapJpaNodeConfigChild.remove(childNodeName) == null) {
      throw new RuntimeException("JpaNodeConfig with name " + childNodeName + " not found.");
    }
  }

  /**
   * We need to override delete which is already defined in {@link JpaNodeConfig}
   * since only a JpaClassificationNodeConfig can be a root ClassificationNodeConfig
   * within a {@link Config}.
   */
  @Override
  public synchronized void delete() {
    super.delete();

    if (this.jpaConfig != null) {
      this.jpaConfig.setJpaClassificationNodeConfigRoot(null);
      this.jpaConfig = null;
    }
  }

  @Override
  public MutableClassificationNodeConfig createChildMutableClassificationNodeConfig() {
    // We ensure the child NodeConfig are loaded before since otherwise it causes a
    // conflict when the new child NodeConfig is finalized.
    this.ensureCreateChildNodeConfig();

    return new JpaClassificationNodeConfig(this, null);
  }

  @Override
  public MutableModuleConfig createChildMutableModuleConfig() {
    // We ensure the child NodeConfig are loaded before since otherwise it causes a
    // conflict when the new child NodeConfig is finalized.
    this.ensureCreateChildNodeConfig();

    return new JpaModuleConfig(this, null);
  }
}
