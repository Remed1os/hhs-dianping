package com.hhsdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhsdp.dto.Result;
import com.hhsdp.dto.ScrollResult;
import com.hhsdp.dto.UserDto;
import com.hhsdp.entity.Blog;
import com.hhsdp.entity.Follow;
import com.hhsdp.mapper.BlogMapper;
import com.hhsdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hhsdp.service.IFollowService;
import com.hhsdp.service.IUserService;
import com.hhsdp.utils.SystemConstants;
import com.hhsdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hhsdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hhsdp.utils.RedisConstants.FEED_KEY;


@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDto user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if(!isSuccess){
            return Result.fail("新增笔记失败！");
        }
        //查询所有的粉丝
        List<Follow> followList = followService.query().eq("follow_user_id",user.getId()).list();
        //将文章推送到所有粉丝的收件箱
        for (Follow follow : followList) {
            Long id = follow.getId();
            String key = FEED_KEY + id;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //获取当前用户、
        UserDto user = UserHolder.getUser();
        //从邮箱中获取博文
        String key = FEED_KEY + user.getId();
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate
                .opsForZSet().reverseRangeByScoreWithScores(key, max, 0, offset, 2);
        //非空判断
        if(typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        // 4.解析数据：blogId、minTime（时间戳）、offset
        ArrayList<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int sameValue = 1;

        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //获取id
            ids.add(Long.valueOf(typedTuple.getValue()));
            //获取时间戳
            long time = typedTuple.getScore().longValue();
            //判断是否有相同分数的值
            if(time == minTime) {
                sameValue++;
            }else {
                sameValue = 1;
                minTime = time;
            }
        }
        // 5.根据id查询blog,注意查询到的数据的排序，直接用query是in查询
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")").list();

        for (Blog blog : blogs) {
            // 5.1.查询blog有关的用户
            queryBlogUser(blog);
            // 5.2.查询blog是否被点赞
            isBlogLiked(blog);
        }

        // 6.封装并返回
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(sameValue);
        r.setMinTime(minTime);
        return Result.ok(r);
    }


    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        //根据id查询笔记信息
        Blog blog = getById(id);

        if (blog == null){
            return Result.fail("笔记不存在");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    public void isBlogLiked(Blog blog) {
        //1.获取登入用户
        UserDto user = UserHolder.getUser();
        if (user != null) {
            String userId = user.getId().toString();
            //2.判断当前用户是否点赞
            String key = BLOG_LIKED_KEY + blog.getId();
            Double score = stringRedisTemplate.opsForZSet().score(key, userId);
            blog.setIsLike(score != null);
        }
    }

    @Override
    public Result likeBlog(Long id) {
        //1.获取登入用户
        UserDto user = UserHolder.getUser();
        if(user == null){
            //用户未登入，无需查询自身点赞数据
            return null;
        }
        String userId = user.getId().toString();
        //2.判断当前用户是否点赞
        String key = BLOG_LIKED_KEY + id;
        Double isMember = stringRedisTemplate.opsForZSet().score(key, userId);
        //未点赞，+1，将用户添加到点赞人员set集合中
        if(isMember == null){
            // 点赞数量 + 1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //将用户添加到set集合中
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(key,userId,System.currentTimeMillis());
            }
        }else{
        //已点赞，-1，将用户从集合中移除
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            //将用户从set集合中移除
            if(isSuccess){
                stringRedisTemplate.opsForZSet().remove(key,userId);
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        //从zset中查询点赞前5的用户
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5 == null || top5.isEmpty()){
            return Result.ok(Collections.EMPTY_LIST);
        }
        //解析用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        List<UserDto> userDtos = userService.query()
                .in("id",ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDto.class))
                .collect(Collectors.toList());
        //返回结果
        return Result.ok(userDtos);
    }



    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        com.hhsdp.entity.User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

}
