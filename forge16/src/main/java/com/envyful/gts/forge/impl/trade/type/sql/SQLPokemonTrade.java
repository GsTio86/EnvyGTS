package com.envyful.gts.forge.impl.trade.type.sql;

import com.envyful.api.database.sql.SqlType;
import com.envyful.gts.forge.EnvyGTSForge;
import com.envyful.gts.forge.impl.trade.type.PokemonTrade;
import com.envyful.gts.forge.player.SQLGTSAttributeAdapter;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SQLPokemonTrade extends PokemonTrade {

    public SQLPokemonTrade(String tradeId, UUID owner, String ownerName, String originalOwnerName, double cost, long expiry, Pokemon pokemon, boolean removed, boolean purchased) {
        super(tradeId, owner, ownerName, originalOwnerName, cost, expiry, pokemon, removed, purchased);
    }

    @Override
    public void delete() {
        notifyTradeStatus("REMOVED");
        EnvyGTSForge.getDatabase().update(SQLGTSAttributeAdapter.REMOVE_TRADE)
                .data(SqlType.text(this.tradeId))
                .execute();
    }

    @Override
    public void save() {
        EnvyGTSForge.getDatabase().update(SQLGTSAttributeAdapter.ADD_TRADE)
                .data(
                        SqlType.text(this.tradeId),
                        SqlType.text(this.owner.toString()),
                        SqlType.text(this.ownerName),
                        SqlType.text(this.originalOwnerName),
                        SqlType.bigInt(this.expiry),
                        SqlType.decimal(this.cost),
                        SqlType.integer(this.removed ? 1 : 0),
                        SqlType.text("INSTANT_BUY"),
                        SqlType.text("p"),
                        SqlType.text(this.getPokemonJson()),
                        SqlType.integer(0)
                )
                .execute();
    }

    @Override
    protected CompletableFuture<Void> setRemoved() {
        setRemoved(true);
        notifyTradeStatus("UPDATE_STATUS", "removed");

        return EnvyGTSForge.getDatabase().update(SQLGTSAttributeAdapter.UPDATE_REMOVED)
                .data(
                        SqlType.integer(1),
                        SqlType.integer(this.purchased ? 1 : 0),
                        SqlType.text(this.tradeId)
                )
                .executeAsync().thenRun(() -> {});
    }


    @Override
    protected void updateOwner(UUID newOwner, String newOwnerName) {
        this.owner = newOwner;
        this.ownerName = newOwnerName;

        EnvyGTSForge.getDatabase().update(SQLGTSAttributeAdapter.UPDATE_OWNER)
                .data(
                        SqlType.text(newOwner.toString()),
                        SqlType.text(newOwnerName),
                        SqlType.text(this.tradeId)
                )
                .executeAsync();
    }
}
