package com.majruszsdifficulty.effects;

import com.majruszsdifficulty.Registries;
import com.majruszsdifficulty.gamemodifiers.configs.BleedingConfig;
import com.majruszsdifficulty.gamemodifiers.contexts.OnBleedingCheck;
import com.majruszsdifficulty.gui.BleedingGui;
import com.mlib.EquipmentSlots;
import com.mlib.Utility;
import com.mlib.annotations.AutoInstance;
import com.mlib.config.StringListConfig;
import com.mlib.effects.ParticleHandler;
import com.mlib.gamemodifiers.Condition;
import com.mlib.gamemodifiers.GameModifier;
import com.mlib.gamemodifiers.contexts.*;
import com.mlib.mobeffects.MobEffectHelper;
import com.mlib.text.TextHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class BleedingEffect extends MobEffect {
	public static final ParticleHandler PARTICLES = new ParticleHandler( Registries.BLOOD, ()->new Vec3( 0.125, 0.5, 0.125 ), ParticleHandler.speed( 0.05f ) );
	static Supplier< Boolean > IS_ENABLED = ()->true;
	static Supplier< Integer > GET_AMPLIFIER = ()->0;
	static Function< EquipmentSlot, Float > GET_ARMOR_MULTIPLIER = slot->1.0f;

	public static boolean isEnabled() {
		return IS_ENABLED.get();
	}

	public static int getAmplifier() {
		return GET_AMPLIFIER.get();
	}

	public BleedingEffect() {
		super( MobEffectCategory.HARMFUL, 0xffdd5555 );
	}

	@Override
	public void applyEffectTick( LivingEntity entity, int amplifier ) {
		if( entity.getEffect( this ) instanceof MobEffectInstance effectInstance ) {
			Vec3 motion = entity.getDeltaMovement();
			entity.hurt( new EntityBleedingDamageSource( effectInstance.damageSourceEntity ), 1.0f );
			entity.setDeltaMovement( motion ); // sets previous motion to avoid any knockback from bleeding
		} else {
			entity.hurt( Registries.BLEEDING_SOURCE, 1.0f );
		}

		if( entity instanceof Player player ) {
			addBloodOnScreen( player, 3 );
		}
	}

	@Override
	public void applyInstantenousEffect( @Nullable Entity source, @Nullable Entity indirectSource, LivingEntity entity,
		int amplifier, double health
	) {}

	@Override
	public boolean isDurationEffectTick( int duration, int amplifier ) {
		int cooldown = Math.max( 4, Utility.secondsToTicks( 4.0 ) >> amplifier );

		return duration % cooldown == 0;
	}

	@Override
	public List< ItemStack > getCurativeItems() {
		return new ArrayList<>(); // removes the default milk bucket from curative items
	}

	private static void addBloodOnScreen( Player player, int count ) {
		DistExecutor.unsafeRunWhenOn( Dist.CLIENT, ()->()->{
			if( player == Minecraft.getInstance().player ) {
				BleedingGui.addBloodOnScreen( count );
			}
		} );
	}

	/** Bleeding damage source that stores information about the causer of bleeding. (required for converting villager to zombie villager etc.) */
	public static class EntityBleedingDamageSource extends DamageSource {
		@Nullable protected final Entity damageSourceEntity;

		public EntityBleedingDamageSource( @Nullable Entity damageSourceEntity ) {
			super( Registries.BLEEDING_SOURCE.msgId );
			this.bypassArmor();

			this.damageSourceEntity = damageSourceEntity;
		}

		@Nullable
		@Override
		public Entity getDirectEntity() {
			return null;
		}

		@Nullable
		@Override
		public Entity getEntity() {
			return this.damageSourceEntity;
		}
	}

	/** Bleeding effect instance that stores information about the causer of bleeding. (required for converting villager to zombie villager etc.) */
	public static class MobEffectInstance extends net.minecraft.world.effect.MobEffectInstance {
		@Nullable protected final Entity damageSourceEntity;

		public MobEffectInstance( int duration, int amplifier, boolean ambient, @Nullable LivingEntity attacker ) {
			super( Registries.BLEEDING.get(), duration, amplifier, ambient, false, true );
			this.damageSourceEntity = attacker;
		}
	}

	@AutoInstance
	public static class Bleeding extends GameModifier {
		static final String ATTRIBUTE_ID = "effect.majruszsdifficulty.bleeding.armor_tooltip";
		final StringListConfig immuneMobs = new StringListConfig( "minecraft:skeleton_horse", "minecraft:zombie_horse" );
		final BleedingConfig effect = new BleedingConfig();

		public Bleeding() {
			super( Registries.Modifiers.DEFAULT );

			new OnEntityTick.Context( this::spawnParticles )
				.addCondition( new Condition.IsServer<>() )
				.addCondition( new Condition.Cooldown< OnEntityTick.Data >( 0.25, Dist.DEDICATED_SERVER ).configurable( false ) )
				.addCondition( new Condition.HasEffect<>( Registries.BLEEDING ) )
				.addCondition( data->data.entity instanceof LivingEntity )
				.insertTo( this );

			new OnDeath.Context( this::spawnParticles )
				.addCondition( new Condition.IsServer<>() )
				.addCondition( new Condition.HasEffect<>( Registries.BLEEDING ) )
				.insertTo( this );

			new OnEffectApplicable.Context( this::cancelEffect )
				.addCondition( data->!BleedingEffect.isEnabled() )
				.addCondition( data->data.effect.equals( Registries.BLEEDING_IMMUNITY.get() ) )
				.insertTo( this );

			new OnDamaged.Context( this::applyBleeding )
				.addCondition( new Condition.IsServer<>() )
				.addCondition( new ExcludableBleeding() )
				.addCondition( new ArmorDependentBleedingChance( 0.8, 0.6, 0.7, 0.9 ) )
				.addCondition( this::isNotImmune )
				.addCondition( OnDamaged.DEALT_ANY_DAMAGE )
				.addConfig( this.immuneMobs
					.name( "immune_mobs" )
					.comment( "Specifies which mobs should not be affected by Bleeding (all undead mobs are immune by default)." )
				).addConfig( this.effect )
				.insertTo( this );

			new OnItemAttributeTooltip.Context( this::addChanceTooltip )
				.addCondition( data->data.item instanceof ArmorItem )
				.addCondition( data->BleedingEffect.isEnabled() )
				.insertTo( this );

			GET_AMPLIFIER = this.effect::getAmplifier;

			this.name( "Bleeding" ).comment( "Common config for all Bleeding effects." );
		}

		private void spawnParticles( OnEntityTick.Data data ) {
			this.spawnParticles( data.level, data.entity, MobEffectHelper.getAmplifier( ( LivingEntity )data.entity, Registries.BLEEDING.get() ) + 3 );
		}

		private void spawnParticles( OnDeath.Data data ) {
			assert data.entity != null;

			this.spawnParticles( data.level, data.entity, 100 );
		}

		private void spawnParticles( ServerLevel level, Entity entity, int amountOfParticles ) {
			Vec3 position = new Vec3( entity.getX(), entity.getY( 0.5 ), entity.getZ() );
			PARTICLES.spawn( level, position, amountOfParticles );
		}

		private void cancelEffect( OnEffectApplicable.Data data ) {
			data.event.setResult( Event.Result.DENY );
		}

		private void applyBleeding( OnDamaged.Data data ) {
			OnBleedingCheck.Data bleedingData = new OnBleedingCheck.Data( data.event );
			OnBleedingCheck.Context.accept( bleedingData );
			if( bleedingData.isEffectTriggered() ) {
				this.effect.apply( data );
			}
		}

		private boolean isNotImmune( OnDamaged.Data data ) {
			return !this.immuneMobs.contains( Utility.getRegistryString( data.target.getType() ) );
		}

		private void addChanceTooltip( OnItemAttributeTooltip.Data data ) {
			for( EquipmentSlot slot : EquipmentSlots.ARMOR ) {
				if( !data.itemStack.getAttributeModifiers( slot ).containsKey( Attributes.ARMOR ) )
					continue;

				String multiplier = TextHelper.minPrecision( GET_ARMOR_MULTIPLIER.apply( slot ) );
				data.add( slot, Component.translatable( ATTRIBUTE_ID, multiplier ).withStyle( ChatFormatting.BLUE ) );
			}
		}

		private static class ArmorDependentBleedingChance extends Condition.ArmorDependentChance< OnDamaged.Data > {
			ArmorDependentBleedingChance( double headChance, double chestChance, double legsChance, double feetChance ) {
				super( headChance, chestChance, legsChance, feetChance );

				GET_ARMOR_MULTIPLIER = slot->this.multipliers.get( slot ).asFloat();
			}
		}

		private static class ExcludableBleeding extends Condition.Excludable< OnDamaged.Data > {
			ExcludableBleeding() {
				super();

				IS_ENABLED = this.availability::isEnabled;
			}
		}
	}
}
