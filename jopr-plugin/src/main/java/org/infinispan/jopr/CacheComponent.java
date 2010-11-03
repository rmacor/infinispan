/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.jopr;

import org.infinispan.lifecycle.ComponentStatus;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;
import org.mc4j.ems.connection.EmsConnection;
import org.mc4j.ems.connection.bean.EmsBean;
import org.mc4j.ems.connection.bean.attribute.EmsAttribute;
import org.mc4j.ems.connection.bean.operation.EmsOperation;
import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.domain.measurement.AvailabilityType;
import org.rhq.core.domain.measurement.DataType;
import org.rhq.core.domain.measurement.MeasurementDataNumeric;
import org.rhq.core.domain.measurement.MeasurementDataTrait;
import org.rhq.core.domain.measurement.MeasurementReport;
import org.rhq.core.domain.measurement.MeasurementScheduleRequest;
import org.rhq.core.pluginapi.inventory.ResourceComponent;
import org.rhq.core.pluginapi.inventory.ResourceContext;
import org.rhq.core.pluginapi.measurement.MeasurementFacet;
import org.rhq.core.pluginapi.operation.OperationFacet;
import org.rhq.core.pluginapi.operation.OperationResult;
import org.rhq.plugins.jmx.MBeanResourceComponent;
import org.rhq.plugins.jmx.ObjectNameQueryUtility;

import javax.management.ObjectName;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Component class for Caches within Infinispan
 *
 * @author Heiko W. Rupp
 * @author Galder Zamarreño
 */
public class CacheComponent extends MBeanResourceComponent<CacheManagerComponent> {
   private static final Log log = LogFactory.getLog(CacheComponent.class);

   private ResourceContext<CacheManagerComponent> context;
   private String cacheManagerName;
   private String cacheName;

   /**
    * Return availability of this resource
    *
    * @see org.rhq.core.pluginapi.inventory.ResourceComponent#getAvailability()
    */
   public AvailabilityType getAvailability() {
      boolean trace = log.isTraceEnabled();
      EmsConnection conn = getConnection();
      try {
         conn.refresh();
         EmsBean bean = queryCacheBean();
         if (bean != null && bean.getAttribute("CacheStatus").getValue().equals(ComponentStatus.RUNNING.toString())) {
            bean.refreshAttributes();
            if (trace) log.trace("Cache {0} within {1} cache manager is running and attributes could be refreshed, so it's up.", cacheName, cacheManagerName);
            return AvailabilityType.UP;
         }
         if (trace) log.trace("Cache status for {0} within {1} cache manager is anything other than running, so it's down.", cacheName, cacheManagerName);
         return AvailabilityType.DOWN;
      } catch (Exception e) {
         if (trace) log.trace("There was an exception checking availability, so cache status is down.");
         return AvailabilityType.DOWN;
      }
   }

   /**
    * Start the resource connection
    */
   public void start(ResourceContext<CacheManagerComponent> context) {
      if (log.isTraceEnabled()) log.trace("Start cache component");
      this.context = context;
      this.cacheManagerName = context.getParentResourceComponent().context.getResourceKey();
      this.cacheName = context.getResourceKey();
   }

   /**
    * Tear down the rescource connection
    *
    * @see org.rhq.core.pluginapi.inventory.ResourceComponent#stop()
    */
   public void stop() {
   }

   /**
    * Gather measurement data
    *
    * @see org.rhq.core.pluginapi.measurement.MeasurementFacet#getValues(org.rhq.core.domain.measurement.MeasurementReport,
    *      java.util.Set)
    */
   public void getValues(MeasurementReport report, Set<MeasurementScheduleRequest> metrics) {
      boolean trace = log.isTraceEnabled();
      if (trace) log.trace("Get values metrics");
      for (MeasurementScheduleRequest req : metrics) {
         if (trace) log.trace("Inspect metric {0}", req);
         String metric = req.getName();
         try {
            EmsBean bean = queryComponentBean(metric);
            if (bean != null) {
               if (trace) log.trace("Retrieved mbean with name {0}", bean.getBeanName());
               bean.refreshAttributes();
               String attName = metric.substring(metric.indexOf(".") + 1);
               EmsAttribute att = bean.getAttribute(attName);
               // Attribute values are of various data types
               if (att != null) {
                  Object o = att.getValue();
                  Class attrType = att.getTypeClass();
                  DataType type = req.getDataType();
                  if (type == DataType.MEASUREMENT) {
                     if (o != null) {
                        MeasurementDataNumeric res = constructMeasurementDataNumeric(attrType, o, req);
                        if (res != null) report.addData(res);
                     } else {
                        if (log.isDebugEnabled()) log.debug("Metric ({0}) has null value, do not add to report", req.getName());
                     }
                  } else if (type == DataType.TRAIT) {
                     String value = (String) o;
                     if (trace) log.trace("Metric ({0}) is trait with value {1}", req.getName(), value);
                     MeasurementDataTrait res = new MeasurementDataTrait(req, value);
                     report.addData(res);
                  }
               } else {
                  log.warn("Attribute {0} not found", attName);
               }
            }
         }
         catch (Exception e) {
            log.warn("getValues failed for " + metric + " : ", e);
         }
      }
   }

