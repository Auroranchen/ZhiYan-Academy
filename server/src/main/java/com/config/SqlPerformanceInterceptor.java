package com.config;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 慢 SQL 监控插件：统计每条 SQL 的执行耗时，
 * 超过阈值（app.sql.slow-threshold-ms，默认 300ms）时以 WARN 记录 Mapper 方法与耗时。
 * MyBatis-Spring-Boot 会自动将实现 Interceptor 的 Bean 注册到 SqlSessionFactory。
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class SqlPerformanceInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger("SLOW_SQL");

    @Value("${app.sql.slow-threshold-ms:300}")
    private long slowThresholdMs;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (cost >= slowThresholdMs) {
                Object[] args = invocation.getArgs();
                String statementId = (args.length > 0 && args[0] instanceof MappedStatement)
                        ? ((MappedStatement) args[0]).getId()
                        : "unknown";
                log.warn("[慢SQL] 耗时 {} ms，Mapper={}", cost, statementId);
            }
        }
    }
}
