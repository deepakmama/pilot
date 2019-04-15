package me.anichakra.poc.pilot.framework.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RestController;

/**
 * This bean post processor validates all the beans that are injected throughout
 * the microservice application in all layers starting from controllers to
 * service and repository. It checks for proper implementation of CQRS pattern.
 * During processing if any invalidation is found then {@link InvalidAnnotationException} will be thrown
 * and the application will be stopped from getting started.
 * <p>
 * The processor will check the annotation on each class that is in class-path,
 * based on the annotations found the following validations will be done:
 * <ul>
 * <li>
 * 
 * @Microservice: Should not contain any declared field or method apart from
 *                main method.</li>
 *                <li>
 * @RestController: Should contain only one declared field that should be
 *                  {@link Inject}ed with either of the following annotated
 *                  class: {@link ApplicationService}, {@link CommandService},
 *                  {@link QueryService}. Any other kind of class injection will
 *                  be invalidated and exception will be thrown.</li>
 *                  <li>
 * @CommandService: Can inject {@link ApplicationService},
 *                  {@link FrameworkService}, {@link Repository} or another
 *                  CommandService, but cannot contain a
 *                  {@link QueryService}</li>
 *                  <li>
 * @QueryService: Can inject {@link ApplicationService},
 *                {@link FrameworkService}, {@link Repository} or another
 *                QueryService, but cannot contain a {@link CommandService}</li>
 *                <li>
 * @ApplicationService: Can {@link Inject} one or more
 *                      {@link ApplicationService}, {@link FrameworkService},
 *                      but cannot inject any {@link CommandService},
 *                      {@link QueryService} or {@link Repository}</li>
 *                      </ul>
 *
 * 
 * @author anirbanchakraborty
 *
 */
@Component
public class AnnotationValidationProcessor implements BeanPostProcessor {

	@Autowired(required = false)
	BuildProperties buildProperties;

	@Autowired
	public AnnotationValidationProcessor(ConfigurableListableBeanFactory beanFactory) {
		annotationValidationMap.put(RestController.class, restControllerValidation);
		annotationValidationMap.put(CommandService.class, commandServiceValidation);
		annotationValidationMap.put(QueryService.class, queryServiceValidation);
		annotationValidationMap.put(ApplicationService.class, applicationServiceValidation);
		annotationValidationMap.put(Microservice.class, microserviceValidation);

	}

	private Map<Class<? extends Annotation>, Consumer<Object>> annotationValidationMap = new HashMap<>();

	private Consumer<Object> microserviceValidation = b -> {
		if (ClassUtils.getUserClass(b.getClass()).getDeclaredFields().length > 0
				&& ClassUtils.getUserClass(b.getClass()).getDeclaredMethods().length > 1)
			throw new InvalidAnnotationException(
					"Microservice annotated class should not contain any field or non-static method", b);
	};

	private Consumer<Object> restControllerValidation = b -> {
		Supplier<Stream<Field>> supplier = () -> Arrays.asList(b.getClass().getDeclaredFields()).stream();
		long count = supplier.get().filter(f -> f.isAnnotationPresent(Inject.class)).count();

		if (count != 1)
			throw new InvalidAnnotationException("Controller class must have only one injectable field", b);

		final String message = "A Controller class can only inject one CommandService, or one QueryService or one ApplicationService";
		validateBean(b, supplier, message, CommandService.class, QueryService.class, ApplicationService.class);

	};	

	private Consumer<Object> commandServiceValidation = b -> {
		Supplier<Stream<Field>> supplier = null;
		if (!b.getClass().getAnnotation(CommandService.class).stateful()) {
			supplier = validateInjection(b);
		}
		final String message = "A CommandService class can only inject another CommandService, an ApplicationService, a FrameworkService or a Repository";
		validateBean(b, supplier, message, CommandService.class, ApplicationService.class, FrameworkService.class,
				Repository.class, ServiceConfig.class);

	};

	private Consumer<Object> queryServiceValidation = b -> {
		Supplier<Stream<Field>> supplier = null;
		if (!b.getClass().getAnnotation(QueryService.class).stateful()) {
			supplier = validateInjection(b);
		}
		final String message = "A QueryService class can only inject another QueryService, an ApplicationService, a FrameworkService or a Repository";
		validateBean(b, supplier, message, QueryService.class, ApplicationService.class, FrameworkService.class,
				Repository.class, ServiceConfig.class);
	};

	private Consumer<Object> applicationServiceValidation = b -> {
		Supplier<Stream<Field>> supplier = null;
		if (!b.getClass().getAnnotation(ApplicationService.class).stateful()) {
			supplier = validateInjection(b);
		}
		final String message = "An ApplicationService class can only inject another ApplicationService or a FrameworkService";
		validateBean(b, supplier, message, ApplicationService.class, FrameworkService.class, ServiceConfig.class);
	};

	@SafeVarargs
	private final void validateBean(Object b, Supplier<Stream<Field>> supplier, String validationMessage,
			Class<? extends Annotation>... annotationClass) {
		if (!supplier.get().filter(f -> f.isAnnotationPresent(Inject.class))
				.allMatch(c -> isFieldAnnotatedWithEither(b, c, annotationClass)))
			throw new InvalidAnnotationException(validationMessage, b);
	}

	@SafeVarargs
	private final boolean isFieldAnnotatedWithEither(final Object bean, final Field field,
			final Class<? extends Annotation>... annotationClass) {
		field.setAccessible(true);
		boolean flag = false;
		flag = Arrays.asList(annotationClass).stream().anyMatch(a -> {
			try {
				return field.getType().isAnnotationPresent(a)
						|| ClassUtils.getUserClass(field.get(bean).getClass()).isAnnotationPresent(a)
						|| isFramework(field.getType())
						|| AopUtils.getTargetClass(field.get(bean)).isAnnotationPresent(a);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				return false;
			}

		});

		return flag;

	}

	private boolean isFramework(final Class<?> clazz) {
		return clazz.getName().startsWith(buildProperties.getGroup()) && clazz.getName().contains(".framework.");
	}

	private Supplier<Stream<Field>> validateInjection(Object b) {
		Supplier<Stream<Field>> supplier = () -> Arrays.asList(b.getClass().getDeclaredFields()).stream();
		if (!supplier.get().allMatch(f -> f.isAnnotationPresent(Inject.class)))
			throw new InvalidAnnotationException("All fields should have proper @Injection", b);
		return supplier;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		if (buildProperties != null && bean.getClass().getName().startsWith(buildProperties.getGroup())
				&& !isFramework(bean.getClass())) {
			Annotation[] annotations = bean.getClass().getAnnotations();
			Supplier<Stream<Annotation>> supplier = () -> Arrays.asList(annotations).stream();
			supplier.get().filter(c -> annotationValidationMap.containsKey(c.annotationType()))
					.forEach(c -> annotationValidationMap.get(c.annotationType()).accept(bean));

		}
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

}