package lombok.javac.handlers;

import java.lang.reflect.Modifier;

import lombok.core.AnnotationValues;
import lombok.experimental.VisitableRoot;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;

import org.mangosdk.spi.ProviderFor;

import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Type.TypeVar;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

/**
 * Handles the {@link VisitableRoot} annotation
 * @see VisitableRoot
 * @author Derek
 *
 */
@ProviderFor(JavacAnnotationHandler.class)
@SuppressWarnings("restriction")
public class HandleVisitableRoot extends JavacAnnotationHandler<VisitableRoot> {
	
	@Override
	public void handle(AnnotationValues<VisitableRoot> annotation, JCAnnotation ast, JavacNode annotationNode) {
		JavacNode typeNode = annotationNode.up(); // the annotated class
		
		JCMethodDecl acceptVisitorMethod = createAbstractAcceptVisitor(typeNode);
		
		JavacHandlerUtil.injectMethod(typeNode, acceptVisitorMethod);
	}
	
	/**
	 * Creates the abstract method for a class to accept a visitor. Will look like, for class ClassName:
	 * <code>
	 *   public abstract <R> R accept(ClassNameVisitor<R> visitor);
	 * </code>
	 * @param node The JavacNode containing the class
	 * @return The <code>accept</code> method to add to the class
	 */
	private JCMethodDecl createAbstractAcceptVisitor(JavacNode node) {
		JavacTreeMaker treeMaker = node.getTreeMaker();
		// the class to add the method to
		JCClassDecl type = (JCClassDecl) node.get();
		
		Name returnTypeVarName = node.toName("R");
		Name methodName = node.toName("accept");
		Name visitorClassName = node.toName(type.name.toString() + "Visitor");
		Name visitorArgName = node.toName("visitor");
		
		// the method is both public and abstract
		JCModifiers modifiers = treeMaker.Modifiers(Modifier.PUBLIC | Modifier.ABSTRACT);
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
		
		// abstract method, so no body
		JCBlock methodBody = null;
		
		JCExpression defaultValue = null;
		
		return treeMaker.MethodDef(modifiers, methodName, methodReturnType, methodGenericTypes, methodParameters, methodThrows, methodBody, defaultValue);
	}
	
}