package br.com.soloibiapaba.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class Utf8LengthValidator implements ConstraintValidator<Utf8Length, String> {
    private int maximum;
    @Override public void initialize(Utf8Length rule) { maximum = rule.max(); }
    @Override public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= maximum;
    }
}
