package io.github.temporalrift.game.shared.infrastructure.adapter.in.kafka;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.util.ReflectionUtils;

/**
 * Finds the {@code @KafkaListener} methods of the application's beans without starting any listener container, so the
 * simulation delivery entry can invoke exactly the methods a broker would. The beans are the proxied ones, so their
 * transaction boundaries apply.
 */
final class KafkaListenerRegistry implements BeanPostProcessor, Ordered {

    private final Environment environment;
    private final Map<String, List<Listener>> listenersByTopic = new ConcurrentHashMap<>();

    KafkaListenerRegistry(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        var targetClass = AopProxyUtils.ultimateTargetClass(bean);
        MethodIntrospector.selectMethods(targetClass, (MethodIntrospector.MetadataLookup<KafkaListener>)
                        method -> AnnotatedElementUtils.findMergedAnnotation(method, KafkaListener.class))
                .forEach((method, annotation) -> register(bean, beanName, method, annotation));
        return bean;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /** The listeners subscribed to the topic, one per consumer group, ordered by group. */
    List<Listener> listenersOf(String topic) {
        return listenersByTopic.getOrDefault(topic, List.of());
    }

    /** Every listener, ordered by group then topic. */
    List<Listener> listeners() {
        return listenersByTopic.values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing(Listener::groupId).thenComparing(Listener::topic))
                .toList();
    }

    private void register(Object bean, String beanName, Method method, KafkaListener annotation) {
        var invocable = AopUtils.selectInvocableMethod(method, bean.getClass());
        ReflectionUtils.makeAccessible(invocable);
        var groupId = environment.resolvePlaceholders(annotation.groupId());
        for (var topic : annotation.topics()) {
            var resolved = environment.resolvePlaceholders(topic);
            listenersByTopic
                    .computeIfAbsent(resolved, key -> new CopyOnWriteArrayList<>())
                    .add(new Listener(groupId, resolved, beanName, bean, invocable));
        }
        listenersByTopic.values().forEach(listeners -> {
            var ordered = new ArrayList<>(listeners);
            ordered.sort(Comparator.comparing(Listener::groupId));
            listeners.clear();
            listeners.addAll(ordered);
        });
    }

    /** One {@code @KafkaListener} method bound to one topic of one consumer group. */
    record Listener(String groupId, String topic, String beanName, Object bean, Method method) {

        Class<?> parameterType() {
            return method.getParameterTypes()[0];
        }

        void invoke(Object argument) {
            try {
                method.invoke(bean, argument);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException cause) {
                    throw cause;
                }
                if (e.getCause() instanceof Error cause) {
                    throw cause;
                }
                throw new IllegalStateException("Listener " + this + " failed", e.getCause());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Listener " + this + " is not accessible", e);
            }
        }

        @Override
        public String toString() {
            return groupId + " on " + topic + " (" + Arrays.toString(method.getParameterTypes()) + ")";
        }
    }
}
