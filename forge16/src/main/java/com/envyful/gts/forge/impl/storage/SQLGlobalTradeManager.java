package com.envyful.gts.forge.impl.storage;

import com.envyful.api.database.impl.redis.Subscribe;
import com.envyful.api.forge.chat.UtilChatColour;
import com.envyful.api.forge.player.util.UtilPlayer;
import com.envyful.api.player.EnvyPlayer;
import com.envyful.gts.api.Trade;
import com.envyful.gts.api.utils.TradeIDUtils;
import com.envyful.gts.forge.EnvyGTSForge;
import com.envyful.gts.forge.impl.ForgeGlobalTradeManager;
import com.envyful.gts.forge.impl.TradeFactory;
import com.envyful.gts.forge.player.SQLGTSAttributeAdapter;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class SQLGlobalTradeManager extends ForgeGlobalTradeManager {

    public SQLGlobalTradeManager() {
        EnvyGTSForge.getDatabase().query(SQLGTSAttributeAdapter.GET_ALL_TRADES)
                .converter(resultSet -> this.activeTrades.add(TradeFactory.fromResultSet(resultSet)))
                .executeWithConverter();
    }

    @Subscribe("trade_update_channel")
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
                    ServerPlayerEntity target = UtilPlayer.getOnlinePlayer(UUID.fromString(parts[3]));
                    if (target == null) return;
                    target.sendMessage(UtilChatColour.colour(
                        EnvyGTSForge.getLocale().getMessages().getItemWasPurchased()
                            .replace("%item%", parts[5])
                            .replace("%buyer%", parts[4])
                            .replace("%tax%", parts[6])
                            .replace("%price%", parts[7])
                    ), Util.NIL_UUID);
                    break;
                case "NEW":
                    try (Connection connection = EnvyGTSForge.getDatabase().getConnection();
                         PreparedStatement preparedStatement = connection.prepareStatement(SQLGTSAttributeAdapter.GET_TRADE_BY_ID)) {
                        preparedStatement.setString(1, tradeId);
                        ResultSet resultSet = preparedStatement.executeQuery();

                        if (resultSet.next()) {
                            Trade newTrade = TradeFactory.fromResultSet(resultSet);
                            this.activeTrades.add(newTrade);
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }
    }

    @Override
    public boolean addTrade(EnvyPlayer<?> player, Trade trade) {
        if (!super.addTrade(player, trade)) {
            return false;
        }

        trade.save();

        String notificationMessage = String.format("%s:%s:%s", TradeIDUtils.SERVER_IDENTIFIER, trade.getTradeId(), "NEW");
        EnvyGTSForge.getRedisDatabase().publish("trade_update_channel", notificationMessage);
        return true;
    }
}
