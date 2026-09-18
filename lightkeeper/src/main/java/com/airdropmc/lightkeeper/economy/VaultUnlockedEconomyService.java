package com.airdropmc.lightkeeper.economy;

import net.milkbowl.vault2.economy.AsyncEconomy;
import net.milkbowl.vault2.economy.Economy;
import net.milkbowl.vault2.economy.EconomyResponse;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

final class VaultUnlockedEconomyService {

	static final String PROVIDER_NAME = "LightKeeper Economy";

	record Operation(
			EconomyOperationType operation,
			String caller,
			UUID playerId,
			BigDecimal amount,
			EconomyLedger.Transaction transaction
	) {
	}

	private VaultUnlockedEconomyService() {
	}

	static Economy create(EconomyLedger ledger, Executor executor, Consumer<Operation> observer) {
		Objects.requireNonNull(ledger, "ledger");
		Objects.requireNonNull(executor, "executor");
		Objects.requireNonNull(observer, "observer");

		AsyncEconomy async = proxy(AsyncEconomy.class,
				(proxy, method, arguments) -> invokeAsync(proxy, method, arguments, ledger, executor, observer));
		return proxy(Economy.class, (proxy, method, arguments) -> {
			if (method.getDeclaringClass() == Object.class) {
				return invokeObjectMethod(proxy, method, arguments, PROVIDER_NAME);
			}
			return switch (method.getName()) {
				case "isEnabled", "supportsAsync" -> true;
				case "getName" -> PROVIDER_NAME;
				case "async" -> Optional.of(async);
				default -> throw unsupported(method);
			};
		});
	}

	private static Object invokeAsync(
			Object proxy,
			Method method,
			Object[] arguments,
			EconomyLedger ledger,
			Executor executor,
			Consumer<Operation> observer
	) {
		if (method.getDeclaringClass() == Object.class) {
			return invokeObjectMethod(proxy, method, arguments, PROVIDER_NAME + " Async");
		}

		EconomyOperationType type = switch (method.getName()) {
			case "canWithdraw" -> EconomyOperationType.CAN_WITHDRAW;
			case "withdraw" -> EconomyOperationType.WITHDRAW;
			case "deposit" -> EconomyOperationType.DEPOSIT;
			default -> throw unsupported(method);
		};
		Request request = Request.from(method, arguments);
		return CompletableFuture.supplyAsync(() -> {
			EconomyLedger.Transaction transaction = switch (type) {
				case CAN_WITHDRAW -> ledger.canWithdraw(request.playerId(), request.amount());
				case WITHDRAW -> ledger.withdraw(request.playerId(), request.amount());
				case DEPOSIT -> ledger.deposit(request.playerId(), request.amount());
			};
			observer.accept(new Operation(
					type, request.caller(), request.playerId(), request.amount(), transaction));
			return response(request.amount(), transaction);
		}, executor);
	}

	private static EconomyResponse response(BigDecimal amount, EconomyLedger.Transaction transaction) {
		EconomyResponse.ResponseType type = transaction.success()
				? EconomyResponse.ResponseType.SUCCESS
				: EconomyResponse.ResponseType.FAILURE;
		return new EconomyResponse(amount, transaction.balance(), type, transaction.errorMessage());
	}

	private static Object invokeObjectMethod(Object proxy, Method method, Object[] arguments, String description) {
		return switch (method.getName()) {
			case "toString" -> description;
			case "hashCode" -> System.identityHashCode(proxy);
			case "equals" -> proxy == arguments[0];
			default -> throw unsupported(method);
		};
	}

	private static UnsupportedOperationException unsupported(Method method) {
		return new UnsupportedOperationException("Unsupported fixture economy call: " + method.toGenericString());
	}

	private static <T> T proxy(Class<T> service, java.lang.reflect.InvocationHandler handler) {
		return service.cast(Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[]{service}, handler));
	}

	private record Request(String caller, UUID playerId, BigDecimal amount) {

		private static Request from(Method method, Object[] arguments) {
			if (arguments == null || arguments.length != 3
					|| arguments[0] == null || arguments[1] == null || arguments[2] == null
					|| method.getParameterTypes()[0] != String.class
					|| method.getParameterTypes()[1] != UUID.class
					|| method.getParameterTypes()[2] != BigDecimal.class) {
				throw unsupported(method);
			}
			BigDecimal amount = BigDecimal.class.cast(arguments[2]);
			if (amount.signum() < 0) {
				throw new IllegalArgumentException("amount must be non-negative");
			}
			return new Request(
					String.class.cast(arguments[0]),
					UUID.class.cast(arguments[1]),
					amount);
		}
	}
}
