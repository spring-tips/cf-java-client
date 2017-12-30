package com.example.deployer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.GetApplicationRequest;
import org.cloudfoundry.operations.applications.PushApplicationRequest;
import org.cloudfoundry.operations.applications.RestartApplicationRequest;
import org.cloudfoundry.operations.services.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * The purpose here is to demonstrate using the Pivotal Reactor Java client to write a program that
 * can deploy another program. This program could be made interactive using Spring Shell. Or, not.
 * It's entirely up to you. In CD there should be one kind of script to deploy everything. As Kelsey Grammar jokes,
 * "You know what `bash` is? `bash` is the reason people leave IT." We can simplify their lives
 * with self-deploying applications. Imagine a program that you can invoke on the shell and it just works. Or, you can interact with
 * it in the interactive environment afforded by Spring Shell. Either way, same kind of thing, usable in any context.
 *
 * @author <a href="mailto:josh@joshlong.com">Josh Long</a>
 */
@SpringBootApplication
public class DeployerApplication {

	private Log log = LogFactory.getLog(getClass());

	private final CloudFoundryOperations operations;

	public DeployerApplication(CloudFoundryOperations operations) {
		this.operations = operations;
	}

	@Bean
	ApplicationRunner client(@Value("file://${ARTIFACT:${HOME}/Desktop/in.jar}") Resource jar) {
		return args -> {
			Assert.isTrue(jar.exists(), "the input .jar must exist");
			String svcName = "cnj-mysql";
			String appName = "cnj-hda";
			long start = System.currentTimeMillis();
			createServiceInstanceIfMissing("p-mysql", svcName, ServicePlan::getFree)
					.then(pushApplication(appName, 1, jar, true))
					.then(bindApplicationToServiceInstance(appName, svcName))
					.then(restartApplication(appName))
					.log()
					.subscribe(x -> log.info(String.format("your application '%s' has been deployed and bound to the service '%s'. It took %s ms to complete.", appName, svcName,
							System.currentTimeMillis() - start)));
		};
	}

	Mono<Boolean> restartApplication(String appName) {
		return operations.applications().restart(RestartApplicationRequest.builder().name(appName).build())
				.then(Mono.just(Boolean.TRUE));
	}

	Mono<Boolean> bindApplicationToServiceInstance(String applicationName, String serviceInstanceName) {
		return this.operations.services().bind(BindServiceInstanceRequest.builder()
				.applicationName(applicationName).serviceInstanceName(serviceInstanceName).build())
				.then(Mono.just(Boolean.TRUE));
	}

	Mono<ApplicationDetail> pushApplication(String appName, int replicas, Resource jar, boolean noStart) throws Exception {
		Path path = jar.getFile().toPath();
		return operations
				.applications()
				.push(PushApplicationRequest.builder().path(path.toAbsolutePath()).randomRoute(true).noStart(noStart).instances(replicas).name(appName).build())
				.then(operations.applications().get(GetApplicationRequest.builder().name(appName).build()));
	}

	Mono<ServiceInstanceSummary> createServiceInstanceIfMissing(String service, String mysqlInstanceName, Predicate<ServicePlan> planPredicate) {

		Flux<ServiceInstanceSummary> existing = operations
				.services()
				.listInstances()
				.filter(si -> si.getName().equalsIgnoreCase(mysqlInstanceName));

		Flux<ServiceInstanceSummary> summaryFlux = operations
				.services()
				.listServiceOfferings(ListServiceOfferingsRequest.builder().build())
				.filter(so -> so.getLabel().equalsIgnoreCase(service))
				.flatMap(so -> {
					ServicePlan freePlan = so.getServicePlans().stream().filter(planPredicate)
							.findAny().orElseThrow(() -> new RuntimeException("couldn't find a free plan!"));
					CreateServiceInstanceRequest instanceRequest = CreateServiceInstanceRequest
							.builder()
							.planName(freePlan.getName())
							.serviceName(so.getLabel())
							.serviceInstanceName(mysqlInstanceName)
							.build();
					return operations.services().createInstance(instanceRequest);
				})
				.thenMany(existing);

		return existing
				.switchIfEmpty(summaryFlux)
				.singleOrEmpty();
	}

	public static void main(String[] args) throws Exception {
		SpringApplication.run(DeployerApplication.class, args);
	}
}
