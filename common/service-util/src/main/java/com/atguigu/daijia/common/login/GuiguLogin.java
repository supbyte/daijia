package com.atguigu.daijia.common.login;

import java.lang.annotation.*;

/**
 * 登录判断注解，用于在方法执行前判断是否登录
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GuiguLogin {

}
