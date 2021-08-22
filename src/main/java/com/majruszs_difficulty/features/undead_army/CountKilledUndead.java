package com.majruszs_difficulty.features.undead_army;

import com.majruszs_difficulty.Instances;
import com.majruszs_difficulty.RegistryHandler;
import com.mlib.damage.DamageHelper;
import com.mlib.nbt.NBTHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Counts killed undead during the Undead Army starts the Undead Army if all conditions were met. */
@Mod.EventBusSubscriber
public class CountKilledUndead {
	@SubscribeEvent
	public static void onUndeadKill( LivingDeathEvent event ) {
		LivingEntity entity = event.getEntityLiving();
		Player player = DamageHelper.getPlayerFromDamageSource( event.getSource() );
		if( !isValidEntity( entity ) || player == null )
			return;

		NBTHelper.setNBTInteger( player, UndeadArmyKeys.KILLED, kills->kills + 1 );
		spawnArmyIfPossible( player );
	}

	/** Checks whether given entity should count towards Undead Army kill counter. */
	private static boolean isValidEntity( LivingEntity entity ) {
		return entity.level instanceof ServerLevel && entity.getMobType() == MobType.UNDEAD;
	}

	/** Spawns Undead Army at player's position if player met all the requirements. */
	private static void spawnArmyIfPossible( Player player ) {
		UndeadArmyConfig config = Instances.UNDEAD_ARMY_CONFIG;
		UndeadArmyManager armyManager = RegistryHandler.UNDEAD_ARMY_MANAGER;

		if( NBTHelper.getNBTInteger( player, UndeadArmyKeys.KILLED ) >= config.getRequiredKills() && player.level instanceof ServerLevel )
			if( armyManager != null && armyManager.tryToSpawn( player ) )
				NBTHelper.setNBTInteger( player, UndeadArmyKeys.KILLED, 0 );
	}
}
