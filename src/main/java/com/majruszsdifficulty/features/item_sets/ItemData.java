package com.majruszsdifficulty.features.item_sets;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;

public class ItemData {
	public final ICondition condition;
	public final INameGetter nameGetter;
	public final EquipmentSlot[] equipmentSlots;

	public ItemData( ICondition condition, INameGetter nameGetter, EquipmentSlot... equipmentSlots ) {
		this.condition = condition;
		this.nameGetter = nameGetter;
		this.equipmentSlots = equipmentSlots;
	}

	public ItemData( Item item, EquipmentSlot... equipmentSlots ) {
		this( itemStack -> item.equals( itemStack.getItem() ), () -> item.getDescription().copy(), equipmentSlots );
	}

	public ItemData( RegistryObject< ? extends Item > item, EquipmentSlot... equipmentSlots ) {
		this( itemStack -> item.get().equals( itemStack.getItem() ), () -> item.get().getDescription().copy(), equipmentSlots );
	}

	public boolean hasItemEquipped( Player player ) {
		for( EquipmentSlot equipmentSlot : this.equipmentSlots )
			if( isSetItemStack( player.getItemBySlot( equipmentSlot ) ) )
				return true;

		return false;
	}

	public boolean isSetItemStack( ItemStack itemStack ) {
		return this.condition.check( itemStack );
	}

	public MutableComponent getTranslatedName() {
		return this.nameGetter.getName();
	}

	public interface ICondition {
		boolean check( ItemStack itemStack );
	}

	public interface INameGetter {
		MutableComponent getName();
	}
}