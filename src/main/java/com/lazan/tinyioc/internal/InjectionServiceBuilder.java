package com.lazan.tinyioc.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import com.lazan.tinyioc.IocException;
import com.lazan.tinyioc.ServiceBuilder;
import com.lazan.tinyioc.ServiceBuilderContext;
import com.lazan.tinyioc.ServiceRegistry;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class InjectionServiceBuilder<T> implements ServiceBuilder<T> {
	static final Map<Class<?>, ContextValueSource> CONTEXT_VALUE_SOURCES = new LinkedHashMap<>();
	static {
		CONTEXT_VALUE_SOURCES.put(Map.class, new ContextValueSource<Map>() {
			@Override
			public Map getValue(ServiceBuilderContext context) {
				return context.getMappedContributions();
			}
		});
		CONTEXT_VALUE_SOURCES.put(List.class, new ContextValueSource<List>() {
			@Override
			public List getValue(ServiceBuilderContext context) {
				return context.getOrderedContributions();
			}
		});
		CONTEXT_VALUE_SOURCES.put(Collection.class, new ContextValueSource<Collection>() {
			@Override
			public Collection getValue(ServiceBuilderContext context) {
				return context.getUnorderedContributions();
			}
		});
	}
	
	private final Class<T> concreteType;
	
	public InjectionServiceBuilder(Class<T> concreteType) {
		super();
		this.concreteType = concreteType;
	}
	
	@Override
	public T build(ServiceBuilderContext context) {
		try {
			Constructor<T> constructor = findConstructor(concreteType);
			Object[] params = getParameters(constructor, context);
			T service = constructor.newInstance(params);
			injectFields(service, context);
			return service;
		} catch (IocException e) {
			throw e;
		} catch (Exception e) {
			throw new IocException(e, "Error building service '%s'", context.getServiceId());
		}
	}
	
	protected Object[] getParameters(Constructor<T> constructor, ServiceBuilderContext context) {
		Class[] paramTypes = constructor.getParameterTypes();
		if (paramTypes.length == 0) {
			return null;
		}
		Object[] params = new Object[paramTypes.length];
		for (int i = 0; i < paramTypes.length; ++i) {
			params[i] = getParameter(constructor, i, context);
		}
		return params;
	}

	protected Object getParameter(Constructor<T> constructor, int paramIndex, ServiceBuilderContext context) {
		Class<?> paramType = constructor.getParameterTypes()[paramIndex];
		Annotation[] annotations = constructor.getParameterAnnotations()[paramIndex];
		ServiceRegistry registry = context.getServiceRegistry();
		Named named = findAnnotation(annotations, Named.class);
		Object param;
		if (named != null) {
			param = registry.getService(named.value(), paramType);
		} else if (CONTEXT_VALUE_SOURCES.containsKey(paramType)) {
			param = CONTEXT_VALUE_SOURCES.get(paramType).getValue(context);
		} else {
			param = registry.getService(paramType);
		}
		return param;
	}

	protected <A extends Annotation> A findAnnotation(Annotation[] anns, Class<A> type) {
		for (Annotation ann : anns) {
			if (type.equals(ann.annotationType())) {
				return type.cast(ann);
			}
		}
		return null;
	}

	protected Constructor<T> findConstructor(Class<T> concreteType) {
		Constructor[] constructors = concreteType.getConstructors();
		if (constructors.length == 0) {
			throw new IocException("No public constructors found for type %s", concreteType.getName());
		}
		if (constructors.length == 1) {
			return constructors[0];
		}
		int injectCount = 0;
		Constructor selected = null;
		for (Constructor current : constructors) {
			if (current.getAnnotation(Inject.class) != null) {
				selected = current;
				injectCount ++;
			}
		}
		if (injectCount == 1) {
			return selected;
		}
		if (injectCount == 0) {
			throw new IocException("Found %s public constructors for type %s, please annotate one with javax.inject.Inject", constructors.length, concreteType.getName());
		}
		throw new IocException("Found %s public constructors annotated with javax.inject.Inject for type %s", injectCount, concreteType.getName());
	}
	
	static interface ContextValueSource<T> {
		T getValue(ServiceBuilderContext context);
	}
	
	protected void injectFields(T service, ServiceBuilderContext context) {
		Class<?> currentType = context.getServiceType();
		ServiceRegistry registry = context.getServiceRegistry();
		while (currentType != null) { 
			for (Field field : currentType.getDeclaredFields()) {
				if (field.getAnnotation(Inject.class) != null) {
					Named named = field.getAnnotation(Named.class);
					try {
						Object value;
						if (named != null) {
							value = registry.getService(named.value(), field.getType());
						} else if (CONTEXT_VALUE_SOURCES.containsKey(field.getType())) {
							value = CONTEXT_VALUE_SOURCES.get(field.getType()).getValue(context);
						} else {
							value = registry.getService(field.getType());
						}
						field.setAccessible(true);
						field.set(service, value);
					} catch (Exception e) {
						throw new IocException(e, "Error injecting field '%s' in serviceId '%s'", field.getName(), context.getServiceId());
					}
				}
			}
			currentType = currentType.getSuperclass();
		}
	}
}
