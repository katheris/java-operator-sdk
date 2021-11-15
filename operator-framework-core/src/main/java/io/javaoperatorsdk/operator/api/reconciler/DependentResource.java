package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.internal.InformerEventSource;

public interface DependentResource<R extends HasMetadata> {
  String getName();

  R build();

  InformerEventSource<R> source();

  default R fetchFor(HasMetadata owner) {
    return source().getAssociated(owner);
  }

  Configuration getConfiguration();

  default R update(R fetched) {
    return fetched;
  }

  interface Configuration {
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
