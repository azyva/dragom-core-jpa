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

import org.azyva.dragom.model.config.PluginDefConfig;
import org.azyva.dragom.model.config.PluginKey;
import org.azyva.dragom.model.config.PropertyDefConfig;

class NodeData {
  /**
   * Id.
   */
  protected int id;

  /**
   * Parent NodeData.
   */
  private NodeData nodeDataParent;
//private List<NodeData> child;
  /**
   * Node type.
   *
   * <p>C for classification node, M for module.
   */
  private char type;

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
   * properties in map keys ({@link PluginKey}).
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
  protected NodeData() {
  }

  public NodeData(char type, NodeData nodeDataParent) {
    this.type = type;
    this.nodeDataParent = nodeDataParent;

    this.mapPropertyDefConfig = new HashMap<String, PropertyDefConfig>();
    this.mapPluginDefConfig = new HashMap<PluginKey, PluginDefConfig>();
  }

  private void postLoad() {
    this.mapPluginDefConfig = new HashMap<PluginKey, PluginDefConfig>();

    for(PluginDefConfig pluginDefConfig: this.listPluginDefConfig) {
      this.mapPluginDefConfig.put(new PluginKey(pluginDefConfig.getClassNodePlugin(), pluginDefConfig.getPluginId()), pluginDefConfig);
    }
  }

  private void preSave() {
    if (this.listPluginDefConfig == null) {
      this.listPluginDefConfig = new ArrayList<PluginDefConfig>();
    } else {
      this.listPluginDefConfig.clear();
    }

    for(PluginDefConfig pluginDefConfig: this.mapPluginDefConfig.values()) {
      this.listPluginDefConfig.add(pluginDefConfig);
    }
  }

  public int getId() {
    return this.id;
  }

  protected NodeData getNodeDataParent() {
    return this.nodeDataParent;
  }

  public char getType() {
    return this.type;
  }

  public String getName() {
    return this.name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Map<String, PropertyDefConfig> getMapPropertyDefConfig() {
    return this.mapPropertyDefConfig;
  }

  public Map<PluginKey, PluginDefConfig> getMapPluginDefConfig() {
    return this.mapPluginDefConfig;
  }

  public Timestamp getTimestampLastMod() {
    return this.timestampLastMod;
  }

  public void setTimestampLastMod(Timestamp timestampLastMod) {
    this.timestampLastMod = timestampLastMod;
  }
}
