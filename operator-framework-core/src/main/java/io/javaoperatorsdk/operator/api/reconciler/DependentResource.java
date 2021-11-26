package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.javaoperatorsdk.operator.processing.event.source.InformerEventSource;

public interface DependentResource<R extends HasMetadata> {
  String getName();

  R build();

  InformerEventSource<R> source();

  default R fetchFor(HasMetadata owner) {
    return source().getAssociated(owner);
  }

  Configuration<R> getConfiguration();

  default R update(R fetched) {
    return fetched;
  }

  interface Configuration<R extends HasMetadata>
      extends ResourceConfiguration<R, Configuration<R>> {
    default boolean created() {
      return true;
    }

    default boolean updated() {
      return false;
    }

    default boolean owned() {
      return true;
    }
  }
}
