/*
 * Copyright 2015 - 2017 AZYVA INC.
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

package org.azyva.dragom.model.support.jpa;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.azyva.dragom.execcontext.plugin.CredentialStorePlugin;
import org.azyva.dragom.model.Model;
import org.azyva.dragom.model.ModelFactory;
import org.azyva.dragom.model.config.impl.jpa.JpaConfig;
import org.azyva.dragom.model.config.impl.xml.XmlConfig;
import org.azyva.dragom.model.impl.DefaultModel;
import org.azyva.dragom.util.Util;

/**
 * {@link ModelFactory} implementation that loads a {@link Model} from
 * configuration stored in a DB using {@link JpaConfig}. {@link DefaultModel} is
 * used as the Model implementation.
 * <p>
 * A static Map of ??? to Model instances is used in order to reuse Model
 * instances. This is useful in case a single JVM instance is used for multiple
 * tool executions.
 * <p>
 * The following initialization properties are used:
 * <ul>
 * <li>PERSISTENCE_UNIT: JPA persistence unit
 * <li>JDBC_URL: JDBC URL of the DB
 * <li>DB_USER: DB user
 * <li>DB_PASSWORD: DB password
 * </ul>
 * If the user or password are not provided, they are obtained from the
 * {@link CredentialStorePlugin}.
 *
 * @author David Raymond
 */
public class JpaModelFactory implements ModelFactory {
  /**
   * Initialization property specifying the model URL.
   *
   * <p>As a convenience, this can also be a file path, relative or absolute.
   */
  private static final String INIT_PROP_URL_MODEL = "PERSISTENCE_UNIT";

  /**
   * Map of URLs (of {@link XmlConfig} XML configuration) to Model.
   */
  private static Map<URL, Model> mapUrlXmlConfigModel = new HashMap<URL, Model>();

  /**
   * Initialization property indicating to ignore any cached Model and instantiate a
   * new one, essentially causing a reload of the {@link XmlConfig}.
   */
  private static final String INIT_PROPERTY_IND_IGNORE_CACHED_MODEL = "IND_IGNORE_CACHED_MODEL";

  @Override
  public Model getModel(Properties propertiesInit) {
    String stringUrlXmlConfig;
    boolean indIgnoreCachedModel;
    Model model;

    stringUrlXmlConfig = propertiesInit.getProperty(JpaModelFactory.INIT_PROP_URL_MODEL);

    if (stringUrlXmlConfig == null) {
      throw new RuntimeException("Initialization property " + JpaModelFactory.INIT_PROP_URL_MODEL + " is not defined.");
    }

    indIgnoreCachedModel = Util.isNotNullAndTrue(propertiesInit.getProperty(JpaModelFactory.INIT_PROPERTY_IND_IGNORE_CACHED_MODEL));

    if (indIgnoreCachedModel) {
      JpaModelFactory.mapUrlXmlConfigModel.remove(stringUrlXmlConfig);
    }

    model = JpaModelFactory.mapUrlXmlConfigModel.get(stringUrlXmlConfig);

    if (model == null) {
      URL urlXmlConfig;
      XmlConfig xmlConfig;

      try {
        urlXmlConfig = new URL(stringUrlXmlConfig);
      } catch (MalformedURLException mue1) {
        try {
          urlXmlConfig = Paths.get(stringUrlXmlConfig).toUri().toURL();
        } catch (MalformedURLException mue2) {
            throw new RuntimeException(mue1);
        }
      }

      xmlConfig = XmlConfig.load(urlXmlConfig);
      model = new DefaultModel(xmlConfig, propertiesInit);

      JpaModelFactory.mapUrlXmlConfigModel.put(urlXmlConfig, model);
    }

    return model;
  }
}
