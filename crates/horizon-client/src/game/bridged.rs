//! Bridged NeoForge block entities in the world that simulates (`horizon_client::bridge`): their
//! ticks, empty-hand right-clicks and the mods' commands.
use super::Game;
use horizon_client::{block_entities::Pos, containers::BlockEntity, text::Text};

impl Game<'_> {
    /// Puts every bridged entity of this world that the JVM doesn't have yet into it, so a
    /// command right after `/setblock` or a load finds it (vanilla's block entity exists at once).
    fn sync_bridged(&mut self) {
        for (pos, entity) in self.world.block_entities.iter_mut() {
            if let BlockEntity::Bridged(b) = entity {
                horizon_client::bridge::sync(b, *pos);
                self.world.modified.insert((pos.0 >> 4, pos.2 >> 4));
            }
        }
    }
    /// `BlockEntityTicker.tick` of the bridged entity at `pos`.
    pub(super) fn tick_bridged(&mut self, pos: Pos) {
        if let Some(BlockEntity::Bridged(b)) = self.world.block_entities.get_mut(&pos) {
            horizon_client::bridge::sync(b, pos);
            horizon_client::bridge::tick(pos);
            // shortcut: the chunk is saved as changed after every tick, not only when the mod's
            // `setChanged` says so; upgrade when the stand-in reports it.
            self.world.modified.insert((pos.0 >> 4, pos.2 >> 4));
        }
    }
    /// `Block.useWithoutItem` of a bridged block, with an empty hand: true when there is one.
    pub(super) fn use_bridged(&mut self, at: [i32; 3]) -> bool {
        let pos = (at[0], at[1], at[2]);
        let Some(BlockEntity::Bridged(b)) = self.world.block_entities.get_mut(&pos) else {
            return false;
        };
        horizon_client::bridge::sync(b, pos);
        self.world.modified.insert((pos.0 >> 4, pos.2 >> 4));
        for line in horizon_client::bridge::use_block(pos) {
            self.system_message(Text::literal(&line));
        }
        true
    }
    /// A bridged mod's command (`RegisterCommandsEvent`): true when one handled it.
    pub(super) fn bridged_command(&mut self, command: &str) -> bool {
        self.sync_bridged();
        match horizon_client::bridge::command(command) {
            None => false,
            Some(Ok(lines)) => {
                for line in lines {
                    self.system_message(Text::literal(&line));
                }
                true
            }
            Some(Err(e)) => {
                self.command_failure(Text::literal(&e));
                true
            }
        }
    }
}