   /**
    * Invoke operations on the Cache MBean instance
    *
    * @param fullOpName       Name of the operation
    * @param parameters       Parameters of the Operation
    * @return OperationResult object if successful
    * @throws Exception       If operation was not successful
    */
   public OperationResult invokeOperation(String fullOpName, Configuration parameters) throws Exception {
      boolean trace = log.isTraceEnabled();
      EmsBean bean = queryComponentBean(fullOpName);
      String opName = fullOpName.substring(fullOpName.indexOf(".") + 1);
      EmsOperation ops = bean.getOperation(opName);
      Collection<PropertySimple> simples = parameters.getSimpleProperties().values();
      if (trace) log.trace("Parameters, as simple properties, are {0}", simples);
      Object[] realParams = new Object[simples.size()];
      int i = 0;
      for (PropertySimple property : simples) {
         // Since parameters are typed in UI, passing them as Strings is the only reasonable way of dealing with this
         realParams[i++] = property.getStringValue();
      }

      if (ops == null)
         throw new Exception("Operation " + fullOpName + " can't be found");
      
      Object result = ops.invoke(realParams);
      if (trace) log.trace("Returning operation result containing {0}", result.toString());
      return new OperationResult(result.toString());
   }

   private EmsConnection getConnection() {
      return context.getParentResourceComponent().getEmsConnection();
   }

   private MeasurementDataNumeric constructMeasurementDataNumeric(Class attrType, Object o, MeasurementScheduleRequest req) {
      boolean trace = log.isTraceEnabled();
      if (trace) log.trace("Metric ({0}) is measurement with value {1}", req.getName(), o);
      if (attrType.equals(Long.class) || attrType.equals(long.class)) {
         Long tmp = (Long) o;
         return new MeasurementDataNumeric(req, Double.valueOf(tmp));
      } else if (attrType.equals(Double.class) || attrType.equals(double.class)) {
         Double tmp = (Double) o;
         return new MeasurementDataNumeric(req, tmp);
      } else if (attrType.equals(Integer.class) || attrType.equals(int.class)) {
         Integer tmp = (Integer) o;
         return new MeasurementDataNumeric(req, Double.valueOf(tmp));
      } else if (attrType.equals(String.class)) {
         String tmp = (String) o;
         return new MeasurementDataNumeric(req, Double.valueOf(tmp));
      } 
      
      log.warn("Unknown {0} attribute type for {1}", attrType, o);
      return null;
   }

//   private EmsBean queryCacheBean(EmsConnection conn, String cacheManagerName, String cacheName) {
//      String pattern = getNamedCachePattern(cacheManagerName, cacheName);
//      if (log.isTraceEnabled()) log.trace("Pattern to query is {0}", pattern);
//      ObjectNameQueryUtility queryUtility = new ObjectNameQueryUtility(pattern);
//      // Assume that a single cache fulfills this pattern since
//      // the most normal thing is for the same domain to be
//      // used by all cache within the same VM
//      return conn.queryBeans(queryUtility.getTranslatedQuery()).get(0);
//   }

   private String getNamedCachePattern(String cacheManagerName, String cacheName) {
      return namedCacheComponentPattern(cacheManagerName, cacheName, "Cache") + ",*";
   }

   private String getSingleComponentPattern(String cacheManagerName, String cacheName, String componentName) {
      return namedCacheComponentPattern(cacheManagerName, cacheName, componentName) + ",*";
   }

   private String namedCacheComponentPattern(String cacheManagerName, String cacheName, String componentName) {
      return CacheDiscovery.cacheComponentPattern(cacheManagerName, componentName)
            + ",name=" + ObjectName.quote(cacheName);
   }

//   private EmsBean getComponentBean(String name) {
//      EmsConnection conn = getConnection();
//      String componentName = name.substring(0, name.indexOf("."));
//      String pattern = getSingleComponentPattern(cacheManagerName, cacheName, componentName);
//      if (log.isTraceEnabled()) log.trace("Pattern to query is {0}", pattern);
//      ObjectNameQueryUtility queryUtility = new ObjectNameQueryUtility(pattern);
//      EmsBean bean = conn.queryBeans(queryUtility.getTranslatedQuery()).get(0);
//      if (bean == null) {
//         if (log.isTraceEnabled()) log.trace("No mbean found with name {0}", pattern);
//      }
//      return bean;
//   }

   private EmsBean queryCacheBean() {
      return queryBean("Cache");
   }

   private EmsBean queryComponentBean(String name) {
      String componentName = name.substring(0, name.indexOf("."));
      return queryBean(componentName);
   }

   private EmsBean queryBean(String componentName) {
      EmsConnection conn = getConnection();
      String pattern = getSingleComponentPattern(cacheManagerName, cacheName, componentName);
      if (log.isTraceEnabled()) log.trace("Pattern to query is {0}", pattern);
      ObjectNameQueryUtility queryUtility = new ObjectNameQueryUtility(pattern);
      List<EmsBean> beans = conn.queryBeans(queryUtility.getTranslatedQuery());
      if (beans.size() > 1) {
         // If more than one are returned, most likely is due to duplicate domains which is not the general case
         log.warn("More than one bean returned from applying {0} pattern: {1}", pattern, beans);
      }
      EmsBean bean = beans.get(0);
      if (bean == null) {
         if (log.isTraceEnabled()) log.trace("No mbean found with name {0}", pattern);
      }
      return bean;
   }
}