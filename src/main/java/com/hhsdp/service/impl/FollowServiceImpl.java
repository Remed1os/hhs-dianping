package com.hhsdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hhsdp.dto.Result;
import com.hhsdp.dto.UserDto;
import com.hhsdp.entity.Follow;
import com.hhsdp.mapper.FollowMapper;
import com.hhsdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hhsdp.service.IUserService;
import com.hhsdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;




@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followerId, boolean isFollow) {

        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //判断是关注还是取关
        if(isFollow){
            //关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followerId);
            boolean isSuccess = save(follow);
            if(isSuccess){
                stringRedisTemplate.opsForSet().add(key,followerId.toString());
            }
        }else{
            //取关
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followerId));
            if(isSuccess){
                stringRedisTemplate.opsForSet().remove(key,followerId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followerId) {
        Long userId = UserHolder.getUser().getId();
        //先查询是否已关注
        Integer count = query().eq("user_id", userId).eq("follow_user_id",followerId).count();
        return Result.ok(count > 0);
    }

    //查询共同关注
    @Override
    public Result common(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key1 = "follows:" + id;
        String key2 = "follows:" + userId;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if(intersect == null){
            return Result.ok(Collections.emptyList());
        }
        List<Long> list = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDto> userDtos = userService.listByIds(list)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDto.class))
                .collect(Collectors.toList());
        return Result.ok(userDtos);
    }

}
