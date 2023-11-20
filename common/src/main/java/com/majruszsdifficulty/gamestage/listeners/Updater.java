package com.majruszsdifficulty.gamestage.listeners;

import com.majruszsdifficulty.gamestage.GameStage;
import com.majruszsdifficulty.gamestage.GameStageHelper;
import com.majruszlibrary.annotation.AutoInstance;
import com.majruszlibrary.contexts.OnDimensionChanged;
import com.majruszlibrary.contexts.OnEntityDied;
import com.majruszlibrary.math.AnyPos;
import com.majruszlibrary.registry.Registries;

@AutoInstance
public class Updater {
	public Updater() {
		OnDimensionChanged.listen( this::tryToChangeGameStage );

		OnEntityDied.listen( this::tryToChangeGameStage );
	}

	private void tryToChangeGameStage( OnDimensionChanged data ) {
		for( GameStage gameStage : GameStageHelper.getGameStages() ) {
			if( gameStage.checkDimension( data.current.dimension().location().toString() ) ) {
				GameStageHelper.increaseGlobalGameStage( gameStage );
				if( GameStageHelper.isPerPlayerDifficultyEnabled() ) {
					GameStageHelper.increaseGameStage( gameStage, data.player );
				}
			}
		}
	}

	private void tryToChangeGameStage( OnEntityDied data ) {
		for( GameStage gameStage : GameStageHelper.getGameStages() ) {
			if( gameStage.checkEntity( Registries.get( data.target.getType() ).toString() ) ) {
				GameStageHelper.increaseGlobalGameStage( gameStage );
				if( GameStageHelper.isPerPlayerDifficultyEnabled() ) {
					data.getLevel()
						.players()
						.stream()
						.filter( player->AnyPos.from( player.position() ).dist( data.target.position() ).floatValue() < 128.0f )
						.forEach( player->GameStageHelper.increaseGameStage( gameStage, player ) );
				}
			}
		}
	}
}
