package lombok.experimental;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Makes a class a leaf of a visitor design pattern hierarchy.
 * 
 * Initial code:
 * <code>
 *  @Visitable(root=RootClass.class)
 * 	class ImplClass extends RootClass {
 *  }
 * </code>
 * After code generation:
 * <code>
 *  @Visitable(root=RootClass.class)
 * 	class ImplClass extends RootClass {
 *    @Override
 * 	  public <R> R accept(ClassNameVisitor<R> visitor) {
 *      return visitor.caseImplClass(this);
 *    }
 *  }
 * </code>
 * 
 * @author Derek
 *
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Visitable {
	String root();
}
