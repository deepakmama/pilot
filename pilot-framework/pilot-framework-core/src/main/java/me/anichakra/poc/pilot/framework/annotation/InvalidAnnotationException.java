package me.anichakra.poc.pilot.framework.annotation;

/**
 * This exception is thrown when processing the annotations during service bean creation fail.
 * @author anirbanchakraborty
 *
 */
public class InvalidAnnotationException extends RuntimeException {

	/**
	 * 
	 * @param string
	 * @param bean
	 */
	public InvalidAnnotationException(String string, Object bean) {
		super(string + ":"+  bean.getClass().getName());
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

}
