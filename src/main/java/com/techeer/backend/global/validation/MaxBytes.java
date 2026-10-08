package com.techeer.backend.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 문자열을 UTF-8 로 인코딩했을 때의 바이트 수가 value 이하여야 한다.
 *
 * <p>@Size 는 글자 수를 세는데, 한글은 한 글자가 3바이트라서 글자 수 제한만으로는 바이트 제한을 지킬 수 없다.
 * bcrypt 는 72바이트를 넘는 비밀번호를 거부하므로(예외) 비밀번호 검증에 쓴다.
 */
@Documented
@Constraint(validatedBy = MaxBytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxBytes {

    String message() default "UTF-8 기준 {value}바이트 이하여야 합니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int value();
}
