/*
 * Copyright (C) 2013-2014 The Project Lombok Authors.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.util.Elements;

import lombok.core.configuration.BubblingConfigurationResolver;
import lombok.core.configuration.ConfigurationKey;
import lombok.core.configuration.ConfigurationProblemReporter;
import lombok.core.configuration.ConfigurationResolver;
import lombok.core.configuration.ConfigurationResolverFactory;
import lombok.core.configuration.FileSystemSourceCache;

public class LombokConfiguration {
	
	private static final ConfigurationResolver NULL_RESOLVER = new ConfigurationResolver() {
		@SuppressWarnings("unchecked") @Override public <T> T resolve(ConfigurationKey<T> key) {
			if (key.getType().isList()) return (T) Collections.emptyList();
			return null;
		}
	};
	
	private static FileSystemSourceCache cache = new FileSystemSourceCache();
	private static ConfigurationResolverFactory configurationResolverFactory;
	private static Map<String, ConfigurationResolver> resolverForPackage = new HashMap<String, ConfigurationResolver>();
	
	static {
		if (System.getProperty("lombok.disableConfig") != null) {
			configurationResolverFactory = new ConfigurationResolverFactory() {
				@Override public ConfigurationResolver createResolver(AST<?, ?, ?> ast) {
					return NULL_RESOLVER;
				}
			};
		}
		else {
			configurationResolverFactory = createFileSystemBubblingResolverFactory();
		}
	}
	
	private LombokConfiguration() {
		// prevent instantiation
	}
	
	public static void overrideConfigurationResolverFactory(ConfigurationResolverFactory crf) {
		configurationResolverFactory = crf == null ? createFileSystemBubblingResolverFactory() : crf;
	}
	
	static <T> T read(ConfigurationKey<T> key, AST<?, ?, ?> ast) {
		return configurationResolverFactory.createResolver(ast).resolve(key);
	}
	
	public static <T> T read(ConfigurationKey<T> key, Element element, Elements utils) {
		PackageElement p = utils.getPackageOf(element);
		return getResolverForPackage(p.isUnnamed() ? null : p.toString()).resolve(key);
	}
	
	private static ConfigurationResolverFactory createFileSystemBubblingResolverFactory() {
		return new ConfigurationResolverFactory() {
			@Override public ConfigurationResolver createResolver(AST<?, ?, ?> ast) {
				ConfigurationResolver resolver = new BubblingConfigurationResolver(cache.sourcesForJavaFile(ast.getAbsoluteFileLocation(), ConfigurationProblemReporter.CONSOLE));
				resolverForPackage.put(ast.getPackageDeclaration(), resolver);
				return resolver;
			}
		};
	}
	
	private static final ConfigurationResolver getResolverForPackage(String packageName) {
		ConfigurationResolver resolver = resolverForPackage.get(packageName);
		if (resolver == null) {
			throw new IllegalArgumentException("Could not find " + packageName + " among " + resolverForPackage.keySet());
		}
		return resolver;
	}
}
