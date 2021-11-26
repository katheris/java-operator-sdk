package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.Cloner;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.javaoperatorsdk.operator.processing.ResourceCache;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getName;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getUID;
import static io.javaoperatorsdk.operator.processing.KubernetesResourceUtils.getVersion;

public abstract class ResourceEventSource<T extends HasMetadata, U extends ResourceConfiguration<T, U>>
    extends AbstractEventSource implements
    ResourceEventHandler<T>, ResourceCache<T> {

  private static final String ANY_NAMESPACE_MAP_KEY = "anyNamespace";
  private static final Logger log = LoggerFactory.getLogger(ResourceEventSource.class);

  private final Map<String, SharedIndexInformer<T>> informers = new ConcurrentHashMap<>();
  private final Cloner cloner;
  private final ResourceEventFilter<T, U> filter;
  private final U configuration;
  private final MixedOperation<T, KubernetesResourceList<T>, Resource<T>> client;

  public ResourceEventSource(U configuration,
      MixedOperation<T, KubernetesResourceList<T>, Resource<T>> client, Cloner cloner) {
    this.configuration = configuration;
    this.client = client;
    this.filter = initFilter(configuration);
    this.cloner = cloner;
  }

  protected abstract ResourceEventFilter<T, U> initFilter(U configuration);

  void eventReceived(ResourceAction action, T resource, T oldResource) {
    log.debug("Event received for resource: {}", getName(resource));
    if (filter.acceptChange(configuration, oldResource, resource)) {
      eventHandler.handleEvent(new ResourceEvent(action, ResourceID.fromResource(resource)));
    } else {
      log.debug(
          "Skipping event handling resource {} with version: {}",
          getUID(resource),
          getVersion(resource));
    }
  }

  @Override
  public void onAdd(T resource) {
    eventReceived(ResourceAction.ADDED, resource, null);
  }

  @Override
  public void onUpdate(T oldResource, T newResource) {
    eventReceived(ResourceAction.UPDATED, newResource, oldResource);
  }

  @Override
  public void onDelete(T resource, boolean b) {
    eventReceived(ResourceAction.DELETED, resource, null);
  }

  @Override
  public void start() throws OperatorException {
    final var targetNamespaces = configuration.getEffectiveNamespaces();
    final var labelSelector = configuration.getLabelSelector();

    if (ResourceConfiguration.allNamespacesWatched(targetNamespaces)) {
      final var filteredBySelectorClient =
          client.inAnyNamespace().withLabelSelector(labelSelector);
      final var informer =
          createAndRunInformerFor(filteredBySelectorClient, ANY_NAMESPACE_MAP_KEY);
      log.debug("Registered {} -> {} for any namespace", this, informer);
    } else {
      targetNamespaces.forEach(
          ns -> {
            final var informer = createAndRunInformerFor(
                client.inNamespace(ns).withLabelSelector(labelSelector), ns);
            log.debug("Registered {} -> {} for namespace: {}", this, informer,
                ns);
          });
    }
  }

  private SharedIndexInformer<T> createAndRunInformerFor(
      FilterWatchListDeletable<T, KubernetesResourceList<T>> filteredBySelectorClient, String key) {
    var informer = filteredBySelectorClient.runnableInformer(0);
    informer.addEventHandler(this);
    informers.put(key, informer);
    informer.run();
    return informer;
  }

  @Override
  public void stop() {
    for (SharedIndexInformer<T> informer : informers.values()) {
      try {
        log.info("Stopping informer {} -> {}", this, informer);
        informer.stop();
      } catch (Exception e) {
        log.warn("Error stopping informer {} -> {}", this, informer, e);
      }
    }
  }

  @Override
  public Optional<T> getCustomResource(ResourceID resourceID) {
    final var informer = informers.get(
        resourceID.getNamespace().orElse(ANY_NAMESPACE_MAP_KEY));
    final var resource = informer.getStore()
        .getByKey(Cache.namespaceKeyFunc(resourceID.getNamespace().orElse(null),
            resourceID.getName()));
    return Optional.ofNullable(cloner.clone(resource));
  }

  /**
   * @return shared informers by namespace. If custom resource is not namespace scoped use
   *         CustomResourceEventSource.ANY_NAMESPACE_MAP_KEY
   */
  public Map<String, SharedIndexInformer<T>> getInformers() {
    return Collections.unmodifiableMap(informers);
  }

  public SharedIndexInformer<T> getInformer(String namespace) {
    return informers.get(Objects.requireNonNullElse(namespace, ANY_NAMESPACE_MAP_KEY));
  }

}
