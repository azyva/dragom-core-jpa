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

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import javax.persistence.Query;

import org.azyva.dragom.model.config.ClassificationNodeConfig;
import org.azyva.dragom.model.config.Config;
import org.azyva.dragom.model.config.MutableClassificationNodeConfig;
import org.azyva.dragom.model.config.MutableConfig;


/**
 * JPA implementation of {@link Config} and {@link MutableConfig}.
 *
 * <p>Note that because of the fact that we need to comply with the Dragom
 * {@link MutableConfig} API, the pattern used for accessing the DB with JPA does
 * not always follow the typical DAO pattern. Also, we sometimes have to fight JPA
 * to make it do what we want since the objects returned to the caller must remain
 * valid even if detached from the EntityManager.
 *
 * @author David Raymond
 * @see org.azyva.dragom.model.config.impl.jpa
 */
public class JpaConfig implements Config, MutableConfig {
  private EntityManagerFactory entityManagerFactory;

  /**
   * Root JpaClassificationNodeConfig.
   */
  private JpaClassificationNodeConfig jpaClassificationNodeConfigRoot;

  public JpaConfig(EntityManagerFactory entityManagerFactory) {
    this.entityManagerFactory = entityManagerFactory;
  }

  EntityManagerFactory getEntityManagerFactory() {
    return this.entityManagerFactory;
  }

  @Override
  public ClassificationNodeConfig getClassificationNodeConfigRoot() {
    EntityManager entityManager;
    Query query;
    JpaClassificationNodeConfig jpaClassificationNodeConfig;

    if (this.jpaClassificationNodeConfigRoot != null) {
      return this.jpaClassificationNodeConfigRoot;
    }

    entityManager = this.entityManagerFactory.createEntityManager();

    try {
      query = entityManager.createNamedQuery("getJpaClassificationNodeConfigRoot");

      this.jpaClassificationNodeConfigRoot = (JpaClassificationNodeConfig)query.getSingleResult();
    } catch (NoResultException nre) {
    } finally {
      entityManager.close();
    }

    if (this.jpaClassificationNodeConfigRoot != null) {
      // We cannot do this in a postLoad JPA event method since the root
      // JpaClassiicationNodeConfig needs to refer to its JpaConfig, which is not
      // available, other than here.
      this.jpaClassificationNodeConfigRoot.initRootAfterLoad(this);
    }

    return this.jpaClassificationNodeConfigRoot;
  }

  /**
   * Sets the root {@link JpaClassificationNodeConfig}.
   * <p>
   * This method is intended to be called by
   * {@link JpaNodeConfig#setNodeConfigTransferObject}.
   *
   * @param jpaClassificationNodeConfigRoot Root JpaClassificationNodeConfig.
   */
  void setJpaClassificationNodeConfigRoot(JpaClassificationNodeConfig jpaClassificationNodeConfigRoot) {
    if (this.jpaClassificationNodeConfigRoot != null && jpaClassificationNodeConfigRoot != null) {
      throw new RuntimeException("Replacing the root JpaClassificationNodeConfig is not allowed.");
    }

    // Setting this.simplClassificationNodeRoot to null is allowed since this
    // can happen when deleting the root JpaClassificationNode.
    this.jpaClassificationNodeConfigRoot = jpaClassificationNodeConfigRoot;
  }

  @Override
  public MutableClassificationNodeConfig createMutableClassificationNodeConfigRoot() {
    return new JpaClassificationNodeConfig(this);
  }
}
