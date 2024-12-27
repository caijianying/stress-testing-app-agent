package com.xiaobaicai.stress.testing.app.agent;

import com.mysql.cj.jdbc.ClientPreparedStatement;
import com.xiaobaicai.stress.testing.app.agent.core.AgentConfig;
import com.xiaobaicai.stress.testing.app.agent.core.ContextManager;
import com.xiaobaicai.stress.testing.app.agent.core.MeltDownManager;
import com.xiaobaicai.stress.testing.app.agent.core.StressTestingConstant;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.*;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.apache.ibatis.session.SqlSession;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.Callable;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * @author xiaobaicai
 * @description 关注微信公众号【程序员小白菜】领取源码
 * @date 2024/12/4 星期三 15:09
 */
public class StressTestingAppAgent {

    public static void premain(String agentArgs, Instrumentation inst) {
        AgentConfig.readArgs(agentArgs);

        AgentBuilder agentBuilder = new AgentBuilder.Default();

        agentBuilder = agentBuilder.type(named("org.springframework.web.servlet.DispatcherServlet")).transform((builder, typeDescription, classLoader, module) -> builder.method(named("doDispatch")).intercept(MethodDelegation.to(new DispatcherServletInterceptor())));
        agentBuilder = agentBuilder.type(named("org.apache.ibatis.mapping.BoundSql")).transform((builder, typeDescription, classLoader, module) -> builder.method(named("getSql")).intercept(MethodDelegation.to(new BoundSqlInterceptor())));
        agentBuilder = agentBuilder.type(named("org.apache.ibatis.session.defaults.DefaultSqlSessionFactory")).transform((builder, typeDescription, classLoader, module) -> builder.method(named("openSession")).intercept(MethodDelegation.to(new DefaultSqlSessionFactoryInterceptor())));
        agentBuilder = agentBuilder.type(named("com.mysql.cj.jdbc.ClientPreparedStatement")).transform((builder, typeDescription, classLoader, module) -> builder.method(named("execute")).intercept(MethodDelegation.to(new ClientPreparedStatementInterceptor())));
        agentBuilder = agentBuilder.type(isAnnotatedWith(named("org.springframework.stereotype.Service"))).transform((builder, typeDescription, classLoader, module) -> builder.method(isPublic().and(not(named("getBaseMapper")))).intercept(MethodDelegation.to(new ServiceAnnotationInterceptor())));


        agentBuilder.with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION).installOn(inst);
    }


    public static void agentmain(String agentArgs, Instrumentation inst) {

    }


    public static class DispatcherServletInterceptor {

        @RuntimeType
        public Object intercept(@This Object obj, @Origin Class<?> clazz, @AllArguments Object[] allArguments, @Origin Method method, @SuperCall Callable<?> callable) throws Throwable {
            String url = null;
            for (Object allArgument : allArguments) {
                boolean matched = Arrays.stream(allArgument.getClass().getInterfaces()).anyMatch(t -> "jakarta.servlet.http.HttpServletRequest".equals(t.getName()));
                String headerValue = null;
                if (matched) {
                    Method getHeader = allArgument.getClass().getDeclaredMethod("getHeader", String.class);
                    getHeader.setAccessible(true);
                    Object invoke = getHeader.invoke(allArgument, StressTestingConstant.HEADER_NAME_STRESS_TESTING_FLAG);
                    headerValue = invoke == null ? null : invoke.toString();

                    Method getRequestURI = allArgument.getClass().getDeclaredMethod("getRequestURI");
                    getRequestURI.setAccessible(true);
                    url = (String) getRequestURI.invoke(allArgument);
                }

                if (headerValue != null) {
                    ContextManager.setProperty(StressTestingConstant.IN_PT_KEY, StressTestingConstant.HEADER_VALUE_STRESS_TESTING_FLAG.equals(headerValue));
                }
            }
            ContextManager.createSpan(url);
            long start = MeltDownManager.meltDownIfNecessary();
            Object call = null;
            try {
                call = callable.call();
            } catch (Throwable ex) {
                // 这里执行方法出现异常，也需要标记
                MeltDownManager.markMeltDownFlag(start);
            }
            MeltDownManager.markMeltDownFlag(start);
            ContextManager.finishSpan();

            return call;
        }
    }

    public static class BoundSqlInterceptor {
        Set<Object> methodInvokeSet = new HashSet<>();

        @RuntimeType
        public Object intercept(@This Object obj, @Origin Class<?> clazz, @AllArguments Object[] allArguments, @Origin Method method, @SuperCall Callable<?> callable) throws Throwable {

            Boolean inPt = (Boolean) ContextManager.getProperty(StressTestingConstant.IN_PT_KEY);
            if (inPt != null && inPt) {
                if (methodInvokeSet.add(obj)) {
                    // 调用getSql方法获取Sql
                    Method getSqlMethod = obj.getClass().getDeclaredMethod("getSql");
                    getSqlMethod.setAccessible(true);
                    Object sqlObj = getSqlMethod.invoke(obj);
                    String originalSql = sqlObj == null ? null : sqlObj.toString();
                    System.out.println("Original SQL: " + originalSql);

                    // 2. 获取压测流量写入模式：影子表或者影子库
                    String shadowMode = AgentConfig.getShadowMode();
                    Field sqlField = obj.getClass().getDeclaredField("sql");

                    // 3. 写入压测流量
                    // 影子表
                    if (AgentConfig.SHADOW_MODE_TABLE.equals(shadowMode)) {
                        // 提取SQL中表名，并替换为 ${TABLE_NAME}_
                        String newSql = this.replaceSqlTables(originalSql);
                        System.out.println("Shadow Table SQL: " + newSql);
                        sqlField.setAccessible(true);
                        sqlField.set(obj, newSql);
                    }

                    // 影子库
                    if (AgentConfig.SHADOW_MODE_DB.equals(shadowMode)) {
                        // 上下文中获取数据库名称
                        Object dataBaseName = ContextManager.getProperty(StressTestingConstant.DATABASE_NAME_KEY);
                        String newDataBaseName = dataBaseName + "_";
                        String modifiedSql = "use " + newDataBaseName + ";" + originalSql;
                        System.out.println("Shadow Database SQL: " + modifiedSql);
                        sqlField.setAccessible(true);
                        sqlField.set(obj, modifiedSql);
                    }
                }
            }

            Object call = null;
            try {
                call = callable.call();
            } catch (Throwable ignored) {

            }
            return call;
        }

        private String replaceSqlTables(String sql) {
            Statement statement = null;
            try {
                statement = CCJSqlParserUtil.parse(sql);
            } catch (JSQLParserException ignored) {

            }
            if (statement == null) {
                return sql;
            }
            Set<String> tableSet = new TablesNamesFinder().getTables(statement);
            for (String tableName : tableSet) {
                sql = sql.replace(tableName, tableName + "_");
            }
            return sql;
        }
    }

    public static class DefaultSqlSessionFactoryInterceptor {

        @RuntimeType
        public Object intercept(@This Object obj, @Origin Class<?> clazz, @AllArguments Object[] allArguments, @Origin Method method, @SuperCall Callable<?> callable) {
            Object call = null;
            try {
                call = callable.call();
            } catch (Throwable ignored) {

            }

            if (call != null) {
                try {
                    SqlSession sqlSession = (SqlSession) call;
                    ContextManager.setProperty(StressTestingConstant.DATABASE_NAME_KEY, sqlSession.getConnection().getCatalog());
                } catch (Throwable ex) {
                    System.err.println(ex.getMessage());
                }
            }
            return call;
        }
    }

    public static class ClientPreparedStatementInterceptor {
        @RuntimeType
        public Object intercept(@This Object obj, @Origin Class<?> clazz, @AllArguments Object[] allArguments, @Origin Method method, @SuperCall Callable<?> callable) {
            String preparedSql = null;
            try {
                if (obj instanceof ClientPreparedStatement) {
                    ClientPreparedStatement stmt = (ClientPreparedStatement) obj;
                    preparedSql = stmt.getPreparedSql();
                }

            } catch (Throwable ignored) {

            }

            ContextManager.createSpan(method.getName() + " Execute SQL. " + (preparedSql == null ? "" : preparedSql));
            Object call = null;
            try {
                call = callable.call();
            } catch (Throwable ignored) {

            }

            ContextManager.finishSpan();
            return call;
        }
    }

    public static class ServiceAnnotationInterceptor {

        @RuntimeType
        public Object intercept(@This Object obj, @Origin Class<?> clazz, @AllArguments Object[] allArguments, @Origin Method method, @SuperCall Callable<?> callable) {
            ContextManager.createSpan(obj.getClass().getName() + "." + method.getName());
            Object call = null;
            try {
                call = callable.call();
            } catch (Throwable ignored) {

            }
            ContextManager.finishSpan();
            return call;
        }
    }
}
