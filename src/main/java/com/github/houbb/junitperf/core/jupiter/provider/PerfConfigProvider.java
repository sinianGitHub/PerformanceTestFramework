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
 *
 * <pre> Created: 2018/6/28 下午5:53  </pre>
 * <pre> Project: junitperf  </pre>
 *
 * @author houbinbin
 * @version 1.0
 * @since JDK 1.7
 */
@API(status = API.Status.INTERNAL)
public class PerfConfigProvider implements TestTemplateInvocationContextProvider {
/**
 * @Author sinian
 * @Description :
 * 根据扫描到的扩展上下文判断，是否测试方法被标记了@JunitPerfConfig注解，则返回true，否则返回false。//扫描阶段，注解识别扩展
 * @Date 2024/4/21 18:35
 * @Param [context]
 * @return boolean
 **/

@Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
                .filter(m -> AnnotationSupport.isAnnotated(m, JunitPerfConfig.class))
                .isPresent();
    }

/**
 * @Author sinian
 * @Description :根据扫描到的扩展上下文，构造一个性能测试需要的上下文//
 * @Date 2024/4/21 18:42
 * @Param [context]
 * @return java.util.stream.Stream<org.junit.jupiter.api.extension.TestTemplateInvocationContext>
 **/

@Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {//扩展配置信息。
        return Stream.of(new PerfConfigContext(context));
    }

}
