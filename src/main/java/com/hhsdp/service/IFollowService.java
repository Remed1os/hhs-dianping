package com.hhsdp.service;

import com.hhsdp.dto.Result;
import com.hhsdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IFollowService extends IService<Follow> {

    Result follow(Long followerId, boolean isFollow);

    Result isFollow(Long followerId);

    Result common(Long id);
}
