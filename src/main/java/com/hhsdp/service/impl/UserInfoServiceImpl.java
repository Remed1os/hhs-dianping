package com.hhsdp.service.impl;

import com.hhsdp.entity.UserInfo;
import com.hhsdp.mapper.UserInfoMapper;
import com.hhsdp.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
