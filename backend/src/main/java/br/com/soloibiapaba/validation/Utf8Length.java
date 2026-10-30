package br.com.soloibiapaba.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = Utf8LengthValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface Utf8Length {
    String message() default "A senha deve ter no máximo {max} bytes em UTF-8";
    int max() default 72;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
