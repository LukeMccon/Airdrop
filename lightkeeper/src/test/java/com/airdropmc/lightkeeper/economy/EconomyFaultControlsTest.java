package com.airdropmc.lightkeeper.economy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.airdropmc.lightkeeper.economy.EconomyOperationType.*;
import static com.airdropmc.lightkeeper.economy.EconomyFaultControls.Mode.*;

class EconomyFaultControlsTest {
	@Test
	void holdIsOneShotAccountAndOperationScoped() {
		try (var controls = new EconomyFaultControls()) {
			UUID player = UUID.randomUUID();
			controls.arm(player, WITHDRAW, HOLD);
			assertThat(controls.claim(UUID.randomUUID(), WITHDRAW).mode()).isEqualTo(NORMAL);
			assertThat(controls.claim(player, DEPOSIT).mode()).isEqualTo(NORMAL);
			var response = controls.claim(player, WITHDRAW);
			assertThat(response.confirmation()).isNotDone();
			assertThat(controls.claim(player, WITHDRAW).mode()).isEqualTo(NORMAL);
			assertThat(controls.release(player, DEPOSIT)).isFalse();
			assertThat(controls.release(player, WITHDRAW)).isTrue();
			assertThat(response.confirmation()).isCompleted();
			assertThat(controls.release(player, WITHDRAW)).isFalse();
		}
	}

	@Test
	void clearAndCloseSettleHoldsAndRemoveUnusedControls() {
		var controls = new EconomyFaultControls();
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		controls.arm(first, WITHDRAW, HOLD);
		controls.arm(first, DEPOSIT, REJECT);
		controls.arm(second, DEPOSIT, HOLD);
		var withdrawal = controls.claim(first, WITHDRAW);
		var refund = controls.claim(second, DEPOSIT);
		controls.clear(first);
		assertThat(withdrawal.confirmation()).isCompleted();
		assertThat(refund.confirmation()).isNotDone();
		assertThat(controls.claim(first, DEPOSIT).mode()).isEqualTo(NORMAL);
		controls.close();
		assertThat(refund.confirmation()).isCompleted();
		assertThatThrownBy(() -> controls.arm(first, WITHDRAW, HOLD)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> controls.claim(first, WITHDRAW)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void duplicateAndUnsupportedFaultsAreRejected() {
		try (var controls = new EconomyFaultControls()) {
			UUID player = UUID.randomUUID();
			assertThatThrownBy(() -> controls.arm(player, CAN_WITHDRAW, HOLD)).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> controls.arm(player, WITHDRAW, REJECT)).isInstanceOf(IllegalArgumentException.class);
			controls.arm(player, DEPOSIT, REJECT);
			assertThatThrownBy(() -> controls.arm(player, DEPOSIT, EXCEPTION)).isInstanceOf(IllegalArgumentException.class);
			assertThat(controls.claim(player, DEPOSIT).mode()).isEqualTo(REJECT);
			assertThat(controls.claim(player, DEPOSIT).mode()).isEqualTo(NORMAL);
		}
	}
}
