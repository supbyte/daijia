package com.atguigu.daijia.common.login;

import com.atguigu.daijia.common.constant.RedisConstant;
import com.atguigu.daijia.common.execption.GuiguException;
import com.atguigu.daijia.common.result.ResultCodeEnum;
import com.atguigu.daijia.common.util.AuthContextHolder;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

/**
 * 登录判断注解切面类
 */
@Component
@Aspect
@SuppressWarnings({"unchecked", "rawtypes"})
public class GuiGuLoginAspect {

    @Resource
    private RedisTemplate redisTemplate;

    /**
     *
     * @param point 切入点
     * @param guiguLogin 需要做登录判断的注解
     * @return 方法返回值
     * @throws Throwable 抛出异常
     */
    @Around("execution(* com.atguigu.daijia.*.controller.*.*(..)) && @annotation(guiguLogin)")
    public Object login(ProceedingJoinPoint point, GuiguLogin guiguLogin) throws Throwable {
        // 1.获取request对象
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) attributes;
        HttpServletRequest request = Objects.requireNonNull(requestAttributes).getRequest();
        // 2.从请求头中获取token
        String token = request.getHeader("token");
        // 3.判断token是否为空，如果为空，则返回登录提示
        if (!StringUtils.hasText(token)) {
            throw new GuiguException(ResultCodeEnum.LOGIN_AUTH);
        }
        // 4.token不为空则查询redis
        String userId = (String) redisTemplate.opsForValue().get(RedisConstant.USER_LOGIN_KEY_PREFIX+token);
        if (!StringUtils.hasText(userId)) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        AuthContextHolder.setUserId(Long.parseLong(userId));
        // 5.查询redis对应的用户ID，把用户ID放到ThreadLocal中
        return point.proceed();
    }
}
