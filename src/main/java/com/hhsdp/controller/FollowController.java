package com.hhsdp.controller;


import com.hhsdp.dto.Result;
import com.hhsdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;


@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id")Long id,@PathVariable("isFollow")boolean isFollow){
        return followService.follow(id,isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id")Long id){
        return followService.isFollow(id);
    }

    @GetMapping("/commmon/{id}")
    public Result common(@PathVariable("id") Long id){
        return followService.common(id);
    }

}
