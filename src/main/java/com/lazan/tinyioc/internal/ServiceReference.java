package com.lazan.tinyioc.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lazan.tinyioc.IocException;
import com.lazan.tinyioc.ServiceBuilder;
import com.lazan.tinyioc.ServiceBuilderContext;
import com.lazan.tinyioc.ServiceDecorator;

public class ServiceReference<T> {
	public class ServiceDependencies {
		private final Class<T> serviceType;
		private final ServiceBuilder<T> builder;
		private final ServiceDecorator<T> decorator;
		private final ContributionType contributionType;
		private final Class<?> contributionKeyType;
		private final Class<?> contributionValueType;
		private final List<UnorderedContributionOptionsImpl> unorderedContributions;
		private final List<OrderedContributionOptionsImpl> orderedContributions;
		private final List<MappedContributionOptionsImpl> mappedContributions;
		public ServiceDependencies(Class<T> serviceType, ServiceBuilder<T> builder, ServiceDecorator<T> decorator,
				ContributionType contributionType, Class<?> contributionKeyType, Class<?> contributionValueType,
				List<UnorderedContributionOptionsImpl> unorderedContributions,
				List<OrderedContributionOptionsImpl> orderedContributions,
				List<MappedContributionOptionsImpl> mappedContributions) {
			super();
			this.serviceType = serviceType;
			this.builder = builder;
			this.decorator = decorator;
			this.contributionType = contributionType;
			this.contributionKeyType = contributionKeyType;
			this.contributionValueType = contributionValueType;
			this.unorderedContributions = unorderedContributions;
			this.orderedContributions = orderedContributions == null ? null : new LinkedList<>(orderedContributions);
			this.mappedContributions = mappedContributions;
		}
	}
	
	private final String serviceId;
	private ServiceDependencies dependencies;
	private volatile Object service;
	
	public ServiceReference(String serviceId, Class<T> serviceType, ServiceBuilder<T> builder,
			ServiceDecorator<T> decorator, ContributionType contributionType, Class<?> contributionKeyType,
			Class<?> contributionValueType, List<UnorderedContributionOptionsImpl> unorderedContributions,
			List<OrderedContributionOptionsImpl> orderedContributions,
			List<MappedContributionOptionsImpl> mappedContributions) {
		super();
		this.serviceId = serviceId;
		this.dependencies = new ServiceDependencies(serviceType, builder, decorator, contributionType, contributionKeyType, contributionValueType, unorderedContributions, orderedContributions, mappedContributions);
	}

	public synchronized Object get(ServiceRegistryImpl registry) {
		if (service == null) {
			Set<String> serviceIdStack = registry.getServiceIdStack();
			if (serviceIdStack.contains(serviceId)) {
				List<String> references = new LinkedList<>(serviceIdStack);
				references.add(serviceId);
				throw new IocException("Circular dependency reference detected %s", references);
			}
			ServiceRegistryImpl registryWrapper = new ServiceRegistryImpl(registry, serviceId);
			ServiceBuilderContextImpl context = new ServiceBuilderContextImpl(registryWrapper, serviceId, dependencies.serviceType);
			
			if (dependencies.contributionType != null) {
				switch (dependencies.contributionType) {
					case MAPPED: 
						context.setMappedConfiguration(dependencies.contributionKeyType, dependencies.contributionValueType, getContributionMap(context));
						break;
					case ORDERED:
						context.setOrderedConfiguration(dependencies.contributionValueType, getContributionList(context));
						break;
					case UNORDERED:
						context.setUnorderedConfiguration(dependencies.contributionValueType, getContributionCollection(context));
						break;
				}
			}
			T candidate = dependencies.builder.build(context);
			if (dependencies.decorator != null) {
				service = dependencies.decorator.decorate(candidate, context);
			} else {
				service = candidate;
			}
			
			// allow dependencies to be garbage collected
			dependencies = null;
		}
		return service;
	}
	
	private Collection<Object> getContributionCollection(ServiceBuilderContext context) {
		if (dependencies.unorderedContributions == null || dependencies.unorderedContributions.isEmpty()) return Collections.emptyList();
		List<Object> list = new LinkedList<>();
		for (UnorderedContributionOptionsImpl current : dependencies.unorderedContributions) {
			list.add(current.getValueBuilder().build(context));
		}
		return Collections.unmodifiableList(list);
	}

	private List<Object> getContributionList(ServiceBuilderContext context) {
		if (dependencies.orderedContributions == null || dependencies.orderedContributions.isEmpty()) return Collections.emptyList();
		List<Object> list = new LinkedList<>();
		Collections.sort(dependencies.orderedContributions);
		for (OrderedContributionOptionsImpl current : dependencies.orderedContributions) {
			list.add(current.getValueBuilder().build(context));
		}
		return Collections.unmodifiableList(list);
	}

	private Map<Object, Object> getContributionMap(ServiceBuilderContext context) {
		if (dependencies.mappedContributions == null || dependencies.mappedContributions.isEmpty()) return Collections.emptyMap();
		Map<Object, Object> map = new LinkedHashMap<>();
		for (MappedContributionOptionsImpl current : dependencies.mappedContributions) {
			Object key = current.getKeyBuilder().build(context);
			if (map.containsKey(key)) throw new IocException("Duplicate contribution key %s", key);
			map.put(key, current.getValueBuilder().build(context));
		}
		return Collections.unmodifiableMap(map);
	}

	public String getServiceId() {
		return serviceId;
	}
}