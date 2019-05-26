package com.xiaojinzi.component.anno.router;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 给 Intent 添加一些 category
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface CategoryAnno {

    /**
     * 表示 Intent 需要添加的 Category
     *
     * @return
     */
    String[] value();

}
