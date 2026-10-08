package com.airdropmc.integration;

import nl.pim16aap2.lightkeeper.framework.CommandResult;
import nl.pim16aap2.lightkeeper.framework.Condition;
import nl.pim16aap2.lightkeeper.framework.FrameworkHandleFactory;
import nl.pim16aap2.lightkeeper.framework.IFrameworkGatewayView;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.IServerControl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AirdropIntegrationSupportTest {
	@Test
	void remoteFixturePollsGrowingOutputSnapshots() {
		assertRemoteFixtureHandlesGrowingOutput(true);
	}

	@Test
	void remoteFixtureFindsResultInGrowingOutputSnapshots() {
		assertRemoteFixtureHandlesGrowingOutput(false);
	}

	@Test
	void ignoresPaperBackgroundYggdrasilKeyFetchFailure() {
		assertThat(AirdropIntegrationSupport.isExpectedServerError(
				"com.mojang.authlib.yggdrasil.YggdrasilServicesKeyInfo",
				"Failed to request yggdrasil public key"))
				.isTrue();
	}

	@Test
	void keepsOtherYggdrasilErrorsVisible() {
		assertThat(AirdropIntegrationSupport.isExpectedServerError(
				"com.mojang.authlib.yggdrasil.YggdrasilServicesKeyInfo",
				"Unexpected authentication failure"))
				.isFalse();
	}

	private static void assertRemoteFixtureHandlesGrowingOutput(boolean growsDuringPolling) {
		List<String> output = new ArrayList<>(List.of("server started"));
		AtomicBoolean growOutput = new AtomicBoolean();
		AtomicReference<String> expectedResult = new AtomicReference<>();
		IServerControl server = proxy(IServerControl.class, (proxy, method, arguments) -> {
			return switch (method.getName()) {
				case "output" -> {
					List<String> snapshot = List.copyOf(output);
					if (growOutput.get()) {
						output.add("background server output");
					}
					yield snapshot;
				}
				case "executeCommand" -> {
					String[] command = ((String) arguments[1]).split(" ");
					output.add("AIRDR_73_REMOTE token=unrelated action=prepare status=OK");
					expectedResult.set("AIRDR_73_REMOTE token=" + command[2]
							+ " action=" + command[1] + " status=OK");
					yield new CommandResult(true, "Command succeeded.");
				}
				default -> throw new UnsupportedOperationException(method.toString());
			};
		});
		ILightkeeperFramework framework = proxy(ILightkeeperFramework.class, (proxy, method, arguments) -> {
			return switch (method.getName()) {
				case "server" -> server;
				case "waitUntil" -> {
					Condition condition = (Condition) arguments[0];
					growOutput.set(growsDuringPolling);
					assertThat(condition.evaluate()).as("ignore unrelated marker").isFalse();
					output.add(expectedResult.get());
					assertThat(condition.evaluate()).as("await correlated marker").isTrue();
					growOutput.set(true);
					yield null;
				}
				default -> throw new UnsupportedOperationException(method.toString());
			};
		});
		IFrameworkGatewayView gateway = proxy(IFrameworkGatewayView.class, (proxy, method, arguments) -> {
			throw new UnsupportedOperationException(method.toString());
		});
		var world = FrameworkHandleFactory.worldHandle(gateway, "snapshot_test");

		assertThatCode(() -> assertThat(AirdropIntegrationSupport.remoteFixture(framework, "prepare", world, 0, 0))
				.isEqualTo(expectedResult.get())).doesNotThrowAnyException();
	}

	private static <T> T proxy(Class<T> type, InvocationHandler handler) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
	}
}
