package org.huihoo.ofbiz.smart.service.engine;



import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.huihoo.ofbiz.smart.base.C;
import org.huihoo.ofbiz.smart.base.util.CommUtil;
import org.huihoo.ofbiz.smart.base.util.Log;
import org.huihoo.ofbiz.smart.base.util.ServiceUtil;
import org.huihoo.ofbiz.smart.base.validation.ConstraintViolation;
import org.huihoo.ofbiz.smart.base.validation.ValidateProfile;
import org.huihoo.ofbiz.smart.base.validation.Validator;
import org.huihoo.ofbiz.smart.entity.Delegator;
import org.huihoo.ofbiz.smart.entity.EntityConverter;
import org.huihoo.ofbiz.smart.entity.GenericEntityException;
import org.huihoo.ofbiz.smart.service.GenericServiceException;
import org.huihoo.ofbiz.smart.service.ServiceDispatcher;
import org.huihoo.ofbiz.smart.service.ServiceModel;


public class EntityAutoEngine extends GenericAsyncEngine {
  private final static String TAG = EntityAutoEngine.class.getName();

  private final static Map<String, Class<?>> ENGITY_CLAZZ_MAP = new ConcurrentHashMap<>();

  public EntityAutoEngine(ServiceDispatcher serviceDispatcher) {
    super(serviceDispatcher);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, Object> runSync(String serviceName, Map<String, Object> ctx) throws GenericServiceException {
    if (CommUtil.isEmpty(serviceName)) {
      throw new GenericServiceException("The serviceName is empty.");
    }
    if (ctx == null) {
      throw new GenericServiceException("The service context is null.");
    }

    ServiceModel serviceModel = serviceDispatcher.getServiceContextMap().get(serviceName);
    if (serviceModel == null) {
      throw new GenericServiceException("Unable to locate the service [" + serviceName + "]");
    }

    if (CommUtil.isEmpty(serviceModel.invoke) || CommUtil.isEmpty(serviceModel.engineName)) {
      throw new GenericServiceException("The service [" + serviceName + "] has not been set invoke or engineName");
    }

    Delegator delegator = ServiceUtil.getDelegator(ctx);
    if (delegator == null) {
      throw new GenericServiceException("Service [" + serviceName + "] required to set Delegator.");
    }

    Class<?> entityClazz = ENGITY_CLAZZ_MAP.get(serviceModel.entityName);
    if (entityClazz == null) {
      try {
        entityClazz = Thread.currentThread().getContextClassLoader().loadClass(serviceModel.entityName);
        ENGITY_CLAZZ_MAP.put(serviceModel.entityName, entityClazz);
      } catch (ClassNotFoundException e) {
        throw new GenericServiceException("EngityClass [" + serviceModel.entityName + "] not found.");
      }
    }
    try {
      Map<String,Object> successResult = ServiceUtil.returnSuccess();
      String returnName = (String) ctx.get(C.ENTITY_RETURN_NAME);
      switch (serviceModel.invoke) {
        case C.SERVICE_ENGITYAUTO_CREATE:
          Object modelObj = EntityConverter.convertFrom(entityClazz, ctx, delegator);
          List<ConstraintViolation> constraintViolations =  Validator.validate(modelObj, ValidateProfile.CREATE);
          if (CommUtil.isNotEmpty(constraintViolations)) {
            return ServiceUtil.returnProplem("VALIDATION_NOT_PASSED", "Validation being unable to be passed.", 
                                                                                            constraintViolations);
          }
          delegator.save(modelObj);  
          if (returnName != null) {
            successResult.put(returnName, modelObj);
          } else {
            successResult.put(C.ENTITY_MODEL_NAME, modelObj);
          }
          break;
        case C.SERVICE_ENGITYAUTO_UPDATE:
          Object id = ctx.get(C.ENTITY_ID_NAME);
          if (CommUtil.isEmpty(id)) {
            return ServiceUtil.returnProplem("ENTITY_ID_REQUIRED","The entity id required.");
          }
          Object obj = delegator.findById(entityClazz, id);
          if (obj != null) {
            modelObj = EntityConverter.convertFrom(obj.getClass(), ctx, delegator);
            delegator.save(modelObj); 

            if (returnName != null) {
              successResult.put(returnName, modelObj);
            } else {
              successResult.put(C.ENTITY_MODEL_NAME, modelObj);
            }
          }
          break;
        case C.SERVICE_ENGITYAUTO_REMOVE:
          id = ctx.get(C.ENTITY_ID_NAME);
          if (CommUtil.isEmpty(id)) {
            return ServiceUtil.returnProplem("ENTITY_ID_REQUIRED","The entity id required.");
          }
          obj = delegator.findById(entityClazz, id);
          if (obj != null) {
            try {
              Object removedObj = obj;
              delegator.remove(obj);
              successResult.put(C.ENTITY_REMOVED_NAME, removedObj);
            } catch(GenericEntityException e) {
              if (e.getMessage() != null && e.getMessage().indexOf("CONSTRAINT") != -1) {
                return ServiceUtil.returnProplem("ENTITY_REFERENCED_CONSTRAINT", "The entity has referenced another entity.");
              }
            }
          }
          break;
        case C.SERVICE_ENGITYAUTO_FINDBYID:
          Boolean useCache = (Boolean) ctx.get(C.ENTITY_USE_CACHE);
          if (useCache == null) {
            useCache = Boolean.FALSE;
          }
          id = ctx.get(C.ENTITY_ID_NAME);
          if (CommUtil.isEmpty(id)) {
            return ServiceUtil.returnProplem("ENTITY_ID_REQUIRED","The entity id required.");
          }
          obj = delegator.findById(entityClazz, id,useCache);
          if (returnName != null) {
            successResult.put(returnName, obj);
          } else {
            successResult.put(C.ENTITY_MODEL_NAME, obj);
          }
          break;
        case C.SERVICE_ENGITYAUTO_FINDLISTBYAND:
        case C.SERVICE_ENGITYAUTO_FINDLISTBYCOND:
          useCache = (Boolean) ctx.get(C.ENTITY_USE_CACHE);
          String condition = (String) ctx.get(C.ENTITY_CONDTION);
          Map<String, Object> andMap = (Map<String, Object>) ctx.get(C.ENTITY_ANDMAP);
          Set<String> fieldsToSelect = (Set<String>) ctx.get(C.ENTITY_FIELDS_TO_SELECT);
          List<String> orderBy = (List<String>) ctx.get(C.ENTITY_ORDERBY);
          if (orderBy == null && entityClazz.getDeclaredField(C.ENTITY_UPDATED_AT) != null) {
            orderBy = Arrays.asList(new String[]{C.ENTITY_ORDERBY_DEFAULT_FIELD});
          }
          List<?> pList = null;
          if (CommUtil.isNotEmpty(condition)) {
            pList = delegator.findListByCond(entityClazz, condition, fieldsToSelect, orderBy, useCache);
          } else {
            pList = delegator.findListByAnd(entityClazz, andMap, fieldsToSelect, orderBy, useCache);
          }
          if (returnName != null) {
            successResult.put(returnName, pList);
          } else {
            successResult.put(C.ENTITY_MODEL_LIST, pList);
          }
          break;
        case C.SERVICE_ENGITYAUTO_FINDPAGEBYAND:
        case C.SERVICE_ENGITYAUTO_FINDPAGEBYCOND:
          useCache = (Boolean) ctx.get(C.ENTITY_USE_CACHE);
          if (useCache == null) {
            useCache = Boolean.FALSE;
          }
          condition = (String) ctx.get(C.ENTITY_CONDTION);
          andMap = (Map<String, Object>) ctx.get(C.ENTITY_ANDMAP);
          fieldsToSelect = (Set<String>) ctx.get(C.ENTITY_FIELDS_TO_SELECT);
          orderBy = (List<String>) ctx.get(C.ENTITY_ORDERBY);
          if (orderBy == null && entityClazz.getDeclaredField(C.ENTITY_UPDATED_AT) != null) {
            orderBy = Arrays.asList(new String[]{C.ENTITY_ORDERBY_DEFAULT_FIELD});
          }
          Integer pageNo = Integer.valueOf((ctx.get(C.PAGE_PAGE_NO) == null ? 1 : ctx.get(C.PAGE_PAGE_NO)) + "");
          Integer pageSize = Integer.valueOf((ctx.get(C.PAGE_PAGE_SIZE) == null ? 20 : ctx.get(C.PAGE_PAGE_SIZE)) + "");
          
          Map<String, Object> pMap = null;
          if (CommUtil.isNotEmpty(condition)) {
            pMap = delegator.findPageByCond(entityClazz, condition, pageNo, pageSize, fieldsToSelect, orderBy, useCache);
          } else {
            pMap = delegator.findPageByAnd(entityClazz, andMap, pageNo, pageSize, fieldsToSelect, orderBy, useCache);
          }
          if (CommUtil.isNotEmpty(returnName)) {
            successResult.put(returnName, pMap);
          } else {
            successResult.putAll(pMap);
          }
          break;
        default:
          //Ingore..
          break;
      }
      return successResult;
    } catch (Exception e) {
      Log.e(e, "EntityAutoEngine has an exception.", TAG);
      return ServiceUtil.returnProplem("ENTITY_AUTO_ENGINE_ERROR", e.getMessage());
    }
  }

  @Override
  public String getName() {
    return "entityAuto";
  }
  
  
  
  //=============================================================
  // Private Method
  //=============================================================
  
}
