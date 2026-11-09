package git.ggwzz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 产业链管理系统启动类。
 *
 * <p>统一扫描 {@code git.ggwzz.**.mapper} 下的 Mapper 接口，开启异步支持以供操作日志异步落库。</p>
 */
@SpringBootApplication
@EnableAsync
@MapperScan("git.ggwzz.**.mapper")
public class IndustryChainApplication {

    public static void main(String[] args) {
        SpringApplication.run(IndustryChainApplication.class, args);
    }
}
