package lombok.javac;

import static lombok.javac.handlers.JavacHandlerUtil.genJavaLangTypeRef;

import java.lang.reflect.Modifier;

import lombok.visitor.VisitorInvariants;

import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Type.TypeVar;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

/**
 * Utility class for Javac creation of visitor-related methods
 * @author Derek
 *
 */
public class VisitableUtils {
	private VisitableUtils() {}
	
	public static VisitableUtils ONLY = new VisitableUtils();
	
	/**
	 * Creates a method for accepting a visitor. If methodBody is supplied, then the method is implemented;
	 * otherwise, it is abstract.
	 * <code>
	 * [@Override]
	 * public [abstract] <R> R accept(RootVisitor<R> visitor) {
	 * 	[methodBody]
	 * }
	 * </code>
	 * @param node The node of the class that this method is for
	 * @param rootName The name of the root visitable class, used to generate the visitor's name
	 * @param methodBody The method body, should execute one of the visitor argument's cases, or null if the method is abstract
	 * @return The corresponding method declaration
	 */
	public JCMethodDecl createAcceptVisitor(JavacNode node, String rootName, JCBlock methodBody) {
		JavacTreeMaker treeMaker = node.getTreeMaker();
		
		Name returnTypeVarName = node.toName(VisitorInvariants.GENERIC_RETURN_TYPE_NAME);
		Name methodName = node.toName(VisitorInvariants.VISITOR_ACCEPT_METHOD_NAME);
		Name visitorClassName = node.toName(VisitorInvariants.createVisitorClassName(rootName));
		Name visitorArgName = node.toName(VisitorInvariants.VISITOR_ARG_NAME);
		
		// the method is public
		long modifierFlags = Modifier.PUBLIC | (methodBody == null ? Modifier.ABSTRACT : 0);
		// create the unbounded generic return type R
		JCTypeParameter returnType = treeMaker.TypeParameter(returnTypeVarName, List.<JCExpression>nil());
		// which is also the only generic type on the method
		List<JCTypeParameter> methodGenericTypes = List.<JCTypeParameter>of(returnType);
		// create the return type var itself
		TypeVar returnTypeVar = new TypeVar(returnTypeVarName, null, null);
		JCExpression methodReturnType = treeMaker.Type(returnTypeVar);
		// create the accepted visitor type, ClassNameVisitor<R>
		ClassType visitorType = new ClassType(Type.noType, List.<Type>of(returnTypeVar), null);
		TypeSymbol visitorSymbol = new TypeSymbol(0, visitorClassName, visitorType, null);
		visitorType.tsym = visitorSymbol;
		// create the visitor argument, ClassNameVisitor<R> visitor (no modifiers, no initialization)
		JCVariableDecl visitorArg = treeMaker.VarDef(treeMaker.Modifiers(0), visitorArgName, treeMaker.Type(visitorType), null);
		List<JCVariableDecl> methodParameters = List.<JCVariableDecl>of(visitorArg);
		
		List<JCExpression> methodThrows = List.<JCExpression>nil();
		
		// if the method is not abstract, then it must be an Override method
		List<JCAnnotation> annotations = List.nil();
		if (methodBody != null) {
			annotations = annotations.prepend(treeMaker.Annotation(genJavaLangTypeRef(node, "Override"), List.<JCExpression>nil()));
		}
		
		JCExpression defaultValue = null;
		
		return treeMaker.MethodDef(treeMaker.Modifiers(modifierFlags, annotations), methodName, methodReturnType, methodGenericTypes, methodParameters, methodThrows, methodBody, defaultValue);
	}
}
