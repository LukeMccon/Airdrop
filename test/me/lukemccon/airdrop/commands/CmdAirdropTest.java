package me.lukemccon.airdrop.commands;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;


public class CmdAirdropTest {
	private CommandSender sender;
	private CmdAirdrop cmdAirdrop;
	@BeforeEach
	void setUp() throws Exception {
		cmdAirdrop = new CmdAirdrop();
		sender = mock(CommandSender.class);
	}

	@Test
	void should_Fail_With_Invalid_Player_Supplied() {
		
		String[] args = {"gift"};
		Boolean result = cmdAirdrop.onCommand(sender, null, null, args);
		assertFalse(result);		
//		when(Bukkit.getPluginManager()).thenReturn(null);
		
	}
	
	@Test
	void should_Fail_With_Invalid_Args() {
		sender = mock(Player.class);
		String[] args = {""};
		boolean result = cmdAirdrop.onCommand(sender, null, null, args);
		assertFalse(result);				
	}
	
	@Test
	void should_Pass_With_Valid_Args() {
		sender = mock(Player.class);
		String[] args = {"packages"};
		boolean result = cmdAirdrop.onCommand(sender, null, null, args);
		assertTrue(result);				
	}
}
