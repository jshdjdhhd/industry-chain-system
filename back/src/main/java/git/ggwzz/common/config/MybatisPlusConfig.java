package git.ggwzz.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置。
 *
 * <p>注册分页拦截器（MySQL 方言），并提供公共字段自动填充处理器：
 * {@code created_at} 在插入时填充，{@code updated_at} 在插入与更新时填充。
 * 全项目时间字段统一采用自动填充方式，禁止再混用数据库默认值。</p>
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页拦截器。
     *
     * @return MyBatis-Plus 拦截器链
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 公共字段自动填充处理器。
     *
     * @return MetaObjectHandler
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                // auth 模块: createdAt / updatedAt
                strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
                // chain/matching 模块: createTime / updateTime
                strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, now);
                strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
            }
        };
    }
}