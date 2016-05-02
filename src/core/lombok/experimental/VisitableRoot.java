package lombok.experimental;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Makes a class or interface the root of a visitor design pattern hierarchy.
 * 
 * Initial code:
 * <code>
 *  @VisitableRoot
 * 	class RootClass {
 *  }
 * </code>
 * After code generation:
 * <code>
 *  @VisitableRoot
 *  class RootClass {
 * 	  public abstract <R> R accept(ClassNameVisitor<R> visitor);
 *  }
 * </code>
 * 
 * @author Derek
 *
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface VisitableRoot {
	
}
