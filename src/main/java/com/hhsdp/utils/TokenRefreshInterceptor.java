package com.hhsdp.utils;

import cn.hutool.core.bean.BeanUtil;
import com.hhsdp.dto.UserDto;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hhsdp.utils.SystemConstants.LOGIN_USER_KEY;
import static com.hhsdp.utils.SystemConstants.LOGIN_USER_TTL;

/**
 * @author: remedios
 * @Description:
 * @create: 2022-10-11 19:45
 */
public class TokenRefreshInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public TokenRefreshInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //1.获取token
        String token = request.getHeader("authorization");
        if(StringUtils.isEmpty(token)){
            return true;
        }

        //2.基于token获取redis中的用户信息
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(key);
        if(map.isEmpty()){
            return true;
        }

        //3.将map中的数据属性转换成UserDto对象
        UserDto userDto = BeanUtil.fillBeanWithMap(map, new UserDto(), false);

        //4.保存并刷新token有效期
        UserHolder.saveUser(userDto);
        stringRedisTemplate.expire(key,LOGIN_USER_TTL, TimeUnit.MINUTES);

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        UserHolder.removeUser();
    }
}
