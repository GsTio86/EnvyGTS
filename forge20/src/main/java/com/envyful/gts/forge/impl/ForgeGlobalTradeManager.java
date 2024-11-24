package com.envyful.gts.forge.impl;

import com.envyful.api.database.sql.SqlType;
import com.envyful.api.forge.chat.UtilChatColour;
import com.envyful.api.forge.player.ForgeEnvyPlayer;
import com.envyful.api.forge.player.util.UtilPlayer;
import com.envyful.api.player.EnvyPlayer;
import com.envyful.api.sqlite.config.SQLiteDatabaseDetailsConfig;
import com.envyful.gts.api.GlobalTradeManager;
import com.envyful.gts.api.Trade;
import com.envyful.gts.api.utils.TradeIDUtils;
import com.envyful.gts.forge.EnvyGTSForge;
import com.envyful.gts.forge.event.TradeCreateEvent;
import com.envyful.gts.forge.player.GTSAttribute;
import com.envyful.gts.forge.player.SQLGTSAttributeAdapter;
import com.envyful.gts.forge.player.SQLiteGTSAttributeAdapter;
import com.google.common.collect.Lists;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public abstract class ForgeGlobalTradeManager implements GlobalTradeManager {

    protected final List<Trade> activeTrades = Collections.synchronizedList(new ArrayList<>());

    public ForgeGlobalTradeManager() {
    }

    @Override
    public void syncTrade(String channel, String message) {
        String[] parts = message.split(":");
        if (parts.length < 3) {
            return;
        }
        String identifier = parts[0];

        if (identifier.equals(TradeIDUtils.SERVER_IDENTIFIER)) {
            return;
        }

        String tradeId = parts[1];
        String action = parts[2];

        synchronized (this.activeTrades) {
            switch (action) {
                case "PURCHASED":
                case "REMOVED":
                    this.activeTrades.removeIf(trade -> trade.getTradeId().equals(tradeId));
                    break;
                case "UPDATE_STATUS":
                    String status = parts[3];
                    Trade trade = this.activeTrades.stream()
                        .filter(t -> t.getTradeId().equals(tradeId))
                        .findFirst().orElse(null);
                    if (trade == null) {
                        return;
                    }
                    if (status.equalsIgnoreCase("purchased")) {
                        trade.setPurchased(true);
                    } else if (status.equalsIgnoreCase("removed")) {
                        trade.setRemoved(true);
                    }
                    break;
                case "WAS_PURCHASED":
                    ServerPlayer target = UtilPlayer.getOnlinePlayer(UUID.fromString(parts[3]));
                    if (target == null) return;
                    target.sendSystemMessage(UtilChatColour.colour(
                        EnvyGTSForge.getLocale().getMessages().getItemWasPurchased()
                            .replace("%item%", parts[5])
                            .replace("%buyer%", parts[4])
                            .replace("%tax%", parts[6])
                            .replace("%price%", parts[7])
                    ));
                    break;
                case "NEW":
                    String query = EnvyGTSForge.getPlayerManager().getSaveManager().getSaveMode().equals(SQLiteDatabaseDetailsConfig.ID)
                        ? SQLiteGTSAttributeAdapter.GET_TRADE_BY_ID : SQLGTSAttributeAdapter.GET_TRADE_BY_ID;
                    EnvyGTSForge.getDatabase().query(query)
                        .data(SqlType.text(tradeId))
                        .converter(resultSet -> this.activeTrades.add(TradeFactory.fromResultSet(resultSet)))
                        .executeAsyncWithConverter();
                    break;
            }
        }
    }

    @Override
    public boolean addTrade(EnvyPlayer<?> player, Trade trade) {
        GTSAttribute attribute = ((ForgeEnvyPlayer) player).getAttributeNow(GTSAttribute.class);

        if (attribute == null) {
            return false;
        }

        TradeCreateEvent event = new TradeCreateEvent((ForgeEnvyPlayer)player, trade);
        MinecraftForge.EVENT_BUS.post(event);

        if (event.isCanceled()) {
            return false;
        }

        this.activeTrades.add(trade);
        attribute.getOwnedTrades().add(trade);
        return true;
    }

    @Override
    public void removeTrade(Trade trade) {
        this.activeTrades.remove(trade);
    }

    @Override
    public List<Trade> getAllTrades() {
        return Lists.newArrayList(this.activeTrades);
    }

    @Override
    public List<Trade> getUserTrades(EnvyPlayer<?> player) {
        GTSAttribute attribute = player.getAttributeNow(GTSAttribute.class);

        if (attribute == null) {
            return Collections.emptyList();
        }

        return attribute.getOwnedTrades();
    }

    @Override
    public List<Trade> getExpiredTrades(EnvyPlayer<?> player) {
        GTSAttribute attribute = player.getAttributeNow(GTSAttribute.class);

        if (attribute == null) {
            return Collections.emptyList();
        }

        List<Trade> expired = Lists.newArrayList();

        for (Trade ownedTrade : attribute.getOwnedTrades()) {
            if ((ownedTrade.hasExpired() || ownedTrade.wasRemoved()) && !ownedTrade.wasPurchased()) {
                expired.add(ownedTrade);
            }
        }

        return expired;
    }

    @Override
    public List<Trade> getPurchasedTrades(EnvyPlayer<?> player) {
        GTSAttribute attribute = player.getAttributeNow(GTSAttribute.class);

        if (attribute == null) {
            return Collections.emptyList();
        }

        List<Trade> purchased = Lists.newArrayList();

        for (Trade trade : attribute.getOwnedTrades()) {
            if (trade.wasPurchased()) {
                purchased.add(trade);
            }
        }

        return purchased;
    }
}
