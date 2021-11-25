package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventFilter;

public interface ControllerConfiguration<R extends HasMetadata> extends
    ResourceConfiguration<R, ControllerConfiguration<R>> {

  default String getName() {
    return ControllerUtils.getDefaultReconcilerName(getAssociatedReconcilerClassName());
  }

  default String getFinalizer() {
    return ControllerUtils.getDefaultFinalizerName(getResourceTypeName());
  }

  default boolean isGenerationAware() {
    return true;
  }

  String getAssociatedReconcilerClassName();

  default RetryConfiguration getRetryConfiguration() {
    return RetryConfiguration.DEFAULT;
  }

  default boolean useFinalizer() {
    return !io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration.NO_FINALIZER
        .equals(getFinalizer());
  }

  @Override
  default ResourceEventFilter<R, ControllerConfiguration<R>> getEventFilter() {
    return ResourceConfiguration.super.getEventFilter();
  }
}
