package com.airdropmc.internal.drop;

import com.airdropmc.api.DropHandle;
import com.airdropmc.api.DropOutcome;
import com.airdropmc.api.DropRequestDescriptor;
import com.airdropmc.api.DropSpawnResult;
import com.airdropmc.api.ResolvedDropContext;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Plugin-owned completion source behind a supported {@link DropHandle}. */
@ApiStatus.Internal
public final class DefaultDropHandle implements DropHandle {

	private final DropRequestDescriptor descriptor;
	private final CompletableFuture<DropSpawnResult> spawn = new CompletableFuture<>();
	private final CompletableFuture<DropOutcome> outcome = new CompletableFuture<>();
	private final CompletionStage<DropSpawnResult> spawnView = spawn.minimalCompletionStage();
	private final CompletionStage<DropOutcome> outcomeView = outcome.minimalCompletionStage();
	private volatile ResolvedDropContext context;

	public DefaultDropHandle(DropRequestDescriptor descriptor) {
		this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
	}

	@Override
	public UUID requestId() {
		return descriptor.requestId();
	}

	@Override
	public DropRequestDescriptor descriptor() {
		return descriptor;
	}

	@Override
	public Optional<ResolvedDropContext> context() {
		return Optional.ofNullable(context);
	}

	@Override
	public CompletionStage<DropSpawnResult> spawn() {
		return spawnView;
	}

	@Override
	public CompletionStage<DropOutcome> outcome() {
		return outcomeView;
	}

	public synchronized void publishContext(ResolvedDropContext resolvedContext) {
		ResolvedDropContext required = Objects.requireNonNull(resolvedContext, "resolvedContext");
		if (!requestId().equals(required.descriptor().requestId())) {
			throw new IllegalArgumentException("Context must belong to this request");
		}
		if (context != null && context != required) {
			throw new IllegalStateException("Request context is already published");
		}
		context = required;
	}

	public synchronized boolean completeSpawned(DropSpawnResult.Spawned result) {
		Objects.requireNonNull(result, "result");
		if (!requestId().equals(result.requestId()) || spawn.isDone() || outcome.isDone()) {
			return false;
		}
		publishContext(result.resolvedContext());
		return spawn.complete(result);
	}

	public synchronized boolean completeNotSpawned(DropOutcome terminalOutcome) {
		DropOutcome required = Objects.requireNonNull(terminalOutcome, "terminalOutcome");
		if (!requestId().equals(required.requestId()) || spawn.isDone() || outcome.isDone()) {
			return false;
		}
		required.context().ifPresent(this::publishContext);
		spawn.complete(new DropSpawnResult.NotSpawned(required));
		return outcome.complete(required);
	}

	public synchronized boolean completeOutcome(DropOutcome terminalOutcome) {
		DropOutcome required = Objects.requireNonNull(terminalOutcome, "terminalOutcome");
		if (!requestId().equals(required.requestId()) || !spawn.isDone() || outcome.isDone()) {
			return false;
		}
		required.context().ifPresent(this::publishContext);
		return outcome.complete(required);
	}
}
