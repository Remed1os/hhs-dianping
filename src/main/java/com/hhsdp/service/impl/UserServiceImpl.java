package com.hhsdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hhsdp.dto.LoginFormDTO;
import com.hhsdp.dto.Result;
import com.hhsdp.dto.UserDto;
import com.hhsdp.entity.User;
import com.hhsdp.mapper.UserMapper;
import com.hhsdp.service.IUserService;
import com.hhsdp.utils.RegexUtils;
import com.hhsdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.hhsdp.utils.RedisConstants.USER_SIGN_KEY;
import static com.hhsdp.utils.SystemConstants.*;


@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    IUserService userService;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    public Result sendCode(String phone, HttpSession session) {
        //验证手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误！");
        }
        //发送验证码
        String code = RandomUtil.randomNumbers(6);

        //保存验证码到redis中
//        session.setAttribute("code",code);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.info("发送验证码成功，您的验证码是{}",code);

        return Result.ok();

    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        String phone = loginForm.getPhone();

        //验证手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误！");
        }
        //从redis中获取验证码并校验
//        String code = (String) session.getAttribute("code");
        String CacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if(CacheCode == null || !loginForm.getCode().equals(CacheCode)){
            return Result.fail("输入的验证码有误");
        }
        //登入成功，判断用户是否已注册
        // 4.一致，根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();

        // 5.判断用户是否存在
        if (user == null) {
            // 6.不存在，创建新用户并保存
            user = createUserWithPhone(phone);
        }
        //将简短用户信息保存到redis中，返回一个token
        //session.setAttribute("user", BeanUtil.copyProperties(user, UserDto.class));
        String token = UUID.randomUUID().toString();
        UserDto userDto = BeanUtil.copyProperties(user, UserDto.class);

        //将用户信息转换为map，存入redis中的Hash
            //注意此处的map中的Object是Long类型，而操作redis的类泛型是<String,String>
            //处理方法有二，自定义一个map集合，手动转换数据到集合中
            //下面是第二种处理方式
        Map<String, Object> map = BeanUtil.beanToMap(userDto, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreCase(true)
                        .setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));
        String loginToken = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(loginToken,map);

        //设置过期时间
        stringRedisTemplate.expire(loginToken,30,TimeUnit.MINUTES);

        //登入成功需要删除redis中的验证码
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);

        return Result.ok(token);
    }

    @Override
    public Result logout() {
        UserHolder.saveUser(null);
        return Result.ok();
    }


    //签到
    @Override
    public Result sign() {
        //获取当前登入用户
        UserDto user = UserHolder.getUser();
        //获取当前时间
        LocalDateTime now = LocalDateTime.now();
        String s = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        //设置key
        String key = USER_SIGN_KEY + user.getId() + s;
        //获取第几天
        int dayOfMonth = now.getDayOfMonth();
        //写入redis中
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth - 1,true);

        return Result.ok();

    }

    //签到统计
    @Override
    public Result signCount() {

        //获取用户信息
        UserDto user = UserHolder.getUser();
        //获取日期并拼接key
        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = USER_SIGN_KEY + user.getId() + format;

        //获取今天是本月第几天
        int dayOfMonth = now.getDayOfMonth();

        //获取到今天位置的签到记录，返回值是一个十进制数字
        // bitfield key get u(类型，u无符号) offset(到哪里结束) 0(从哪里开始)
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0)
        );
        //判断是否空
        if (result == null || result.isEmpty()){
            return Result.ok(0);
        }
        //取值
        Long num = result.get(0);
        if(num == null || num == 0){
            return Result.ok(0);
        }

        //判断连续签到次数和总签到次数
        int count = 0;//总签到次数
        int tempCount = 0;//暂存数
        int constantCount = 0;//最大连续签到次数
        int days = 0;//本月的天数

        while(true){
            if ((num & 1) == 0){
                // 如果为0，说明未签到
                if(tempCount > constantCount){
                    constantCount = tempCount;
                }
                tempCount = 0;
                days++;
                if(days >= 30){
                    break;
                }
            }else{
                // 如果不为0，已签到，计数器+1
                count++;
                tempCount++;
            }
            //把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
        }
        return Result.ok(count);
    }


    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));

        userService.save(user);
        return user;
    }


}
