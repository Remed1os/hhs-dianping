package com.hhsdp.controller;

import cn.hutool.core.bean.BeanUtil;
import com.hhsdp.dto.LoginFormDTO;
import com.hhsdp.dto.Result;
import com.hhsdp.dto.UserDto;
import com.hhsdp.entity.User;
import com.hhsdp.entity.UserInfo;
import com.hhsdp.service.IBlogService;
import com.hhsdp.service.IUserInfoService;
import com.hhsdp.service.IUserService;
import com.hhsdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;


@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private IBlogService blogService;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // TODO 发送短信验证码并保存验证码
        return userService.sendCode(phone,session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        return userService.login(loginForm,session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        return userService.logout();
    }

    @PostMapping
    public Result sign(){
        return userService.sign();
    }

    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }

    @GetMapping("/me")
    public Result me(){
        UserDto user = UserHolder.getUser();

        return Result.ok(user);
    }

    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId){
        // 查询详情
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok();
        }
        UserDto userDto = BeanUtil.copyProperties(user, UserDto.class);
        // 返回
        return Result.ok(userDto);
    }



    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
//        info.setCreateTime(null);
//        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }

}
