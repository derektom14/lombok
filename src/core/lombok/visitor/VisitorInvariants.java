package lombok.visitor;

public class VisitorInvariants {

	public static final String VISITOR_ARG_NAME = "visitor";
	public static final String GENERIC_RETURN_TYPE_NAME = "R";
	public static final String VISITOR_ACCEPT_METHOD_NAME = "accept";
	public static String createVisitorClassName(String rootName) {
		return rootName + "Visitor";
	}
	public static String createVisitorMethodName(String typeName) {
		return "case" + Character.toUpperCase(typeName.charAt(0)) + typeName.substring(1);
	}
	
}
