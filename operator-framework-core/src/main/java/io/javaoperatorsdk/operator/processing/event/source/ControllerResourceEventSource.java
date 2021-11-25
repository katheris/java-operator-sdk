package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.MDCUtils;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * This is a special case since is not bound to a single custom resource
 */
public class ControllerResourceEventSource<T extends HasMetadata>
    extends ResourceEventSource<T, ControllerConfiguration<T>> {

  public static final String ANY_NAMESPACE_MAP_KEY = "anyNamespace";

  private final Controller<T> controller;
  private OnceWhitelistEventFilterEventFilter<T> onceWhitelistEventFilterEventFilter;

  public ControllerResourceEventSource(Controller<T> controller) {
    super(controller.getConfiguration(),
        controller.getCRClient(),
        controller.getConfiguration().getConfigurationService().getResourceCloner());
    this.controller = controller;
  }

  @Override
  protected ResourceEventFilter<T, ControllerConfiguration<T>> initFilter(
      ControllerConfiguration<T> configuration) {
    var filters = new ResourceEventFilter[] {
        ResourceEventFilters.finalizerNeededAndApplied(),
        ResourceEventFilters.markedForDeletion(),
        ResourceEventFilters.and(
            configuration.getEventFilter(),
            ResourceEventFilters.generationAware()),
        null
    };

    if (configuration.isGenerationAware()) {
      onceWhitelistEventFilterEventFilter = new OnceWhitelistEventFilterEventFilter<>();
      filters[filters.length - 1] = onceWhitelistEventFilterEventFilter;
    } else {
      onceWhitelistEventFilterEventFilter = null;
    }
    return ResourceEventFilters.or(filters);
  }

  @Override
  public void start() {
    try {
      super.start();
    } catch (Exception e) {
      if (e instanceof KubernetesClientException) {
        KubernetesClientException ke = (KubernetesClientException) e;
        if (404 == ke.getCode()) {
          // only throw MissingCRDException if the 404 error occurs on the target CRD
          final var targetCRDName = controller.getConfiguration().getResourceTypeName();
          if (targetCRDName.equals(ke.getFullResourceName())) {
            throw new MissingCRDException(targetCRDName, null, e.getMessage(), e);
          }
        }
      }
      throw e;
    }
  }

  public void eventReceived(ResourceAction action, T resource, T oldResource) {
    try {
      MDCUtils.addResourceInfo(resource);
      super.eventReceived(action, resource, oldResource);
    } finally {
      MDCUtils.removeResourceInfo();
    }
  }

  /**
   * This will ensure that the next event received after this method is called will not be filtered
   * out.
   *
   * @param resourceID - to which the event is related
   */
  public void whitelistNextEvent(ResourceID resourceID) {
    if (onceWhitelistEventFilterEventFilter != null) {
      onceWhitelistEventFilterEventFilter.whitelistNextEvent(resourceID);
    }
  }

}
