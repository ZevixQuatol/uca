package com.twlic.uca.base.registry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class InstanceRegistry {

    private static final Comparator<RegisteredInstance> INSTANCE_ORDER =
            Comparator.comparing(RegisteredInstance::instanceId);

    private final ConcurrentMap<String, ApplicationEntry> applications = new ConcurrentHashMap<>();
    private final Clock clock;
    private final RoundRobinSelector roundRobinSelector = new RoundRobinSelector();

    public InstanceRegistry(Clock clock) {
        this.clock = clock;
    }

    public RegistrationResult register(InstanceRegistration registration) {
        Instant now = clock.instant();
        AtomicReference<RegistrationResult> result = new AtomicReference<>();

        applications.compute(registration.applicationCode(), (applicationCode, existingApplication) -> {
            ApplicationEntry application = existingApplication == null
                    ? new ApplicationEntry(registration.applicationName())
                    : existingApplication;
            application.updateApplicationName(registration.applicationName());
            application.instances.compute(registration.instanceId(), (instanceId, existingInstance) -> {
                boolean created = existingInstance == null;
                RegisteredInstance instance = created
                        ? RegisteredInstance.firstRegistration(registration, now)
                        : existingInstance.reRegister(registration, now);
                result.set(new RegistrationResult(created, instance));
                return instance;
            });
            return application;
        });

        return result.get();
    }

    public List<ApplicationSnapshot> findAll() {
        return applications.entrySet().stream()
                .map(entry -> entry.getValue().snapshot(entry.getKey()))
                .sorted(Comparator.comparing(ApplicationSnapshot::applicationCode))
                .toList();
    }

    public RegisteredInstance heartbeat(String applicationCode, String instanceId) {
        Instant now = clock.instant();
        AtomicReference<RegisteredInstance> result = new AtomicReference<>();

        applications.computeIfPresent(applicationCode, (code, application) -> {
            application.instances.computeIfPresent(instanceId, (id, instance) -> {
                RegisteredInstance renewed = instance.heartbeat(now);
                result.set(renewed);
                return renewed;
            });
            return application;
        });

        if (result.get() == null) {
            throw new RegistryException(
                    RegistryError.INSTANCE_NOT_FOUND,
                    "Instance '%s/%s' is not registered".formatted(applicationCode, instanceId));
        }
        return result.get();
    }

    public void deregister(String applicationCode, String instanceId) {
        applications.computeIfPresent(applicationCode, (code, application) -> {
            application.instances.remove(instanceId);
            return application.instances.isEmpty() ? null : application;
        });
    }

    public void updateLifecycle(Duration heartbeatTimeout, Duration offlineRetention) {
        Instant now = clock.instant();
        applications.forEach((applicationCode, ignored) ->
                applications.computeIfPresent(applicationCode, (code, application) -> {
                    application.instances.forEach((instanceId, ignoredInstance) ->
                            application.instances.computeIfPresent(instanceId, (id, instance) -> {
                                if (instance.status() == InstanceStatus.ONLINE
                                        && hasReached(now, instance.lastHeartbeatAt(), heartbeatTimeout)) {
                                    return instance.markOffline(now);
                                }
                                if (instance.status() == InstanceStatus.OFFLINE
                                        && hasReached(now, instance.statusChangedAt(), offlineRetention)) {
                                    return null;
                                }
                                return instance;
                            }));
                    return application.instances.isEmpty() ? null : application;
                }));
    }

    public RegisteredInstance discover(String applicationCode) {
        ApplicationEntry application = applications.get(applicationCode);
        if (application == null) {
            throw new RegistryException(
                    RegistryError.APPLICATION_NOT_FOUND,
                    "Application '%s' is not registered".formatted(applicationCode));
        }

        List<RegisteredInstance> onlineInstances = application.instances.values().stream()
                .filter(instance -> instance.status() == InstanceStatus.ONLINE)
                .toList();
        if (onlineInstances.isEmpty()) {
            throw new RegistryException(
                    RegistryError.NO_ONLINE_INSTANCE,
                    "Application '%s' has no online instances".formatted(applicationCode));
        }
        return roundRobinSelector.select(onlineInstances, application.roundRobinCursor);
    }

    public ApplicationSnapshot findApplication(String applicationCode) {
        ApplicationEntry application = applications.get(applicationCode);
        if (application == null) {
            throw new RegistryException(
                    RegistryError.APPLICATION_NOT_FOUND,
                    "Application '%s' is not registered".formatted(applicationCode));
        }
        return application.snapshot(applicationCode);
    }

    private static boolean hasReached(Instant now, Instant since, Duration threshold) {
        return !since.plus(threshold).isAfter(now);
    }

    private static final class ApplicationEntry {

        private final ConcurrentMap<String, RegisteredInstance> instances = new ConcurrentHashMap<>();
        private final AtomicLong roundRobinCursor = new AtomicLong();
        private volatile String applicationName;

        private ApplicationEntry(String applicationName) {
            this.applicationName = applicationName;
        }

        private void updateApplicationName(String applicationName) {
            this.applicationName = applicationName;
            instances.replaceAll((instanceId, instance) -> instance.withApplicationName(applicationName));
        }

        private ApplicationSnapshot snapshot(String applicationCode) {
            List<RegisteredInstance> instanceSnapshots = instances.values().stream()
                    .sorted(INSTANCE_ORDER)
                    .toList();
            return new ApplicationSnapshot(applicationCode, applicationName, instanceSnapshots);
        }
    }
}
