package com.github.houbb.junitperf.core.jupiter.provider;

import com.github.houbb.junitperf.core.annotation.JunitPerfConfig;
import com.github.houbb.junitperf.core.jupiter.context.PerfConfigContext;

import org.apiguardian.api.API;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;
import org.junit.platform.commons.support.AnnotationSupport;

import java.util.stream.Stream;

/**
 * <p> 配置实现 </p>
 */
@API(status = API.Status.INTERNAL)
public class PerfConfigProvider implements TestTemplateInvocationContextProvider {


@Override//从扩展上下文中拿到测试方法，判断是否测试方法被标记了@JunitPerfConfig注解，则返回true，否则返回false。
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
                .filter(m -> AnnotationSupport.isAnnotated(m, JunitPerfConfig.class))
                .isPresent();//
    }


@Override//通过扩展上下文构造出一个性能测试配置上下文，并返回性能测试配置上下文
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {//扩展配置信息。
        return Stream.of(new PerfConfigContext(context));
    }
}
