package lombok.javac.handlers;

import static lombok.javac.Javac.CTC_VOID;

import java.lang.reflect.Modifier;

import lombok.HelloWorld;
import lombok.core.AnnotationValues;
import lombok.javac.Javac;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;

import org.mangosdk.spi.ProviderFor;

import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCBlock;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCModifiers;
import com.sun.tools.javac.tree.JCTree.JCStatement;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

@ProviderFor(JavacAnnotationHandler.class)
@SuppressWarnings("restriction") 
public class HandleHelloWorld extends JavacAnnotationHandler<HelloWorld>{

	@Override public void handle(AnnotationValues<HelloWorld> annotation, JCAnnotation ast,
			   JavacNode annotationNode) {
			  JavacNode typeNode = annotationNode.up();
			  
			  JCMethodDecl helloWorldMethod = createHelloWorld(typeNode);
			  
			  JavacHandlerUtil.injectMethod(typeNode, helloWorldMethod);
			 }


private JCMethodDecl createHelloWorld(JavacNode type) {
 JavacTreeMaker treeMaker = type.getTreeMaker();
 
 JCModifiers           modifiers          = treeMaker.Modifiers(Modifier.PUBLIC);
 List<JCTypeParameter> methodGenericTypes = List.<JCTypeParameter>nil();
 JCExpression          methodType         = treeMaker.Type(Javac.createVoidType(treeMaker, CTC_VOID));
 Name                  methodName         = type.toName("helloWorld");
 List<JCVariableDecl>  methodParameters   = List.<JCVariableDecl>nil();
 List<JCExpression>    methodThrows       = List.<JCExpression>nil();

 JCExpression printlnMethod = 
  JavacHandlerUtil.chainDots(type, "System", "out", "println"); 
 List<JCExpression> printlnArgs = List.<JCExpression>of(treeMaker.Literal("hello world"));
 JCMethodInvocation printlnInvocation = 
  treeMaker.Apply(List.<JCExpression>nil(), printlnMethod, printlnArgs);
 JCBlock methodBody = 
  treeMaker.Block(0, List.<JCStatement>of(treeMaker.Exec(printlnInvocation)));

 JCExpression defaultValue = null;

 return treeMaker.MethodDef(
   modifiers, 
   methodName, 
   methodType,
   methodGenericTypes, 
   methodParameters, 
   methodThrows, 
   methodBody, 
   defaultValue 
  );
}
	
}