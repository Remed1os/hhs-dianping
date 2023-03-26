package com.hhsdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hhsdp.dto.LoginFormDTO;
import com.hhsdp.dto.Result;
import com.hhsdp.entity.User;

import javax.servlet.http.HttpSession;


public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();

    Result logout();
}
