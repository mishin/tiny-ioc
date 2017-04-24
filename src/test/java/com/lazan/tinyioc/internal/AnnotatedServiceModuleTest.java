package com.lazan.tinyioc.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.sql.Date;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Named;

import org.junit.Test;

import com.lazan.tinyioc.IocException;
import com.lazan.tinyioc.MappedConfiguration;
import com.lazan.tinyioc.OrderedConfiguration;
import com.lazan.tinyioc.ServiceBinder;
import com.lazan.tinyioc.ServiceRegistry;
import com.lazan.tinyioc.ServiceRegistryBuilder;
import com.lazan.tinyioc.UnorderedConfiguration;
import com.lazan.tinyioc.annotations.Bind;
import com.lazan.tinyioc.annotations.Contribute;
import com.lazan.tinyioc.annotations.Decorate;
import com.lazan.tinyioc.annotations.Service;
import com.lazan.tinyioc.annotations.ServiceOverride;

public class AnnotatedServiceModuleTest {
	public static class TestModule {
		@Bind
		public void bind(ServiceBinder binder) {
			binder.bind(String.class, "foo").withServiceId("string1");
			binder.bind(String.class, "bar").withServiceId("string2");
			binder.bind(String.class, "baz").withServiceId("string3");
			binder.bind(Long.class, 200L);
		}
		
		@Service
		public Date createDate(Integer integer) {
			return new Date(integer);
		}
		
		@ServiceOverride("string2")
		public String overrideString2(@Named("string1") String string1) {
			return string1 + "x";
		}
		
		@Service
		public Integer createInteger() {
			return 1000;
		}
		
		@Decorate(decoratorId="d1")
		public Long decorateLong(Long delegate, Integer integer) {
			return delegate + integer;
		}
	}

	@Test
	public void testAnnotatedModule() {
		ServiceRegistry registry = buildRegistry(TestModule.class);
		assertEquals("foo", registry.getService("string1"));
		assertEquals("foox", registry.getService("string2"));
		assertEquals("baz", registry.getService("string3"));
		assertEquals(1000, registry.getService(Date.class).getTime());
		assertEquals(1000, registry.getService(Integer.class).intValue());
		assertEquals(1200, registry.getService(Long.class).longValue());
	}
	
	public static class DecoratorModule {
		@Bind
		public void bind(ServiceBinder binder) {
			binder.bind(String.class, "foo").withServiceId("string1");
			binder.bind(String.class, "bar").withServiceId("string2");
			binder.bind(String.class, "baz").withServiceId("string3");
		}
		
		@Decorate(serviceId="string1", decoratorId="decorator1")
		public String decorateString1(@Named("string1") String string1) {
			return string1 + "x";
		}

		@Decorate(serviceId="string2", decoratorId="decorator2")
		public String decorateString2(@Named("string2") String string2, @Named("string3") String string3) {
			return string2 + "y" + string3;
		}
	}
	
	@Test
	public void testDecorateNonUniqueServiceType() {
		ServiceRegistry registry = buildRegistry(DecoratorModule.class);
		assertEquals("foox", registry.getService("string1"));
		assertEquals("barybaz", registry.getService("string2"));
	}
	
	public static class ErrorModule1 {
		public ErrorModule1(String foo) {}
		
		@Service
		public String service1() {
			return "service1";
		}
	}
	
	@Test
	public void testBadConstructor() {
		try {
			buildRegistry(ErrorModule1.class);	
			fail();
		} catch (IocException e) {
			assertEquals("Error instantiating ErrorModule1", e.getMessage());
		}
	}
	
	public static class DecoratorModule2 {
		@Bind
		public void bind(ServiceBinder binder) {
			binder.bind(String.class, "HELLO").withServiceId("foo");
		}
		
		@Decorate(serviceId="foo", decoratorId="d1", after="d2")
		public String decorateFoo1(String foo) {
			return "1" + foo + "1";
		}

		@Decorate(serviceId="foo", decoratorId="d2", before="d3")
		public String decorateFoo2(String foo) {
			return "2" + foo + "2";
		}

		@Decorate(serviceId="foo", decoratorId="d3", after="d1")
		public String decorateFoo3(String foo) {
			return "3" + foo + "3";
		}
	}
	
	@Test
	public void testMultipleDecorators() {
		ServiceRegistry registry = buildRegistry(DecoratorModule2.class);
		String foo = registry.getService("foo", String.class);
		assertEquals("312HELLO213", foo);
	}
	
	public static class EagerLoadModule {
		@Service(value="service1", eagerLoad=true)
		public String service1(@Named("list") List<String> instances) {
			instances.add("service1");
			return "foo";
		}
		@Service(value="service2", eagerLoad=false)
		public String service2(@Named("list") List<String> instances) {
			instances.add("service2");
			return "foo";
		}
		@Bind
		public void bind(ServiceBinder binder) {
			binder.bind(List.class, new LinkedList<String>());
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testEagerLoad() {
		ServiceRegistry registry = buildRegistry(EagerLoadModule.class);
		List<String> instances = registry.getService(List.class);
		assertEquals(Arrays.asList("service1"), instances);
		
		registry.getService("service2");
		assertEquals(Arrays.asList("service1", "service2"), instances);
	}
	
	public static class ContributeModule {
		@Service
		public List<String> list(List<String> contributions) {
			return contributions;
		}
		@Service
		public Map<String, String> map(Map<String, String> contributions) {
			return contributions;
		}
		@Service
		public Collection<String> collection(Collection<String> contributions) {
			return contributions;
		}
		@Contribute(serviceType=List.class)
		public void contributeList(OrderedConfiguration<String> config) {
			config.add("c1", "value1").before("c2");
		}
		@Contribute(serviceId="list")
		public void contributeList2(OrderedConfiguration<String> config) {
			config.add("c2", "value2");
		}
		@Contribute(serviceType=Map.class)
		public void contributeMap(MappedConfiguration<String, String> config) {
			config.add("c1", "key1", "value1");
		}
		@Contribute(serviceId="map")
		public void contributeMap2(MappedConfiguration<String, String> config) {
			config.add("c2", "key2", "value2");
		}
		@Contribute(serviceType=Collection.class)
		public void contributeCollection(UnorderedConfiguration<String> config) {
			config.add("c1", "value1");
		}
		@Contribute(serviceId="collection")
		public void contributeCollection2(UnorderedConfiguration<String> config) {
			config.add("c2", "value2");
		}		
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void testContribute() {
		ServiceRegistry registry = buildRegistry(ContributeModule.class);
		List list = registry.getService(List.class);
		assertEquals(Arrays.asList("value1", "value2"), list);
		
		Map<String, String> expectedMap = new LinkedHashMap<>();
		expectedMap.put("key1", "value1");
		expectedMap.put("key2", "value2");
		assertEquals(expectedMap, registry.getService(Map.class));
		
		Set<String> expectedCollection = new LinkedHashSet<>(Arrays.asList("value1", "value2"));
		Set<String> actualCollection = new LinkedHashSet<>(registry.getService(Collection.class));
		assertEquals(expectedCollection, actualCollection);
	}
	
	private ServiceRegistry buildRegistry(Class<?>... moduleTypes) {
		return new ServiceRegistryBuilder().withModuleTypes(moduleTypes).build();
	}
}
