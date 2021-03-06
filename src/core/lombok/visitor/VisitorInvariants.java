package lombok.visitor;

import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;

import lombok.ConfigurationKeys;
import lombok.core.AST;
import lombok.core.LombokConfiguration;
import lombok.core.configuration.ConfigurationKey;

public class VisitorInvariants {

	private static final String VISITOR_ARG_NAME = "visitor";
	private static final String GENERIC_RETURN_TYPE_NAME = "R";
	private static final String VISITOR_ACCEPT_METHOD_NAME = "accept";
	private static final String GENERIC_ARGUMENT_TYPE_NAME = "A";
	private static final String ARGUMENT_NAME = "arg";
	
	public static String createVisitorClassName(String rootName) {
		return rootName + "Visitor";
	}
	public static String getVisitorCasePrefix(ConfigReader reader) {
		return readConfiguration(reader, ConfigurationKeys.VISITOR_CASE_PREFIX, "case");
	}
	
	public static String getVisitorAcceptMethodName(ConfigReader reader) {
		return readConfiguration(reader, ConfigurationKeys.VISITOR_ACCEPT_NAME, VISITOR_ACCEPT_METHOD_NAME);
	}
	
	public static String getVisitorArgName(ConfigReader reader) {
		return readConfiguration(reader, ConfigurationKeys.VISITOR_VISITOR_ARG_NAME, VISITOR_ARG_NAME);
	}
	
	public static String getArgumentVariableName(ConfigReader reader) {
		return readConfiguration(reader, ConfigurationKeys.VISITOR_ARG_VAR_NAME, ARGUMENT_NAME);
	}
	
	public static String getArgumentTypeVariableName(ConfigReader reader) {
		return readConfiguration(reader, ConfigurationKeys.VISITOR_ARG_TYPE_VAR, GENERIC_ARGUMENT_TYPE_NAME);
	}
	
	public static String getReturnTypeVariableName(ConfigReader reader) {
		return readConfiguration(reader, ConfigurationKeys.VISITOR_RETURN_TYPE_VAR, GENERIC_RETURN_TYPE_NAME);
	}
	
	public static boolean getConstantImplEnabled(ConfigReader reader) {
		return readConfiguration(reader, ConfigurationKeys.VISITOR_CONSTANT_IMPL, false);
	}
	
	public static boolean getLambdaImplEnabled(ConfigReader reader) {
		return readConfiguration(reader, ConfigurationKeys.VISITOR_LAMBDA_IMPL, false);
	}
	
	public static boolean getLambdaBuilderEnabled(ConfigReader reader) {
		return readConfiguration(reader, ConfigurationKeys.VISITOR_LAMBDA_BUILDER, false);
	}
	
	public static boolean getDefaultImplEnabled(ConfigReader reader) {
		return readConfiguration(reader, ConfigurationKeys.VISITOR_DEFAULT_IMPL, false);
	}
	
	public static boolean getDefaultBuilderEnabled(ConfigReader reader) {
		return readConfiguration(reader, ConfigurationKeys.VISITOR_DEFAULT_BUILDER, false);
	}

	private static <T> T readConfiguration(ConfigReader configReader, ConfigurationKey<T> key, T defaultVal) {
		T val = configReader.readConfiguration(key);
		if (val == null) {
			return defaultVal;
		} else {
			System.out.println("Using default for " + key.getKeyName() + ": " + defaultVal);
			return val;
		}
	}
	
	public interface ConfigReader {
		<T> T readConfiguration(ConfigurationKey<T> key);
	}
	
	public static class ASTConfigReader implements ConfigReader {

		private final AST<?,?,?> ast;
		
		public ASTConfigReader(AST<?,?,?> ast) {
			this.ast = ast;
		}
		
		@Override public <T> T readConfiguration(ConfigurationKey<T> key) {
			return ast.readConfiguration(key);
		}
		
	}
	
	public static class ElementConfigReader implements ConfigReader {
		
		private final Element element;
		private final Elements elementUtils;
		
		public ElementConfigReader(Element element, Elements elementUtils) {
			this.element = element;
			this.elementUtils = elementUtils;
		}

		@Override public <T> T readConfiguration(ConfigurationKey<T> key) {
			return LombokConfiguration.read(key, element, elementUtils);
		}
		
		
	}
	
}