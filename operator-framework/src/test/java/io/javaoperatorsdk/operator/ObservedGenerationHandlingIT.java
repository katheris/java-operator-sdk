package io.javaoperatorsdk.operator;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService;
import io.javaoperatorsdk.operator.junit.OperatorExtension;
import io.javaoperatorsdk.operator.sample.observedgeneration.ObservedGenerationTestCustomResource;
import io.javaoperatorsdk.operator.sample.observedgeneration.ObservedGenerationTestReconciler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class ObservedGenerationHandlingIT {
  @RegisterExtension
  OperatorExtension operator =
      OperatorExtension.builder()
          .withConfigurationService(DefaultConfigurationService.instance())
          .withReconciler(new ObservedGenerationTestReconciler())
          .build();

  @Test
  public void testReconciliationOfNonCustomResourceAndStatusUpdate() {
    var resource = new ObservedGenerationTestCustomResource();
    resource.setMetadata(new ObjectMeta());
    resource.getMetadata().setName("observed-gen1");

    var createdResource = operator.create(ObservedGenerationTestCustomResource.class, resource);

    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
      var d = operator.get(ObservedGenerationTestCustomResource.class,
          createdResource.getMetadata().getName());
      assertThat(d.getStatus().getObservedGeneration()).isNotNull();
      assertThat(d.getStatus().getObservedGeneration()).isEqualTo(1);
    });
  }
}
